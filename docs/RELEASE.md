# RELEASE — 发布门禁与发布状态

> 发布门禁是**真正的门禁**：任何一项 release-blocking gate 未通过即不可发布。易变的候选 SHA/CI 和当前门禁裁定见 [CURRENT_STATUS.md](CURRENT_STATUS.md)，可复验的 SHA 绑定证据见 [TESTING.md](TESTING.md)。
> 历史发布检查表（含旧业务规则）归档于 `docs/archive/progress/RELEASE_CHECKLIST.md`。

## 验收范围调整（2026-10-25，产品负责人决定）

- **固定 24 小时连续运行不再是验收或交付前置条件（`FIXED_24H_ACCEPTANCE=NOT_REQUIRED`）**，改由负责人及自愿试用者在日常使用中反馈稳定性问题；不设置最低使用时长。旧记录中的 `NOT_EXECUTED` 保留为历史事实，不改成 PASS，也不要求补做该固定时长任务。
- 保留核心场景短测、状态真实性、安全关闭、数据与隐私检查。`FB-P1-08` 等真实失败不会因取消时长要求自动关闭，Gate F 当前仍为 FAIL。
- 不要求借用负责人的日常 Redmi。未来兼容候选按实际机型、系统与构建来源记录结果，不预先限定必须是哪一部手机；当前 vivo/iQOO 品牌拒绝仍是代码事实，解除前须定位 `FB-P1-05` 并复验，不能把安全拒绝算作兼容成功。
- 代码、兼容修复、构建复验、既有签名核验、升级/恢复检查及商店材料纳入**同一批完整收尾**，不再以“留给以后”为处理结论。发行人身份、账号、真实资质和平台审批是外部依赖；明确缺项，不伪造、不代签，也不擅自提交或发布。

## 当前发布状态

**RELEASE PREPARATION · NOT APPROVED**。本轮包含运行时代码、回归测试、验证工作流及发行资料，不是 docs-only 变更。PR #29 仍使用 `fix/fb-p1-08-protection-integrity`，保留现有视觉与功能；源码 SHA、测试数量、产物指纹及精确 CI 锚点集中记录在 [CURRENT_STATUS.md](CURRENT_STATUS.md) 和 [TESTING.md](TESTING.md)。最终推送 HEAD 必须通过自己的 [PR #29 checks](https://github.com/guo6x/flowbreak/pull/29/checks)，不能继承历史绿色 Run；PR 的签名 release job 按策略 SKIPPED，不是签名验收 PASS。未合并 master、未使用生产签名、未创建 release。

- 当前本地代码验证、自动化数量与边界见 [TESTING.md](TESTING.md)；Room schema v4 与 1→2→3→4 迁移、诊断实际数据库版本状态和重复事件幂等均有自动化覆盖。最终 SHA 的 hosted 结果必须以 PR checks 的精确 HEAD 为准。
- npm 审计来源必须分开：本地官方 `https://registry.npmjs.org` 的生产依赖 high 与全依赖 critical 审计为 **PASS / 0 vulnerabilities**；本地配置镜像 `https://registry.npmmirror.com` 审计端点 404/`NOT_IMPLEMENTED`，明确为 **BLOCKED / NOT_PASS**。GitHub CI 的独立结果必须绑定它实际检查的 source SHA 与 Run，见 [验证快照](TESTING.md)；官方 registry 或 hosted 的成功不改写镜像阻塞，历史 SHA 的成功也不覆盖后继提交。
- **GATE F = FAIL / NOT_CLOSED**：候选核心场景与状态真实性的设备复验未完成，历史约 33 秒 REST 倒计时异常根因仍未通过新证据排除；固定 24 小时验收已按负责人决定移除，不再列为阻塞项。当前 iQOO 代码仍拒绝启用保护；此前对旧候选的 Play Debug 安装返回 `INSTALL_FAILED_ABORTED: User rejected permissions`，安装未完成，未启动或改变权限，既有 `.cn` 应用未触碰。未来使用哪台获授权设备，取决于兼容候选与实际可用条件，不要求提供日常 Redmi。见 [设备验收交接](FB-P1-08-FINAL-DEVICE-HANDOFF.md)。
- **GATE G = PENDING**：`CROSS_MACHINE_SIGNING_RECOVERY=NOT_YET_TESTED`，`TRUE_VERSION_UPGRADE=NOT_TESTED`。本地恢复、已有安装/同签名覆盖安装与 off-machine backup 记录不能替代这两个独立验证；本候选没有签名发布包。
- **GATE H = PENDING**：负责人已提供支持邮箱 `15015091629@163.com`，收发验证尚未执行；发行主体、公开隐私政策 URL、最终包网络/依赖审查、平台申报与审核资质仍未完成。静态扫描不等于运行时流量证明。
- **GATE I = PENDING / NOT_STARTED**：真实小规模 Beta 未开始。
- **绝对门禁**：`STORE_READY=NO`；`PRODUCTION_RELEASE_APPROVED=NO`；不得创建正式发布 tag、使用生产密钥进行未授权发布或将现有失败门禁标为通过。

## 发布门禁总览

| Gate | 内容 | 状态 |
| ---- | ---- | ---- |
| GATE A | Core automated validation | **Local checks PASS; final pushed PR HEAD must pass hosted verify before any merge decision** |
| GATE B | Redmi targeted P1/P2 revalidation | **Historical R1–R4 only; current candidate smoke NOT_EXECUTED** |
| GATE C | Artifact Provenance / Controlled Release Build | **Source-bound local unsigned verification is recorded in TESTING; the final pushed HEAD must use its own successful PR artifact. Historical binaries do not certify a successor. These are engineering artifacts, not signed releases** |
| GATE D | Supported OEM scope + fail-closed boundary | **Prior PASS_SUPPORTED_SCOPE; current iQOO install smoke NOT_EXECUTED** |
| GATE E | Usage accounting accuracy / blocking latency + documented Android execution boundary | **Prior PASS_SUPPORTED_SCOPE_WITH_DOCUMENTED_PLATFORM_LIMITATION; current Redmi evidence not added** |
| GATE F | Protection Integrity + core-scenario smoke; everyday-use feedback | **FAIL — Issue #28 / FB-P1-08 remains OPEN; fixed 24h acceptance NOT_REQUIRED** |
| GATE G | Signing / Versioning / Publishable Build | **PENDING — cross-machine recovery and true version upgrade not tested** |
| GATE H | Store / Compliance Readiness | **PENDING** |
| GATE I | Small-scale Beta | **PENDING** |

最终发布条件：**所有 release-blocking gates PASS**。

表中的 B/D/E 是既有 v1.1.0 的历史设备/支持范围裁定，不转移为本候选新验收。Gate F 的 FAIL 保留真实缺陷；虚拟设备、自动化、静态审计、被拒绝启动的 iQOO 或旧签名 APK 不等于当前候选的设备核心场景通过。固定时长要求已取消，不再要求补交 24 小时证据。

### GATE A — Core automated validation：LOCAL PASS / HOSTED HEAD CHECK REQUIRED

- 历史 CI Run `31900444404`（PR #1，verify Job `95050260127`）**SUCCESS**，HEAD = `ea53a22136dd58663291581763eb3614c4b10ba6`（不是当前最新全矩阵验证）。
- 基线运行 `31577669420`（verify Job `94053353542`）SUCCESS，HEAD = `99fdcc2f6f357e78fb70dd127adedfa31a098a71`。
- frontend 151/151（14 test files）+ provenance 18/18（`npm run test:provenance`）；Play JVM 244；Domestic JVM 254；RecoveryIntegration 23（仍是 23，不是 26）；Room migrations 6/6。
- `assemblePlayDebugAndroidTest` = AndroidTest APK successfully ASSEMBLED，**不等于** instrumentation tests executed（真机验证归 GATE B/D/F）。

### GATE B — Redmi targeted P1/P2 revalidation：PASS

- 2026-08-14 Redmi Note 13 Pro 5G（Android 16 / HyperOS 3.0）R1–R4 全部 PASS，基线 `99fdcc2`。
- 关闭：`FB-P1-01`（R1 ×3）、`FB-P1-02`（R2）、`FB-P1-03`（R3 ×3）、`FB-P2-01`（R4）。
- 证据：External device evidence: `reports/R1-R4-retest-2026-08-14.md`、`reports/final-report.md`（第二轮结论）。

### GATE C — Artifact Provenance / Controlled Release Build：LOCAL CANDIDATE VERIFIED; HOSTED CURRENT HEAD VIA PR CHECKS

> 术语说明：「Controlled / reproducible」指**受控、可重复执行的构建流程**（同一 SHA 可按同一工作流重复构建并复核），**不是 bit-for-bit deterministic build**——`build-provenance.json` 含冻结的 `buildUtc`，同一 SHA 在不同时间重建的产物字节哈希可以不同。下列历史和本地检查各自绑定明确 SHA；最终 hosted artifact 只以 PR 当前精确 HEAD 的成功 workflow 产物为准。

**ARTIFACT_PROVENANCE 要求（7 条已实现，并由历史 PR #1/PR #42 SHA 验证）**：

1. 前端 build ✓
2. cap sync ✓
3. Android build ✓
4. 同一工作流连续完成 ✓
5. 产物记录 Git SHA ✓
6. 对 APK / AAB 计算 SHA-256 ✓
7. release artifact 不允许使用来源不明的旧本地产物 ✓（CI 干净 checkout + source identity 校验）

已建立的链路（`.github/workflows/android.yml` + `app/scripts/`，详见 `TESTING.md`）：

- CI checkout 按 `SOURCE_COMMIT_SHA` 固定（PR = head SHA，master push = pushed SHA），构建前校验 `git rev-parse HEAD` 等于该 SHA 且工作区干净（`SOURCE_IDENTITY=PASS`）。
- `npm run build` → 生成 `dist/build-provenance.json`（allowlist 字段：schemaVersion / sourceGitSha / workflowSha / gitRef / runId / runNumber / workflow / buildUtc / versionCode / versionName / prHeadSha；**不含任何 secret/token/keystore 信息**）→ `npx cap sync android`。
- Web 资产同步校验：dist 内每个文件必须在 Android web assets 中存在且 SHA-256 一致（缺失或 mismatch 直接 CI FAIL）；允许 Capacitor 额外运行时文件。
- 最终二进制内部校验：Domestic APK 与 Play AAB 内 `build-provenance.json` 存在且 sourceGitSha = 本轮 source SHA；Domestic APK 版本经 `aapt2 dump badging` 独立读取对照；Play AAB 版本经其自身打包的 `base/manifest/AndroidManifest.xml`（wrapper zip + `aapt2 dump xmltree`）独立读取对照（AGP 8.13 对 AAB 不产出 output-metadata.json，已在 workflow 注明）。
- `artifact-manifest.json`（binaries 的 filename/size/sha256）+ `SHA256SUMS.txt`（binaries + 双渠道 mapping + manifest 自身）。
- GitHub Actions artifact `flowbreak-unsigned-<sourceSha>` 持久上传（retention 90 天）；PR / master verify 产物明确 **UNSIGNED / TEST ONLY**，不冒充可发布产物；tag 签名发布（GATE G 范围）复用同一 provenance 链（signing 设计未改）。

历史 GATE C PASS 的范围（重要；不转移到当前候选）：

- **历史功能修复源已验证**：PR #42 hosted verify Run `39301143402`，source `f7abbad7e900b32db5291cf26754cbd492721948`；Play AAB SHA-256 `989af971a7683ba615fca335335c44b51acbf6e96c97fc8ef15aca995c0c796`，Domestic unsigned APK SHA-256 `cc8e9f01cb11d41ef58ff25751863496ab47bb36a82e74069216d361d488376e`；artifact manifest 与 `SHA256SUMS.txt` 已上传并可复核，`signed=false`，版本为 CI test-only `versionCode=1` / `versionName=ci-2a7b01cceecf46031ec5ea04ac43b467fd280453`。这些哈希不代表后续 HEAD 的二进制。
- **unsigned CI artifact provenance pipeline 已验证**：PR #1 CI Run `31900444404` verify SUCCESS；artifact 可下载、SHA256SUMS 复核一致、manifest sourceGitSha = PR head SHA、APK/AAB 内部 provenance 一致、版本信息与二进制独立读取一致。
- **signed publishable build 仍属于 GATE G**；STORE READY 仍为 NO。

- **历史本地 PR #29 候选代码源**：`0996ca32b8cc471236b8724665dd32a683a72067` 的 dist→Android Web asset sync、Play AAB 与 Domestic APK 内部 provenance、二进制版本独立读取、双渠道 R8 mapping、runtime dependency 清单、unsigned artifact manifest 与 7/7 SHA-256 清单已按本地工程流程复核。工件均 `signed=false`，不用于发布。该证据不表示最终 PR HEAD hosted workflow 已通过；文档提交后的新 HEAD 必须以 PR checks 对应 workflow 与 artifact 再确认。
- **本轮本地代码源**：`a15b54d5e88ea59245a724c3cb29eb4820583ca9` 的 Web asset sync **51/51**、双渠道 unsigned Release 的同项来源/版本/清单检查及 **7/7 SHA-256** 均 PASS；API 36 AVD 实际安装身份测试两渠道各 **2/2**，拉取包哈希与同批 Debug 工件一致。详细指纹与证据边界见 [TESTING.md](TESTING.md)，不关闭真机、签名升级或商店门禁。

历史缺口（已关闭，保留记录）：本地曾出现「native 已更新、Web bundle 仍旧」的不一致 APK；旧 CI Run `31577669420` 未持久上传 artifacts。

### GATE D — Supported OEM scope + fail-closed boundary：PASS_SUPPORTED_SCOPE

- v1.1.0 支持范围已明确：Redmi/Xiaomi 参考设备保持支持证据；vivo/iQOO 因 `FB-P1-05` 真实失败证据明确不支持。
- vivo/iQOO 的 Play 与 Domestic 激活、配置重载、开机恢复和服务入口均 fail closed，原因固定为 `UNSUPPORTED_DEVICE_FOR_RELIABLE_MONITORING`；不以“再开启权限”作为绕过方案。
- PR #20 已合并至 master `2af5e93e88687cdb8b7903f270ae67380773a200`；合并后的 master verify Run `34227077948` **SUCCESS**。
- 同一 master 的 signed dry-run Run `34227560491` 的 verify 与 release 均 **SUCCESS**。Domestic APK artifact `flowbreak-signed-dry-run-v1.1.0-2af5e93e88687cdb8b7903f270ae67380773a200`，SHA-256 = `2ee7d2147b8d19a4f83e45dd85bc5eb3ea61504ab87e875260fedcdc1b0b1875`，Domestic fingerprint = `8d69d1786ea63b05ff3b8d1f5a78266a2fa1eda7b823af0c295cbfdc10e77f20`。
- iQOO `V2073A` / OriginOS 13.5 / Android 13 / SDK 33 上，最终签名 Domestic APK 通过 `adb install -r` 安装且未清除数据；`unsupportedDevice=true`、`protectionRuntimeAvailable=false`，UI 与 Diagnostics 均如实显示不支持，保护启动控件不可用，未观察到活动保护 FGS。启用 Accessibility 后，B 站 `tv.danmaku.bili` 短时保持可用，未观察到 HOME、`BlockedTargetBanner` 或 `BlockActivity`。
- 本阶段未执行 iQOO S6–S13 计时测试，也未重新调查 `FB-P1-05` 根因；该问题继续保持 **OPEN — UNSUPPORTED IN V1.1.0**。
- 新 signed build 的 Redmi smoke 因设备未连接而 `NOT_EXECUTED`；既有 Redmi R1–R4 外部证据仍为 PASS，足以保留当前 supported reference。
- 其他未完成真机验证的 OEM 不因未命中 fail-closed 集合而自动获得已验证声明，后续兼容性矩阵仍可独立扩展。
- 同时决定 `COMPAT-001`（HyperOS best-effort BlockActivity 后台启动被拒）的处理方向：删除 `tryStartBlockActivity` / OEM 条件化 / 保留 best-effort。

### GATE E — Supported-scope usage / blocking evidence：PASS_SUPPORTED_SCOPE_WITH_DOCUMENTED_PLATFORM_LIMITATION

- `E1_USAGE_ACCOUNTING = PASS_CARRIED_FORWARD_WITH_EQUIVALENCE`：此前有效 Redmi 真机 PASS（`54f6c54a313e96fb374514b2bb4bac8bfd689545`）与当前 master normal-path equivalence。
- `E2A_HEALTHY_RUNTIME_BLOCKING_LATENCY = PASS_CARRIED_FORWARD_WITH_EQUIVALENCE`：此前签名 Redmi 约 `361s` BLOCKED 证据（`c79fa0...`）与当前 master normal-path equivalence。
- `E2B_EXECUTION_GAP_RECOVERY = PASS_CURRENT_SIGNED_DEVICE_INTEGRATION`：当前签名 `ccacb6738a44d3bc08da9a4ceac75766316b2521` 已在 Redmi 上实际执行 reconciliation path，并恢复非零 machine session。
- `TEST_A_ACCOUNTING_PRECISION = INCONCLUSIVE`；`TEST_A_RECOVERY_SIGNAL = PASS`。精确 foreground transition timing 与 after-usage export 不完整，因此不称为 exact accounting PASS，也没有据此证明 overcount。
- `REALTIME_ENFORCEMENT_DURING_OS_EXECUTION_SUSPENSION = NOT_GUARANTEED_PLATFORM_LIMITATION`：Android/OEM 不给 FlowBreak 进程或 worker 执行时间时，实时 overlay 与状态迁移不属于 v1.1.0 保证。

本 Gate 不声称当前 master 独立重新执行了完整的 E1/E2 physical test sequence。Gate E 的结论是 documented supported-scope evidence carry-forward，加上当前签名 execution-gap recovery integration。

以上保留 Gate E 的既有裁定。现行 Gate F 范围以本文“验收范围调整”为准；Gate F 仍为 **FAIL**，Gate G/H/I 为 **PENDING**，因此发布仍为 **NOT APPROVED**。

### GATE F — Protection Integrity + 核心场景复验：FAIL

- Issue #28 / `FB-P1-08` 当前 **OPEN_SUPPORTED_SCOPE_RELEASE_BLOCKER**。历史真实设备摘要显示 Bilibili 前台约 33 秒时 REST 页面仍显示倒计时；摘要未包括原生持久状态、service tick、前台分类器与取消判定，因此保留真实失败，但根因 **UNDETERMINED**。不得把问题归结为某个实现分支已修复。
- **固定 24 小时验收 = NOT_REQUIRED（2026-10-25，负责人移除）**。历史连续运行记录仍为 `NOT_EXECUTED`，不改成 PASS；日常使用发现的稳定性问题按实际反馈登记、修复，不要求专门占机满一天。
- Protection Integrity 原则（`TESTING.md`）：**UI promise ≤ actual protection capability**。最终候选应在正常路径同步做失效注入：撤销 Usage Access / Overlay、关闭可选 Accessibility、service 被杀、battery saver、OEM 后台限制、force-stop 后手动重开；逐项验证实际保护行为与界面是否如实反映能力。
- force-stop 语义：不要求自动复活；手动重开后 UI 不得谎称旧保护状态仍完全有效，应走合法恢复。
- 当前 iQOO 仍被代码拒绝启动；此前未成功安装的尝试不算核心流程验证。先完成兼容修复，再在获授权、确实可运行候选的设备上做短时定向复验，不固定要求 Redmi。AVD 可验证受控功能与页面状态，不模拟任意 OEM 的调度行为；自动化结果不自动关闭 Issue #28。设备复验的隐私边界见 [FB-P1-08 交接协议](FB-P1-08-FINAL-DEVICE-HANDOFF.md)。

### GATE G — Signing / Versioning / Publishable Build：PENDING

**当前细分状态（主工作站 final identity 与本地 custody 已完成）**：

- **Stage A groundwork = PASS**（版本策略、签名工程机制、签名验证链已实现并实测）。
- **Stage B（首轮）technical dry-run = PASS，但身份已降级**：首轮身份的 signed dry-run（Run `31934183213`）仅验证了完整技术链；因其初始密码曾存在于自动化进程与旧交接文件中，标记为 **SUPERSEDED_PRE_PRODUCTION**（无泄露证据、不是安全事故），**禁止用于正式发行**。
- **GATE_G_SIGNING_WORKFLOW_BOUNDARY**：
  - 工作流边界（PR #6，已合并）：release job 仅 `workflow_dispatch && refs/heads/master` 或 `v*` tag 触发，dispatch 另有 `SIGNED_DISPATCH_REF=PASS` 显式守卫；负向测试要求 `NON_MASTER_SIGNING_ACCESS = DENIED`。
  - Environment 部署分支策略：产品负责人已在 UI 设置 `master` branch + `v*` tag，`ENV_BRANCH_POLICY = SET`；当前无 v* tag，UI 显示 0 matching tags 属正常状态。
  - 最终签名身份：`FINAL_SIGNING_IDENTITY = GENERATED`。Play upload key 与 Domestic app-signing key 已在主工作站生成并完成公开证书核验。
  - 最终 policy：仓库 policy 已标记 `PROVISIONED`，并写入两个 final public certificate fingerprint；旧 technical dry-run identity 仍不接受为 active identity。
  - Portable vault：`age v1.3.2` 已批准；本地加密 vault 创建与恢复均 PASS，off-machine backup = `PASS`，cross-machine recovery 保持 `NOT_YET_TESTED`。
- **SIGNED_INSTALL = PASS**：已有最终签名 Domestic APK 的安装验证通过；正式 tag 未创建、商店未发布。
- **SIGNED_REPLACEMENT = PASS**：历史 `adb install -r` 覆盖安装保留用户数据的验证通过。
- **DATA_PRESERVATION = PASS**：覆盖安装后的配置、目标应用与本地数据保留。
- **TRUE_VERSION_UPGRADE = NOT_TESTED_NO_VALID_LOWER_FINAL_SIGNED_BUILD**：没有可用的更低版本最终签名生产产物，不能把覆盖安装改称真实版本升级。
- **GITHUB_FINAL_SIGNING_SECRETS = PROVISIONED**：旧 identity 的 secrets 已清理，本轮仅写入最终 identity 对应的 8 个 Environment Secrets；secret values 未读取或写入仓库。

**GATE G 当前 = PENDING**。既有记录中的 final identity、local custody、最终 GitHub signing secrets、signed dry-run、signed install/replacement 与 off-machine backup 是历史范围内的证据；它们不代表本轮集成候选已签名。`CROSS_MACHINE_SIGNING_RECOVERY=NOT_YET_TESTED` 与 `TRUE_VERSION_UPGRADE=NOT_TESTED_NO_VALID_LOWER_FINAL_SIGNED_BUILD` 是两个独立未完成项。即使其中一项完成，另一项仍需单独验收后方可讨论关闭 Gate G。

### Signing Portability

CURRENT_PC_ROLE = `PRIMARY_WORKSTATION`

FINAL_SIGNING_IDENTITY = `GENERATED`

SUPERSEDED_PRE_PRODUCTION_IDENTITIES = `DISABLED`

MACHINE_BOUND_CUSTODY = `NO`

PORTABLE_VAULT = `LOCAL_ENCRYPTED_AGE_VAULT`

VAULT_ENCRYPTION = `AGE_V1_3_2_APPROVED_LOCAL_RESTORE_PASS`

SEC-PORTABILITY-001 = `CLOSED_AFTER_TARGETED_REGRESSION`

RECOVERY_SECRET_HANDOFF = `OWNER_PASSWORD_MANAGER_CONFIRMED`

OFF_MACHINE_BACKUP = `PASS`

CROSS_MACHINE_SIGNING_RECOVERY = `NOT_YET_TESTED`

ORIGINAL_LAPTOP_RECOVERY = `NOT_YET_TESTED`

最终身份已在 PRIMARY_WORKSTATION 生成；本地 vault、owner password-manager recovery handoff 与 off-machine copy 已完成。跨机器恢复测试纳入本次收尾，当前仍未执行；不得重建密钥来冒充原身份恢复。

`SEC-PORTABILITY-001` 的代码修复已完成：keytool 密码只通过短生命周期的子进程环境变量引用（`-storepass:env` / `-keypass:env`），不进入 child argv。经批准的 portable backend 为官方 `age v1.3.2`；本轮仅使用其交互式 `-p` / `-d` 流程，拒绝 PATH、Adobe-bundled、batchpass 或其他任意归档器。

只负责**工程发行产物**：

- release signing（双渠道签名身份：Play upload key + Domestic app-signing key，`SIGNING_IDENTITY_DECISION = SEPARATE`）
- keystore / CI secret strategy（`FLOWBREAK_PLAY_*` / `FLOWBREAK_DOMESTIC_*`，secret 只在 `$RUNNER_TEMP` 解码、`if: always()` 清理、绝不进日志/artifact/provenance）
- versionName（Source of Truth = `app/package.json` version，stable SemVer；正式 tag 必须精确等于 `v<versionName>`，如 `v1.1.0`）
- versionCode（确定性规则：`MAJOR*1_000_000 + MINOR*1_000 + PATCH`，1.1.0 → 1001000；MINOR/PATCH < 1000、总量 ≤ 2100000000；不依赖 CI run_number）
- channel / version consistency（`scripts/release-version.mjs` 交叉验证 + APK badging / AAB 内 manifest 独立读回）
- signed Play AAB
- signed / 最终 Domestic APK（按实际发行策略）
- 签名验证（Domestic：`apksigner verify --print-certs`；Play AAB：`jarsigner -verify` + `keytool -printcert -jarfile`；证书 SHA-256 与 `app/release-signing-policy.json` allowlist 对照，不匹配即 FAIL）
- signed artifact provenance（signed=true / signingRole / certificateSha256 进入 artifact-manifest；artifact 命名 `flowbreak-signed-v<version>-<sha>`，dry-run 为 `flowbreak-signed-dry-run-…`）
- install / replacement verification（需生产签名身份 + 真机；已有 Domestic final-signed artifact 的安装与 `adb install -r` 数据保留验证 = PASS；真实 lower-final-signed-build → current-final-signed-build upgrade = NOT TESTED）
- release artifact metadata
- CHANGELOG / version consistency

**不由** GATE G 负责：Data Safety、Accessibility declaration、商店文案、隐私政策 URL、商店截图、合规申报材料（归 GATE H）。

**GATE G 当前不具备整体 PASS 条件**：final identity、8 个最终 secrets、signed dry-run、signed install/replacement、portable vault 与 off-machine backup 是既有历史范围内的完成项，并非当前候选签名验收。真实 lower-final-signed-build 到当前 final-signed-build 的版本升级未测试，cross-machine recovery 也未测试；这两个条件均须分别完成，不能只做其中一项后关闭 Gate G。

### GATE H — Store / Compliance Readiness：PENDING

负责商店与合规发行材料：

- Google Play Data Safety
- FGS declaration（前台服务声明）
- privacy policy URL
- support contact
- screenshots / store listing
- Accessibility disclosure（如果对应渠道存在）
- Domestic permission-use explanations（国内版权限用途说明）
- APP 备案 / 版权 / 主体等经过确认的发行资质
- 渠道审核材料
- distribution compliance

> 本轮尚无可核验的外部合规审查或平台申报记录；旧版“研究进行中”不代表已完成研究。只维护源码事实与发行待办，不把未经负责人确认的材料写成已确认的法律或商店结论。

Gate H source facts and owner-dependent deliverables:

| Item | Current evidence | State |
| --- | --- | --- |
| Product data / permission description | Updated in `PRIVACY.md`; source declares Android `INTERNET`, local Room/SharedPreferences/WebView storage, and Domestic-only optional Accessibility; full export includes existing SharedPreferences values | Draft; final merged-manifest/dependency/network verification required |
| Diagnostics sharing | 本机完整运行时诊断可含 `runtimeTracking.restCheatEvidence`；脱敏分享字段白名单排除整个 `runtimeTracking`。完整本地导出是不同且更广的数据范围 | Source and whitelist reviewed; runtime/network proof and release-build review remain required |
| Public privacy policy URL | None configured; no page deployed or confirmed | Prepare a static-hosting option, then confirm publishing authority and actual accessibility; no compulsory domain/server purchase |
| Public support email | `15015091629@163.com`, owner-provided on 2026-10-25 | Public use authorized; delivery not tested; not a registered store account or proof of publisher identity |
| Publisher identity | Legal name, type and region unconfirmed | Owner must confirm true issuer details through the applicable official process |
| Play Data Safety / FGS / Accessibility declarations | No final Console submission evidence in this workspace | Owner account and final artifact required |
| Domestic filing, copyright and distribution qualifications | No verified issuer documents attached | Owner/legal review required |
| Store screenshots and listing | UI source and web-preview evidence exist; no final store listing pack | Prepare against final channel builds |
| Static network-surface audit | PR #39 是历史审计来源之一，现 CLOSED / unmerged；PR #42 是已合并的历史集成。当前候选生命周期与静态检查以 [PR #29 精确 HEAD checks](https://github.com/guo6x/flowbreak/pull/29/checks) 为准 | 运行时网络审查仍需真实环境；静态扫描不等于运行时流量证明 |

GATE H remains **PENDING**. Do not submit store forms, invent a contact, or mark the app Store Ready based only on repository evidence.

国内账号、备案、软著与隐私地址的实际办理说明见 [国内渠道办理指南](compliance/DOMESTIC_PUBLISHING_GUIDE.md)。该指南供负责人决定是否启动，不表示已注册或提交。

### GATE I — Small-scale Beta：PENDING

- 小规模 Beta 验证（在商店正式发行前）。

## 细项清单

### Code

- [x] 产品行为与 `docs/PRODUCT.md` 一致（当前基线 `99fdcc2` 已通过 R1–R4 复测，与 PRODUCT 目标语义一致；不一致按 Bug 登记）
- [ ] 当前无支持范围内 OPEN P1：**未满足**，`FB-P1-08` / Issue #28 仍是支持范围内的 P1 发布阻塞项并使 Gate F = FAIL；`FB-P1-05` 为 **OPEN — UNSUPPORTED IN V1.1.0**，不可伪装为已修复。历史 P0=0 不代表当前发布可放行。
- [ ] 无未经验证的「实验性」能力被作为正式功能宣传（微信视频号识别仍未真机专项验证）

### CI

- [x] 前端、来源与隐私**静态检查**通过；单项数量见 [TESTING.md](TESTING.md)。依赖审计必须按官方 registry、本地镜像和 GitHub CI 分别记录，hosted 结果必须绑定 PR checks 当前精确 HEAD
- [x] Play / Domestic JVM、lint 与构建矩阵通过；JVM 数量及 failure/error/skipped 总数见 [TESTING.md](TESTING.md)
- [x] `assemblePlayDebug`、`assembleDomesticDebug`、`bundlePlayRelease`、`assembleDomesticRelease` 通过，R8 mapping 存在
- [x] Room schema drift 检查通过（schema v4，迁移 1→2→3→4；schema 文件须与候选一同提交）
- [x] 历史 GitHub CI npm audit（PR #42 Run `39301143402` / source `f7abbad7…`）生产依赖 high、全依赖 critical 及 build 后重跑均为 **0 vulnerabilities**；这是历史 SHA 的 CI 证据，不代表当前 PR #29 hosted 检查
- [x] 当前本地官方 npm registry audit：生产依赖 high 与全依赖 critical 均 **0 vulnerabilities**
- [ ] 本地 npm audit（`https://registry.npmmirror.com`）可复核（当前返回 404/`NOT_IMPLEMENTED`，状态为 **BLOCKED / NOT_PASS**；该环境阻塞不覆盖 hosted CI 结论）
- [x] PR #29 推送前基线 GitHub CI Run `40145072882` / SHA `29573ee…`：生产依赖与全依赖 critical 均 **0 vulnerabilities**；该证据不覆盖本地镜像审计，也不覆盖当前候选差异或未来 SHA
- [x] PR #29 首个收口候选 GitHub CI Run `40151649558` / SHA `1070f920…`：verify 全步骤通过，production-high/all-critical 审计含复跑均 **0 vulnerabilities**；artifact `12582807009` 的 source SHA、signed=false 与 7/7 sums 已核验。该记录是历史锚点；包含本轮代码和资料的最终 HEAD 仍以自身 PR checks 为准
- [x] **历史 SHA** hosted unsigned artifact provenance 已上传并独立复核：PR #1 Run `31900444404` / source `ea53a22136dd58663291581763eb3614c4b10ba6`；PR #42 Run `39301143402` / source `f7abbad7e900b32db5291cf26754cbd492721948`。不代表当前 PR #29 有产物。
- [x] provenance 工具链测试 `npm run test:provenance` 20/20（原 18 + signing evidence 2）
- [x] **历史 SHA** Web asset sync 校验（dist → Android assets SHA-256 全量一致，缺失/mismatch 即 FAIL；见 PR #1 Run `31900444404`）
- [x] **历史 SHA** APK/AAB 内部 provenance 校验（sourceGitSha + 版本独立读取对照；见 PR #1 Run `31900444404`）
- [x] 本轮本地代码源 `a15b54d…` 的 Web asset sync 51/51、APK/AAB 内部 provenance、独立版本信息、双渠道 mapping、runtime dependency inventory 与 unsigned artifact manifest / SHA-256 清单 7/7 已复验；该产物不替代 PR 当前 HEAD 的 hosted artifact 核验
- [x] release 版本策略测试 `npm run test:release` 8/8（SemVer / tag / versionCode 编码 / overflow）
- [x] 签名验证链已实现（apksigner / jarsigner / keytool + fingerprint allowlist；TEST ONLY 密钥本地实测）
- [x] 历史 technical dry-run secrets provision（8 secrets，2026-08-16；对应身份已 superseded，不得用于正式发行）
- [x] 历史 signed dry-run（Run `31934183213` 全绿 + 独立下载复核；不代表最终 production identity）
- [x] Superseded `production-signing` Environment secrets 删除并核对不存在
- [x] 真机 signed install / signed replacement / data preservation（GATE G；真实版本升级仍 NOT TESTED）
- [x] 最终 human-generated signing identity / public policy allowlist（旧 technical dry-run identity superseded）
- [x] Portable vault（官方 age v1.3.2 + encrypted file names）创建并本地解密验证
- [x] Recovery secret 存入 owner 跨设备密码管理器（owner-confirmed）
- [x] GitHub production signing secrets replacement（最终 8 secrets；signed dry-run Run `34227560491` 使用并验证）
- [x] Vault off-machine copy（加密 `.age` 文件 SHA-256 已复核）
- [ ] 原笔记本/独立环境跨机器恢复（keytool metadata + policy fingerprints）

### Security / 隐私

- [ ] `PRIVACY.md` 与实际权限/数据行为一致（发布前最终核对）
- [ ] 无密钥/敏感信息入库；release 使用环境变量签名
- [x] 脱敏诊断分享采用字段白名单，与完整 JSON 数据导出严格区分；该范围的历史回归快照见 `TESTING.md`。本机完整运行时诊断可含 `runtimeTracking.restCheatEvidence`；分享白名单排除整个 `runtimeTracking`。完整本地导出包含全部现存 SharedPreferences 值（见 `PRIVACY.md`）
- [ ] 公开隐私政策 URL 可访问、支持邮箱可收信；邮箱 `15015091629@163.com` 已由负责人提供，URL 尚未配置，收发尚未验证

### Android / 双渠道

- [x] Play 清单不含 AccessibilityService；国内版包含且默认关闭
- [ ] 两个 applicationId 可同时安装，设置互不污染（待发布版最终验证）
- [ ] 本体验分支 release-candidate 的 AAB/APK 签名与安装验收（当前只有功能基线 unsigned 产物；生产签名与安装验收仍归 Gate G）
- [x] 既有 final-signed Domestic APK 安装 / `adb install -r` 数据保留验证通过（GATE G）；真实 lower-version 到 current-version 升级仍未测试，且不代表本分支 release-candidate 已验收

### 真机设备矩阵

- [x] Redmi 精准复测 R1–R4（FB-P1-01/02/03、FB-P2-01）全部 PASS（GATE B）
- [x] Gate E supported-scope evidence：E1/E2A carry-forward equivalence、E2B current signed integration、Android execution suspension boundary 已记录；Test A exact accounting precision 保持 INCONCLUSIVE
Gate E 沿用已记录的支持范围裁定；完整当前构建 E1/E2 物理复跑不是保留该裁定的额外前置条件，也不得写成已经复跑。本次核心复验范围见 Gate F。
- [ ] 核心短测无时间暴涨、重复通知、ANR 或 silent protection drift；状态失效时如实提示，停止/恢复可用（GATE F；固定 24 小时要求已移除）
- [x] v1.1.0 supported scope + vivo/iQOO fail-closed boundary 的最终真机检查（GATE D = `PASS_SUPPORTED_SCOPE`；iQOO fail-closed PASS，Redmi 新 signed smoke NOT_EXECUTED，既有 R1–R4 reference PASS）
- [ ] 本次兼容候选：权限、后台限制、计时与重启恢复复验；未覆盖机型明确标未验证，不宣称已支持全品牌

### 商店

- [ ] Play：前台服务声明、Data Safety、隐私政策 URL、商店截图、Android Vitals（GATE H）
- [ ] 国内：无障碍用途说明清晰，关闭后不执行强阻断；后台/自启动引导实测（GATE H）
- [ ] 版本号与 `CHANGELOG.md` 一致（GATE G）

## 已删除/修正的旧检查项

- ~~「离开全部目标应用 30 秒后归零」~~：与 `PRODUCT.md` BLOCKED sticky 语义冲突（FB-P1-02），从门禁中移除；现行规则：30 秒 reset 仅适用于尚未进入 BLOCKED 的连续会话，BLOCKED 只能通过正式休息 / 合法紧急使用 / 明确的监控配置生命周期转换退出。
- ~~「79/80/100/120% 状态边界」~~：修正为 80/100/120%（PERCEPTION/COGNITION/BLOCKED），无 79% 档位。
- 旧「定时提醒」相关检查项：功能已移除，不再作为发布要求。

## 本次完整收尾（执行范围，不代表已经完成）

1. 修复 iQOO 计时兼容与所有已知核心状态问题，统一启动/重载/恢复的能力检查；保留现有视觉和功能。
2. 完成自动化、双渠道 Android、隐私、产物来源与核心场景复验；不再安排固定 24 小时验收，也不要求占用日常 Redmi。
3. 复用已生成的最终签名身份，核对公开指纹、既有签名构建、备份与剩余恢复/真实升级条件；不重新生成密钥。当前候选签名必须遵守既有授权和工作流边界。
4. 同步准备完整商店文案、隐私政策、权限/前台服务声明、图标、最终构建截图和必要演示；发行人集中确认主体、渠道、联系方式、托管地址、账号与适用资质，实际提交和审批据实记录。
5. 组织自愿日常试用并处理实际反馈，与代码/材料在同一批汇总；不承诺不存在的试用者、设备覆盖或平台批准。负责人未提供的外部条件明确列为依赖，不改称已完成。
6. 整批核对后统一推送工程分支；是否合并、正式签名、创建 tag 或商店提交仍须满足各自授权，不能用本清单替代批准。
