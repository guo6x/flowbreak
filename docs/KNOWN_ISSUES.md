# KNOWN_ISSUES — 已知问题与缺陷

> 所有未解决问题统一登记在这里。缺陷只能通过「真实修复 + 设备复测」从 OPEN 转为 RESOLVED；文档整理不得改变缺陷状态。
> 历史 Bug 不删除；已解决项保留在下方「Resolved」区。

## 缺陷格式

| 字段 | 说明 |
| ---- | ---- |
| ID | `FB-<severity>-<seq>` |
| Severity | P1（核心保护失效）/ P2（一致性/体验）/ P1候选 |
| Status | OPEN / RESOLVED（附复测日期与证据） |
| Affected version/SHA | 复现时的代码基线 |
| Environment | 设备/系统/渠道 |
| Observed | 现象 |
| Expected | 目标产品语义（以 `PRODUCT.md` 为准） |
| Evidence | 证据位置 |
| Suspected root cause | 根因判断 |
| Next action | 下一步 |

## Open

### FB-P1-01 前台追踪冷启动失效（ForegroundAppTracker 同包 Activity 切换误清前台）

- Severity：P1
- Status：**OPEN**（2026-08-12 第四次复现；master 已有修复提交 `027af94`，未复测）
- Affected version/SHA：`a06a772bd0b12bc6a31e78d91a4d634ec7027437`
- Environment：Redmi Note 13 Pro 5G / Android 16 / HyperOS 3.0 / domestic debug APK（com.flowbreak.app.cn）
- Observed：目标应用（夸克等）冷启动且含启动页同包 Activity 跳转时，`sessionMs` 恒为 0，轻提醒/强提醒/阻断全部不触发；从最近任务热切换后恢复计时。08-10 15:05、08-11 17:23、08-12 13:28、08-12 14:18 共 4 次复现。
- Expected：冷启动进入目标应用后连续使用计时正常累计，提醒与阻断按 80/100/120% 触发。
- Evidence：`D:\ai_code\flowbreak-device-evidence\reports\BUG-P1-foreground-tracker.md`（含 usagestats 事件序列与 sessionMs 采样）
- Suspected root cause：`ForegroundAppTracker.accept()` 以包名为键处理 ACTIVITY 级事件，同包新 Activity RESUMED 后旧 Activity 的 PAUSED/STOPPED 仍按包名匹配清空前台。
- Next action：代码修复已提交（master `027af94`）；Redmi 精准复测 R1（冷启动路径计时）+ 回归热切换路径。

### FB-P1-02 BLOCKED 可被「离开目标应用 30 秒」绕过

- Severity：P1
- Status：**OPEN**（2026-08-12 实测；master 已有修复提交 `9c34fe9`，未复测）
- Affected version/SHA：`a06a772bd0b12bc6a31e78d91a4d634ec7027437`
- Environment：Redmi Note 13 Pro 5G / Android 16 / HyperOS 3.0 / domestic debug APK
- Observed：BLOCKED 状态下离开目标应用满 30 秒即被 `BlockStateMachine.LEAVE_RESET_MS=30_000` reset 为 IDLE/sessionMs=0，无需完成休息即可重新获得全新会话（13:46:44 BLOCKED → 13:49:3X 离开 → 重置 → 13:49:35 重进获得全新 18 分钟会话）。
- Expected：BLOCKED 必须 sticky；解除途径只有完成休息（10 分钟 GRACE）或每日一次紧急使用（5 分钟 GRACE），普通离开不能解除（`PRODUCT.md` 第 5 节）。
- Evidence：`D:\ai_code\flowbreak-device-evidence\reports\T38-strong-blocking.md` §6
- Suspected root cause：`BlockStateMachine.update()` 在 `targetInForeground=false` 且离开满 `LEAVE_RESET_MS` 时无条件 `reset()`，未豁免 BLOCKED/RESTING 状态。
- Next action：代码修复已提交（master `9c34fe9`）；Redmi 精准复测 R2（BLOCKED 后离开 30s 仍保持 BLOCKED）。

### FB-P1-03（候选）BlockActivity 后台启动被 HyperOS 拒绝

- Severity：P1 候选（兼容性）
- Status：**OPEN**（2026-08-12 两次实测被拒；master 已有修复提交 `3600d97`，未复测）
- Affected version/SHA：`a06a772bd0b12bc6a31e78d91a4d634ec7027437`
- Environment：Redmi Note 13 Pro 5G / Android 16 / HyperOS 3.0 / domestic debug APK
- Observed：悬浮窗权限缺失或 addView 失败时回退的 `BlockActivity` 阻断页无法显示——MIUI「后台弹出界面」限制拒绝后台 startActivity（logcat：`MIUILOG- Permission Denied Activity` / `Abort background activity starts from 10473`）；`FlowAccessibilityService.startBlockActivity` 的 `catch (Exception ignored)` 静默吞掉异常；应用内无「后台弹出界面」权限引导。
- Expected：无悬浮窗权限时阻断页 fallback 可用，或至少给出可操作的权限引导。
- Evidence：`D:\ai_code\flowbreak-device-evidence\reports\T38-strong-blocking.md` §3
- Suspected root cause：MIUI 厂商限制 + 异常被吞 + 无权限引导入口。
- Next action：代码修复已提交（master `3600d97` 移除强阻断对后台 Activity 的依赖）；Redmi 精准复测 R3。

### FB-P2-01 系统返回键不拦截未保存修改

- Severity：P2
- Status：**OPEN**（2026-08-12 实测；master 已有修复提交 `99fdcc2`，未复测）
- Affected version/SHA：`a06a772bd0b12bc6a31e78d91a4d634ec7027437`
- Environment：Redmi Note 13 Pro 5G / Android 16 / HyperOS 3.0 / domestic debug APK
- Observed：目标应用选择页存在未保存修改时，页面内返回按钮正常弹「有未保存的修改」对话框；系统返回键/手势返回直接退出到桌面，修改静默丢失。
- Expected：所有退出路径（含系统返回键）行为一致，未保存修改需确认。
- Evidence：`D:\ai_code\flowbreak-device-evidence\reports\BUG-P2-back-key.md`
- Suspected root cause：拦截逻辑只绑定在自定义返回按钮上，未接入系统返回事件。
- Next action：代码修复已提交（master `99fdcc2`）；Redmi 精准复测 R4。

## 观察项（非缺陷，需要更多证据）

- Dashboard「下一阶段倒计时」为预测值，与实际触发时间存在偏差（08-10 实测偏差约 6 分钟），阈值随轮次变化，待后续验证（证据：`progress-17-05.md`）。
- 后台 WebView innerText 冻结：后台时界面文本不随状态更新，需前台读取（证据：`progress-17-05.md`）。
- 微信视频号识别：实现存在，未真机专项验证。

## Resolved

> 已解决项保持简要记录。解决判定标准：修复合入 + 相关验证通过。

- **FB-P1-01（旧表述：getTopAppName 识别不准）**：已通过 UsageEvents 重构（`ForegroundUsageDetector`/`ForegroundAppTracker`）解决原「queryUsageStats lastTimeUsed 不可靠」问题；但同包 Activity 切换误清前台的衍生问题仍 OPEN（见 FB-P1-01 当前状态）。
- **屏幕时间暴涨（曾出现 59 小时）**：已通过 UsageEvents 配对算法 + 当日 0 点物理上限解决（`progress_report_usage_events.md`，2026-06-07；真机 T40 无跳变）。
- **无悬浮窗权限时层级不回传/不复位**：已修复（通知兜底设层级 + 清零分支显式复位，2026-06-07）。
- **成就解锁不发分 / videoTime 只增不减**：已修复（统一发分入口 + 直接赋值，`progress-achievements-unification.md`，2026-06-07）。
- **定时提醒服务重建后立即补发**：功能入口本身已随「定时提醒」移除而不再适用（CHANGELOG）；内部机制相关修复见归档。
- **首次休息积分 0→20、再次休息 +10**：逻辑核验通过（2026-06-07）。
