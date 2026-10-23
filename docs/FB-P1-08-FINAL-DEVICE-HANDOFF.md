# FB-P1-08 — Final short device handoff (GATE F remains FAIL)

**Scope:** Redmi Note 13 Pro 5G / Android 16 / HyperOS, Domestic channel, v1.1.0 supported-scope only. This document is a test recipe, **not** evidence of success.

## Current applicability — 2026-10-23

**HISTORICAL_RECIPE / CURRENT_DIAGNOSTIC_CONTRACT_NOT_READY.** Sections 0–4 below preserve the 2026-10-10 PR #29 recipe; they are not an executable acceptance protocol for the PR #42 integration candidate. PR #31 has since merged. The integration candidate's sanitized `shareDiagnostics()` output is built from an explicit allowlist and **excludes `runtimeTracking`**; its current native diagnostics output does not expose a `restCheatEvidence` JSON object. The counter implementation and deterministic tests are not proof that these fields can be exported from the current app.

Do not run the historical recipe expecting `runtimeTracking.restCheatEvidence` in a shared export, interpret absent fields as zero observations, or widen the diagnostic sharing allowlist to satisfy the recipe. A future separately authorized device run first needs a source-bound, privacy-reviewed diagnostic contract that is actually available in that build. Use only verified current fields and report missing evidence as `INCONCLUSIVE` / `NOT_EXECUTED`. Full local data export is broader and is not a substitute for sanitized diagnostics.

This cleanup changes no native collection or sharing behavior, authorizes no phone or production-signing operation, and does not close Issue #28 / Gate F. Ordinary engineering merge status is tracked in PR #42 and does not certify device behavior or the 24-hour run.

## 0. Historical no-phone work prepared (2026-10-10)

- PR #29: native REST UI reconciliation; asynchronous BEGIN_REST acknowledgment; passive, package-free native anti-cheat diagnostic counters and persisted REST status; deterministic Robolectric UsageEvents integration tests for approximately 33 seconds of Bilibili foreground usage.
- PR #31: patched Capacitor 8.4.3 security upgrade, separate PR with successful complete CI, still unmerged.
- Validation-only PR #32: PR #29 overlaid with PR #31's npm manifest/lockfile so the security audit stays enforced. **CI success on this branch is not Gate F device approval**.
- Existing UI: Profile → 效果验证与运行诊断 → 导出脱敏诊断. Plugin API: `shareDiagnostics()`. This exports the new fields; there is no need to create or permission an additional data-collection application.
- **Never treat** `assemblePlayDebugAndroidTest` as executed instrumentation tests; it only assembles an APK.

## 1. Historical preconditions for an eventual true-device run

Do **not** consume the user's phone yet. Before booking a short test slot:

1. Confirm the *exact approved source commit* and recorded CI run, including PR #29 changes and patched Capacitor dependencies. Verify `sourceGitSha` / artifact-manifest, APK web-asset provenance, binary version and package identity all refer to that source. A PR32 **unsigned** artifact is not installable as a production replacement.
2. Build/sign the Domestic APK using the established **final production signing identity** in the authorized signing environment only. Verify certificate SHA-256 with `apksigner verify --print-certs` against both the already-installed application's certificate and the repository's signing allowlist. Never upload the key, passwords, recovery archive, or signing environment to this repo, chats or a public CI job.
3. Check installed versionCode and new versionCode; use a legitimate non-downgrading, same-package replacement. Preserve Room and SharedPreferences. **Do not** `adb uninstall`, `pm clear`, `adb install -r -d`, or change user permissions/security/target-app configuration to make a test pass.
4. Keep raw local device evidence outside GitHub. Only include minimized redacted diagnostic fields and observed state transitions in an issue comment.

If any precondition fails, result = `NOT_EXECUTED`, not PASS.

## 2. Historical short, user-authorized device session

Record the device time and APK SHA/certificate. Note the existing list of selected target apps, particularly whether `tv.danmaku.bili` is present. Confirm Usage Access, overlay permissions and service/runtime health without changing them.

1. Open FlowBreak, begin a *verified* native REST session; only count the test once `state == RESTING`, `persistedBlockState == RESTING` and `persistedRestSessionPresent == true`.
2. Capture a baseline sanitized diagnostics export and the REST countdown (timestamps).
3. While still resting, open Bilibili as the actual foreground app for **at least 15 seconds** (33 seconds can be used to reproduce the original failure). Return to FlowBreak and immediately capture the visible state and a new sanitized diagnostics export.
4. For cumulative behavior, perform a *fresh* rest: target app → safe non-target foreground for a real interval → target app again, totaling more than 5 seconds of target-foreground observations. Record the observed native cancellation and export, not merely the UI countdown.
5. Recheck prior Gate F passing scenarios (screen off, keyguard, safe non-target interval, Usage Access fail-closed) using the established acceptance criteria. A 24-hour stability PASS additionally requires real elapsed, device-backed evidence; automated CI cannot establish it.

The run may be split into separately documented device sessions rather than monopolizing the user's phone. Stop immediately if the user needs the device or any installation/permission check cannot be satisfied.

## 3. Historical evidence interpretation (not the current export schema)

The root JSON includes `state`, `persistedBlockState`, `persistedRestSessionPresent`, `serviceAlive`, `monitoringEnabled`, `targetCount` and `runtimeTracking`.

Within `runtimeTracking`, inspect:
- `tickCount`, `monitorThreadAlive`, `heartbeatFresh`, `scheduler`, `lastLiveUsageQuerySucceeded`: was the monitor actually running reliably?
- `runtimeTargetsMatchPersisted`, `reasonCounters.foregroundInRuntimeTargetTickCount`, `reasonCounters.classifierFalseForegroundInRuntimeTargetCount`: was Bilibili in the real runtime target set and actually classified?
- `restCheatEvidence.sessionObservations`, `sessionPausedObservations`, `sessionTargetObservations`, `lastForegroundInTargets`, `lastIsTarget`: did the *paused vs active* REST inspection path see it?
- `restCheatEvidence.lastAccumulatedMs`, `maxAccumulatedMs`, `thresholdMs` (expected 5000), `sessionHistoricalReplayMs`: did cumulative verified time reach the threshold?
- `restCheatEvidence.cancellationCount`, `lastCancelledAccumulatedMs`, `lastCancellationAt`: did the native cancel path run?

Use **changes relative to the baseline** to avoid accidentally attributing an older session's lifetime cancellation counter to the current run. This diagnostic is in-memory process-local, not a persistent forensic journal; if the process is restarted, record that as an integrity/liveness event rather than claiming that zero counters prove no prior cancellation.

| Observation | Next owner / action |
| --- | --- |
| Missing/stale monitoring ticks or unsuccessful UsageEvents query | Monitor lifecycle / Android scheduler / usage permissions; don't accuse tracker |
| Healthy ticks but Bilibili not recognized or runtime targets differ | ForegroundUsageDetector / target snapshot / configuration |
| Foreground belongs to runtime targets but target classifier false | TargetAppClassifier / channel-specific branch |
| Repeated target observations but cumulative stays below 5000 | Observation timestamps, paused/normal path and execution gaps |
| Cumulative reaches 5000, cancellation count does not advance, REST remains persisted | RestCheatReplay → BlockStateMachine → Service cancellation integration |
| Native cancellation advances and persisted state leaves RESTING, but old countdown is visible | WebView REST UI and resume reconciliation |
| Data unavailable, test not signed with approved key, or results ambiguous | `INCONCLUSIVE`; collect a second short run, **never PASS by inference** |

## 4. Historical release decision

After actual device revalidation, attach a signed-source SHA, exact installed binary SHA-256 and certificate comparison, minimized diagnostic deltas, short timeline and scenario-by-scenario PASS/FAIL/NOT_EXECUTED table to Issue #28. **Only with real accepted evidence** may the release commander reconsider `FB-P1-08` and GATE F. Do not merge PR #29, publish to a store, or declare GATE F complete based on this handoff or Android JVM/CI tests.

The decision recorded on 2026-10-10 was `GATE_F=FAIL`, `PR29_MERGE=WAIT_FOR_COMMANDER`, `PRODUCTION_RELEASE=NOT_APPROVED`. Current engineering branch/PR lifecycle is separate; Gate F remains FAIL until actual accepted device evidence and the required stability run exist.
