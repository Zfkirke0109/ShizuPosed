# Galaxy S26 Ultra / Android 17 — ShizuPosed bootstrap fix handoff

## Source and isolation
- Upstream: `Sharif-bot-cmd/ShizuPosed`
- User's existing writable fork: `Zfkirke0109/ShizuPosed`
- Working branch: `fix/s26-android17-bootstrap-env-20261109`
- Baseline upstream and fork `main`: `bdb6ae7deaa3560e9588b68a10ea94fa09c929c4`
- **Do not** overwrite or uninstall the user's existing `com.shizuposed.manager` 1.4.4 installation during experimentation. Test using a separately signed/package-identified clone where feasible, or an explicit user-approved update with a complete backup/rollback plan.

## Observed S26 environment (live DroidBridge, 2026-11-09)
- Samsung SM-S948U, Android 17/API 37, arm64-v8a; locked bootloader, no root.
- ShizuPosed 1.4.4 (versionCode 5730), Xposed API 101, Shizuku authorization via Shevery(stub) v13 and shell UID 2000.
- XStealth standard enabled; XStealth Next and Bridge disabled for test.
- `codepath.apps.demointroandroid` (CodePath Android Demo) installed as disposable test target.
- `com.hhvvg.anytext` (AnyText 1.0.2) installed/enabled with **only** that test app scoped.
- Legacy `SampleModule.apk` was **not** installed: Android rejected targetSdk 23 with `INSTALL_FAILED_DEPRECATED_SDK_VERSION`. Do not bypass Android's minimum-target security policy.
- READ_CONTACTS permission for the demo target was not granted. Don't grant it.
- Ordinary shell environment had `BOOTCLASSPATH` length 3278 bytes, `DEX2OATBOOTCLASSPATH` length 865 bytes; `SYSTEMSERVERCLASSPATH` length 1229 bytes.

## Failure reproduction
ShizuPosed's process watcher detects a target launch, then issues a detached `/system/bin/app_process64` bootstrap. It reports `spawn accepted`, but `hooked/` contains zero marker files and `launch.log` is empty.
Native crash buffer on Nov 9 includes `SIGABRT` in `app_process64` (also occurred at ~08:30 and ~09:26 before this disposable test).
Android Runtime abort message:
```
Check failed: !location.empty() BOOTCLASSPATH and DEX2OATBOOTCLASSPATH must not be empty
```
Relevant source: `app/src/main/java/com/shizuposed/manager/service/ShizuPosedService.java`, original line ~2439:
```java
String inner =
    "unset CLASSPATH; unset BOOTCLASSPATH; "
    + "( " ...
```
This explicitly removes the inherited ART boot classpath required for Java VM creation on this Android 17 image. The native failure precedes any module hook.

## Change already committed
- Test first: `scripts/test_bootstrap_classpath.sh`
- RED on unpatched source: failed, reported original `unset CLASSPATH; unset BOOTCLASSPATH;` line.
- Fix: launcher now begins with `"unset CLASSPATH; "`, leaving `BOOTCLASSPATH` and `DEX2OATBOOTCLASSPATH` inherited.
- GREEN on patched branch: `PASS: launcher preserves BOOTCLASSPATH and DEX2OATBOOTCLASSPATH`.
- Static regression passed on S26 shell; **no patched APK was built or installed** yet. Injection remains unverified.

## Next engineering work
1. Pull the working branch and review the exact change against upstream. Avoid unnecessary modifications to XStealth, native hooks, service lifecycle, or sensitive installed apps.
2. Run reproducible Gradle compilation for API 37 with Java 21, then the current project checks. Confirm signing/package requirements; a normal debug build signed with a different key **cannot update** the upstream-signed `com.shizuposed.manager`.
3. Perform a separate-package clone build or equivalent safe test plan, ensuring manifest authorities and app-id-dependent lookups are changed coherently and *not* invoking manager cloak/migrate flow that uninstalls the existing app.
4. In a controlled no-root test, validate process starts without `SIGABRT`, nonempty `launch.log`, marker creation, and demonstrable Xposed effect in CodePath Demo (AnyText must be configured to change a visible TextView and compared with baseline).
5. Capture concise filtered logcat, actual exit codes, ART bootstrap errors, module hook successes/failures, exact SHA-256 of built APK. Distinguish `spawn accepted` from genuine hook execution.
6. Verify regression/rollback: keep Shizuku, DroidBridge, ChatGPT, Termux/Sidekick, other apps and Knox untouched; preserve existing installed ShizuPosed, and remove disposable clone/tests only with user approval.
7. Produce a tested APK artifact and draft PR, with evidence and warnings where verification is still pending.

## Device reference paths
- `/sdcard/Download/ShizuPosed-Hook-Test/`: DemoTextApp.apk, SampleModule.apk (uninstalled), AnyText-1.0.2.apk, test-report-20261109.txt
- `/sdcard/Download/ShizuPosed-Bootstrap-Work/source/`: the extracted regression test and patched ShizuPosedService.java
- `/data/user/0/com.android.shell/files/.syscall_cache/`: manager's shell-side bootstrap payload and marker directory. **Inspect only; do not wipe.**

## Definition of done
Compiled, installable, safely isolated APK plus evidence that the app process survives ART initialization, that AnyText's hook executes on the scoped demo app, and that existing user applications remain functional. Do not call the test passed merely because a module is enabled or the manager says activated.
