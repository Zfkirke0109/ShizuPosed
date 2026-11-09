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

## Work session verification on November 9, 2026

### Launcher regression and live ART evidence

- The script failed on `9b40662` with exit 1 at the explicit `unset BOOTCLASSPATH`. The patched branch passed with exit 0 on the host, and the classpath-only revision also passed on the S26.
- The classpath-only revision exposed a second launcher error. With both boot classpaths present, the process interpreted the Java class token as its command directory. A controlled two-class probe ran the wrong entry point with exit 0 and wrote no marker. With `/system/bin` in the command-directory slot, the intended main ran as UID 2000, wrote a 77-byte log and a matching marker, was alive after writing the marker, and exited 0. The source check was RED before the directory fix and GREEN after. See the [AOSP app_process parser](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/cmds/app_process/app_main.cpp#195).
- The original manager's crash buffer showed `SIGABRT` and the empty boot classpath assertion. The isolated QA DEX then reached `XposedHook.main` on API 37 and produced a 323-byte no-target log. A scoped CodePath invocation produced a 2,516-byte log, ran as UID 2000, exited, and created zero hook markers. The filtered crash window contained no matching `app_process64` entry. A negative invocation with AnyText itself as the target emitted no bootstrap log.
- The scoped run reached the UID boundary and logged `Continuing as uid 2000 (target uid 10520)`. Its subsequent hidden `IPackageManager.getApplicationInfo(String, int)` lookup also failed and the fallback found no Application. A separate probe using the final code's public `android.system.Os` calls confirmed `setgid failed: EPERM`, errno 1, with the effective UID still 2000. The fix records `UID_MISMATCH` and returns before module loading or post-Application fallback when the process cannot adopt the requested identity. A manager message saying that a detached spawn was accepted does not establish a hook.

### Build and package verification

Toolchain used: Temurin JDK 21.0.13, Gradle 9.8.2, AGP 9.4.1, SDK Platform 37.0 revision 2, Build-Tools 36.0.0 and 37.0.0. The Linux invocation supplied the SDK's `aapt2` override in place of the project's Termux path and ran with the configuration cache disabled.

```sh
sh scripts/test_bootstrap_classpath.sh
gradle --no-configuration-cache \
  -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/37.0.0/aapt2" \
  -Ps26BootstrapTest=true :app:clean :app:assembleDebug \
  :app:testDebugUnitTest :app:lintDebug :apkzlib:check
```

- QA build output: `BUILD SUCCESSFUL in 53s` and `67 actionable tasks: 47 executed, 18 from cache, 2 up-to-date`. Both strict scope tests passed. The final DEX output directory contains the payload, with the build's auxiliary class files staged separately.
- Standard debug and unsigned release output: `BUILD SUCCESSFUL in 1m 30s` and `118 actionable tasks: 77 executed, 9 from cache, 32 up-to-date`. Both manifests use `com.shizuposed.manager`, and their bootstrap asset differs from the QA build's. Debug and release lint each reported 0 errors and 642 warnings.
- QA APK: `com.shizuposed.manager.s26test`, version 1.4.4 (5730), min SDK 28, target SDK 37, Android Debug certificate with verified v2 signature. The status, Shizuku, watcher, FileProvider and AndroidX Startup authorities all use the clone's ID. Seven libraries are ELF64 AArch64. Basic ZIP alignment passed. The 16 KiB ZIP alignment check failed with the project's legacy extracted-library packaging. The tested S26 has 4 KiB pages.
- APK SHA-256: `fb9190fda76bee690a8c204e9bc10a9a61e01183ddc768fa51c73671d00f5bcb`. Packaged `assets/XposedHook.dex` SHA-256: `ea61ee56ca0d44ce9d172c8a2754d3e8941b13d8d2132e4da74f3c6a346fc7ec`.

### Device status and rollback

- DroidBridge reached its transfer capacity after 110 of 181 chunks for the signed APK. Package Manager reported the QA package absent. Live evidence came from the isolated intermediate bootstrap DEX and the separate shell identity probe. The final APK's installation and manager-to-Shizuku service path remain unverified. No AnyText visual effect was demonstrated and the draft remains open.
- The QA build uses `/data/local/tmp/shizuposed-s26-qa` for shell payloads, restricts the launch target to the CodePath demo, and accepts only AnyText with the demo as its exclusive scope. Standard XStealth, Next, Bridge, Sysku, cloak and update actions are disabled in the QA build. The original manager was left installed and CodePath's contacts permission remained denied. Device checks showed SELinux Enforcing, Verified Boot green, locked bootloader and warranty bit 0.
- To roll back a future QA installation, stop and uninstall **ShizuPosed S26 QA** (`com.shizuposed.manager.s26test`) only. The QA shell payload path is listed above for separate cleanup. Preserve the original manager and its `.syscall_cache`. To revert the source, use `git revert` on the follow-up code commit on this branch.
