# CURRENT_STATUS — 项目当前状态

> 这是「项目现在在哪」的唯一当前视图。易变事实（HEAD、测试数、Run ID、APK SHA）只出现在本文档与 `TESTING.md` 的验证快照中。
> 2026-08-12 的「真机验收 FAILED / RELEASE BLOCKED」快照已降级为历史记录（见下方「历史状态」），不再代表当前状态。

## 当前产品推进（2026-10-18，体验分支）

本轮为独立体验分支交付。以下只记录该分支自己的本地验证；不把下方既有 signed / Redmi 证据自动继承成新构建验收，也不把 AVD 结果外推为真机/OEM 证据。

| 项 | 值 |
| ---- | ---- |
| 分支 | `product/experience-polish-20261018` |
| 本地验证代码与测试提交 | `154a7cfab7da1c7924ae1acb53acd63a931c2626`（文档快照随后单独提交，不改变 App 运行时源码） |
| 本地自动化 | **PASS** — Frontend 263、Play JVM 382、Domestic JVM 412；网页 44 项、品牌 36 项、provenance 20 项、release policy 8 项；双渠道 Lint 0 error、Debug 构建通过（warning 数量见 [TESTING.md](TESTING.md)） |
| GitHub Actions | **PASS** — [run 39095168568](https://github.com/guo6x/flowbreak/actions/runs/39095168568)，验证 SHA `147a64567f56290c3b8c0dce0aa934dd2c3b494f`；unsigned verify job 完成，体验分支签名 release job 按策略跳过 |
| 范围 | 全页视觉系统、原创矢量品牌、应用内帮助、统计口径与失败状态、设置草稿和原生读取保护、诊断分享字段白名单、Domestic 强阻断默认关闭、权限轮询状态稳定、品牌与网页检查 |
| API 36 AVD | **PASS（范围有限）** — `Medium_Phone_API_36.0` / Android 16；Domestic debug 覆盖安装后可进入权限页，状态稳定为 0/2；从系统“使用情况访问”设置返回后仍为 0/2，未授予权限、未启动保护服务。原始截图见外部本地证据目录 |
| 新构建真机 / OEM / 发布验收 | **NOT_EXECUTED**；无真实手机；未调用生产签名、未合并、未发布。AVD 结果不关闭 Gate D/E/F 的真机/OEM/稳定性门禁 |
| 文档 | 整理 [PRIVACY.md](../PRIVACY.md)、[PRODUCT.md](PRODUCT.md)、[KNOWN_ISSUES.md](KNOWN_ISSUES.md)、[RELEASE.md](RELEASE.md) 与工作站恢复边界；品牌及使用指南见 [BRAND.md](BRAND.md)、[USER_GUIDE.md](USER_GUIDE.md) |
| 本分支已修复、待合并的问题 | `FB-P2-03`：Domestic 强阻断缺省值按关闭处理，显式保存开/关不被覆盖；JVM 回归与 API 36 AVD 验证通过。按缺陷台账规则，合入 master 前仍为 OPEN |

原工作区的两处未提交 Java 修改保留原状，本轮不包含它们。没有更改原生状态机、计时、GRACE、生产签名身份或发布版本。AVD 只覆盖新手引导与权限页/默认偏好回归，不是完整保护链路或真实 OEM 验收；本分支仍未达到发布验收。

## 既有发布基线速览（2026-09-11，历史验证范围）

下表保留原发布基线的门禁与实机证据，不代表本轮体验分支的最新 master 状态或新包验收。表内问题计数也是该基线当时的结论。

## 2026-10-12 最新状态（取代下方 2026-09-11 历史快照的“当前”口径）

| 项 | 当前可验证状态 |
| --- | --- |
| master | `f9ef7a1f0cbcb3925eb87cd9bd16032428054fde`，PR #31 Capacitor 安全升级已合并；master [CI 38205826590](https://github.com/guo6x/flowbreak/actions/runs/38205826590) **SUCCESS** |
| PR #29 | `b00b9cc75073e77759e28ba5b71f960033cbf1d5`，OPEN，原生 REST 防作弊修复候选和取证文件仍未合并 |
| Gate F | **FAIL**：之前 Redmi 约 33 秒 B站前台使用未能通过保护完整性验证；新候选尚无物理实测；24h 稳定性未完 |
| PR #32 | 验证专用 PR，**CLOSED/UNMERGED**；[CI 38053274608](https://github.com/guo6x/flowbreak/actions/runs/38053274608) 只能证明模拟链与构建成功 |
| PR #33 | 无手机 Gate H/G 准备 + 国内无障碍独立告知；**审查/CI 中，不代表已上架** |
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
| 发布状态 | **GATE A/B/C PASS；GATE D PASS_SUPPORTED_SCOPE；GATE E PASS_SUPPORTED_SCOPE_WITH_DOCUMENTED_PLATFORM_LIMITATION；GATE F/G/H/I PENDING** |

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

`FB-P2-03` 已在体验分支修复并通过针对性 JVM + API 36 AVD 验证，仍未合并；详见本节顶部与 [KNOWN_ISSUES.md](KNOWN_ISSUES.md)。合并后按缺陷台账规则更新状态。

完整历史（原现象、root cause、fix SHA、自动化回归覆盖、设备证据）保留在 `KNOWN_ISSUES.md` 的 Resolved 区。

## 当前未解决问题

1. **`FB-P1-05`（P1 / OPEN_UNSUPPORTED_V1_1_0）**：vivo/iQOO native tick path 的原始兼容性缺陷未解决。v1.1.0 不支持 vivo/iQOO runtime，而是通过 `UNSUPPORTED_DEVICE_FOR_RELIABLE_MONITORING` fail closed；不得降级 severity，也不得标记为 RESOLVED。
2. **`COMPAT-001`（NON-BLOCKING / COMPATIBILITY OBSERVATION，OPEN OBSERVATION）**：HyperOS 仍可能拒绝尽力而为的 `tryStartBlockActivity` 后台启动，但强阻断已不依赖它。
3. **`FB-P1-07 / PLATFORM-EXEC-001`（P1 / MITIGATED_ACCEPTED_PLATFORM_LIMITATION）**：原始 Redmi execution gap 仍是历史有效失败；PR #26 提供历史恢复缓解，实时 enforcement 在 OS/OEM 不给执行时间期间不属于 v1.1.0 保证，不阻塞当前修订后的 Gate E 支持范围。
4. **`FB-P2-02`（P2 / OPEN_NON_BLOCKING_V1_1_0）**：recovered usage DB write 与 replay checkpoint durability 非 atomic；不重新打开 Gate E。
5. **Gate F = PENDING**：24h stability + Protection Integrity。
6. **Gate G = PENDING_CROSS_MACHINE_RECOVERY**：跨机器恢复尚未完成。
7. **Gate H = PENDING**：Store / Compliance Readiness。
8. **Gate I = PENDING**：Small-scale Beta。

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
- 仍需完成：**GATE F / G / H / I**。
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
