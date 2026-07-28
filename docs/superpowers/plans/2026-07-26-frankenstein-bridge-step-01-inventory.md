# Frankenstein Bridge Step 1: Authoritative OS Inventory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> `superpowers:executing-plans` for this stage only. Do not dispatch subagents
> and do not begin Stage 2. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and run a deterministic, secret-safe inventory collector that
enumerates the actual final Android tree, assembled product, and matching
runtime device surfaces needed for the ROM freeze classification.

**Architecture:** A Python 3 standard-library tool collects framework/native
registrations, assembled-image declarations, APEX/init/configuration surfaces,
and runtime services into one canonical surface model with separately retained
evidence references. It never stores property values or protected file
contents. Generated records remain `UNCLASSIFIED` and future-app reachability
remains `UNTESTED`; the measured reachability and human-reviewed A/B/C/D
assignment are Stage 2.

**Tech Stack:** Python 3 standard library (`argparse`, `csv`, `dataclasses`,
`hashlib`, `json`, `pathlib`, `re`, `subprocess`, `unittest`,
`xml.etree.ElementTree`), Android repo/Soong output, adb.

## Global Constraints

- Android tree: `/home/leimapokpampremika/bliss/waterlily`.
- Product output:
  `/home/leimapokpampremika/bliss/waterlily/out/target/product/I001D`.
- Tool root:
  `vendor/bliss/tools/frankenstein_inventory`.
- Evidence root:
  `device/asus/sm8150-common/frankenstein/inventory`.
- Execute all tree reads, writes, tests, and collection as Unix user
  `leimapokpampremika`; never create root- or `opencode`-owned files in the
  tree.
- Runtime adb uses the loopback SSH reverse tunnel. Preserve
  `ANDROID_ADB_SERVER_PORT=15037` and put
  `out/host/linux-x86/bin` first in `PATH` for every preflight/collection
  process. If the tunnel is absent, stop rather than starting an unrelated adb
  server.
- This stage may create only the inventory tool, its tests/fixtures, temporary
  APEX extraction directories under `/tmp`, and the five evidence outputs
  named below.
- Do not modify framework, service, AIDL, init, SELinux, VINTF, device product,
  broker, or build-definition files.
- Do not build, flash, sync, reboot, remount, change SELinux state, alter
  properties, or install/uninstall packages.
- Do not run `adb root`; collect the exact shell-domain view and record
  permission-denied/unavailable surfaces instead of changing adbd state.
- Do not capture property values (except the build/product identity and public
  `init.svc.*` state required below), environment secrets, protected file contents,
  command-line arguments of processes, app data, credentials, tokens, serial
  numbers, account identifiers, or network payloads.
- Do not classify any record in this stage. `bucket` must equal
  `UNCLASSIFIED`, `future_app_reachability` must equal `UNTESTED`, and
  `later_access_path`, `rationale`, `provider_id`, and `test_id` must be empty.
- If the assembled product output is absent, the connected device is absent, or
  its multi-property build identity does not match the product output, stop and
  report the exact blocker. The intentionally preserved ASUS fingerprint is
  necessary but not sufficient proof of build identity. Static-only output is
  not a completed Stage 1.
- Do not commit. Stop after the review packet.

## Files

**Create:**

- `vendor/bliss/tools/frankenstein_inventory/frankenstein_inventory/__init__.py`
- `vendor/bliss/tools/frankenstein_inventory/frankenstein_inventory/model.py`
- `vendor/bliss/tools/frankenstein_inventory/frankenstein_inventory/static.py`
- `vendor/bliss/tools/frankenstein_inventory/frankenstein_inventory/runtime.py`
- `vendor/bliss/tools/frankenstein_inventory/frankenstein_inventory/render.py`
- `vendor/bliss/tools/frankenstein_inventory/frankenstein_inventory/__main__.py`
- `vendor/bliss/tools/frankenstein_inventory/tests/__init__.py`
- `vendor/bliss/tools/frankenstein_inventory/tests/test_model.py`
- `vendor/bliss/tools/frankenstein_inventory/tests/test_static.py`
- `vendor/bliss/tools/frankenstein_inventory/tests/test_runtime.py`
- `vendor/bliss/tools/frankenstein_inventory/tests/test_cli.py`
- `vendor/bliss/tools/frankenstein_inventory/tests/fixtures/SystemServer.java`
- `vendor/bliss/tools/frankenstein_inventory/tests/fixtures/NativeServices.cpp`
- `vendor/bliss/tools/frankenstein_inventory/tests/fixtures/LocalOwner.java`
- `vendor/bliss/tools/frankenstein_inventory/tests/fixtures/sample.rc`
- `vendor/bliss/tools/frankenstein_inventory/tests/fixtures/manifest.xml`
- `vendor/bliss/tools/frankenstein_inventory/tests/fixtures/apex-info-list.xml`
- `vendor/bliss/tools/frankenstein_inventory/tests/fixtures/permissions.xml`
- `vendor/bliss/tools/frankenstein_inventory/tests/fixtures/sysconfig.xml`
- `vendor/bliss/tools/frankenstein_inventory/tests/fixtures/overlay-manifest.txt`
- `vendor/bliss/tools/frankenstein_inventory/tests/fixtures/overlay-resources.txt`
- `vendor/bliss/tools/frankenstein_inventory/tests/fixtures/service_contexts`
- `vendor/bliss/tools/frankenstein_inventory/tests/fixtures/property_contexts`
- `vendor/bliss/tools/frankenstein_inventory/README.md`
- `device/asus/sm8150-common/frankenstein/inventory/static-inventory.json`
- `device/asus/sm8150-common/frankenstein/inventory/runtime-inventory.json`
- `device/asus/sm8150-common/frankenstein/inventory/classification.csv`
- `device/asus/sm8150-common/frankenstein/inventory/source-revisions.txt`
- `device/asus/sm8150-common/frankenstein/inventory/collection-report.md`

**Do not modify or delete any existing file.** If any listed output already
exists, report it before editing and preserve it unless the reviewer explicitly
authorizes replacement.

## Produced Interfaces

`model.py` must define:

```python
SCHEMA_VERSION = 1
UNCLASSIFIED = "UNCLASSIFIED"
UNTESTED = "UNTESTED"

@dataclass(frozen=True, order=True)
class EvidenceRef:
    evidence_kind: str
    location: str
    line: int = 0
    sha256: str = ""

@dataclass(frozen=True, order=True)
class CoverageEntry:
    family: str
    location: str
    status: str
    scanned_inputs: int
    record_count: int
    limitation: str = ""

@dataclass(frozen=True, order=True)
class SurfaceRecord:
    kind: str
    name: str
    instance: str = ""
    partition: str = ""
    owner: str = ""
    runtime_present: str = "UNKNOWN"
    interface_descriptor: str = ""
    interface_version: str = ""
    interface_hash: str = ""
    selinux_context: str = ""
    availability_condition: str = ""
    evidence: tuple[EvidenceRef, ...] = ()
    future_app_reachability: str = UNTESTED
    bucket: str = UNCLASSIFIED
    later_access_path: str = ""
    rationale: str = ""
    provider_id: str = ""
    test_id: str = ""

    def stable_key(self) -> tuple[str, ...]: ...
    def record_id(self) -> str: ...
    def to_dict(self) -> dict[str, object]: ...

@dataclass(frozen=True)
class CollectionResult:
    records: tuple[SurfaceRecord, ...]
    coverage: tuple[CoverageEntry, ...]

def canonical_records(records: Iterable[SurfaceRecord]) -> list[SurfaceRecord]: ...
def canonical_coverage(entries: Iterable[CoverageEntry]) -> list[CoverageEntry]: ...
def canonical_json(document: dict[str, object]) -> bytes: ...
```

`record_id()` must equal `"fs1:" + sha256(NUL-separated stable-key fields)`.
The stable-key fields, in order, are `kind`, `name`, and `instance`.
Partition, owner, source locations, source line movement, classification,
reachability, and runtime presence are not identity fields. This lets a
runtime observation with no partition metadata merge with its assembled
declaration. Multiple source, assembled, or runtime observations of one surface
merge into its sorted, deduplicated `evidence` list; they do not create
duplicate classification rows. Contradictory non-empty partition or owner
metadata is a conflict, not a second identity.

The canonical document shape is:

```json
{
  "schemaVersion": 1,
  "target": {
    "product": "I001D",
    "lunch": "bliss_I001D-bp4a-userdebug",
    "buildFingerprint": "<matching fingerprint>",
    "buildId": "<matching build ID>",
    "buildIncremental": "<matching incremental>",
    "buildDateUtc": "<matching decimal UTC epoch>",
    "buildDescription": "<matching description>",
    "buildType": "userdebug",
    "buildTags": "dev-keys",
    "blissDevice": "I001D",
    "repoManifestSha256": "<sha256>"
  },
  "records": [
    {
      "recordId": "fs1:<sha256>",
      "kind": "<enum below>",
      "name": "<stable runtime or source name>",
      "instance": "",
      "partition": "",
      "owner": "",
      "runtimePresent": "YES|NO|UNKNOWN",
      "interfaceDescriptor": "",
      "interfaceVersion": "",
      "interfaceHash": "",
      "selinuxContext": "",
      "availabilityCondition": "",
      "evidence": [
        {
          "evidenceKind": "SOURCE|ASSEMBLED|RUNTIME",
          "location": "<repo-relative path or redacted command ID>",
          "line": 0,
          "sha256": "<sha256 of the smallest normalized evidence unit>"
        }
      ],
      "futureAppReachability": "UNTESTED",
      "bucket": "UNCLASSIFIED",
      "laterAccessPath": "",
      "rationale": "",
      "providerId": "",
      "testId": ""
    }
  ],
  "coverage": [
    {
      "family": "SYSTEM_SERVER",
      "location": "frameworks/base/services/java/com/android/server/SystemServer.java",
      "status": "COLLECTED|ABSENT|UNAVAILABLE",
      "scannedInputs": 1,
      "recordCount": 1,
      "limitation": ""
    }
  ]
}
```

Allowed `kind` values:

```text
SYSTEM_SERVER_SERVICE
LOCAL_SERVICE_PRODUCER
LOCAL_SERVICE_CONSUMER
LOCAL_MANAGER_PRODUCER
LOCAL_MANAGER_CONSUMER
INIT_SERVICE
BINDER_SERVICE
DUMPSYS_SERVICE
CMD_SERVICE
VINTF_AIDL_INSTANCE
VINTF_HIDL_INSTANCE
SERVICE_CONTEXT
HWSERVICE_CONTEXT
VNDSERVICE_CONTEXT
FILE_CONTEXT
GENFS_CONTEXT
PROPERTY_CONTEXT
SEAPP_CONTEXT
SELINUX_STATE
SYSTEM_FEATURE
SYSCONFIG_ENTRY
PRIVAPP_PERMISSION_GRANT
APEX_MODULE
OVERLAY_PACKAGE
PROPERTY_NAME
INIT_RUNTIME_STATE
PROCESS_DOMAIN
DEVICE_NODE
UNIX_SOCKET
SOURCE_PROTECTED_PATH
```

Canonical identity mapping is fixed:

| Kinds | `name` | `instance` | `partition` |
|---|---|---|---|
| `SYSTEM_SERVER_SERVICE` | started Java service class or literal logical name | empty | `system` |
| `BINDER_SERVICE`, `DUMPSYS_SERVICE`, `CMD_SERVICE` | published/listed service name | empty | known assembled partition, otherwise empty |
| `LOCAL_SERVICE_*`, `LOCAL_MANAGER_*` | fully qualified class literal | empty | `system` |
| `INIT_SERVICE` | rc stanza service name | empty | assembled partition or `apex:<module>` |
| `VINTF_AIDL_INSTANCE` | fully qualified interface | declared instance | declaring partition |
| `VINTF_HIDL_INSTANCE` | package plus interface | declared/runtime instance | declaring partition, otherwise empty |
| `*_CONTEXT` | exact left-hand selector/regex | empty | context-file partition |
| `SELINUX_STATE` | `kernel` | empty | empty |
| `SYSTEM_FEATURE` | feature name | empty | declaring partition, otherwise empty |
| `SYSCONFIG_ENTRY` | XML element name | canonical identity attribute (`name`, `package`, `component`, or `library`) | declaring partition |
| `PRIVAPP_PERMISSION_GRANT` | package name | permission name, prefixed `grant:` or `deny:` | declaring partition |
| `APEX_MODULE` | module name | empty | source partition |
| `OVERLAY_PACKAGE` | overlay package name | target package | source partition |
| `PROPERTY_NAME`, `INIT_RUNTIME_STATE` | property or init-service name | empty | known declaring partition, otherwise empty |
| `PROCESS_DOMAIN` | process name | Android user name | empty |
| `DEVICE_NODE`, `UNIX_SOCKET`, `SOURCE_PROTECTED_PATH` | normalized absolute path or abstract-socket name | empty | known declaring partition, otherwise empty |

If a required identity cannot be resolved to this form, emit a coverage
limitation and fail the completed-stage gate; do not invent an ID from an
unstable object address, PID, source expression, or ordinal.

Evidence hashes are also fixed. `SOURCE` hashes the exact balanced call,
stanza, or referenced token after newline and whitespace normalization;
`ASSEMBLED` hashes the canonical parsed XML element/context line/stanza;
`RUNTIME` hashes only the normalized redacted record line accepted by its
parser. Whole protected files and discarded runtime columns are never hashed.

Every collection family must emit a `CoverageEntry`, even when its location is
legitimately absent or its runtime command is unavailable. Allowed coverage
states are `COLLECTED`, `ABSENT`, and `UNAVAILABLE`; an unexpected parse/tool/
permission failure is fatal and produces no evidence directory. A completed
Stage 1 may contain documented `ABSENT` entries but no `UNAVAILABLE` entry.
The report and JSON `coverage` array must account for these families:

```text
SYSTEM_SERVER
BINDER_REGISTRATIONS
LOCAL_SERVICES
ASSEMBLED_INIT
VINTF
SELINUX_CONTEXTS
FRAMEWORK_CONFIGURATION
APEX
OVERLAYS
PROTECTED_REFERENCES
RUNTIME_BINDER
RUNTIME_DUMPSYS_CMD
RUNTIME_HALS
RUNTIME_FEATURES
RUNTIME_APEX
RUNTIME_OVERLAYS
RUNTIME_PROPERTIES
RUNTIME_SELINUX
RUNTIME_PROCESSES
RUNTIME_DEVICE_NODES
RUNTIME_SOCKETS
```

`static.py` must define:

```python
def collect_system_server(tree: Path) -> CollectionResult: ...
def collect_binder_registrations(tree: Path) -> CollectionResult: ...
def collect_local_services(tree: Path) -> CollectionResult: ...
def collect_init_services(product_out: Path) -> CollectionResult: ...
def collect_vintf(product_out: Path) -> CollectionResult: ...
def collect_contexts(product_out: Path) -> CollectionResult: ...
def collect_framework_configuration(product_out: Path) -> CollectionResult: ...
def collect_apex_surfaces(tree: Path, product_out: Path) -> CollectionResult: ...
def collect_overlays(tree: Path, product_out: Path) -> CollectionResult: ...
def collect_source_references(tree: Path) -> CollectionResult: ...
def collect_static(tree: Path, product_out: Path) -> CollectionResult: ...
```

`runtime.py` must define:

```python
@dataclass(frozen=True)
class CommandEvidence:
    command_id: str
    redacted_argv: tuple[str, ...]
    exit_code: int
    stdout_sha256: str
    stderr_sha256: str

@dataclass(frozen=True)
class DeviceIdentity:
    product_device: str
    build_fingerprint: str
    build_id: str
    build_incremental: str
    build_date_utc: str
    build_description: str
    product_system_name: str
    build_type: str
    build_tags: str
    bliss_device: str

def adb(serial: str, *args: str) -> subprocess.CompletedProcess[str]: ...
def require_matching_device(serial: str, product_out: Path) -> DeviceIdentity: ...
def collect_runtime(serial: str) -> tuple[CollectionResult,
                                          tuple[CommandEvidence, ...]]: ...
```

`render.py` must define:

```python
def write_json(path: Path, document: dict[str, object]) -> None: ...
def write_classification_csv(path: Path,
                             records: Sequence[SurfaceRecord]) -> None: ...
def write_revisions(path: Path, tree: Path,
                    product_out: Path, serial: str) -> None: ...
def write_report(path: Path, static_count: int, runtime_count: int,
                 coverage: Sequence[CoverageEntry],
                 commands: Sequence[CommandEvidence],
                 output_hashes: Mapping[str, str]) -> None: ...
```

The CLI is:

```text
python3 -m frankenstein_inventory collect \
  --tree PATH \
  --product-out PATH \
  --serial SERIAL \
  --output PATH

python3 -m frankenstein_inventory verify \
  --output PATH
```

`collect` writes exactly the five named evidence outputs and refuses any
non-empty output directory. `verify` performs no adb/source collection and no
writes; it validates schemas, canonical ordering, internal hashes, required
coverage, and the four non-report hashes recorded by
`collection-report.md`. The report cannot contain its own SHA-256; that fifth
hash is calculated for the review packet. Neither command overwrites evidence.

---

## Agent Instruction

Implement only Stage 1 from this file. Work in
`/home/leimapokpampremika/bliss/waterlily`. First read
`/home/home/bliss/OS Bridge/docs/frankenstein-bridge-architecture.md`,
`/home/home/bliss/OS Bridge/AGENTS.md`, and every applicable `AGENTS.md` in the
Android tree. Track the checklist in this plan, use test-first development, and
stop with the review packet. Do not modify any Android runtime/framework
implementation and do not begin classification or Stage 2.

### Task 1: Preflight And Evidence Identity

- [ ] **Step 1: Verify the exact tree and preserve dirty state**

Run:

```bash
test "$(id -un)" = leimapokpampremika
cd /home/leimapokpampremika/bliss/waterlily
export PATH="$PWD/out/host/linux-x86/bin:$PATH"
export ANDROID_ADB_SERVER_PORT=15037
test "$(command -v adb)" = "$PWD/out/host/linux-x86/bin/adb"
pwd -P
repo status
git -C frameworks/base status --short
git -C system/core status --short
git -C system/sepolicy status --short
git -C device/asus/sm8150-common status --short
git -C vendor/bliss status --short
```

Expected: the canonical tree path is exact. Record all pre-existing changes in
the review packet. The exact build-host adb and tunneled server port are
selected. Do not clean, stash, reset, checkout, or modify existing work.

- [ ] **Step 2: Verify required roots and ensure outputs are new**

Run:

```bash
test -d frameworks/base
test -d system/core
test -d system/sepolicy
test -d device/asus/sm8150-common
test -d vendor/bliss
test -d out/target/product/I001D
if test -d device/asus/sm8150-common/frankenstein/inventory; then
  find device/asus/sm8150-common/frankenstein/inventory \
    -maxdepth 1 -type f -print
fi
```

Expected: all six source/product roots exist. The final command prints nothing
or the directory is absent. If it prints an existing evidence file, stop and
request review; do not replace it.

- [ ] **Step 3: Verify a matching runtime device without changing it**

Run:

```bash
export FRANKENSTEIN_DEVICE_SERIAL='<exact-adb-serial-from-adb-devices>'
adb -s "$FRANKENSTEIN_DEVICE_SERIAL" get-state
adb -s "$FRANKENSTEIN_DEVICE_SERIAL" shell getprop ro.product.device
for prop in \
  ro.build.fingerprint \
  ro.build.id \
  ro.build.version.incremental \
  ro.build.date.utc \
  ro.build.description \
  ro.product.system.name \
  ro.build.type \
  ro.build.tags \
  ro.bliss.device; do
  adb -s "$FRANKENSTEIN_DEVICE_SERIAL" shell getprop "$prop"
done
rg '^(ro\\.build\\.fingerprint|ro\\.build\\.id|ro\\.build\\.version\\.incremental|ro\\.build\\.date\\.utc|ro\\.build\\.description|ro\\.product\\.system\\.name|ro\\.build\\.type|ro\\.build\\.tags|ro\\.bliss\\.device)=' \
  out/target/product/I001D/system/build.prop \
  out/target/product/I001D/product/build.prop
printf '%s' "$FRANKENSTEIN_DEVICE_SERIAL" | sha256sum
```

Expected: select exactly one online device explicitly, product is `I001D`, and
the nine build-identity properties match their system/product output values.
The preserved ASUS fingerprint is expected, but the BP4A build ID,
incremental/date, Bliss product description/name, type/tags, and Bliss device
must independently match. Use the explicit serial for every remaining adb
command, but record only its SHA-256. If any identity field differs, stop; do
not collect mixed evidence.

- [ ] **Step 4: Record source revisions without creating files yet**

Run:

```bash
repo manifest -r | sha256sum
repo forall -c 'printf "%s %s\n" "$REPO_PATH" "$(git rev-parse HEAD)"' | LC_ALL=C sort
git -C frameworks/base rev-parse HEAD
git -C system/core rev-parse HEAD
git -C system/sepolicy rev-parse HEAD
git -C device/asus/sm8150-common rev-parse HEAD
git -C vendor/bliss rev-parse HEAD
```

Expected: every command succeeds. Hash the canonical manifest and retain the
sorted complete project-path/HEAD map in the generated revisions file; the five
explicit project commands are cross-checks. Do not substitute branch names for
commit IDs.

### Task 2: Canonical Record Model

**Files:**

- Create `frankenstein_inventory/model.py`
- Create `tests/test_model.py`
- Create package `__init__.py` files

- [ ] **Step 1: Write failing model tests**

Tests must assert:

```python
def test_record_id_ignores_runtime_and_classification():
    base = SurfaceRecord(kind="BINDER_SERVICE", name="activity")
    changed = replace(
        base,
        runtime_present="YES",
        bucket="A",
        rationale="framework private",
    )
    assert base.record_id() == changed.record_id()

def test_record_id_survives_source_line_movement():
    a = SurfaceRecord(
        kind="LOCAL_SERVICE_PRODUCER",
        name="Foo",
        evidence=(EvidenceRef("SOURCE", "Owner.java", 10, "a" * 64),),
    )
    b = SurfaceRecord(
        kind="LOCAL_SERVICE_PRODUCER",
        name="Foo",
        evidence=(EvidenceRef("SOURCE", "Owner.java", 11, "b" * 64),),
    )
    assert a.record_id() == b.record_id()

def test_canonical_records_merges_evidence_and_sorts():
    records = canonical_records([
        SurfaceRecord(
            kind="BINDER_SERVICE",
            name="window",
            evidence=(EvidenceRef("SOURCE", "Wms.java", 1, "a" * 64),),
        ),
        SurfaceRecord(kind="BINDER_SERVICE", name="activity"),
        SurfaceRecord(
            kind="BINDER_SERVICE",
            name="window",
            runtime_present="YES",
            evidence=(EvidenceRef("RUNTIME", "service-list", 0, "b" * 64),),
        ),
    ])
    assert [r.name for r in records] == ["activity", "window"]
    assert records[1].runtime_present == "YES"
    assert len(records[1].evidence) == 2

def test_canonical_json_is_byte_stable():
    document = {"schemaVersion": 1, "records": [{"name": "activity"}]}
    assert canonical_json(document) == canonical_json(document)
    assert canonical_json(document).endswith(b"\n")

def test_coverage_keeps_absence_visible():
    entries = canonical_coverage([
        CoverageEntry("APEX", "system/apex", "ABSENT", 0, 0, "not in product"),
    ])
    assert entries[0].status == "ABSENT"
```

- [ ] **Step 2: Run tests and verify the expected failure**

Run:

```bash
PYTHONPATH=vendor/bliss/tools/frankenstein_inventory \
python3 -m unittest discover \
  -s vendor/bliss/tools/frankenstein_inventory/tests \
  -p 'test_model.py' -v
```

Expected: import/module failure because the model does not exist.

- [ ] **Step 3: Implement the exact record contract**

Implement the interfaces, merge rules, and field ordering specified above. A
merge must reject contradictory non-empty partition/owner/interface/context
metadata rather than silently choose one observation. Use
`json.dumps(..., sort_keys=True, separators=(",", ":"), ensure_ascii=False)`
plus one trailing newline. Validate `kind`, bucket, runtime presence,
reachability state, non-negative evidence line, SHA-256 syntax, and that
IDs/names/locations contain no NUL.

- [ ] **Step 4: Run the model tests**

Run:

```bash
PYTHONPATH=vendor/bliss/tools/frankenstein_inventory \
python3 -m unittest discover \
  -s vendor/bliss/tools/frankenstein_inventory/tests \
  -p 'test_model.py' -v
```

Expected: all model tests pass.

### Task 3: Static Collector

**Files:**

- Create `frankenstein_inventory/static.py`
- Create `tests/test_static.py`
- Create the static fixtures listed in **Files**

- [ ] **Step 1: Write failing parser tests**

The fixture/test set must prove:

1. `SystemServer.java` extraction handles multiline nested call arguments,
   string service names, class literals, `publishBinderService`, direct
   `ServiceManager.addService`, conditional starts, and comments without
   treating commented code as a registration.
2. Java and native source extraction covers `ServiceManager.addService`,
   `IServiceManager::addService`, `AServiceManager_addService`,
   `AServiceManager_registerLazyService`, and
   `LazyServiceRegistrar::registerService` without treating lookups as
   registrations.
3. `LocalServices.addService(FooInternal.class, ...)` and
   `LocalServices.getService(FooInternal.class)` become separate producer and
   consumer records. Do the same for `LocalManagerRegistry` add/get calls.
4. An init service with a backslash-continued command becomes one
   `INIT_SERVICE` record with service name, executable owner, source line, and
   inferred assembled partition.
5. One HIDL and one AIDL VINTF instance are recorded with interface, instance,
   version/hash when present, transport/partition, and source evidence hash.
6. Context parsers ignore comments/blank lines but preserve regex names and
   SELinux labels for service, hwservice, vndservice, file, genfs, property, and
   seapp inputs.
7. Assembled `etc/permissions` and `etc/sysconfig` parsing records system
   features, named sysconfig entries, and package/permission pairs without
   storing unrelated XML contents.
8. Installed APEX metadata is correlated with init/permissions/sysconfig
   declarations extracted from that exact APEX payload. An APEX extraction
   failure is fatal, not silently skipped.
9. Overlay APK metadata records partition, package, target package, static/
   priority state, APK hash, and any service-start condition resource it
   affects. Parser tests use fixed `aapt2 dump xmltree` fixture output.
10. Absolute source references under `/proc`, `/sys`, `/dev`, `/data/tombstones`,
   `/data/anr`, `/sys/fs/pstore`, `/cache/recovery`, and `/metadata` become
   `SOURCE_PROTECTED_PATH`; paths under `/data/user`, `/data/misc/keystore`,
   or credential stores are still inventoried and visibly deny-candidate data
   with `availability_condition="DENY_CANDIDATE"`, never opened. Fixture tests
   prove this behavior without adding fixture records to production evidence.
11. Duplicate surfaces from source, assembled, APEX, overlay, and runtime-style
    fixtures merge deterministically without losing evidence references.

- [ ] **Step 2: Run the static tests and verify failure**

Run:

```bash
PYTHONPATH=vendor/bliss/tools/frankenstein_inventory \
python3 -m unittest discover \
  -s vendor/bliss/tools/frankenstein_inventory/tests \
  -p 'test_static.py' -v
```

Expected: import/module or missing-function failures.

- [ ] **Step 3: Implement balanced Java call extraction**

Strip Java/C++ comments while preserving newlines, scan for the tested
registration and LocalServices/LocalManager call prefixes, and consume balanced
parentheses while respecting quoted strings and escapes. Do not depend on
one-line regexes. Store repo-relative POSIX source paths and one-based source
lines. For a guarded SystemServer start, retain a normalized guard expression
and referenced feature/resource/property names as `availability_condition`;
never evaluate source conditionals by guesswork.

- [ ] **Step 4: Implement assembled init/VINTF/context collection**

Search only these assembled roots when they exist:

```text
out/target/product/I001D/root
out/target/product/I001D/system
out/target/product/I001D/system_ext
out/target/product/I001D/product
out/target/product/I001D/vendor
out/target/product/I001D/odm
```

Parse files under `etc/init`, `etc/vintf`, and `etc/selinux`, plus root
ramdisk equivalents. Use `ElementTree` for XML. Do not parse a VINTF interface
with regex.

- [ ] **Step 5: Implement configuration, APEX, and overlay collection**

Parse every assembled `etc/permissions/*.xml` and `etc/sysconfig/*.xml` under
the six roots. Preserve feature names and package/permission or named-element
identities, not arbitrary element bodies.

Parse `apex-info-list.xml`, then inspect every installed APEX payload listed
there.
Use the matching build-host `deapexer` in
`out/host/linux-x86/bin/deapexer` and a `tempfile.TemporaryDirectory` under
`/tmp`. Set `ANDROID_HOST_OUT` to `out/host/linux-x86`, run
`deapexer info --print-type PAYLOAD`, normalize compressed and uncompressed
inputs with `deapexer decompress --input PAYLOAD --output TEMP_APEX
--copy-if-uncompressed`, then run `deapexer extract TEMP_APEX TEMP_DIRECTORY`.
Collect its `etc/init`, `etc/permissions`, and `etc/sysconfig` entries, then
remove the temporary extraction. If installed APEX metadata exists but the
matching payload/tool cannot be inspected, fail collection and name the
uncovered module.

For APKs below assembled `overlay/` directories, invoke the matching build-host
`out/host/linux-x86/bin/aapt2` as:

```text
aapt2 dump xmltree --file AndroidManifest.xml OVERLAY_APK
aapt2 dump resources OVERLAY_APK
```

Record overlay package, target, `isStatic`, priority, partition, APK SHA-256,
and any final service-start guard resource found in Step 3. If an assembled
overlay cannot be inspected, fail rather than omit it.

- [ ] **Step 6: Implement repository-wide protected/reference collection**

Use `repo list -p` to enumerate every checked-out project and `git -C PROJECT
ls-files -co --exclude-standard -z` to enumerate tracked plus non-ignored
untracked build inputs. Scan text across the complete manifest, not only
framework/device projects. Skip `.git`, `.repo`, `out`, generated
intermediates, test-data directories, this inventory tool/evidence directory,
and files larger than 8 MiB. Skip genuinely binary payloads, but never silently
skip a tracked or non-ignored build-source/config file because Git labels its
diff binary: decode UTF-8 and BOM-declared UTF-16, and fail coverage with the
exact path for an undecodable required source. Record protected
path/property/node/socket/HAL references and source location, not target
contents. References found in test data are excluded from the production
inventory but covered by parser tests.
`source-revisions.txt` must include, per project, HEAD plus separate SHA-256
values for the worktree diff, index diff, NUL-delimited porcelain status, and
every non-ignored untracked build input after filtering the two inventory
directories from each command's path set. This prevents a dirty final tree from
masquerading as its HEAD revision without creating a self-referential evidence
hash.

- [ ] **Step 7: Run static tests**

Run:

```bash
PYTHONPATH=vendor/bliss/tools/frankenstein_inventory \
python3 -m unittest discover \
  -s vendor/bliss/tools/frankenstein_inventory/tests \
  -p 'test_static.py' -v
```

Expected: all static tests pass.

### Task 4: Secret-Safe Runtime Collector

**Files:**

- Create `frankenstein_inventory/runtime.py`
- Create `tests/test_runtime.py`

- [ ] **Step 1: Write failing runtime parser tests**

Mock `adb()` output and prove:

```python
def test_getprop_keeps_names_not_values():
    output = "[ro.build.id]: [SECRET_BUILD]\n[persist.vendor.foo]: [SECRET_VALUE]\n"
    records = parse_getprop_names(output)
    rendered = json.dumps([r.to_dict() for r in records])
    assert "ro.build.id" in rendered
    assert "persist.vendor.foo" in rendered
    assert "SECRET_BUILD" not in rendered
    assert "SECRET_VALUE" not in rendered

def test_init_properties_keep_only_public_state():
    output = "[init.svc.netd]: [running]\n[init.svc_debug_pid.netd]: [1234]\n"
    records = parse_init_states(output)
    assert [(r.name, r.availability_condition) for r in records] == [
        ("netd", "running")
    ]

def test_service_list_extracts_name_and_descriptor():
    output = "0 activity: [android.app.IActivityManager]\n"
    record = parse_service_list(output)[0]
    assert record.name == "activity"
    assert record.interface_descriptor == "android.app.IActivityManager"
```

Also test `dumpsys -l`, `cmd -l`, `lshal`, `pm list features`,
`apexservice getActivePackages`, `overlay list --user 0`, `getenforce`,
`ps -AZ`, device-node paths, and Unix-socket paths. Process records may contain
only SELinux label, Android user name, process name, and runtime-present
state—never PID, arguments, or command line. APEX and overlay parsers retain
module/package/target/active-state identity but discard filesystem paths and
unrelated dump text. A nonzero allowlisted command must raise
`InventoryError` carrying only redacted command evidence.

- [ ] **Step 2: Run runtime tests and verify failure**

Run:

```bash
PYTHONPATH=vendor/bliss/tools/frankenstein_inventory \
python3 -m unittest discover \
  -s vendor/bliss/tools/frankenstein_inventory/tests \
  -p 'test_runtime.py' -v
```

Expected: import/module or missing-function failures.

- [ ] **Step 3: Implement multi-property build-identity enforcement**

`require_matching_device()` must:

1. verify `adb -s SERIAL get-state` equals `device`;
2. verify `ro.product.device` equals `I001D`;
3. read the nine identity properties listed in Task 1 from the assembled
   system/product build properties;
4. compare every value byte-for-byte with the corresponding device property;
5. accept the preserved ASUS fingerprint only when all independent Bliss/BP4A
   identity fields also match;
6. return the populated `DeviceIdentity`, or raise `InventoryError` before any
   other runtime command on a missing or mismatched field.

- [ ] **Step 4: Implement the fixed runtime command allowlist**

After the device-identity checks in Step 3, run only:

```text
adb -s SERIAL shell service list
adb -s SERIAL shell dumpsys -l
adb -s SERIAL shell cmd -l
adb -s SERIAL shell lshal --neat
adb -s SERIAL shell pm list features
adb -s SERIAL shell cmd apexservice getActivePackages
adb -s SERIAL shell cmd overlay list --user 0
adb -s SERIAL shell getprop
adb -s SERIAL shell getenforce
adb -s SERIAL shell ps -AZ -o LABEL,USER,NAME
adb -s SERIAL shell find /dev -maxdepth 3 '(' -type c -o -type b ')'
adb -s SERIAL shell cat /proc/net/unix
```

If any listed command is unsupported, denied, times out, or exits nonzero,
retain its redacted command/exit/hash in memory, raise `InventoryError`, write
no evidence directory, and print that sanitized evidence in the CLI failure
report. Do not substitute a broader command or self-waive a missing runtime
family. An exit-zero empty result is
`COLLECTED` with zero records, not `ABSENT`. Parse `getprop` in memory and
discard all non-`init.svc.*` values before rendering, while retaining every
property *name* as `PROPERTY_NAME`. Parse `/proc/net/unix` to retain only
pathname entries, never inode/refcount/flags. `CommandEvidence.redacted_argv`
replaces the serial with literal `SERIAL`. `stdout_sha256` hashes the
normalized, redacted parser input—not raw `getprop` values or other discarded
columns—and stderr is serial-redacted before hashing.

- [ ] **Step 5: Run runtime tests**

Run:

```bash
PYTHONPATH=vendor/bliss/tools/frankenstein_inventory \
python3 -m unittest discover \
  -s vendor/bliss/tools/frankenstein_inventory/tests \
  -p 'test_runtime.py' -v
```

Expected: all runtime tests pass and the secret sentinel strings are absent
from serialized records.

### Task 5: Rendering And CLI

**Files:**

- Create `frankenstein_inventory/render.py`
- Create `frankenstein_inventory/__main__.py`
- Create `tests/test_cli.py`
- Create `README.md`

- [ ] **Step 1: Write a failing end-to-end fixture test**

Use a temporary tree/product/output and mocked runtime collector. Assert:

- exactly five output files;
- deterministic JSON bytes and row order;
- CSV header exactly:

```text
record_id,kind,name,instance,partition,owner,runtime_present,interface_descriptor,interface_version,interface_hash,selinux_context,availability_condition,evidence_count,evidence_refs_json,future_app_reachability,bucket,later_access_path,rationale,provider_id,test_id
```

- every CSV `bucket` is `UNCLASSIFIED`;
- every CSV `future_app_reachability` is `UNTESTED`;
- the report lists every required coverage family/location/status, scanned
  input and record counts, counts by `kind`, command exit codes, limitations,
  and each non-report output SHA-256;
- revisions contain manifest hash, every project commit, dirty-diff/status and
  non-ignored-untracked-input hashes, product/device fingerprints, and serial
  hash—not the raw serial;
- rendering identical in-memory evidence twice produces byte-identical files;
- `collect` against any non-empty output raises `InventoryError`;
- `verify` accepts valid evidence without collecting or writing and rejects a
  byte, ordering, schema, required-coverage, or internal-hash change.

- [ ] **Step 2: Run the CLI test and verify failure**

Run:

```bash
PYTHONPATH=vendor/bliss/tools/frankenstein_inventory \
python3 -m unittest discover \
  -s vendor/bliss/tools/frankenstein_inventory/tests \
  -p 'test_cli.py' -v
```

Expected: import/module or missing-function failures.

- [ ] **Step 3: Implement atomic evidence writes**

Collect and render into a sibling temporary directory first. Calculate the
four non-report output hashes, render the report with those hashes, `fsync`
every file and the temporary directory, then rename the complete directory
into place. Remove the temporary directory on error. The destination must not
exist or must be an empty directory that the tool can remove before the atomic
rename. Never replace a non-empty destination.

- [ ] **Step 4: Document exact use and limitations**

The README must include both CLI commands, allowed runtime commands, record
schema, merge/conflict rules, redaction rules, deterministic verification
procedure, fingerprint requirement,
and statement that classification occurs only in Stage 2.

- [ ] **Step 5: Run all tool tests**

Run:

```bash
PYTHONPATH=vendor/bliss/tools/frankenstein_inventory \
python3 -m unittest discover \
  -s vendor/bliss/tools/frankenstein_inventory/tests \
  -p 'test_*.py' -v
```

Expected: all tests pass with zero skipped tests.

### Task 6: Collect Actual Evidence

- [ ] **Step 1: Hash the already selected device serial**

Use the explicit serial selected during preflight:

```bash
test -n "$FRANKENSTEIN_DEVICE_SERIAL"
printf '%s' "$FRANKENSTEIN_DEVICE_SERIAL" | sha256sum
```

Do not put the raw serial in generated evidence or the review report.

- [ ] **Step 2: Run the collector once**

Run:

```bash
cd /home/leimapokpampremika/bliss/waterlily
PYTHONPATH=vendor/bliss/tools/frankenstein_inventory \
python3 -m frankenstein_inventory collect \
  --tree /home/leimapokpampremika/bliss/waterlily \
  --product-out /home/leimapokpampremika/bliss/waterlily/out/target/product/I001D \
  --serial "$FRANKENSTEIN_DEVICE_SERIAL" \
  --output device/asus/sm8150-common/frankenstein/inventory
```

Expected: exit 0 and exactly five evidence files.

- [ ] **Step 3: Verify canonical evidence without recollecting or overwriting**

Hash outputs, run the read-only verifier twice, and hash again:

```bash
find device/asus/sm8150-common/frankenstein/inventory \
  -maxdepth 1 -type f -print0 | sort -z | xargs -0 sha256sum
PYTHONPATH=vendor/bliss/tools/frankenstein_inventory \
python3 -m frankenstein_inventory verify \
  --output device/asus/sm8150-common/frankenstein/inventory
PYTHONPATH=vendor/bliss/tools/frankenstein_inventory \
python3 -m frankenstein_inventory verify \
  --output device/asus/sm8150-common/frankenstein/inventory
find device/asus/sm8150-common/frankenstein/inventory \
  -maxdepth 1 -type f -print0 | sort -z | xargs -0 sha256sum
```

Expected: both verifier calls exit 0 and both hash sets are identical. Do not
recollect live runtime state merely to claim byte determinism; live state may
legitimately change.

- [ ] **Step 4: Run redaction and classification assertions**

Run:

```bash
python3 - <<'PY'
import csv
import json
from pathlib import Path

root = Path("device/asus/sm8150-common/frankenstein/inventory")
static = json.loads((root / "static-inventory.json").read_text())
runtime = json.loads((root / "runtime-inventory.json").read_text())
assert static["schemaVersion"] == 1
assert runtime["schemaVersion"] == 1
for document in (static, runtime):
    for record in document["records"]:
        assert record["bucket"] == "UNCLASSIFIED"
        assert record["futureAppReachability"] == "UNTESTED"
        assert record["laterAccessPath"] == ""
        assert record["rationale"] == ""
        assert record["providerId"] == ""
        assert record["testId"] == ""
with (root / "classification.csv").open(newline="") as stream:
    rows = list(csv.DictReader(stream))
assert rows
assert all(row["bucket"] == "UNCLASSIFIED" for row in rows)
assert all(row["future_app_reachability"] == "UNTESTED" for row in rows)
assert all(not row["later_access_path"] for row in rows)
assert all(not row["rationale"] for row in rows)
assert all(not row["provider_id"] for row in rows)
assert all(not row["test_id"] for row in rows)
print(
    f"static={len(static['records'])} "
    f"runtime={len(runtime['records'])} "
    f"worksheet={len(rows)}"
)
PY
if rg -F "$FRANKENSTEIN_DEVICE_SERIAL" \
    device/asus/sm8150-common/frankenstein/inventory; then
  echo "raw device serial leaked into evidence" >&2
  exit 1
fi
```

Expected: exit 0 and nonzero counts.

- [ ] **Step 5: Re-run the complete unit suite after actual collection**

Run the full unittest discovery command from Task 5.

Expected: all tests pass.

- [ ] **Step 6: Inspect final changes without committing**

Run:

```bash
git -C vendor/bliss status --short
git -C device/asus/sm8150-common status --short
git -C vendor/bliss diff --check
git -C device/asus/sm8150-common diff --check
git -C vendor/bliss diff --stat
git -C device/asus/sm8150-common diff --stat
```

Expected: only the planned tool/test files and five inventory outputs are new.
No commit is created.

### Task 7: Stop For Review

- [ ] **Step 1: Produce the Stage 1 review packet**

Report:

1. Selected device serial SHA-256 and matching build fingerprint.
2. Source project commit IDs and repo-manifest SHA-256.
3. Counts by every record kind, plus static/runtime/worksheet totals.
4. Runtime commands and exit codes, plus confirmation that coverage has no
   `UNAVAILABLE` entry.
5. Five output SHA-256 values and proof both read-only verification passes left
   them unchanged.
6. Unit test command and exact pass count.
7. Files created and pre-existing dirty files untouched.
8. Known collection limitations.
9. Explicit statements:
   - no classification was performed;
   - no framework/runtime implementation was changed;
   - no build/flash/reboot/remount/property/SELinux mutation occurred;
   - Stage 2 was not started;
   - no commit was created.

- [ ] **Step 2: End the turn**

Do not propose classifications or begin Stage 2. Wait for human review.
