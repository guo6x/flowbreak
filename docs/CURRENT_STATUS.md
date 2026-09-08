# CURRENT_STATUS — 项目当前状态

> 这是「项目现在在哪」的唯一当前视图。易变事实（HEAD、测试数、Run ID、APK SHA）只出现在本文档与 `TESTING.md` 的验证快照中。
> 2026-08-12 的「真机验收 FAILED / RELEASE BLOCKED」快照已降级为历史记录（见下方「历史状态」），不再代表当前状态。

## 当前状态速览

| 项 | 值 |
| ---- | ---- |
| Last verified date | **2026-09-08** |
| 代码基线 | 当前 master：`2af5e93e88687cdb8b7903f270ae67380773a200` |
| Master CI | **PASS** — Run `34227077948` |
| Signed candidate | **PASS** — Run `34227560491`（verify + release） |
| Signed Domestic APK SHA256 | `2ee7d2147b8d19a4f83e45dd85bc5eb3ea61504ab87e875260fedcdc1b0b1875` |
| 阶段 | **RELEASE PREPARATION**（不是 STORE READY，更不是 PRODUCTION RELEASE APPROVED） |
| 支持参考设备 | **Xiaomi / Redmi**；R1–R4 **PASS** |
| 新 Redmi signed smoke | **NOT_EXECUTED** |
| 当前 P0 | **0** |
| Supported-scope release-blocking P1 | **0** |
| `FB-P1-05` | **P1 / OPEN_UNSUPPORTED_V1_1_0**（vivo/iQOO runtime 不支持，fail closed） |
| 发布状态 | **GATE A/B/C PASS；GATE D PASS_SUPPORTED_SCOPE；GATE E/F/G/H/I PENDING** |

## 支持设备范围与最终范围验收

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

## 当前自动化验证

- PR #20 合并后的自动化验证：**PASS**。
- Frontend：**159 / 159 PASS**。
- Release validation：**8 / 8 PASS**。
- Full Android verify：Run `34227077948` **PASS**。
- 精确 Android JVM 套件数量以 `TESTING.md` 为准；下方旧计数仅保留为历史记录。

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

完整历史（原现象、root cause、fix SHA、自动化回归覆盖、Redmi 证据）保留在 `KNOWN_ISSUES.md` 的 Resolved 区。

## 当前未解决问题

1. **`FB-P1-05`（P1 / OPEN_UNSUPPORTED_V1_1_0）**：vivo/iQOO native tick path 的原始兼容性缺陷未解决。v1.1.0 不支持 vivo/iQOO runtime，而是通过 `UNSUPPORTED_DEVICE_FOR_RELIABLE_MONITORING` fail closed；不得降级 severity，也不得标记为 RESOLVED。
2. **`COMPAT-001`（NON-BLOCKING / COMPATIBILITY OBSERVATION，OPEN OBSERVATION）**：HyperOS 仍可能拒绝尽力而为的 `tryStartBlockActivity` 后台启动，但强阻断已不依赖它。
3. **Gate E = PENDING**：Usage accounting accuracy / blocking latency。
4. **Gate F = PENDING**：24h stability + Protection Integrity。
5. **Gate G = PENDING_CROSS_MACHINE_RECOVERY**：跨机器恢复尚未完成。
6. **Gate H = PENDING**：Store / Compliance Readiness。
7. **Gate I = PENDING**：Small-scale Beta。

> 历史 RELEASE ENGINEERING GAP（产物溯源）已关闭：曾出现「本地 APK 原生 dex 已更新、Web bundle 仍旧」的不一致产物与「CI 未持久上传 artifacts」两个缺口，已由 GATE C 实现并实测关闭（`RELEASE.md` GATE C = PASS，`TESTING.md` 产物溯源）。

## 签名与 GATE G

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
- 已完成：**GATE A / B / C / D**。Gate D 的 `PASS_SUPPORTED_SCOPE` 表示 Xiaomi/Redmi 支持参考有效，并且已知不可靠的 vivo/iQOO 在 v1.1.0 明确不支持且 fail closed。
- 仍需完成：**GATE E / F / G / H / I**。其中 FB-P1-05 本身仍 OPEN，但不阻塞已定义支持范围的 Gate D。

## 历史状态（2026-08-12 快照，不再代表当前）

- 2026-08-12：physical-device acceptance **FAILED**，RELEASE BLOCKED due open P1（`FB-P1-01/02/03`、`FB-P2-01` OPEN）。
- 该快照的详细记录保留在 `KNOWN_ISSUES.md` 的 Resolved 历史与 `TESTING.md` 的首轮记录中；旧结论不得冒充当前状态。

## 下一路线（建议排序）

1. GATE E：在已定义支持范围内完成 usage accounting accuracy + blocking latency
2. GATE F：完成最小 stability / Protection Integrity closeout
3. GATE H：完成首发渠道合规与发布材料
4. GATE I：小规模 Beta
5. GATE G remaining：完成 cross-machine recovery 后再进行 production release
6. 最终 RC / tag / release

其他 OEM 扩展属于 v1.1.0 之后或 beta 工作，不是当前 release-blocking Gate D 工作。
