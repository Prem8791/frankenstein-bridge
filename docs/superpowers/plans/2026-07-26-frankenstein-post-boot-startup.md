# Frankenstein Post-Boot Startup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent the Frankenstein bridge and diagnostic daemon from starting until Android reports boot completion.

**Architecture:** `SystemServer` registers a failure-contained one-shot boot-completed receiver instead of synchronously starting the bridge before Package Manager. The receiver starts and retains the bridge asynchronously after `ACTION_BOOT_COMPLETED`; init keeps the native daemon disabled until `sys.boot_completed=1`.

**Tech Stack:** Android framework Java, `system_server`, Android init RC, Binder, LocalServices.

## Global Constraints

- No Frankenstein constructor, `onStart`, provider, Binder publication, or daemon work may run before boot completion.
- Any post-boot bridge startup failure must be caught and must not terminate `system_server`.
- Do not build the ROM in this task.

---

### Task 1: Framework post-boot gate

**Files:**
- Modify: `frameworks/base/services/java/com/android/server/SystemServer.java`
- Test: static source assertions executed against `SystemServer.java`

**Interfaces:**
- Consumes: `Intent.ACTION_BOOT_COMPLETED`, `BackgroundThread.getExecutor()`.
- Produces: one retained `FrankensteinBridgeService` instance started after boot completion.

- [ ] Remove the synchronous `startService(FrankensteinBridgeService.class)` block before Package Manager.
- [ ] Add a one-shot receiver registration near the end of `startOtherServices`.
- [ ] In the receiver, dispatch initialization to `BackgroundThread`, catch `Throwable`, retain the service, call `onStart()`, then replay phases 500, 600, and 1000.
- [ ] Guard receiver registration and asynchronous startup independently so neither failure propagates into boot.
- [ ] Verify statically that no synchronous bridge start remains and the boot-completed/catch-all gate exists.

### Task 2: Native daemon post-boot gate

**Files:**
- Modify: `system/core/frankenstein_diag/frankenstein_diag.rc`
- Test: `host_init_verifier` and static source assertions.

**Interfaces:**
- Consumes: Android property `sys.boot_completed=1`.
- Produces: init start request for `frankenstein_diag` after boot completion.

- [ ] Add `disabled` to the service stanza.
- [ ] Add `on property:sys.boot_completed=1` with `start frankenstein_diag`.
- [ ] Preserve `class late_start` and keep the service non-critical.
- [ ] Verify RC syntax and assert the post-boot trigger and absence of critical/reboot directives.
