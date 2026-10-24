# CURRENT_STATUS — 项目当前状态

> 这是「项目现在在哪」的唯一当前视图。易变事实（HEAD、测试数、Run ID、APK SHA）只出现在本文档与 `TESTING.md` 的验证快照中。
> 2026-08-12 的「真机验收 FAILED / RELEASE BLOCKED」快照已降级为历史记录（见下方「历史状态」），不再代表当前状态。

## 当前合并基线与验证来源（2026-10-24）

PR #42 已于 2026-10-23 合并到 `master`，merge SHA = `67d1b7c3ee82ef4f6dd275299d51969869d7db4d`。仍开放的 Protection Integrity P1 是 PR #29（分支 `fix/fb-p1-08-protection-integrity`，HEAD `b00b9cc75073e77759e28ba5b71f960033cbf1d5`）；它与 PR #42 的合并身份分开记录，不能把其失败 CI 或历史取证倒灌为 master、真机或 24 小时验收结果。本轮运行时代码修复位于 `8cf1d653fe34d01096130322199ed4f4d6623039`；PR #43 的最新完整候选 HEAD 为 `a66013d4f24bd6e282eda0904f6c735afcfa0446`，其上收口状态文档。最新候选的 [Run `40039686623`](https://github.com/guo6x/flowbreak/actions/runs/40039686623) `verify` 成功、`release` 按策略跳过，unsigned 产物已上传并可复核；master 的 [Run `39928579175`](https://github.com/guo6x/flowbreak/actions/runs/39928579175) 保留为合并基线。上述 hosted 证据只覆盖工程验证，不扩大为生产签名、真机候选安装或 24 小时稳定性通过。旧产物和旧 Run 仍只归属原始 source SHA。

| 项 | 当前可核验状态 |
| ---- | ---- |
| 当前 master HEAD | `67d1b7c3ee82ef4f6dd275299d51969869d7db4d`（PR #42 merge commit） |
| 未合并 P1 取证分支 HEAD | `b00b9cc75073e77759e28ba5b71f960033cbf1d5`（PR #29 OPEN；最近一次 verify FAILURE，不能并入当前候选） |
| 运行时代码 SHA | `8cf1d653fe34d01096130322199ed4f4d6623039`（Android REST 作弊持久化失败时 fail closed；含回归测试） |
| 本轮候选验证 HEAD | `a66013d4f24bd6e282eda0904f6c735afcfa0446`（运行时代码与三份状态文档收口） |
| 本轮收口分支 | `integration/flowbreak-closeout-20261024`（PR #43，Draft/Open；基于 master `67d1b7c3…`，代码与文档均已推送） |
| 最新候选 CI | 完整候选验证 HEAD 的 [Run `40039686623`](https://github.com/guo6x/flowbreak/actions/runs/40039686623) `verify` **PASS**；双渠道 JVM/Lint/Debug/unsigned Release、R8、Room、Manifest、Provenance 与审计均通过；`release` **SKIPPED** |
| 工程集成身份 | **INTEGRATION_ENGINEERING_READY / DEVICE_ACCEPTANCE_PENDING**；PR #42 已 MERGED，PR #43 仍 Draft/Open；不创建正式 tag |
| 历史 unsigned 产物 | hosted verify [Run `39301143402`](https://github.com/guo6x/flowbreak/actions/runs/39301143402)（source `f7abbad7…`）：[artifact `flowbreak-unsigned-f7abbad…`](https://github.com/guo6x/flowbreak/actions/runs/39301143402/artifacts/12211868639)；Play AAB SHA-256 `989af971a7683ba615fca335335c44b51acbf6e96c97fc8ef15aca995c0c796`；Domestic unsigned Release APK SHA-256 `cc8e9f01cb11d41ef58ff25751863496ab47bb36a82e74069216d361d488376e`；`versionCode=1`、`versionName=ci-2a7b01cceecf46031ec5ea04ac43b467fd280453`，只归属该旧 SHA；本地 `1.1.0` unsigned 包仅为功能基线/AVD 使用 |
| 最后完成的工程验证快照 | 完整候选验证 HEAD `a66013d4f24bd6e282eda0904f6c735afcfa0446` / Run `40039686623`：Vitest **296/296 PASS**；UI smoke **44/44**；Brand **36/36**；Privacy static **15/15**；Provenance **20/20**；Release policy **8/8**；Web asset sync **50/50**；同源 JUnit Play **456/456**、Domestic **489/489**，0 failure/error/skip；双渠道 JVM/Lint/Debug/unsigned Release、R8、Room、Manifest 与包内 provenance 检查均 PASS（详见 [TESTING.md](TESTING.md)）。运行时代码 SHA `8cf1d653…` 的先前 Run `40033048741` 仍单独保留 |
| 最后完成的 unsigned provenance artifact | 完整候选 [Run `40039686623`](https://github.com/guo6x/flowbreak/actions/runs/40039686623) 的 [artifact `flowbreak-unsigned-a66013d…`](https://github.com/guo6x/flowbreak/actions/runs/40039686623/artifacts/12539036479)（source `a66013d…`，ID `12539036479`）；Play AAB SHA-256 `97ebb2efa038f3f6c250f1a2c7bcf2ac0673bdffda8012725b80d0b6710a5062`；Domestic unsigned Release APK SHA-256 `8bb6c5cbca1f487b81ea579c3759be297837753dbdaff4230e7071c422f0123f`；均为 `UNSIGNED / TEST ONLY` |
| npm audit（GitHub CI，生产依赖 high） | **PASS — 0 vulnerabilities** |
| npm audit（GitHub CI，全依赖 critical） | **PASS**；开发依赖仍有 Vite high advisory，不能写成“全依赖零漏洞” |
| npm audit（本地镜像） | **BLOCKED / NOT_PASS**：`https://registry.npmmirror.com` 返回 404/`NOT_IMPLEMENTED`；不覆盖 hosted CI 结论 |
| API 36 AVD | **受控范围 PASS**：`Medium_Phone_API_36.0` / `emulator-5554`；启动、权限页 0/2、系统 Usage Access/Overlay 页面往返、个性化、目标应用选择、首页、统计、个人中心、阻断设置和运行诊断均已截图；证据目录 `D:\temp\flowbreak-avd-evidence-20261019` |
| AVD 诊断边界 | 诊断页显示服务心跳、监控线程、设备支持、监控配置、关键权限、Room v3 和 1 个目标应用；这是模拟器受控证据，不是 Redmi/HyperOS 后台稳定性或真实用户证据 |
| 2026-10-24 USB 实体机 smoke | **PASS_UNSUPPORTED_SCOPE_ONLY**：vivo `V2073A` / Android 13 / SDK 33；现有已签名 Domestic v1.1.0（APK SHA-256 `2ee7d214…`，来源 `2af5e93…`）的 Dashboard 与 Diagnostics 均显示 `设备不支持` / `protectionRuntimeAvailable=false`，未发现 FlowBreak 保护 FGS；强制停止后重新打开可恢复，未见 `FATAL EXCEPTION`。这不是 PR #43 候选包验收 |
| 2026-10-24 候选安装边界 | **NOT_EXECUTED**：最新 CI Domestic APK `8bb6c5cb…` 是 unsigned，`adb install -r` 被系统以 `INSTALL_PARSE_FAILED_NO_CERTIFICATES` 拒绝；未卸载、未清除现有签名包，候选 SHA 与设备包 SHA 不混用。原始截图/日志/UI dump：`D:\temp\flowbreak-device-evidence-20261024` |
| Gate A–E | 既有发布基线的历史/支持范围证据保留；不转移为本候选的真实设备验收 |
| Gate F | **FAIL / NOT_CLOSED** — Issue #28 / `FB-P1-08` 仍开放；24 小时稳定性 `NOT_EXECUTED`，AVD/JVM/CI 不关闭此门禁 |
| Gate G | **PENDING_CROSS_MACHINE_RECOVERY** — 跨机器签名恢复与真实版本升级仍未完成；当前无签名发布包 |
| Gate H | **PENDING** — 发行主体、公开政策 URL/联系方式、备案与商店材料/提交均未完成；当前候选仅有无手机合规准备 |
| Gate I | **PENDING / NOT_STARTED** — 尚无真实小规模 Beta |
| 独立复审 | PR #42 全部合并差异与 PR #43 最新 Android 修复已复核；saveSettings/saveTargetApps 的原生状态对账、REST marker 写入/提交失败 fail-closed 与清理路径均有回归覆盖；本轮 USB smoke 仅确认 vivo/iQOO fail-closed 边界，未发现新的 P0/P1/P2 代码阻断。PR #29 的 Protection Integrity 与 24 小时稳定性仍按 Gate F 保持未关闭；最新 unsigned 候选尚未安装到实体机 |
| 合并与发布边界 | 2026-10-23 用户授权在最新 checks 通过后执行常规工程合并及冗余分支清理；合并不关闭设备/发布门禁。`STORE_READY=NO`；`PRODUCTION_RELEASE_APPROVED=NO`；不得 force-push、读取生产签名材料或创建正式 release |

本工作允许使用 Android 虚拟设备开展受控功能与界面验证，但 AVD 不模拟 Redmi/HyperOS 的后台调度，也不能替代 Gate F 的真实设备与 24 小时稳定性门禁。每次修补后的 HEAD、hosted CI 与合并决定单独记录在 PR；仍需真实设备、运行时网络与其它发布门禁。

## 体验分支历史快照（非当前候选）

以下记录仅描述独立体验分支及其各自验证范围，不代表当前集成工作树或最终候选通过：

| 项 | 历史证据 |
| ---- | ---- |
| 分支 | `product/experience-polish-20261018` |
| 本地验证代码与测试提交 | `154a7cfab7da1c7924ae1acb53acd63a931c2626`（文档快照随后单独提交，不改变 App 运行时源码） |
| 本地自动化 | **PASS（该分支）** — Frontend 263、Play JVM 382、Domestic JVM 412；网页 44 项、品牌 36 项、provenance 20 项、release policy 8 项；双渠道 Lint 0 error、Debug 构建通过（详见 [TESTING.md](TESTING.md)） |
| GitHub Actions | **PASS（该分支的验证提交）** — [run 39095168568](https://github.com/guo6x/flowbreak/actions/runs/39095168568)，验证 SHA `147a64567f56290c3b8c0dce0aa934dd2c3b494f`；unsigned verify job 完成，签名 release job 按策略跳过 |
| API 36 AVD | **PASS（范围有限）** — `Medium_Phone_API_36.0` / Android 16；Domestic debug 覆盖安装后可进入权限页，状态为 0/2；从系统“使用情况访问”设置返回后仍为 0/2；未授予权限、未启动保护服务。截图只作为该分支的受控证据 |
| 范围 | 视觉系统、矢量品牌、应用内帮助、统计与失败状态、设置草稿和原生读取保护、诊断分享白名单、Domestic 默认偏好、权限轮询、品牌与网页检查 |
| 新构建真机 / OEM / 发布验收 | **NOT_EXECUTED**；无真实手机；未调用生产签名、未合并、未发布。上述 AVD 不关闭 Gate D/E/F |
| `FB-P2-03` | 在该体验分支修复并有 JVM + API 36 AVD 针对性证据；缺陷关闭仍需按缺陷台账规则确认合并与适配范围 |

## 既有发布基线速览（2026-09-11，历史验证范围）

下表保留原发布基线的门禁与实机证据，不代表本轮体验分支的最新 master 状态或新包验收。表内问题计数也是该基线当时的结论。

## 2026-10-12 历史发布快照（仅供时间线参考）

以下是当日的发布状态记录；其中 PR #33 的“审查/CI 中”等状态已过时。当前 Issue/PR 与工程集成来源以本文开头及所链接的云端记录为准。

| 项 | 当前可验证状态 |
| --- | --- |
| master | `f9ef7a1f0cbcb3925eb87cd9bd16032428054fde`，PR #31 Capacitor 安全升级已合并；master [CI 38205826590](https://github.com/guo6x/flowbreak/actions/runs/38205826590) **SUCCESS** |
| PR #29 | `b00b9cc75073e77759e28ba5b71f960033cbf1d5`，OPEN，原生 REST 防作弊修复候选和取证文件仍未合并 |
| Gate F | **FAIL**：已报告约 33 秒 Bilibili 前台使用时 REST 页面仍显示倒计时；当时摘要没有足够原生诊断确定根因。新候选无设备复验，24h 稳定性未执行 |
| PR #32 | 验证专用 PR，**CLOSED/UNMERGED**；[CI 38053274608](https://github.com/guo6x/flowbreak/actions/runs/38053274608) 只能证明模拟链与构建成功 |
| PR #33 | 无手机 Gate H/G 准备 + 国内无障碍独立告知；当时仍在审查/CI，后于 2026-10-12 合并；不代表已上架 |
| Gate G | **PENDING_CROSS_MACHINE_RECOVERY**；真实版本升级另需实测 |
| Gate H | **PENDING**；草案和官方依据已形成，公开身份/邮箱/URL、截图、审核、备案等未完成 |
| Gate I | **PENDING**；真实 Beta 未完成 |
| 发布 | `RELEASE_PREPARATION`，**STORE_READY=NO / PRODUCTION_RELEASE_APPROVED=NO** |

下方“当前状态速览”保留的是旧版本设备与已签名构建历史证据，不得用它替代本节的 2026-10-12 事实。


| 项 | 值 |
| ---- | ---- |
| Last verified date | **2026-09-11** |
| Gate E verified behavior baseline | `ccacb6738a44d3bc08da9a4ceac75766316b2521` |
| Master CI | **PASS** — Run `34569928299` |
| Signed candidate | **PASS** — Run `34570309806`（verify + release） |
| Signed artifact | `flowbreak-signed-dry-run-v1.1.0-ccacb6738a44d3bc08da9a4ceac75766316b2521`（ID `10187690618`） |
| Signed Domestic APK SHA256 | `24bcff9dbe365c5cbd0ee65c00999b904850885ee9472f542be3c05f1dfd29a1` |
| Domestic certificate fingerprint | `8d69d1786ea63b05ff3b8d1f5a78266a2fa1eda7b823af0c295cbfdc10e77f20` |
| 阶段 | **RELEASE PREPARATION**（不是 STORE READY，更不是 PRODUCTION RELEASE APPROVED） |
| 支持参考设备 | **Xiaomi / Redmi**；R1–R4 **PASS** |
| 新 Redmi signed smoke | **NOT_EXECUTED** |
| 当前 P0 | **0** |
| Supported-scope release-blocking P1 | **0** |
| `FB-P1-05` | **P1 / OPEN_UNSUPPORTED_V1_1_0**（vivo/iQOO runtime 不支持，fail closed） |
| 发布状态 | 该历史基线当时记录为 **GATE A/B/C PASS；GATE D PASS_SUPPORTED_SCOPE；GATE E PASS_SUPPORTED_SCOPE_WITH_DOCUMENTED_PLATFORM_LIMITATION；GATE F/G/H/I PENDING**。2026-10-18 当前状态为 Gate F **FAIL**、Gate G/H/I **PENDING**（见本文开头） |

## 既有基线的支持设备范围与最终范围验收

- 已验证支持参考：**Xiaomi / Redmi**。Redmi Note 13 Pro 5G（`2312DRA50C` / garnet）/ Android 16 / SDK 36 / HyperOS 3.0（`OS3.0.306.0.WNRCNXM`）的 R1–R4 全部 PASS；该参考证据不等同于本次新 signed build smoke。
- 被测 APK：domestic debug（`com.flowbreak.app.cn`）。复测前在代码工作区 `99fdcc2` 上重新执行前端 build → Capacitor sync → Android assemble 后生成；该重建解决了此前旧 Web bundle 混入问题。正式 Artifact Provenance 链已由 `RELEASE.md` GATE C 建立并通过（2026-08-15，PR #1 实测）。
- 复测报告（外部设备证据工作区）：`reports/R1-R4-retest-2026-08-14.md`、`reports/final-report.md`（第二轮结论）
- `REDMI_R1_R4=PASS`
- `REDMI_NEW_BUILD_SMOKE=NOT_EXECUTED`
- **R1 冷启动前台追踪 ×3 PASS** → `FB-P1-01` RESOLVED（修复 `027af94`）：MainActivity→BrowserActivity 同包跳转事件形态复现，sessionMs 1:1 连续增长、不再卡 0。
- **R2 BLOCKED sticky PASS** → `FB-P1-02` RESOLVED（修复 `9c34fe9`）：离开 29s/31s/64s/约 2min 重进均仍 BLOCKED，sessionMs 未被 30 秒规则重置；completeRest → GRACE 10min 正常；Emergency 长按约 11s → GRACE 5min，emergencyUnlockDay 更新、DB emergency_unlock 正常记录。
- **R3 HyperOS 强阻断 ×3 PASS** → `FB-P1-03` RESOLVED（修复 `3600d97`）：BLOCKED → 打开目标 App → Accessibility 立即 HOME → 顶部横幅（TYPE_ACCESSIBILITY_OVERLAY）可见且含「开始休息」入口；横幅单实例不堆叠、非目标应用正常、无全手机锁死、连续三次重进均继续阻断；核心强阻断不依赖 BlockActivity 启动成功。
- **R4 系统返回键 PASS** → `FB-P2-01` RESOLVED（修复 `99fdcc2`）：物理 Back 与手势 Back 在未保存修改时均弹「有未保存的修改」，应用不退出；无修改保持系统默认退出。
- Smoke：IDLE→COGNITION→BLOCKED→RESTING→GRACE 全链路走通；force-stop 语义正常（不自动复活）；覆盖安装数据保留。

### vivo/iQOO v1.1.0 支持边界

- `vivo V2073A` / `OriginOS 13.5` / Android 13 / SDK 33：最终范围验收已完成。
- `IQOO_SUPPORTED=NO`
- `IQOO_FAIL_CLOSED=PASS`
- `unsupportedDevice=true`、`protectionRuntimeAvailable=false`；UI 与 Diagnostics 如实显示不支持，保护启动入口不可用，未观察到活动保护 FGS。
- Accessibility 启用后，B 站 `tv.danmaku.bili` 短时保持可用，未观察到 HOME、`BlockedTargetBanner` 或 `BlockActivity`。
- 该结论只适用于本次验证的设备与系统版本，不泛化为所有 vivo、iQOO 或 OriginOS 版本。`FB-P1-05` 仍为 P1 OPEN，不标记为 RESOLVED；v1.1.0 明确排除 vivo/iQOO runtime 支持并 fail closed。

### 其他 OEM

- **UNVERIFIED / BETA SCOPE**。其他 OEM 不因未命中当前 fail-closed 集合而自动获得 v1.1.0 支持声明。

## 既有发布基线自动化验证（历史）

- Gate E verified behavior baseline `ccacb6738a44d3bc08da9a4ceac75766316b2521` 的自动化验证：**PASS**。
- Master verify：Run `34569928299` **PASS**。
- Signed workflow：Run `34570309806` verify + release **PASS**。
- Signed artifact：`flowbreak-signed-dry-run-v1.1.0-ccacb6738a44d3bc08da9a4ceac75766316b2521`，ID `10187690618`。
- 精确 Frontend / Android JVM 套件数量以 `TESTING.md` 为准；下方旧计数仅保留为历史记录。

## 既有发布基线的 Gate E 支持范围决策

- `GATE_E = PASS_SUPPORTED_SCOPE_WITH_DOCUMENTED_PLATFORM_LIMITATION`。
- `E1_USAGE_ACCOUNTING = PASS_CARRIED_FORWARD_WITH_EQUIVALENCE`：此前有效 Redmi 真机 PASS + 当前实现的 normal-path equivalence。
- `E2A_HEALTHY_RUNTIME_BLOCKING_LATENCY = PASS_CARRIED_FORWARD_WITH_EQUIVALENCE`：此前签名 Redmi 约 `361s` BLOCKED 证据 + 当前实现的 normal-path equivalence。
- `E2B_EXECUTION_GAP_RECOVERY = PASS_CURRENT_SIGNED_DEVICE_INTEGRATION`：当前签名 `ccacb6738a44d3bc08da9a4ceac75766316b2521` 在 Redmi 上实际执行 reconciliation path，并恢复非零 machine session。
- Android/OEM execution suspension：`NOT_GUARANTEED_PLATFORM_LIMITATION`。进程或 worker 没有执行时间时，不保证实时 overlay 或状态迁移；这不是 HyperOS suspension 已修复的声明。
- `FB-P1-07 = MITIGATED_ACCEPTED_PLATFORM_LIMITATION`：原始 P1 失败与严重性保留，缓解与可恢复影响已记录，残余实时调度边界不属于 v1.1.0 保证。
- `FB-P2-02 = OPEN_NON_BLOCKING_V1_1_0`：recovered usage DB write 与 replay checkpoint durability 非 atomic；不重新打开 Gate E。
- Test A：`TEST_A_RECOVERY_SIGNAL = PASS`；`TEST_A_ACCOUNTING_PRECISION = INCONCLUSIVE`。本次 Gate E closeout 未独立重跑完整 E1/E2 physical test sequence。

## 历史自动化快照（99fdcc2，2026-08-14）

| 层级 | 数量 |
| ---- | ---- |
| Frontend (Vitest) | **151 / 151 PASS**（14 test files） |
| Play JVM (Robolectric) | **244 PASS** |
| Domestic JVM (Robolectric) | **254 PASS**（比 Play 多出的测试包含新的 Accessibility 强阻断回归测试） |
| RecoveryIntegration | 23 @Test（**仍是 23，不是 26**） |
| Room migrations | **6 / 6** |

> `assemblePlayDebugAndroidTest` = AndroidTest APK **successfully ASSEMBLED**，**不等于** instrumentation tests 已在真机执行。真机验证以外部设备证据为准（`TESTING.md`）。

## 已关闭的 P1/P2（历史保留）

- `FB-P1-01` 前台追踪冷启动失效 → **RESOLVED**（R1 ×3，修复 `027af94`）
- `FB-P1-02` BLOCKED 30 秒离开绕过 → **RESOLVED**（R2，修复 `9c34fe9`）
- `FB-P1-03`（候选）BlockActivity 后台启动被 HyperOS 拒绝 → **RESOLVED**（R3 ×3，修复 `3600d97`）
- `FB-P2-01` 系统返回键不拦截未保存修改 → **RESOLVED**（R4，修复 `99fdcc2`）

`FB-P2-03` 已在体验分支修复并通过针对性 JVM + API 36 AVD 验证，本轮工程集成包含该修复；合入主线与缺陷关闭记录分别见 PR #42 和 [KNOWN_ISSUES.md](KNOWN_ISSUES.md)。不把分支上的 AVD 结果升级为真机/OEM 验收。

完整历史（原现象、root cause、fix SHA、自动化回归覆盖、设备证据）保留在 `KNOWN_ISSUES.md` 的 Resolved 区。

## 当前未解决问题

1. **`FB-P1-08`（P1 / OPEN_SUPPORTED_SCOPE_RELEASE_BLOCKER；Issue #28 OPEN）**：Protection Integrity 可能在必要运行能力缺失或状态过期时仍呈现保护有效。历史设备摘要记录 Bilibili 前台约 33 秒时 REST 画面仍在倒计时；没有相应原生状态/tick/分类器/取消证据，因此根因未定。它阻塞 Gate F；模拟器与代码侧测试不关闭此门禁。
2. **`FB-P1-05`（P1 / OPEN_UNSUPPORTED_V1_1_0）**：vivo/iQOO native tick path 的原始兼容性缺陷未解决。v1.1.0 不支持 vivo/iQOO runtime，而是通过 `UNSUPPORTED_DEVICE_FOR_RELIABLE_MONITORING` fail closed；不得降级 severity，也不得标记为 RESOLVED。
3. **`COMPAT-001`（NON-BLOCKING / COMPATIBILITY OBSERVATION，OPEN OBSERVATION）**：HyperOS 仍可能拒绝尽力而为的 `tryStartBlockActivity` 后台启动，但强阻断已不依赖它。
4. **`FB-P1-07 / PLATFORM-EXEC-001`（P1 / MITIGATED_ACCEPTED_PLATFORM_LIMITATION）**：原始 Redmi execution gap 仍是历史有效失败；PR #26 提供历史恢复缓解，实时 enforcement 在 OS/OEM 不给执行时间期间不属于 v1.1.0 保证，不阻塞当前修订后的 Gate E 支持范围。
5. **`FB-P2-02`（P2 / OPEN_NON_BLOCKING_V1_1_0）**：recovered usage DB write 与 replay checkpoint durability 非 atomic；不重新打开 Gate E。
6. **Gate F = FAIL**：Issue #28 / `FB-P1-08` 仍开放，Protection Integrity 未关闭；24 小时稳定性运行 **NOT_EXECUTED**。
7. **Gate G = PENDING**：`CROSS_MACHINE_SIGNING_RECOVERY=NOT_YET_TESTED`，且 `TRUE_VERSION_UPGRADE=NOT_TESTED`；这是两个独立未完成项。
8. **Gate H = PENDING**：商店/合规材料和发行方资料尚未齐备或提交。
9. **Gate I = PENDING / NOT_STARTED**：小规模 Beta 未开始。

> 历史 RELEASE ENGINEERING GAP（产物溯源）已关闭：曾出现「本地 APK 原生 dex 已更新、Web bundle 仍旧」的不一致产物与「CI 未持久上传 artifacts」两个缺口，已由 GATE C 实现并实测关闭（`RELEASE.md` GATE C = PASS，`TESTING.md` 产物溯源）。

## 既有签名托管记录与 GATE G

下列状态沿用已记录的独立托管证据，本轮未操作密钥或重新执行恢复。仓库内 PortableSigningVault 创建/恢复脚本目前是 fail-closed 占位工具，不应把它们的安全拒绝测试等同于恢复 PASS。

CURRENT_PC_ROLE = `PRIMARY_WORKSTATION`

FINAL_SIGNING_IDENTITY = `GENERATED`

GITHUB_FINAL_SIGNING_SECRETS = `PROVISIONED`

PORTABLE_VAULT = `LOCAL_ENCRYPTED_AGE_VAULT`

LOCAL_RESTORE = `PASS`

OFF_MACHINE_BACKUP = `PASS`

CROSS_MACHINE_SIGNING_RECOVERY = `NOT_YET_TESTED`

GATE_G = `PENDING_CROSS_MACHINE_RECOVERY`

SIGNED_INSTALL = `PASS`

SIGNED_REPLACEMENT = `PASS`

DATA_PRESERVATION = `PASS`

TRUE_VERSION_UPGRADE = `NOT_TESTED_NO_VALID_LOWER_FINAL_SIGNED_BUILD`

## 发布状态边界（重要）

- **RELEASE PREPARATION ≠ STORE READY ≠ PRODUCTION RELEASE APPROVED**。
- 已完成：**GATE A / B / C / D / E（支持范围内）**。Gate E 的 `PASS_SUPPORTED_SCOPE_WITH_DOCUMENTED_PLATFORM_LIMITATION` 表示健康运行时与执行间隔恢复证据可接受，同时明确 Android/OEM 无执行时间期间不保证实时 enforcement。已知不可靠的 vivo/iQOO 在 v1.1.0 明确不支持且 fail closed。
- 仍需完成：**GATE F / G / H / I**。其中 Gate F 当前为 **FAIL**，Gate G/H/I 为 **PENDING**；详细事实见本文开头与 `RELEASE.md`。
- `FB-P1-05` 仍为 **P1 / OPEN_UNSUPPORTED_V1_1_0**；由于 vivo/iQOO 在 v1.1.0 明确不支持并 fail closed，它不阻塞 Gate D 或当前 v1.1.0 release scope，也不是 Gate E 问题。
- `FB-P1-07` 仍为 **P1 / MITIGATED_ACCEPTED_PLATFORM_LIMITATION**；在修订后的执行调度边界下，它不阻塞 Gate E。`FB-P2-02` 为 non-blocking follow-up。

## 历史状态（2026-08-12 快照，不再代表当前）

- 2026-08-12：physical-device acceptance **FAILED**，RELEASE BLOCKED due open P1（`FB-P1-01/02/03`、`FB-P2-01` OPEN）。
- 该快照的详细记录保留在 `KNOWN_ISSUES.md` 的 Resolved 历史与 `TESTING.md` 的首轮记录中；旧结论不得冒充当前状态。

## 下一路线（建议排序）

1. 在真实受支持 Android 设备上完成体验分支 smoke（设置保存、统计、完整休息、系统返回键、权限撤销后的如实降级）；这是更广泛的产品验收，不影响 `FB-P2-03` 已完成的 JVM + API 36 AVD 针对性验证。
2. Gate F — minimal stability / Protection Integrity closeout
3. Gate H — actual first-release channel compliance/materials
4. Gate I — small beta
5. Gate G remaining — cross-machine recovery before production release
6. Final RC / tag / release

其他 OEM 兼容性扩展属于 parallel/beta follow-up，不是当前 release-blocking main-path gate。
