# FlowBreak workstation recovery contract

`PHASE = PRE_WORKSTATION_MIGRATION_PREP`

This document is the handoff for restoring development on a primary laptop.
The repository is the source of truth for code; local evidence and signing
material are deliberately outside the repository.  Paths in examples are
examples only.  A recovery machine may use any working directory and drive.

## 0. Safety boundary for this phase

The phone, ADB, and a real device are currently unavailable.  Do **not** run
`adb`, `scrcpy`, or any other device command, and do not run connected
instrumentation tests.  Gate D (OEM/device matrix), Gate E (usage/latency),
and Gate F (24-hour/protection integrity) cannot be completed in this phase.
Building an AndroidTest APK is not executing a device test.

The current checkout contains known dirty generated files.  Preserve them and
inspect their diff; never discard them as part of recovery:

```text
app/android/app/capacitor.build.gradle
app/android/capacitor.settings.gradle
```

The following operations are prohibited unless the owner explicitly authorizes
them: `git reset --hard`, `git clean`, `git restore .`, `git checkout -- .`,
`git add .`, and `git add -A`.  Do not copy `node_modules`, `.gradle`, Android
build caches, APK caches, Git object databases, signing keys, passwords, or
credentials into a migration package.

`tools/Check-Workstation.ps1` is a read-only audit.  It does not install
software, invoke Gradle, probe a device, inspect authentication state, or read
secret values.

## 1. GitHub source of truth

| Item | Value |
| --- | --- |
| Repository | `https://github.com/guo6x/flowbreak.git` |
| Remote branch model | `master` only |
| Expected baseline `master` SHA (this handoff) | `0cae8b2ffbf24c6869f842efce646b5e29c85d0e` |

Verify identity without changing the working tree beyond the requested fetch:

```powershell
git fetch origin --prune
git status --short
git rev-parse HEAD
git rev-parse origin/master
```

The two SHA values should be compared with the expected baseline and with the
handoff record.  A changed upstream SHA is a review point, not a reason to
reset local files.  On a fresh laptop, clone and check out `master`:

```powershell
$repo = 'C:\work\flowbreak'       # choose a path suitable for this machine
git clone https://github.com/guo6x/flowbreak.git $repo
Set-Location $repo
git checkout master
git pull --ff-only origin master
git status --short
```

If a pre-existing checkout is dirty, stop before destructive commands and
preserve the files for owner review.

## 2. Dependency inventory

Record the actual executable path and version on the destination machine.  A
tool being present is not enough: record whether it is required for offline
development or only for a later gate, and whether it is machine-managed or
repo-managed.

| Tool | Requirement / verification | Role and ownership |
| --- | --- | --- |
| Git | `git --version`; repository and remote are `guo6x/flowbreak`, `master` | Required; machine-managed |
| GitHub CLI | `gh --version` (do not run `gh auth status` in this audit) | Optional for local build, useful for PR/CI; machine-managed |
| Node.js | `app/package.json` engines: `>=22.22.0 <23` (CI uses 22.23.1) | Required; machine-managed |
| npm | `npm --version`; install from `app/package-lock.json` with `npm ci` | Required; machine-managed (bundled with Node) |
| JDK / Java | JDK 21; `java -version` | Required; machine-managed |
| `keytool` | Must be the JDK 21 `bin/keytool` | Required for public certificate/provenance verification; machine-managed |
| Android SDK | Platform `android-36` (compile), target API 35, min API 24 | Required; machine-managed |
| Android Build Tools | Stable `35.0.0` or newer compatible with API 36; `aapt2`, `apksigner`, and `zipalign` must exist | Required for Android build/provenance; machine-managed |
| Android platform-tools / `adb` | `platform-tools/adb`; presence may be recorded, but no command is run in this phase | Needed when device gates resume; machine-managed |
| Gradle wrapper | `app/android/gradlew.bat`, `gradlew`, wrapper JAR/properties; wrapper distribution is Gradle 8.13 and AGP is 8.13.0 | Required and repo-managed; wrapper downloads only when a build is intentionally run |
| PowerShell | Windows PowerShell 5.1+ or PowerShell 7+ | Required to run the recovery script; machine-managed |
| 7-Zip | `7z.exe` or `7zz.exe` | Optional until Gate G portability work; machine-managed; do not run/create a vault in this phase |

The current temporary workstation uses `D:\environment\` for some tools.  It
is an inventory example, not a canonical path and must not be copied as a
requirement.  On another machine, record only FlowBreak dependencies found in
that directory (if it exists), not unrelated personal software.

If the current machine has `D:\environment\flowbreak-env.ps1`, it is only a
session-local convenience file for pointing Java/Android tools at that
directory.  Revalidate every path with `Check-Workstation.ps1`; do not treat
that helper, its paths, or its caches as part of the source recovery contract.

Use this worksheet (or the output of `Check-Workstation.ps1`) when recording a
machine.  Replace the angle-bracketed fields with non-sensitive values; keep
the worksheet out of Git if it contains a private local path.

| Tool | Actual executable path | Actual version | Required for recovery? | Owner |
| --- | --- | --- | --- | --- |
| Git | `<record>` | `<record>` | YES | machine-managed |
| GitHub CLI (`gh`) | `<record or ABSENT>` | `<record or ABSENT>` | OPTIONAL | machine-managed |
| Node.js | `<record>` | `<record>` | YES | machine-managed |
| npm | `<record>` | `<record>` | YES | machine-managed |
| Java (JDK 21) | `<record>` | `<record>` | YES | machine-managed |
| `keytool` | `<record>` | `<record>` | YES | machine-managed |
| Android SDK / platform 36 | `<record>` | `<record>` | YES | machine-managed |
| Build Tools (`aapt2`, `apksigner`, `zipalign`) | `<record>` | `<record>` | YES | machine-managed |
| `adb` / platform-tools | `<record or ABSENT>` | `NOT PROBED IN THIS PHASE` | DEVICE PHASE ONLY | machine-managed |
| Gradle wrapper | `app/android/gradlew(.bat)` | `8.13` (checked in) | YES | repo-managed |
| PowerShell | `<record>` | `<record>` | YES | machine-managed |
| 7-Zip | `<record or ABSENT>` | `<record or ABSENT>` | GATE G ONLY | machine-managed |

## 3. Environment-variable audit

Only these non-secret names and their non-sensitive path values are relevant:

| Variable | What to record |
| --- | --- |
| `JAVA_HOME` | `PRESENT`/`ABSENT`; if present, the JDK root path |
| `ANDROID_HOME` | `PRESENT`/`ABSENT`; if present, the SDK root path |
| `ANDROID_SDK_ROOT` | `PRESENT`/`ABSENT`; if present, the SDK root path |
| `PATH` | Only entries that point to the tools above (Git, Node/npm, JDK, SDK platform-tools/build-tools, `gh`, or 7-Zip) |

Never print or copy token/password/credential values, keystore passwords,
keystore contents, or recovery secrets.  If a sensitive variable is noticed,
record only `PRESENT` or `ABSENT`; do not enumerate the environment block.
The recovery script follows this rule and does not inspect `FLOWBREAK_*`
signing variables or authentication state.

## 4. Restore the JavaScript workspace

From the fresh clone:

```powershell
Set-Location "$repo\app"
npm ci
```

`npm ci` is intentionally lockfile-driven.  Do not substitute `npm install`
for a clean recovery, and do not copy a `node_modules` directory from another
machine.

Run the read-only workstation audit from the repository root:

```powershell
Set-Location $repo
powershell -ExecutionPolicy Bypass -File tools/Check-Workstation.ps1
```

The script prints `PASS`, `WARN`, and `FAIL` records, lists missing required
items, and ends with `WORKSTATION_READY = YES` only when no required check is
failed.  Warnings for ADB, `gh`, and 7-Zip are non-blocking for offline coding
but must be resolved before their corresponding gate or workflow is resumed.

## 5. Build and test commands

All commands below are run only after the workstation has the requirements in
the inventory.  On Windows use `gradlew.bat`; on a POSIX shell use `./gradlew`.

### Frontend and provenance-tool tests

```powershell
Set-Location "$repo\app"
npm test
npm run test:provenance
npm run test:release
npm run build
```

`npm run build` is the TypeScript + Vite production build and writes `dist/`.

### Capacitor sync and Android JVM/build checks

After every frontend change, keep this order in one workflow:

```powershell
npx cap sync android
Set-Location android
.\gradlew.bat testPlayDebugUnitTest testDomesticDebugUnitTest
.\gradlew.bat lintPlayDebug lintDomesticDebug
.\gradlew.bat assemblePlayDebug assembleDomesticDebug
.\gradlew.bat bundlePlayRelease assembleDomesticRelease
```

The release tasks above produce unsigned release artifacts unless signing is
explicitly provisioned in the protected release workflow.  A no-device
instrumentation APK may be assembled for packaging checks:

```powershell
.\gradlew.bat assemblePlayDebugAndroidTest
```

That task only assembles an APK; it does not run a connected test.  Do not use
`connected...AndroidTest`, `adb`, or `scrcpy` while the phone is unavailable.

## 6. Provenance build order

The provenance chain must describe the exact source that was built.  The
canonical order is:

1. Confirm `git rev-parse HEAD`; keep the worktree reviewable.
2. In `app/`, run `npm ci`, then `npm run build`.
3. Set safe build metadata (`SOURCE_COMMIT_SHA` and `GITHUB_SHA` to the source
   SHA; `VERSION_NAME`/`VERSION_CODE` from `node scripts/release-version.mjs`).
   The release-version script prints only `VERSION_NAME=...` and
   `VERSION_CODE=...` on stdout.
4. Run `node scripts/generate-build-provenance.mjs dist`.
5. Run `npx cap sync android` so the frozen `dist/build-provenance.json` and
   web assets are copied into Android assets.
6. Run `node scripts/verify-web-asset-sync.mjs dist android/app/src/main/assets/public`.
7. Build the required Play and Domestic JVM/lint/debug/release tasks with the
   Gradle wrapper.
8. For a controlled artifact handoff, run
   `node scripts/generate-artifact-manifest.mjs` with the built binaries,
   mapping files, and provenance JSON as shown in
   `.github/workflows/android.yml`.  Recalculate and verify `SHA256SUMS.txt`.

The CI workflow additionally reads provenance back from APK/AAB contents and
checks independent version metadata.  A local build must not claim that CI or
real-device gates passed.  Provenance and manifests contain allowlisted public
metadata only; they must never contain secrets, tokens, passwords, or JKS
files.

## 7. Known generated/dirty-file behavior

`npx cap sync android` regenerates Capacitor Android settings and web assets.
It may change these generated files, which are currently known dirty and must
be preserved during migration:

```text
app/android/app/capacitor.build.gradle
app/android/capacitor.settings.gradle
```

Gradle/Vite output directories are build products and should be regenerated on
the destination machine, not copied.  Before and after sync, inspect
`git status --short` and the targeted diff.  Do not use reset/clean/restore to
make the worktree appear clean, and do not stage unrelated files.

## 8. Git change strategy for this preparation

When the change set is limited to this recovery document and check script, use
one temporary branch named `chore/workstation-recovery`.  Do not open a second
branch for the same preparation, and do not merge/rebase/reset or force-push as
part of the handoff.  The owner may run the following gates from that branch:

```powershell
Set-Location "$repo\app"
npm test
npm run test:provenance
npm run test:release
npm run build
Set-Location android
.\gradlew.bat testPlayDebugUnitTest testDomesticDebugUnitTest
```

Open the normal pull request, wait for CI to pass, merge to `master`, and let
the temporary branch be deleted according to repository policy.  Afterward
verify that the remote branch model is again `master` only.  Keep the known
dirty generated files out of unrelated commits and preserve their provenance.

## 9. Gate status at this handoff

This phase carries the following frozen status from the migration request:

| Gate | Status |
| --- | --- |
| A — Core automated validation | PASS |
| B — Redmi targeted revalidation | PASS |
| C — Artifact provenance / controlled unsigned build | PASS |
| D — OEM compatibility matrix | PENDING |
| E — Usage accounting / latency | PENDING |
| F — 24-hour stability / protection integrity | PENDING |
| G — Signing/versioning/publishable build | `PAUSED_UNTIL_PRIMARY_WORKSTATION_FINAL_KEY_GENERATION` |
| H — Store/compliance readiness | PENDING |
| I — Small-scale beta | PENDING |

Gate G is intentionally frozen.  The current temporary workstation must not
generate final Play/Domestic identities, create a portable vault, create a
release tag, or run the portable signing scripts.  `SEC-PORTABILITY-001`
records that `New-PortableSigningVault.ps1` and
`Test-PortableSigningVault.ps1` still have process-command-line secret
exposure risk.  This phase is **DO NOT FIX / DO NOT RUN / DO NOT CREATE VAULT**;
the handoff requirement is `MUST_FIX_BEFORE_GATE_G_RESUME`.

## 10. What cannot be completed without a phone

No-device work can cover frontend tests, provenance tooling tests, JVM tests,
lint, debug/release assembly, and static artifact checks.  It cannot establish
real-device evidence for:

- Gate D OEM permissions, background limits, restart recovery, or compatibility;
- Gate E UsageStats accuracy against system wellbeing and measured blocking
  latency;
- Gate F 24-hour stability, service/protection integrity, or failure-injection
  behavior;
- signed install/upgrade and any connected instrumentation behavior.

Keep these gates `PENDING`; do not infer a device `PASS` from a successful
Gradle build or from an assembled AndroidTest APK.  If an authorized future
device attempt cannot see the specified handset, record `DEVICE_UNAVAILABLE`
and stop before build/install/trials; do not substitute another device.

## 11. Resume point on the primary laptop

1. Fresh-clone `master` from GitHub and verify the source SHA.
2. Run `tools/Check-Workstation.ps1`; resolve required `FAIL` items.
3. Run `npm ci`, frontend/provenance tests, and the documented build order.
4. Copy only the external local-evidence migration directory (the current
   workstation example is `D:\ai_code\flowbreak-migration\`; another machine
   may choose a different location); verify its `SHA256SUMS.txt` before using
   any evidence.
5. Reconfirm the Git SHA and review the two known generated dirty files.
6. When a phone is available, resume the authorized Gate D/E/F device plan.
7. Resume Gate G final identity generation **only on the primary laptop**, then
   follow the separate signing-custody and cross-machine recovery procedure.

The code should always be recovered from GitHub.  Local migration material is
evidence and handoff metadata only, never a replacement for a fresh source
clone.

For the external migration handoff, keep the directory limited to local-only
evidence, `LOCAL_ONLY_INVENTORY.md`, `WORKSTATION_HANDOFF.md`, and a checksum
manifest (`SHA256SUMS.txt`; an optional
`flowbreak-local-evidence-migration.zip` may wrap the same files).  Recompute
the manifest after staging and verify it a second time before handoff, then
record `MIGRATION_CHECKSUM_VERIFY = PASS`.  Record
`secrets copied = NO`, `JKS copied = NO`, and `credentials copied = NO`.
The preparation report should end with `WORKSTATION_SWITCH_READY = YES` only
when the required recovery checks and checksum verification are actually
green; otherwise use `NO` and list the blocker.
