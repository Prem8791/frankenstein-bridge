"""Static source and assembled-product inventory collectors."""

from __future__ import annotations

from dataclasses import replace
from hashlib import sha256
import os
from pathlib import Path
import re
import subprocess
import tempfile
from typing import Iterable
import xml.etree.ElementTree as ET

from .model import (
    CollectionResult,
    CoverageEntry,
    EvidenceRef,
    SurfaceRecord,
    canonical_coverage,
    canonical_records,
    normalized_sha256,
)

PARTITIONS = ("root", "system", "system_ext", "product", "vendor", "odm")
TEXT_SUFFIXES = {
    ".java", ".kt", ".aidl", ".cpp", ".cc", ".c", ".h", ".hpp", ".rs",
    ".rc", ".te", ".cil", ".xml", ".mk", ".bp", ".prop", ".conf", ".sh",
}
SOURCE_LIMIT = 8 * 1024 * 1024
EXCLUDED_PARTS = {
    ".git", ".repo", "out", "test", "tests", "testdata", "test_data",
    "frankenstein_inventory",
}
PROTECTED_PATTERN = re.compile(
    r"""(?P<path>
    /(?:proc|sys|dev|metadata|cache/recovery|data/(?:tombstones|anr|misc/keystore|user))
    /[A-Za-z0-9_@%+.,:=~*/?\-\[\]]*
    )""",
    re.X,
)
PROPERTY_PATTERN = re.compile(
    r"""(?<![A-Za-z0-9_.-])((?:ro|persist|vendor|sys|init\.svc)\.[A-Za-z0-9_.-]+)"""
)
SOCKET_PATTERN = re.compile(r"""(?:^|["'\s])(@[A-Za-z0-9_.:/-]+)""")


def _is_binary_payload(data: bytes) -> bool:
    """Reject compiled artifacts stored below source-looking suffixes."""
    return b"\x00" in data[:4096]


def _decode(path: Path) -> str:
    data = path.read_bytes()
    if len(data) > SOURCE_LIMIT:
        raise ValueError(f"required source exceeds 8 MiB: {path}")
    if data.startswith((b"\xff\xfe", b"\xfe\xff")):
        return data.decode("utf-16")
    try:
        return data.decode("utf-8-sig")
    except UnicodeDecodeError as error:
        if b"\x00" in data:
            raise ValueError(f"undecodable required source: {path}") from error
        return data.decode("iso-8859-1")


def _source_evidence(location: str, line: int, unit: str, kind: str = "SOURCE") -> tuple[EvidenceRef, ...]:
    return (EvidenceRef(kind, location, line, normalized_sha256(unit)),)


def _strip_comments(source: str) -> str:
    result: list[str] = []
    index = 0
    in_string = ""
    while index < len(source):
        char = source[index]
        nxt = source[index + 1] if index + 1 < len(source) else ""
        if in_string:
            result.append(char)
            if char == "\\" and index + 1 < len(source):
                result.append(source[index + 1])
                index += 2
                continue
            if char == in_string:
                in_string = ""
            index += 1
            continue
        if char in {'"', "'"}:
            in_string = char
            result.append(char)
            index += 1
            continue
        if char == "/" and nxt == "/":
            result.extend("  ")
            index += 2
            while index < len(source) and source[index] != "\n":
                result.append(" ")
                index += 1
            continue
        if char == "/" and nxt == "*":
            result.extend("  ")
            index += 2
            while index < len(source):
                if index + 1 < len(source) and source[index:index + 2] == "*/":
                    result.extend("  ")
                    index += 2
                    break
                result.append("\n" if source[index] == "\n" else " ")
                index += 1
            continue
        result.append(char)
        index += 1
    return "".join(result)


CALL_PATTERN = re.compile(
    r"""(?P<call>
      (?:ServiceManager\.)?addService|
      publishBinderService|
      AServiceManager_addService|
      AServiceManager_registerLazyService|
      registerService|
      LocalServices\.(?:addService|getService)|
      LocalManagerRegistry\.(?:addManager|getManager)|
      (?:mSystemServiceManager|SystemServiceManager)\.startService
    )\s*\(""",
    re.X,
)


def _balanced_call(source: str, open_paren: int) -> tuple[str, int]:
    depth = 0
    string = ""
    index = open_paren
    while index < len(source):
        char = source[index]
        if string:
            if char == "\\":
                index += 2
                continue
            if char == string:
                string = ""
        elif char in {'"', "'"}:
            string = char
        elif char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return source[open_paren:index + 1], index + 1
        index += 1
    raise ValueError("unbalanced registration call")


def _first_string(call: str) -> str:
    match = re.search(r'"((?:\\.|[^"\\])*)"', call, re.S)
    return bytes(match.group(1), "utf-8").decode("unicode_escape") if match else ""


def _first_class(call: str) -> str:
    match = re.search(r"\b([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*)\.class\b", call)
    return match.group(1) if match else ""


def parse_java_native_registrations(
    source: str, location: str, partition: str = ""
) -> list[SurfaceRecord]:
    clean = _strip_comments(source)
    records: list[SurfaceRecord] = []
    for match in CALL_PATTERN.finditer(clean):
        call_name = match.group("call")
        open_paren = clean.find("(", match.start())
        call, _ = _balanced_call(clean, open_paren)
        line = clean.count("\n", 0, match.start()) + 1
        evidence = _source_evidence(location, line, call)
        class_name = _first_class(call)
        literal = _first_string(call)
        if "LocalServices.addService" in call_name:
            kind, name = "LOCAL_SERVICE_PRODUCER", class_name
        elif "LocalServices.getService" in call_name:
            kind, name = "LOCAL_SERVICE_CONSUMER", class_name
        elif "LocalManagerRegistry.addManager" in call_name:
            kind, name = "LOCAL_MANAGER_PRODUCER", class_name
        elif "LocalManagerRegistry.getManager" in call_name:
            kind, name = "LOCAL_MANAGER_CONSUMER", class_name
        elif "startService" in call_name:
            kind, name = "SYSTEM_SERVER_SERVICE", class_name or literal
        else:
            kind, name = "BINDER_SERVICE", literal
        if not name:
            continue
        records.append(
            SurfaceRecord(
                kind=kind,
                name=name,
                partition=partition,
                evidence=evidence,
            )
        )
    return records


def parse_init_file(source: str, location: str, partition: str) -> list[SurfaceRecord]:
    logical = re.sub(r"\\\r?\n\s*", " ", source)
    records: list[SurfaceRecord] = []
    for match in re.finditer(r"(?m)^\s*service\s+(\S+)\s+(\S+)([^\n]*)", logical):
        name, executable = match.group(1), match.group(2)
        line = logical.count("\n", 0, match.start()) + 1
        unit = match.group(0)
        records.append(
            SurfaceRecord(
                kind="INIT_SERVICE",
                name=name,
                partition=partition,
                owner=executable,
                evidence=_source_evidence(location, line, unit, "ASSEMBLED"),
            )
        )
    return records


def _xml_local(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def _child_text(element: ET.Element, name: str) -> str:
    for child in element:
        if _xml_local(child.tag) == name:
            return (child.text or "").strip()
    return ""


def parse_vintf_xml(source: str, location: str, partition: str) -> list[SurfaceRecord]:
    root = ET.fromstring(source)
    records: list[SurfaceRecord] = []
    for hal in root.iter():
        if _xml_local(hal.tag) != "hal":
            continue
        fmt = hal.attrib.get("format", "hidl")
        package = _child_text(hal, "name")
        versions = [
            (child.text or "").strip()
            for child in hal
            if _xml_local(child.tag) == "version"
        ]
        version = ",".join(filter(None, versions))
        transport = _child_text(hal, "transport")
        for interface in hal:
            if _xml_local(interface.tag) != "interface":
                continue
            interface_name = _child_text(interface, "name")
            instances = [
                (child.text or "").strip()
                for child in interface
                if _xml_local(child.tag) in {"instance", "regex-instance"}
            ]
            for instance in filter(None, instances):
                name = f"{package}.{interface_name}" if fmt == "aidl" else f"{package}::{interface_name}"
                records.append(
                    SurfaceRecord(
                        kind="VINTF_AIDL_INSTANCE" if fmt == "aidl" else "VINTF_HIDL_INSTANCE",
                        name=name,
                        instance=instance,
                        partition=partition,
                        owner=transport,
                        interface_version=version,
                        evidence=_source_evidence(location, 0, ET.tostring(hal, encoding="unicode"), "ASSEMBLED"),
                    )
                )
        for fqname in (
            (child.text or "").strip()
            for child in hal
            if _xml_local(child.tag) == "fqname"
        ):
            match = re.match(r"@([^:]+)::([^/]+)/(.+)", fqname)
            if match:
                records.append(
                    SurfaceRecord(
                        kind="VINTF_HIDL_INSTANCE",
                        name=f"{package}::{match.group(2)}",
                        instance=match.group(3),
                        partition=partition,
                        interface_version=match.group(1),
                        owner=transport,
                        evidence=_source_evidence(location, 0, fqname, "ASSEMBLED"),
                    )
                )
    return records


def parse_context_file(
    source: str, location: str, partition: str, kind: str
) -> list[SurfaceRecord]:
    records: list[SurfaceRecord] = []
    for line_number, raw in enumerate(source.splitlines(), 1):
        stripped = raw.strip()
        if not stripped or stripped.startswith("#"):
            continue
        parts = stripped.split()
        if len(parts) < 2:
            continue
        selector = " ".join(parts[:-1]) if kind in {"GENFS_CONTEXT", "SEAPP_CONTEXT"} else parts[0]
        label = parts[-1]
        records.append(
            SurfaceRecord(
                kind=kind,
                name=selector,
                partition=partition,
                selinux_context=label,
                evidence=_source_evidence(location, line_number, stripped, "ASSEMBLED"),
            )
        )
    return records


def parse_permissions_xml(source: str, location: str, partition: str) -> list[SurfaceRecord]:
    root = ET.fromstring(source)
    records: list[SurfaceRecord] = []
    for element in root.iter():
        tag = _xml_local(element.tag)
        if tag == "feature" and element.attrib.get("name"):
            records.append(
                SurfaceRecord(
                    kind="SYSTEM_FEATURE",
                    name=element.attrib["name"],
                    partition=partition,
                    evidence=_source_evidence(location, 0, ET.tostring(element, encoding="unicode"), "ASSEMBLED"),
                )
            )
        elif tag == "privapp-permissions":
            package = element.attrib.get("package", "")
            for child in element:
                child_tag = _xml_local(child.tag)
                permission = child.attrib.get("name", "")
                if package and permission and child_tag in {"permission", "deny-permission"}:
                    prefix = "deny:" if child_tag == "deny-permission" else "grant:"
                    records.append(
                        SurfaceRecord(
                            kind="PRIVAPP_PERMISSION_GRANT",
                            name=package,
                            instance=prefix + permission,
                            partition=partition,
                            evidence=_source_evidence(location, 0, ET.tostring(child, encoding="unicode"), "ASSEMBLED"),
                        )
                    )
    return records


def parse_sysconfig_xml(source: str, location: str, partition: str) -> list[SurfaceRecord]:
    root = ET.fromstring(source)
    records: list[SurfaceRecord] = []
    for element in root:
        tag = _xml_local(element.tag)
        identity = next(
            (element.attrib[key] for key in ("name", "package", "component", "library") if key in element.attrib),
            "",
        )
        if identity:
            records.append(
                SurfaceRecord(
                    kind="SYSCONFIG_ENTRY",
                    name=tag,
                    instance=identity,
                    partition=partition,
                    evidence=_source_evidence(location, 0, ET.tostring(element, encoding="unicode"), "ASSEMBLED"),
                )
            )
    return records


def _partition_roots(product_out: Path) -> list[tuple[str, Path]]:
    return [(partition, product_out / partition) for partition in PARTITIONS if (product_out / partition).exists()]


def _result(family: str, location: str, records: Iterable[SurfaceRecord], scanned: int) -> CollectionResult:
    canonical = tuple(canonical_records(records))
    return CollectionResult(
        canonical,
        (CoverageEntry(family, location, "COLLECTED" if scanned else "ABSENT", scanned, len(canonical)),),
    )


def collect_system_server(tree: Path) -> CollectionResult:
    path = tree / "frameworks/base/services/java/com/android/server/SystemServer.java"
    records = parse_java_native_registrations(_decode(path), str(path.relative_to(tree)), "system")
    records = [record for record in records if record.kind == "SYSTEM_SERVER_SERVICE"]
    return _result("SYSTEM_SERVER", str(path.relative_to(tree)), records, 1)


def _repo_projects(tree: Path) -> list[Path]:
    result = subprocess.run(
        ["repo", "list", "-p"], cwd=tree, text=True, capture_output=True, check=True
    )
    return [tree / line.strip() for line in result.stdout.splitlines() if line.strip()]


def _source_files(tree: Path) -> Iterable[Path]:
    for project in _repo_projects(tree):
        result = subprocess.run(
            ["git", "-C", str(project), "ls-files", "-co", "--exclude-standard", "-z"],
            text=False,
            capture_output=True,
            check=True,
        )
        for raw in result.stdout.split(b"\0"):
            if not raw:
                continue
            relative = Path(os.fsdecode(raw))
            path = project / relative
            try:
                tree_relative = path.relative_to(tree)
            except ValueError:
                continue
            if any(part.lower() in EXCLUDED_PARTS for part in tree_relative.parts):
                continue
            if path.suffix.lower() not in TEXT_SUFFIXES or not path.is_file():
                continue
            if path.stat().st_size > SOURCE_LIMIT:
                continue
            with path.open("rb") as source:
                if _is_binary_payload(source.read(4096)):
                    continue
            yield path


def collect_binder_registrations(tree: Path) -> CollectionResult:
    records: list[SurfaceRecord] = []
    scanned = 0
    for path in _source_files(tree):
        if path.suffix.lower() not in {".java", ".kt", ".cpp", ".cc", ".c", ".rs"}:
            continue
        text = _decode(path)
        if not any(token in text for token in ("addService", "publishBinderService", "registerService")):
            continue
        scanned += 1
        records.extend(parse_java_native_registrations(text, str(path.relative_to(tree))))
    records = [record for record in records if record.kind == "BINDER_SERVICE"]
    return _result("BINDER_REGISTRATIONS", "repo-manifest", records, scanned)


def collect_local_services(tree: Path) -> CollectionResult:
    records: list[SurfaceRecord] = []
    scanned = 0
    for path in _source_files(tree):
        if path.suffix.lower() not in {".java", ".kt"}:
            continue
        text = _decode(path)
        if "LocalServices." not in text and "LocalManagerRegistry." not in text:
            continue
        scanned += 1
        records.extend(parse_java_native_registrations(text, str(path.relative_to(tree)), "system"))
    records = [record for record in records if record.kind.startswith("LOCAL_")]
    return _result("LOCAL_SERVICES", "repo-manifest", records, scanned)


def collect_init_services(product_out: Path) -> CollectionResult:
    records: list[SurfaceRecord] = []
    paths: list[Path] = []
    for partition, root in _partition_roots(product_out):
        for path in sorted({*root.glob("etc/init/**/*.rc"), *root.glob("etc/init/*.rc")}):
            paths.append(path)
            records.extend(parse_init_file(_decode(path), str(path.relative_to(product_out)), partition))
    return _result("ASSEMBLED_INIT", "assembled-partitions", records, len(paths))


def collect_vintf(product_out: Path) -> CollectionResult:
    records: list[SurfaceRecord] = []
    paths: list[Path] = []
    for partition, root in _partition_roots(product_out):
        for path in sorted(root.glob("etc/vintf/**/*.xml")):
            paths.append(path)
            records.extend(parse_vintf_xml(_decode(path), str(path.relative_to(product_out)), partition))
    return _result("VINTF", "assembled-partitions", records, len(paths))


CONTEXT_NAMES = {
    "service_contexts": "SERVICE_CONTEXT",
    "hwservice_contexts": "HWSERVICE_CONTEXT",
    "vndservice_contexts": "VNDSERVICE_CONTEXT",
    "file_contexts": "FILE_CONTEXT",
    "genfs_contexts": "GENFS_CONTEXT",
    "property_contexts": "PROPERTY_CONTEXT",
    "seapp_contexts": "SEAPP_CONTEXT",
}


def collect_contexts(product_out: Path) -> CollectionResult:
    records: list[SurfaceRecord] = []
    paths: list[Path] = []
    for partition, root in _partition_roots(product_out):
        for path in sorted(root.glob("etc/selinux/**/*")):
            if not path.is_file():
                continue
            kind = next((value for key, value in CONTEXT_NAMES.items() if key in path.name), "")
            if not kind:
                continue
            paths.append(path)
            records.extend(parse_context_file(_decode(path), str(path.relative_to(product_out)), partition, kind))
    return _result("SELINUX_CONTEXTS", "assembled-partitions", records, len(paths))


def collect_framework_configuration(product_out: Path) -> CollectionResult:
    records: list[SurfaceRecord] = []
    paths: list[Path] = []
    for partition, root in _partition_roots(product_out):
        for pattern, parser in (
            ("etc/permissions/*.xml", parse_permissions_xml),
            ("etc/sysconfig/*.xml", parse_sysconfig_xml),
        ):
            for path in sorted(root.glob(pattern)):
                paths.append(path)
                records.extend(parser(_decode(path), str(path.relative_to(product_out)), partition))
    return _result("FRAMEWORK_CONFIGURATION", "assembled-partitions", records, len(paths))


def collect_apex_surfaces(tree: Path, product_out: Path) -> CollectionResult:
    records: list[SurfaceRecord] = []
    payloads = sorted(product_out.glob("*/apex/*.apex")) + sorted(product_out.glob("*/apex/*.capex"))
    host = tree / "out/host/linux-x86"
    deapexer = host / "bin/deapexer"
    if payloads and not deapexer.exists():
        raise ValueError("installed APEX payloads exist but deapexer is absent")
    for payload in payloads:
        partition = payload.relative_to(product_out).parts[0]
        module = payload.stem
        records.append(
            SurfaceRecord(
                kind="APEX_MODULE",
                name=module,
                partition=partition,
                evidence=_source_evidence(str(payload.relative_to(product_out)), 0, sha256(payload.read_bytes()).hexdigest(), "ASSEMBLED"),
            )
        )
        with tempfile.TemporaryDirectory(prefix="frankenstein-apex-", dir="/tmp") as temporary:
            temp = Path(temporary)
            normalized = temp / f"{module}.apex"
            environment = dict(os.environ, ANDROID_HOST_OUT=str(host))
            decompress = subprocess.run(
                [str(deapexer), "decompress", "--input", str(payload), "--output", str(normalized), "--copy-if-uncompressed"],
                text=True, capture_output=True, env=environment,
            )
            if decompress.returncode != 0:
                normalized = payload
            extracted = temp / "extracted"
            result = subprocess.run(
                [str(deapexer), "extract", str(normalized), str(extracted)],
                text=True, capture_output=True, env=environment,
            )
            if result.returncode != 0:
                raise ValueError(f"cannot inspect APEX {module}: {result.stderr.strip()}")
            for path in extracted.glob("etc/init/**/*.rc"):
                records.extend(parse_init_file(_decode(path), f"apex:{module}/{path.relative_to(extracted)}", f"apex:{module}"))
            for path in extracted.glob("etc/permissions/*.xml"):
                records.extend(parse_permissions_xml(_decode(path), f"apex:{module}/{path.relative_to(extracted)}", f"apex:{module}"))
            for path in extracted.glob("etc/sysconfig/*.xml"):
                records.extend(parse_sysconfig_xml(_decode(path), f"apex:{module}/{path.relative_to(extracted)}", f"apex:{module}"))
    return _result("APEX", "assembled-apex", records, len(payloads))


def collect_overlays(tree: Path, product_out: Path) -> CollectionResult:
    records: list[SurfaceRecord] = []
    payloads: list[tuple[str, Path]] = []
    aapt2 = tree / "out/host/linux-x86/bin/aapt2"
    for partition, root in _partition_roots(product_out):
        payloads.extend((partition, path) for path in root.glob("overlay/**/*.apk"))
    for partition, payload in payloads:
        result = subprocess.run(
            [str(aapt2), "dump", "xmltree", "--file", "AndroidManifest.xml", str(payload)],
            text=True, capture_output=True,
        )
        if result.returncode != 0:
            raise ValueError(f"cannot inspect overlay {payload}: {result.stderr.strip()}")
        package = ""
        target = ""
        for line in result.stdout.splitlines():
            package_match = re.search(r"\bpackage(?:\(.*\))?=\"([^\"]+)\"", line)
            target_match = re.search(r"\btargetPackage(?:\(.*\))?=\"([^\"]+)\"", line)
            if package_match:
                package = package_match.group(1)
            if target_match:
                target = target_match.group(1)
        if not package:
            package = payload.stem
        records.append(
            SurfaceRecord(
                kind="OVERLAY_PACKAGE",
                name=package,
                instance=target,
                partition=partition,
                evidence=(EvidenceRef("ASSEMBLED", str(payload.relative_to(product_out)), 0, sha256(payload.read_bytes()).hexdigest()),),
            )
        )
    return _result("OVERLAYS", "assembled-overlays", records, len(payloads))


def collect_source_references(tree: Path) -> CollectionResult:
    records: list[SurfaceRecord] = []
    scanned = 0
    for path in _source_files(tree):
        text = _decode(path)
        scanned += 1
        records.extend(_reference_records(text, str(path.relative_to(tree))))
    return _result("PROTECTED_REFERENCES", "repo-manifest", records, scanned)


def _reference_records(text: str, location: str) -> list[SurfaceRecord]:
    records: list[SurfaceRecord] = []
    for line_number, line in enumerate(text.splitlines(), 1):
        for match in PROTECTED_PATTERN.finditer(line):
            name = match.group("path").rstrip(".,;:)]}\"'")
            deny = any(part in name for part in ("/data/user/", "/data/misc/keystore"))
            records.append(
                SurfaceRecord(
                    kind="SOURCE_PROTECTED_PATH",
                    name=name,
                    availability_condition="DENY_CANDIDATE" if deny else "",
                    evidence=_source_evidence(location, line_number, name),
                )
            )
        for match in PROPERTY_PATTERN.finditer(line):
            records.append(
                SurfaceRecord(
                    kind="PROPERTY_NAME",
                    name=match.group(1),
                    evidence=_source_evidence(location, line_number, match.group(1)),
                )
            )
        for match in SOCKET_PATTERN.finditer(line):
            records.append(
                SurfaceRecord(
                    kind="UNIX_SOCKET",
                    name=match.group(1),
                    evidence=_source_evidence(location, line_number, match.group(1)),
                )
            )
    return records


def _collect_repo_source_parts(
    tree: Path,
) -> tuple[CollectionResult, CollectionResult, CollectionResult]:
    binder_records: list[SurfaceRecord] = []
    local_records: list[SurfaceRecord] = []
    reference_records: list[SurfaceRecord] = []
    binder_scanned = 0
    local_scanned = 0
    total_scanned = 0
    for path in _source_files(tree):
        text = _decode(path)
        location = str(path.relative_to(tree))
        total_scanned += 1
        reference_records.extend(_reference_records(text, location))
        if path.suffix.lower() in {".java", ".kt", ".cpp", ".cc", ".c", ".rs"}:
            has_registration = any(
                token in text for token in ("addService", "publishBinderService", "registerService")
            )
            has_local = "LocalServices." in text or "LocalManagerRegistry." in text
            if has_registration or has_local:
                parsed = parse_java_native_registrations(text, location)
                if has_registration:
                    binder_scanned += 1
                    binder_records.extend(record for record in parsed if record.kind == "BINDER_SERVICE")
                if has_local:
                    local_scanned += 1
                    local_records.extend(record for record in parsed if record.kind.startswith("LOCAL_"))
    return (
        _result("BINDER_REGISTRATIONS", "repo-manifest", binder_records, binder_scanned),
        _result("LOCAL_SERVICES", "repo-manifest", local_records, local_scanned),
        _result("PROTECTED_REFERENCES", "repo-manifest", reference_records, total_scanned),
    )


def collect_static(tree: Path, product_out: Path) -> CollectionResult:
    binder, local, references = _collect_repo_source_parts(tree)
    parts = (
        collect_system_server(tree),
        binder,
        local,
        collect_init_services(product_out),
        collect_vintf(product_out),
        collect_contexts(product_out),
        collect_framework_configuration(product_out),
        collect_apex_surfaces(tree, product_out),
        collect_overlays(tree, product_out),
        references,
    )
    return CollectionResult(
        tuple(canonical_records(record for part in parts for record in part.records)),
        tuple(canonical_coverage(entry for part in parts for entry in part.coverage)),
    )
