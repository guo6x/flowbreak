# KNOWN_ISSUES — 已知问题与缺陷

> 所有未解决问题统一登记在这里。缺陷只能通过「真实修复 + 设备复测」从 OPEN 转为 RESOLVED；文档整理不得改变缺陷状态。
> 
> - **Bug ID 唯一性**：一个 ID 在整个项目生命周期只能代表一个缺陷，禁止复用。已解决项保留原 ID 与完整历史，不删除。
> - **证据存储**：设备原始证据不入 Git 仓库，由测试工作区外部保存；下文 Evidence 中的 External device evidence: 路径为测试工作区报告名。
> - **状态语义**：RESOLVED 的判定标准 = 修复已合入 + 相关验证通过（附复测日期与真机证据）。非阻塞的兼容性观察使用 `COMPAT-xxx` 编号，状态为 OPEN OBSERVATION，不得在无真实用户可见失败证据时升级为 P1/P2。
> 历史 Bug 不删除；已解决项保留在下方「Resolved」区。

## 缺陷格式

| 字段 | 说明 |
| ---- | ---- |
| ID | `FB-<severity>-<seq>`（缺陷）/ `COMPAT-xxx`（兼容性观察） |
| Severity | P1（核心保护失效）/ P2（一致性/体验）/ P1候选 / NON-BLOCKING COMPATIBILITY OBSERVATION |
| Status | OPEN / RESOLVED（附复测日期与证据）/ OPEN OBSERVATION |
| Affected version/SHA | 复现时的代码基线 |
| Environment | 设备/系统/渠道 |
| Observed | 现象 |
| Expected | 目标产品语义（以 `PRODUCT.md` 为准） |
| Evidence | 证据位置 |
| Suspected root cause | 根因判断 |
| Next action | 下一步 |

## Open

> 当前**没有** OPEN 的 P0/P1/P2 产品缺陷（Redmi R1–R4 复测后，原 `FB-P1-01/02/03`、`FB-P2-01` 全部 RESOLVED，见下方历史）。

### COMPAT-001 HyperOS may reject best-effort BlockActivity background launch

- Severity：**NON-BLOCKING / COMPATIBILITY OBSERVATION**（不是产品缺陷，不是 P1/P2）
- Status：**OPEN OBSERVATION**
- Environment：Redmi Note 13 Pro 5G / Android 16 / HyperOS 3.0 / domestic debug APK（com.flowbreak.app.cn）
- Observed：`tryStartBlockActivity(...)` 尽力而为路径在 HyperOS 上仍可能被「后台弹出界面」限制拒绝（2026-08-14 R3 轮次 logcat 记录 4 条 `MIUILOG- Permission Denied Activity` 系统级记录）。
- Impact（无用户可见失败）：强阻断核心行为已 PASS——Accessibility 立即执行 HOME、`TYPE_ACCESSIBILITY_OVERLAY` 顶部横幅可见且含「开始休息」入口、横幅单实例不堆叠、非目标应用正常使用、无全手机锁死、连续三次重进目标应用均继续阻断；核心强阻断**不依赖** BlockActivity 成功启动（修复 `3600d97`）。
- Next action：多 OEM 矩阵中决定：删除 `tryStartBlockActivity` / OEM 条件化 / 继续作为 best-effort compatibility path。**不要**在无真实用户可见失败证据时升级为新的 P1/P2。
- Evidence：External device evidence: `reports/R1-R4-retest-2026-08-14.md`（R3）、`reports/T38-strong-blocking.md`（§3 历史）

## 观察项（非缺陷，需要更多证据）

- Dashboard「下一阶段倒计时」为预测值，与实际触发时间存在偏差（08-10 实测偏差约 6 分钟），阈值随轮次变化，待后续验证（证据：`progress-17-05.md`）。
- 后台 WebView innerText 冻结：后台时界面文本不随状态更新，需前台读取（证据：`progress-17-05.md`）。
- 微信视频号识别：实现存在，未真机专项验证。

## Resolved（2026-08-14 Redmi R1–R4 复测关闭）

> 判定标准：修复已合入 master + Redmi 真机复测 PASS。每条保留完整历史：原现象、root cause、fix SHA、automated regression coverage、Redmi evidence。

### FB-P1-01 前台追踪冷启动失效（ForegroundAppTracker 同包 Activity 切换误清前台）

- 原 Severity：P1；现 Status：**RESOLVED**（2026-08-14，R1 ×3 PASS）
- Affected version/SHA：`a06a772bd0b12bc6a31e78d91a4d634ec7027437`（复现基线）
- Environment：Redmi Note 13 Pro 5G / Android 16 / HyperOS 3.0 / domestic debug APK
- Observed（原现象）：目标应用（夸克等）冷启动且含启动页同包 Activity 跳转时，`sessionMs` 恒为 0，轻提醒/强提醒/阻断全部不触发；从最近任务热切换后恢复计时。08-10 15:05、08-11 17:23、08-12 13:28、08-12 14:18 共 4 次复现。
- Root cause：`ForegroundAppTracker.accept()` 以包名为键处理 ACTIVITY 级事件，同包新 Activity RESUMED 后旧 Activity 的 PAUSED/STOPPED 仍按包名匹配清空前台。
- Fix SHA：`027af94` fix(android): harden foreground usage bootstrap tracking —— 按 Activity 实例（package + className）跟踪，旧实例 PAUSED/STOPPED 不清当前实例；`ForegroundUsageDetector` 增加 bootstrap 60s 回看重试。
- Automated regression coverage：ForegroundAppTracker / ForegroundUsageDetector 单测（Play 244 / Domestic 254 JVM 套件内）。
- Redmi evidence：R1 ×3 冷启动（MainActivity→BrowserActivity 同包跳转事件形态完全复现），sessionMs 均 1:1 连续增长，不再卡 0。
- Evidence：External device evidence: `reports/BUG-P1-foreground-tracker.md`（历史 + RESOLVED 标记）、`reports/R1-R4-retest-2026-08-14.md`（R1）

### FB-P1-02 BLOCKED 可被「离开目标应用 30 秒」绕过

- 原 Severity：P1；现 Status：**RESOLVED**（2026-08-14，R2 PASS）
- Affected version/SHA：`a06a772bd0b12bc6a31e78d91a4d634ec7027437`（复现基线）
- Environment：Redmi Note 13 Pro 5G / Android 16 / HyperOS 3.0 / domestic debug APK
- Observed（原现象）：BLOCKED 状态下离开目标应用满 30 秒即被 `BlockStateMachine.LEAVE_RESET_MS=30_000` reset 为 IDLE/sessionMs=0，无需完成休息即可重新获得全新会话（13:46:44 BLOCKED → 13:49:3X 离开 → 重置 → 13:49:35 重进获得全新 18 分钟会话）。
- Root cause：`BlockStateMachine.update()` 在 `targetInForeground=false` 且离开满 `LEAVE_RESET_MS` 时无条件 `reset()`，未豁免 BLOCKED/RESTING 状态。
- Fix SHA：`9c34fe9` fix(android): make blocked state sticky after threshold —— BLOCKED 状态豁免 LEAVE_RESET_MS。
- Automated regression coverage：`BlockStateMachineTest` 新增 BLOCKED sticky 测试（`9c34fe9`，+90 行）。
- Redmi evidence：R2 同一个真实 BLOCKED 状态下：离开 29s、31s、64s、约 2min 重进均仍 BLOCKED，sessionMs 冻结（1158988）未被 30 秒规则重置；正式休息 completeRest → GRACE 10min 正常；Emergency 长按约 11s → GRACE 5min，emergencyUnlockDay 正常更新，DB emergency_unlock 正常记录。
- Evidence：External device evidence: `reports/T38-strong-blocking.md`（§6 历史）、`reports/R1-R4-retest-2026-08-14.md`（R2）

### FB-P1-03（候选）BlockActivity 后台启动被 HyperOS 拒绝

- 原 Severity：P1 候选（兼容性）；现 Status：**RESOLVED**（2026-08-14，R3 ×3 PASS）
- Affected version/SHA：`a06a772bd0b12bc6a31e78d91a4d634ec7027437`（复现基线）
- Environment：Redmi Note 13 Pro 5G / Android 16 / HyperOS 3.0 / domestic debug APK
- Observed（原现象）：悬浮窗权限缺失或 addView 失败时回退的 `BlockActivity` 阻断页无法显示——MIUI「后台弹出界面」限制拒绝后台 startActivity（logcat：`MIUILOG- Permission Denied Activity` / `Abort background activity starts from 10473`）；`FlowAccessibilityService.startBlockActivity` 的 `catch (Exception ignored)` 静默吞掉异常；应用内无「后台弹出界面」权限引导。
- Root cause：产品把 BlockActivity 当成强阻断后可见引导入口，而 HyperOS 拒绝后台启动该 Activity（厂商后台弹出限制），造成引导链路失效风险。
- Fix SHA：`3600d97` fix(domestic): remove background-activity dependency from strong blocking —— 强阻断改为 HOME + 无障碍顶部横幅（`BlockedTargetBanner`，TYPE_ACCESSIBILITY_OVERLAY）独立可靠入口；`tryStartBlockActivity` 降级为尽力而为（失败 `Log.w`，不再静默）。
- Automated regression coverage：新增 Accessibility 强阻断回归测试（Domestic 比 Play 多出的测试，Play 244 vs Domestic 254）。
- Redmi evidence：R3 ×3：真实 BLOCKED → 打开目标 App → Accessibility 立即执行 HOME → 回到桌面 → TYPE_ACCESSIBILITY_OVERLAY 顶部横幅可见 +「开始休息」入口存在；横幅单实例、重复命中不堆叠、非目标 App 可正常使用、没有全手机锁死、连续三次重新进入目标 App 都继续阻断；核心强阻断不依赖 BlockActivity 成功启动。
- 遗留：`COMPAT-001`（见 Open 区，NON-BLOCKING 观察）。
- Evidence：External device evidence: `reports/T38-strong-blocking.md`（§3 历史 + 第二轮复测补充）、`reports/R1-R4-retest-2026-08-14.md`（R3）

### FB-P2-01 系统返回键不拦截未保存修改

- 原 Severity：P2；现 Status：**RESOLVED**（2026-08-14，R4 PASS）
- Affected version/SHA：`a06a772bd0b12bc6a31e78d91a4d634ec7027437`（复现基线）
- Environment：Redmi Note 13 Pro 5G / Android 16 / HyperOS 3.0 / domestic debug APK
- Observed（原现象）：目标应用选择页存在未保存修改时，页面内返回按钮正常弹「有未保存的修改」对话框；系统返回键/手势返回直接退出到桌面，修改静默丢失。
- Root cause：拦截逻辑只绑定在自定义返回按钮上，未接入系统返回事件。
- Fix SHA：`99fdcc2` fix(android): correct device back navigation —— `MainActivity.onBackPressed` 经 `evaluateJavascript` 询问 `window.__flowbreakHandleBack`；`TargetApps` 注册全局钩子，dirty 时弹确认框并返回 true 消费按键。涉及 `MainActivity.java`、`TargetApps.tsx`、`TargetApps.test.tsx`。
- Automated regression coverage：frontend tests 148 → 151（+3 back-key 测试）。
- Redmi evidence：R4：KEYCODE_BACK 与手势返回在未保存修改时均弹「有未保存的修改 / 保存并返回 / 放弃修改 / 继续编辑」且应用不退；「放弃修改」正常返回、配置未保存；无修改时保持系统默认退出。
- 附注（非本 Bug）：复测中发现 08-12 16:14 本地 APK 的 web 资产为旧包（钩子未进 bundle），重建后通过——产物溯源问题，见 `TESTING.md` 与 `RELEASE.md` GATE C。
- Evidence：External device evidence: `reports/BUG-P2-back-key.md`（历史 + RESOLVED 标记）、`reports/R1-R4-retest-2026-08-14.md`（R4）

## 历史已解决项（旧机制修复，保留简要记录）

> 解决判定标准：修复合入 + 相关验证通过。

- **HIST-001 前台识别改用 UsageEvents**（历史已解决项，不占用 Bug ID）：原「`queryUsageStats` lastTimeUsed 不可靠」问题已通过 UsageEvents 重构（`ForegroundUsageDetector`/`ForegroundAppTracker`）解决。后续的同包 Activity 切换缺陷 `FB-P1-01` 为独立缺陷，已于 2026-08-14 RESOLVED。
- **屏幕时间暴涨（曾出现 59 小时）**：已通过 UsageEvents 配对算法 + 当日 0 点物理上限解决（`progress_report_usage_events.md`，2026-06-07；真机 T40 无跳变）。
- **无悬浮窗权限时层级不回传/不复位**：已修复（通知兜底设层级 + 清零分支显式复位，2026-06-07）。
- **成就解锁不发分 / videoTime 只增不减**：已修复（统一发分入口 + 直接赋值，`progress-achievements-unification.md`，2026-06-07）。
- **定时提醒服务重建后立即补发**：功能入口本身已随「定时提醒」移除而不再适用（CHANGELOG）；内部机制相关修复见归档。
- **首次休息积分 0→20、再次休息 +10**：逻辑核验通过（2026-06-07）。
