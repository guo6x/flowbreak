# CURRENT_STATUS — 项目当前状态

> 这是「项目现在在哪」的唯一当前视图。易变事实（HEAD、测试数、Run ID、APK SHA）只出现在本文档与 `TESTING.md` 的验证快照中。
> 2026-08-12 的「真机验收 FAILED / RELEASE BLOCKED」快照已降级为历史记录（见下方「历史状态」），不再代表当前状态。

## 当前状态速览

| 项 | 值 |
| ---- | ---- |
| Last verified date | **2026-08-14**（Redmi R1–R4 真机复测） |
| 代码基线 | `99fdcc2f6f357e78fb70dd127adedfa31a098a71` |
| CI | **SUCCESS** — Run `31577669420` / verify Job `94053353542`（对应 HEAD = `99fdcc2`） |
| 阶段 | **RELEASE PREPARATION**（发布准备；不是 STORE READY，更不是 PRODUCTION RELEASE APPROVED） |
| Redmi 核心真机验收 | **PASSED**（2026-08-14，R1–R4 全部通过） |
| 当前确认阻塞 | **P0 = 0，P1 = 0**（原 `FB-P1-01/02/03`、`FB-P2-01` 全部 RESOLVED，见 `KNOWN_ISSUES.md`） |
| 发布状态 | **RELEASE PREPARATION**（GATE A/B/C PASS；GATE D–I PENDING，见 `RELEASE.md`） |

## Redmi 设备验证（2026-08-14 当前快照）

- 设备：Redmi Note 13 Pro 5G（`2312DRA50C` / garnet）/ Android 16 / SDK 36 / HyperOS 3.0（`OS3.0.306.0.WNRCNXM`）
- 被测 APK：domestic debug（`com.flowbreak.app.cn`）。复测前在代码工作区 `99fdcc2` 上重新执行前端 build → Capacitor sync → Android assemble 后生成；该重建解决了此前旧 Web bundle 混入问题。正式 Artifact Provenance 链已由 `RELEASE.md` GATE C 建立并通过（2026-08-15，PR #1 实测）。
- 复测报告（外部设备证据工作区）：`reports/R1-R4-retest-2026-08-14.md`、`reports/final-report.md`（第二轮结论）
- **R1 冷启动前台追踪 ×3 PASS** → `FB-P1-01` RESOLVED（修复 `027af94`）：MainActivity→BrowserActivity 同包跳转事件形态复现，sessionMs 1:1 连续增长、不再卡 0。
- **R2 BLOCKED sticky PASS** → `FB-P1-02` RESOLVED（修复 `9c34fe9`）：离开 29s/31s/64s/约 2min 重进均仍 BLOCKED，sessionMs 未被 30 秒规则重置；completeRest → GRACE 10min 正常；Emergency 长按约 11s → GRACE 5min，emergencyUnlockDay 更新、DB emergency_unlock 正常记录。
- **R3 HyperOS 强阻断 ×3 PASS** → `FB-P1-03` RESOLVED（修复 `3600d97`）：BLOCKED → 打开目标 App → Accessibility 立即 HOME → 顶部横幅（TYPE_ACCESSIBILITY_OVERLAY）可见且含「开始休息」入口；横幅单实例不堆叠、非目标应用正常、无全手机锁死、连续三次重进均继续阻断；核心强阻断不依赖 BlockActivity 启动成功。
- **R4 系统返回键 PASS** → `FB-P2-01` RESOLVED（修复 `99fdcc2`）：物理 Back 与手势 Back 在未保存修改时均弹「有未保存的修改」，应用不退出；无修改保持系统默认退出。
- Smoke：IDLE→COGNITION→BLOCKED→RESTING→GRACE 全链路走通；force-stop 语义正常（不自动复活）；覆盖安装数据保留。

## 自动化验证快照（基线 99fdcc2）

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

## 当前未解决问题（非 P0/P1 产品阻塞）

1. **`COMPAT-001`（NON-BLOCKING / COMPATIBILITY OBSERVATION，OPEN OBSERVATION）**：HyperOS 仍可能拒绝尽力而为的 `tryStartBlockActivity` 后台启动（logcat `MIUILOG Permission Denied Activity`），但强阻断已不依赖它（无障碍横幅独立可用）。后续多 OEM 矩阵中决定删除 / OEM 条件化 / 保留 best-effort。
2. **GATE G — PAUSED_UNTIL_PRIMARY_WORKSTATION_FINAL_KEY_GENERATION**：首轮技术身份的 signed dry-run（Run `31934183213`）仅作为历史工程证据，身份已降级为 **SUPERSEDED_PRE_PRODUCTION**，禁止正式发行。当前 `app/release-signing-policy.json` 为 `PENDING_FINAL_HUMAN_GENERATION`，不接受旧指纹；最终 Play upload key 与 Domestic app-signing key 明确延后到原来的长期笔记本生成。当前电脑仅是 **TEMPORARY_WORKSTATION**；旧 CI signing credentials 已删除，portable vault 尚未开始。
3. **PENDING VALIDATION**：OEM 矩阵、UsageStats 精度对照、阻断延迟对照、24h stability + Protection Integrity、商店材料、小规模 Beta（`RELEASE.md` GATE D–F、H–I）。

> 历史 RELEASE ENGINEERING GAP（产物溯源）已关闭：曾出现「本地 APK 原生 dex 已更新、Web bundle 仍旧」的不一致产物与「CI 未持久上传 artifacts」两个缺口，已由 GATE C 实现并实测关闭（`RELEASE.md` GATE C = PASS，`TESTING.md` 产物溯源）。

## 发布状态边界（重要）

- **RELEASE PREPARATION ≠ STORE READY ≠ PRODUCTION RELEASE APPROVED**。
- Redmi 核心验收已通过，artifact provenance（GATE C）也已通过；GATE G 的版本策略与签名验证链已就绪（Stage A），但 production signing 已安全冻结，最终身份 deferred to primary laptop。发布准备仍需完成：最终身份 provision、signed 真机 install/upgrade、OEM matrix、usage accuracy validation、block latency validation、long-duration stability / 24h Protection Integrity、distribution compliance / store materials、beta validation。

## 历史状态（2026-08-12 快照，不再代表当前）

- 2026-08-12：physical-device acceptance **FAILED**，RELEASE BLOCKED due open P1（`FB-P1-01/02/03`、`FB-P2-01` OPEN）。
- 该快照的详细记录保留在 `KNOWN_ISSUES.md` 的 Resolved 历史与 `TESTING.md` 的首轮记录中；旧结论不得冒充当前状态。

## 下一路线（建议排序）

1. GATE E：UsageStats 精度对照 + blocking latency 对照
2. GATE F：24h stability + Protection Integrity
3. GATE D：多 OEM 真机矩阵（取决于设备可用性）
4. 原笔记本恢复后再恢复 GATE G：最终身份 → 新 secrets → signed dry-run → portable vault → cross-machine recovery → Stage C
5. 小规模 Beta（GATE I）
6. 商店正式发行准备（GATE H）
