"""Command line entry point."""

from __future__ import annotations

import argparse
from hashlib import sha256
from pathlib import Path
import subprocess
import sys

from .model import canonical_coverage
from .classify import classify, load_overrides, load_probe, write_manifest
from .freeze import package_inventory, reconcile
from .probe import run_probe
from .render import InventoryError, verify_output, write_evidence
from .runtime import collect_runtime, require_matching_device
from .static import collect_static


def _run(tree: Path, *argv: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(argv, cwd=tree, text=True, capture_output=True, check=True)


def _source_revisions(tree: Path, product_out: Path, serial: str, identity: object) -> str:
    manifest = _run(tree, "repo", "manifest", "-r").stdout.encode("utf-8")
    projects = _run(tree, "repo", "list", "-p").stdout.splitlines()
    lines = [
        "schemaVersion=1",
        f"repoManifestSha256={sha256(manifest).hexdigest()}",
        f"serialSha256={sha256(serial.encode('utf-8')).hexdigest()}",
        f"productOut={product_out.relative_to(tree)}",
    ]
    for key, value in identity.to_target().items():
        lines.append(f"{key}={value}")
    excluded = {
        "vendor/bliss/tools/frankenstein_inventory",
        "device/asus/sm8150-common/frankenstein/inventory",
    }
    for project in sorted(filter(None, (item.strip() for item in projects))):
        head = _run(tree, "git", "-C", project, "rev-parse", "HEAD").stdout.strip()
        status = _run(
            tree, "git", "-C", project, "status", "--porcelain=v1", "-z", "--untracked-files=all"
        ).stdout
        filtered_status = "\0".join(
            item for item in status.split("\0")
            if item and not any((Path(project) / item[3:]).as_posix().startswith(prefix) for prefix in excluded)
        )
        if filtered_status:
            worktree = _run(tree, "git", "-C", project, "diff", "--binary").stdout
            index = _run(tree, "git", "-C", project, "diff", "--cached", "--binary").stdout
            lines.append(
                f"project={project} head={head} "
                f"statusSha256={sha256(filtered_status.encode()).hexdigest()} "
                f"worktreeDiffSha256={sha256(worktree.encode()).hexdigest()} "
                f"indexDiffSha256={sha256(index.encode()).hexdigest()}"
            )
        else:
            lines.append(f"project={project} head={head}")
    return "\n".join(lines) + "\n"


def collect(arguments: argparse.Namespace) -> int:
    tree = Path(arguments.tree).resolve()
    product_out = Path(arguments.product_out).resolve()
    output = Path(arguments.output)
    if not output.is_absolute():
        output = tree / output
    identity = require_matching_device(arguments.serial, product_out)
    static = collect_static(tree, product_out)
    runtime, commands = collect_runtime(arguments.serial)
    coverage = canonical_coverage([*static.coverage, *runtime.coverage])
    target = {
        "lunch": "bliss_I001D-bp4a-userdebug",
        **identity.to_target(),
    }
    revisions = _source_revisions(tree, product_out, arguments.serial, identity)
    manifest_line = next(line for line in revisions.splitlines() if line.startswith("repoManifestSha256="))
    target["repoManifestSha256"] = manifest_line.partition("=")[2]
    write_evidence(
        output,
        target=target,
        static_records=static.records,
        runtime_records=runtime.records,
        coverage=coverage,
        commands=commands,
        revisions=revisions,
    )
    verify_output(output)
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="frankenstein_inventory")
    subparsers = parser.add_subparsers(dest="command", required=True)
    collect_parser = subparsers.add_parser("collect")
    collect_parser.add_argument("--tree", required=True)
    collect_parser.add_argument("--product-out", required=True)
    collect_parser.add_argument("--serial", required=True)
    collect_parser.add_argument("--output", required=True)
    verify_parser = subparsers.add_parser("verify")
    verify_parser.add_argument("--output", required=True)
    classify_parser = subparsers.add_parser("classify")
    classify_parser.add_argument("--worksheet", required=True)
    classify_parser.add_argument("--broker-probe", required=True)
    classify_parser.add_argument("--ordinary-probe", required=True)
    classify_parser.add_argument("--overrides", required=True)
    classify_parser.add_argument("--output", required=True)
    freeze_parser = subparsers.add_parser("freeze")
    freeze_parser.add_argument("--static", required=True)
    freeze_parser.add_argument("--runtime", required=True)
    freeze_parser.add_argument("--decisions", required=True)
    freeze_parser.add_argument("--output", required=True)
    probe_parser = subparsers.add_parser("probe")
    probe_parser.add_argument("--worksheet", required=True)
    probe_parser.add_argument("--output", required=True)
    probe_parser.add_argument("--apk", required=True)
    probe_parser.add_argument("--identity", choices=("broker", "ordinary"), required=True)
    probe_parser.add_argument("--adb", default="adb")
    probe_parser.add_argument("--serial", required=True)
    arguments = parser.parse_args(argv)
    try:
        if arguments.command == "collect":
            return collect(arguments)
        if arguments.command == "classify":
            decisions = classify(
                Path(arguments.worksheet),
                load_probe(Path(arguments.broker_probe)),
                load_probe(Path(arguments.ordinary_probe)),
                load_overrides(Path(arguments.overrides)),
            )
            write_manifest(decisions, Path(arguments.output))
            return 0
        if arguments.command == "freeze":
            packaged = reconcile(
                Path(arguments.static), Path(arguments.runtime), Path(arguments.decisions)
            )
            print(package_inventory(packaged, Path(arguments.output)))
            return 0
        if arguments.command == "probe":
            run_probe(
                Path(arguments.worksheet), Path(arguments.output), Path(arguments.apk),
                arguments.identity, arguments.adb, arguments.serial,
            )
            return 0
        verify_output(Path(arguments.output))
        return 0
    except (InventoryError, ValueError, subprocess.SubprocessError) as error:
        print(f"frankenstein_inventory: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
