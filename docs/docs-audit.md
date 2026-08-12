# 文档审计表（docs-audit）

- 审计日期：2026-08-12
- 文档基线：`a06a772bd0b12bc6a31e78d91a4d634ec7027437`
- 审计人：Codex 文档整理任务（第一阶段）
- 方法：逐份阅读仓库根目录文档 + 关键源码 + `D:\ai_code\flowbreak-device-evidence\reports\` 真机证据；冲突以「源码与真实测试证据 > 当前文档 > 历史进度报告 > 旧愿景文档」为序裁定，无法裁定的记入冲突矩阵并标注「未确定」。

处理代号说明：

- `KEEP` 保留并作为当前文档
- `REWRITE` 重写后作为当前文档
- `MERGE` 内容并入其他当前文档后归档原文
- `ARCHIVE` 移入 `docs/archive/`，只作历史资料
- `DELETE` 删除（本任务不采用；历史资料一律保留）

## 1. 现有文件审计表

| 现有文件 | 当前用途 | 是否仍有效 | 问题 | 最终处理 |
| ---- | ---- | ----- | -- | ---- |
| `README.md` | 项目入口 | 部分 | 混杂大量验证流水账、旧机制描述（30秒归零、5s轮询等）、Beta状态，且缺少与文档体系的链接 | REWRITE（项目入口，链接到 docs/） |
| `CHANGELOG.md` | 版本变更记录 | 有效 | 内容本身是历史记录，无冲突 | KEEP |
| `PRIVACY.md` | 隐私政策 | 有效 | 与源码/权限模型一致 | KEEP |
| `THIRD_PARTY_NOTICES.md` | 第三方许可 | 有效 | 依赖版本需随升级核对 | KEEP |
| `RELEASE_CHECKLIST.md` | 发布检查表 | 部分过时 | 含旧规则「离开全部目标应用30秒后归零」；与2026-08-12真机FAIL冲突；检查项未区分当前阻塞 | MERGE（内容并入 `docs/RELEASE.md`，原文归档） |
| `VERIFICATION.md` | Beta验证记录（2026-07-17） | 已过时 | 只记录到 Beta 1.1；2026-08-12 真机结论为「核心验收暂未通过」，该文件未反映 | ARCHIVE（迁移到 `docs/archive/progress/`，内容摘要并入 `docs/TESTING.md` / `docs/CURRENT_STATUS.md`） |
| `FlowBreak 最终产品文档（开发锚点）.md` | 曾经的「唯一锚点」 | 部分 | 混合同步：旧协作模式、旧构建链（JDK17、5s轮询、定时提醒）、旧P0/P1 backlog、旧「用户暂不能自定义目标App」；与当前源码多处冲突 | ARCHIVE（迁移到 `docs/archive/product/`；现行产品规则并入 `docs/PRODUCT.md`，协作/开发内容并入 `docs/DEVELOPMENT.md`） |
| `FlowBreak 全平台产品设计（v1.0.0，2026-04-17）.md` | 全平台愿景蓝图 | 否（愿景） | iOS/Windows/macOS/Web/云同步/付费等均为远期愿景，不属于当前产品 | ARCHIVE（迁移到 `docs/archive/product/`） |
| `FlowBreak 执行提示词（第一批）.md` | 第一批执行提示词 | 否（任务已结束） | 一次性任务分派文档，含大量当时代码细节 | ARCHIVE（迁移到 `docs/archive/prompts/`） |
| `project-memory.md` | 项目记忆流水账 | 否 | 旧任务流水、旧架构描述（fatigueEngine 双口径等）混在一起，已被源码演进覆盖 | ARCHIVE（迁移到 `docs/archive/progress/`；有价值的当前信息已并入 PRODUCT/ARCHITECTURE/DEVELOPMENT） |
| `progress_report.md` | 单任务进度报告（2026-06-07） | 否（历史记录） | 「无遗留问题」为执行AI自述，被2026-08-12真机证据推翻 | ARCHIVE（迁移到 `docs/archive/progress/`） |
| `progress_report_fatigue_alignment.md` | 单任务进度报告 | 否（历史记录） | 同上 | ARCHIVE（迁移到 `docs/archive/progress/`） |
| `progress_report_usage_events.md` | 单任务进度报告 | 否（历史记录） | 同上 | ARCHIVE（迁移到 `docs/archive/progress/`） |
| `progress-achievements-unification.md` | 单任务进度报告 | 否（历史记录） | 同上 | ARCHIVE（迁移到 `docs/archive/progress/`） |
| `walkthrough.md` | 休息页美化进度文档 | 否（历史记录） | 一次性打磨记录 | ARCHIVE（迁移到 `docs/archive/progress/`） |
| `profile-bottom.png` / `stats-chart.png` | 根目录图片 | 无引用 | 未被任何文档引用（遗留图片） | 保留原位（不删除；避免引入非文档变更） |

## 2. Fact Conflict Matrix

| 主题 | 文档A | 文档B/源码 | 当前真相 | 处理 |
| -- | --- | ------ | ---- | -- |
| 连续使用30秒reset | README/RELEASE_CHECKLIST/VERIFICATION/锚点：「离开全部目标应用满30秒后连续会话重置」是产品机制 | 源码 `BlockStateMachine.LEAVE_RESET_MS=30_000` 确实会 reset；真机 T38：BLOCKED 后离开30秒被绕过，reset 为 IDLE | 实现存在，但该行为与「BLOCKED 粘滞、解锁需休息」的产品语义冲突，2026-08-12 定为 P1 缺陷 | PRODUCT 写明目标行为（BLOCKED sticky）与当前缺陷（FB-P1-02）；RELEASE 删除旧检查项 |
| BLOCKED 解除规则 | README：「完成休息后开放10分钟」 | 源码：`reset()` 无任何条件即可把 BLOCKED→IDLE；真机 T40：BLOCKED 粘滞等待主动休息；T38：离开30秒可绕过 | 目标语义：BLOCKED 必须通过完成休息（或每日一次紧急使用）解除；当前源码违反（FB-P1-02） | PRODUCT 定义目标语义；KNOWN_ISSUES 记录缺陷 |
| 前台检测方式 | 锚点/旧进度报告：`queryUsageStats(lastTimeUsed)` | 源码 `ForegroundUsageDetector` + `ForegroundAppTracker`：UsageEvents（MOVE_TO_FOREGROUND/ACTIVITY_RESUMED）+ 游标推进 | 当前实现为 UsageEvents | ARCHITECTURE 按源码描述；旧描述只存在于归档 |
| 轮询频率 | 锚点/旧报告：「每5s轮询」「5s tick」 | 源码 `FlowForegroundService.monitor`：`handler.postDelayed(this, 2_000L)`（2秒）；delta 单次上限 10s | 当前 2 秒 tick | ARCHITECTURE 写 2 秒；旧文档归档 |
| 目标App是否可自定义 | 锚点：「用户暂不能自定义（Personalize只读展示）」「T6 待办」 | 源码 `TargetApps.tsx`：搜索/清空/已选置顶/保存，上限30；真机 T14 PASS | 可自定义（≤30） | PRODUCT/ARCHITECTURE 按源码；旧文档归档 |
| 默认目标App | 锚点：抖音/B站/快手/微信/小红书 5个 | 源码 `appNames.ts DEFAULT_TARGET_APPS`：同上 5 个 | 一致 | PRODUCT 保留 |
| 首次权限数量 | 锚点：「四项权限（使用情况/悬浮窗/通知/电池白名单）」 | 源码 `Permissions.tsx`：必需=使用情况访问+悬浮窗；可选=通知、电池豁免、无障碍 | 必需 2 项 + 可选 | PRODUCT 按源码；真机 T06-T11 佐证 |
| Login是否真实账号 | 锚点：「Login页为本地占位」 | 源码 `Login.tsx`：直接重定向 `/permissions`，仅本地昵称；真机 T05「旧Login不出现」 | 无账号体系，Login 为遗留占位页 | PRODUCT 明确「无账号」 |
| 数据存储方式 | README：SharedPreferences + Room；锚点：Web localStorage / 原生 SharedPreferences | 源码：Web UI=localStorage；原生=SharedPreferences + Room v3（FlowDatabase version=3） | 三层本地存储并存 | ARCHITECTURE 写明三种存储的职责 |
| Room 版本 | THIRD_PARTY_NOTICES：「Room 2.7.2」 | 源码 `FlowDatabase` version=3，迁移 1→2、2→3 | 两者不矛盾：2.7.2 是 AndroidX Room 库版本，3 是数据库 schema 版本 | 文档中明确区分「库版本」与「schema版本」 |
| GRACE 时长 | README/锚点：完成休息后 10 分钟 | 源码 `FlowServiceStateStore.GRACE_MS = 10 * 60_000`；真机 T27/T28 PASS | 一致：10 分钟 | PRODUCT 保留 |
| Emergency 时长 | README/锚点：每日一次、5分钟 | 源码 `EMERGENCY_GRACE_MS = 5 * 60_000`、长按10秒、每日一次（`EmergencyUnlockManager`）；真机 T37/T38 PASS | 一致：5 分钟、每日一次 | PRODUCT 保留 |
| 强阻断渠道差异 | README：Play不含无障碍、国内版可选实验性 | 源码：`src/domestic/AndroidManifest.xml` 声明 `FlowAccessibilityService`，main manifest 无；真机 T38 授权/自动回桌面 PASS | 一致 | PRODUCT/ARCHITECTURE 保留 |
| 微信视频号能力 | README：国内版「实验性、依赖可选无障碍服务」 | 源码 `FlowAccessibilityService`：className 关键词 `finder`/`videochannel` 匹配 + 60s TTL | 实现存在，但真机未对微信视频号专项验证 | PRODUCT 标注「实验性、未真机验证」 |
| START_STICKY | 旧报告未提及 | 源码 `onStartCommand` 返回 `START_STICKY`；真机 T31 进程重建 PASS | START_STICKY 恢复 | ARCHITECTURE 保留 |
| BOOT_COMPLETED | README 权限表含「开机启动」 | 源码 `BootReceiver`（BOOT_COMPLETED + QUICKBOOT_POWERON）+ main manifest；真机 T41/T42 PASS | 一致 | ARCHITECTURE 保留 |
| 测试数量 | 各文档未统一记录 | 任务基线（a06a772 已验证快照）：Frontend 148 / Play JVM 220 / Domestic JVM 220 / RecoveryIntegration 23 / Room migrations 6（本机复核：Frontend 148、RecoveryIntegration 23、Room migrations 6 相符；master 已含修复提交后本机本地结果为 Play 244 / Domestic 254，不属于 a06a772 快照） | 数字只进 CURRENT_STATUS/TESTING 快照，不散落 | 集中到快照，其他文档引用 |
| 当前 Beta 状态 | README：「MVP / Beta 验证阶段」；VERIFICATION：「Beta 1.1 重新打包」 | 真机证据 2026-08-12：正式真机验收 FAILED，P1/P2 blockers open | 处于「真机验收阶段」，未通过 | CURRENT_STATUS 覆盖旧描述 |
| 发布状态 | README：「尚未正式发布到应用商店」 | 真机证据：核心验收未通过，P1/P2 open | 未发布 + RELEASE BLOCKED | CURRENT_STATUS/RELEASE 明确 |
| 定时提醒 | 锚点 MVP 范围含「定时休息提醒」；旧进度报告含 `checkTimedReminder` | CHANGELOG：「删除未真正由 Android 原生调度的定时提醒入口」；源码 `FlowForegroundService` 已无定时提醒逻辑 | 当前产品无「定时提醒」功能 | PRODUCT 不写定时提醒；旧描述只存在于归档 |
| Snooze | 旧进度报告/project-memory：行为层「再看10分钟」按钮移除、内部 snooze 10分钟冷却 | 源码 `App.tsx`/`useStore.ts`：React 层仍有 10 分钟 snooze 冷却与连续时长扣减 | 行为层按钮已移除；内部冷却存在 | PRODUCT 不把 Snooze 写成用户功能；只写冷却语义 |

## 3. 未确定事实（不得猜）

- 微信视频号强阻断在真机上的实际效果：未专项实测（T38 使用夸克验证），仅源码层面存在。
- 修复提交（`99fdcc2` 及之前 3 个提交）在 Redmi 真机上的复测结果：尚未执行，P1/P2 保持 OPEN。
- 与系统数字健康误差 ≤10%、阻断触发延迟 ≤2s、24 小时稳定性：尚无 2026-08-12 之后的证据。
- 目标应用统计在多机型（小米/OPPO/vivo/华为）的误差对照：未完成。

## 4. 处理规则

- 归档文件不改写原文；`docs/archive/` 各 README 统一声明「历史资料，不具当前规范效力」。
- 任务完成报告（progress_report* 等）代表当时执行 AI 的自述，不等同于独立验收结果。
- 当前文档不得把 2026-08-12 真机 FAIL 写成 PASS。
