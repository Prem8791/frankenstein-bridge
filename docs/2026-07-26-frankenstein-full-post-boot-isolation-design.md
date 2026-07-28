# Frankenstein Full Post-Boot Isolation

## Objective

Make Android boot success completely independent of every Frankenstein component. A bridge,
diagnostic daemon, provider, or hook failure may disable Frankenstein functionality, but must not
delay or terminate `init`, Zygote, `system_server`, Package Manager, System UI, or boot completion.

## Lifecycle contract

1. `SystemServer` does not construct or start `FrankensteinBridgeService` in any synchronous boot
   phase.
2. After Android has completed the normal boot sequence, `SystemServer` schedules bridge startup on
   a dedicated asynchronous handler.
3. The asynchronous startup boundary catches every `Throwable`, logs the failure, and does not
   propagate it onto a critical framework thread.
4. `FrankensteinBridgeService` initializes directly into the post-boot state: Package Manager is
   available, framework providers may become ready, external registration may be enabled, and a
   boot-completed health event may be emitted.
5. Starting the bridge more than once is prevented by an explicit process-local state guard.
6. Framework capture hooks continue to enqueue work off their callers' locks. If the bridge
   `LocalService` is absent, they discard the event without retrying, blocking, or throwing.
7. `frankenstein_diag` is an init-disabled, non-critical service. Init starts it only after
   `sys.boot_completed=1`.
8. The bridge uses non-blocking diagnostic-daemon discovery. Daemon absence or death changes only
   diagnostic provider availability.

## Failure containment

- Bridge constructor, `onStart`, registration, Binder publication, provider initialization, and
  daemon connector failures are contained after boot completion.
- Diagnostic daemon executable, linker, SELinux, Binder registration, or runtime failures cannot
  affect Android boot and may be retried by init after boot.
- No Frankenstein component uses `critical`, `reboot_on_failure`, a pre-boot property trigger, or a
  blocking service wait.

## Verification contract

Static checks must establish:

- no synchronous Frankenstein start remains before boot completion;
- post-boot startup has an idempotent guard and a catch-all failure boundary;
- the daemon RC is `disabled`, remains non-critical, and has only a
  `sys.boot_completed=1` start trigger;
- hooks tolerate an absent `LocalService`;
- init RC syntax remains valid.

Runtime testing is intentionally deferred until the complete ROM is built and installed.
