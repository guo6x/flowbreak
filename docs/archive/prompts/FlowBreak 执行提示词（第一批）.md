# FlowBreak 执行提示词（第一批）

> 用法：把下面每个【任务 Tx】整段复制发给一个执行 AI。
> 五个任务（T1–T5）**互不改同一文件，可全部并行**。每个执行 AI 只动自己授权的文件。
> 完成后每人按各自 prompt 末尾要求回交一份“进度文档”。
> 公共前置：所有人先读仓库根目录 `FlowBreak 最终产品文档（开发锚点）.md`（下称“锚点”），尤其第 4（数据模型）、7.4（红线）、8（构建）。

---

## 并行/串行说明（给用户看，不用转发）
- T1、T2、T3、T4、T5 **全部可并行**：分别开独立 clone 或 git worktree，各自基于当前 master 改，跑完分别提交，最后依次合并（无冲突）。也可在同一仓库按任意顺序串行。
- 屏幕时间正确性 = T2(原生取数) + T3(去上限) 合并后才能联测；写代码阶段互不依赖。
- T6（自定义监控 App）放第二轮，等 T1–T5 合并后再发，且需用户先确认是否要做。

---

# 【任务 T1】原生前台服务正确性加固

【任务】加固 FlowForegroundService 的三层干预与定时提醒逻辑，并补充可诊断日志，让真机能验证。

【背景】读锚点第 2.2（三层干预）、2.4（定时提醒）、第 5 节 P0-2 / P2-1 / P2-2。当前真机上三层覆盖层是否真弹、被杀后是否恢复，需要靠日志验证；另有两个已知 bug 要修。

【只改这一个文件】`app/android/app/src/main/java/com/flowbreak/app/FlowForegroundService.java`。**不要动任何其它文件。**

【怎么做】
1. 前台应用检测加固：`getTopAppName()` 当前用 `queryUsageStats(最近60s)` 取 lastTimeUsed，在 Android 10+ 多机型不可靠（取不到正在前台的目标 App）。改用 `UsageStatsManager.queryEvents(now-10s, now)` 遍历事件，取最后一个 `MOVE_TO_FOREGROUND`/`ACTIVITY_RESUMED` 的包名作为当前前台 App；取不到时再回退到原 queryUsageStats 逻辑。目的：确保用户在抖音/B站时能被正确识别为目标 App。
2. 修复定时提醒“服务重建后立即补发”：`checkTimedReminder()` 依赖 `lastTimedReminderMs`，服务重建后它为 0，会在下一次 tick 立刻发一条提醒。在 `onCreate()`（或 `loadFromPrefs()` 之后）把 `lastTimedReminderMs = System.currentTimeMillis()` 初始化，使重建后也要等满一个完整间隔才提醒。
3. 修复“无悬浮窗权限时层级不回传”：`showLevel1/2/3()` 在 `!canShowOverlay()` 时只发通知就 `return`，没有更新 `staticCurrentLevel`/`currentLevel`，导致 JS 端 `getCurrentFatigueLevel` 仍读到 0，App 内显示与通知不一致。修复：发通知兜底时也把 `staticCurrentLevel = currentLevel = 对应层级` 设上。
   - **注意联动 bug**：`dismissOverlay()` 在 `overlayView == null` 时会提前 return，**不会**复位 `staticCurrentLevel`。所以一旦用通知兜底设了层级，离开目标 App / 低于阈值时必须仍能把层级复位为 0。请确保：在 `checkUsage()` 的“离开目标 App 满 30s 清零”分支、以及 `determineAndShowOverlay()` 的“低于阈值”分支里，无论有没有 overlayView，都把 `staticCurrentLevel = currentLevel = 0` 显式复位。
4. 诊断日志（关键）：在 `checkUsage()` 每个 tick 打一条汇总日志，统一 TAG 用现有 `"FlowForegroundService"`，至少包含：当前前台包名、是否目标、连续分钟数、limitMinutes、百分比、当前层级、是否有悬浮窗权限。在 `showLevel1/2/3` 进入时各打一条。便于用 `adb logcat -s FlowForegroundService` 验证。

【不要做】不要改阈值(80/100/120)、不要改冷却时长、不要改 UI 样式、不要重构悬浮窗绘制代码、不要动 NativeFlowPlugin、不要加无关 try-catch/注释/抽象。

【验收标准】
- 编译通过：`cd app/android && ./gradlew assembleDebug`（JDK 17，见锚点第 8）。
- 代码层面：上述 4 点均落实；`staticCurrentLevel` 在通知兜底时被设、在清零分支被复位（无论 overlayView 是否为 null）。
- 可验证：真机 logcat 能看到每 tick 的层级/百分比汇总日志（这条由用户真机验，你只需保证日志已加且能编译）。

【交付】按锚点 7.2 模板写进度文档。第 4 节自测里务必贴 `assembleDebug` 结果；第 3 节说明 getTopAppName 改法与 staticCurrentLevel 复位的处理。

---

# 【任务 T2】屏幕使用时间根治（UsageEvents 重写取数）

【任务】重写 `getUsageStats`，用 UsageEvents 精确计算“当日目标 App 前台总时长”，根除“屏幕时间暴涨到几十小时”的脏值。

【背景】读锚点第 2.3、第 5 节 P0-1。现状用 `getTotalTimeInForeground()` 累加，部分机型返回跨日累计/翻倍脏值，导致曾出现 59 小时。当前靠 JS 端 24h 硬上限缓解（那是创可贴，由 T3 去掉）。

【只改这一个文件】`app/android/app/src/main/java/com/flowbreak/app/NativeFlowPlugin.java`，**只改 `getUsageStats` 方法体**。**不要动其它方法、其它文件。**

【怎么做】
1. 时间窗：当日 0 点 → now（沿用现有 Calendar 取 startTime 的写法）。
2. 目标 App 集合：沿用 `PreferenceUtils.getMigratedTargetApps(prefs)`（StringSet，见锚点 4.2，勿改成 JSON）。
3. 用 `usm.queryEvents(startTime, endTime)`，遍历 `UsageEvents.Event`：
   - 对每个包名维护“最近一次进入前台的时间戳”。遇到 `MOVE_TO_FOREGROUND`(或 `ACTIVITY_RESUMED`) 记录开始；遇到 `MOVE_TO_BACKGROUND`(或 `ACTIVITY_PAUSED`/`ACTIVITY_STOPPED`) 时，若有配对的开始时间则累加 `(end-start)` 到该包名时长，并清除开始时间。
   - 只统计在目标集合内的包名。
   - 遍历结束后，对仍处于“前台未配对”的目标包名，用 `endTime` 作为结束补齐。
   - 对每段时长做基本健壮性约束：忽略负值；单段超过 24h 视为异常丢弃。
4. 汇总所有目标 App 时长（毫秒）求和，返回 `screenTimeSeconds = 总毫秒/1000`。键名、返回结构保持与现在一致（`{ screenTimeSeconds }`）。

【不要做】不要改返回字段名/结构、不要改 `getCurrentApp`/`getAppUsageList`/`saveSettings` 等其它方法、不要去 JS 端动 24h 上限（那是 T3 的活）、不要加缓存层或新工具类。

【验收标准】
- 编译通过：`cd app/android && ./gradlew assembleDebug`。
- 逻辑：返回值只含目标 App 当日前台时长之和，单调合理、不可能超过 (now-今日0点)。
- 真机（用户验）：与系统“数字健康/屏幕使用时间”里这几个 App 之和误差 ≤10%，连续使用一天无跳变。

【交付】按锚点 7.2 写进度文档，第 3 节讲清 UsageEvents 配对算法与异常段处理，第 4 节贴 assembleDebug 结果。

---

# 【任务 T3】Web 监控层对齐与去创可贴

【任务】去掉屏幕时间 24h 硬上限（依赖 T2 已修），并让浏览器侧疲劳判定口径对齐原生（按用户单次限额百分比，而非固定 60 分钟）。

【背景】读锚点第 2.1（两套疲劳口径）、2.3、第 5 节 P0-1 / P1-1。原生真机以“连续分钟/limitMinutes 百分比、80/100/120%”判层级；浏览器侧 `fatigueEngine.ts` 用固定 `min(1, 分钟/60)` 算分数，两者不一致。统一以原生百分比口径为准，Web 仅作降级预览但口径要一致。

【只改这两个文件】`app/src/App.tsx`、`app/src/backend/fatigueEngine.ts`。**不要动其它文件。**

【怎么做】
1. 去 24h 创可贴：`App.tsx` 的 GlobalMonitor 里这行
   `const safeTotal = Math.min(result.screenTimeSeconds, 86400);`
   改为直接使用 `result.screenTimeSeconds`（T2 已保证其可靠）。仅保留对负值的防御（<0 取 0）。
2. Web 疲劳口径对齐：当前 web 分支（`Capacitor.isNativePlatform()===false` 时）用 `calculateFatigueScore` + `getInterventionLevel` 由“连续秒数→分钟→/60 分数”判层级。改为按百分比口径：`percent = 连续分钟 / profile.sessionLimit * 100`，映射 `≥80%→PERCEPTION、≥100%→COGNITION、≥120%→ACTION、否则 NONE`，与原生一致。可在 `fatigueEngine.ts` 新增一个纯函数（如 `getLevelByPercent(percent)`）供调用，保持 `App.tsx` 改动最小；并保留/不破坏原生分支（原生分支仍轮询 `getCurrentFatigueLevel`，不要动）。
3. 在 `fatigueEngine.ts` 顶部用一行注释标明：浏览器口径为降级预览，真机以原生百分比为准（仅注释，不展开）。

【不要做】不要动原生轮询分支、不要改 InterventionOverlay、不要改 storage/useStore、不要改 native 文件、不要把 web 计时逻辑推倒重写（只换层级判定口径）、不要加多余抽象。

【验收标准】
- `cd app && npx tsc --noEmit` 零错误；`npm run build` 通过。
- 代码：24h 上限已移除；web 层级判定改为 sessionLimit 百分比口径，与原生 80/100/120 一致。
- 浏览器 `npm run dev` 自测：连续停留达到 sessionLimit 的 80%/100%/120% 时长时，分别出现一/二/三层覆盖层。

【交付】按锚点 7.2 写进度文档，第 3 节说明 web 口径改动；第 4 节贴 tsc/build 结果与浏览器自测现象。

---

# 【任务 T4】统计与积分一致性修正

【任务】统一“成就解锁即发积分”，消除部分成就解锁不发分的不一致；修正 `setTodayScreenTime` 里 videoTime 只增不减的问题。

【背景】读锚点第 2.5、第 5 节 P1-2 / P2-4。现状：`storage.unlockAchievement` 解锁成就但不发分；只有“完成休息 addPoints(10)”和“store 层显式调用 unlockAchievement 时 addPoints(10)”两条路给分，导致 `evaluateAchievements` 自动解锁的成就（如一周坚持、护眼达人）发不出分，且 store 层有竞态补丁。

【只改这两个文件】`app/src/backend/storage.ts`、`app/src/hooks/useStore.ts`。**不要动其它文件。**

【怎么做】
1. 单一发分入口：在 `storage.unlockAchievement(id)` 内，当且仅当成就由未解锁翻转为已解锁时 `addPoints(10)`（每个成就解锁固定 +10，只发一次）。
2. 简化 store 层：`useStore.ts` 的 `unlockAchievement` 去掉它自己的 `addPoints(10)` 与“alreadyUnlocked 竞态补丁”，改为只调用 `storage.unlockAchievement(id)` 然后从 storage 刷新 `achievements` 和 `points` 到 state（积分由 storage 统一负责，不再重复加）。保持函数签名与返回值 `Achievement | null` 不变（App.tsx / RestMode 仍在调用，勿改其调用方）。
3. 休息行为奖励保留：`completeRestActivity` 末尾的 `addPoints(10)` 保留（这是“完成休息”动作奖励，与成就分相互独立）。确认：首次完成休息 = 10(休息) + 10(health_guardian 成就) = 20；之后每次休息 = 10。不要出现同一成就重复发分。
4. videoTime 修正：`setTodayScreenTime` 里 `videoTime: Math.max(current.videoTime, seconds)` 改为 `videoTime: seconds`（目标 App 当日总时长≈视频时长，直接用传入值，避免只增不减导致跨日不准）。

【不要做】不要改成就列表/评判条件 `evaluateAchievements`、不要改 store 其它 action、不要动 UI、不要动 native、不要改 addPoints 的实现、不要加抽象。

【验收标准】
- `cd app && npx tsc --noEmit` 零错误；`npm run build` 通过。
- 逻辑：任意成就解锁都恰好 +10 一次（含自动解锁）；store.unlockAchievement 不再自行加分；竞态补丁已移除；videoTime 用传入值。
- 浏览器自测：完成一次休息，积分由 0 → 20（首次，含成就）；再次休息 +10。

【交付】按锚点 7.2 写进度文档，第 3 节讲清“单一发分入口”改法与去竞态补丁；第 4 节贴自测积分变化。

---

# 【任务 T5】休息页体验美化（背景 + 背景音乐 + 提示音）

【任务】把休息页做得更精美：丰富 5 套主题背景（不再是单一纯色/平淡渐变），加入可开关的轻柔背景音乐，保留并打磨步骤提示音。

【背景】读锚点第 2.5、第 5 节 P1-4。用户原话：感觉没有背景音乐、休息背景单一颜色、希望更精美。

【只改这一个文件（+ 可新增资源）】`app/src/pages/RestMode.tsx`。如需音频/图片资源，可新增到 `app/public/`（如 `app/public/sounds/`）并在该文件引用。**不要动其它源码文件。**

【怎么做】
1. 背景升级：现有 5 套主题（森林/海洋/山脉/花园/日落）的背景从单色/平淡渐变升级为更有层次的多停点渐变 + 缓慢动效（可用 framer-motion 做极慢的渐变位移/光晕），与现有主题粒子叠加，保证柔和不刺眼、不影响倒计时与引导文字可读性。背景按 `profile.selectedBackground` 选择（沿用现有逻辑）。
2. 背景音乐：进入休息后播放一段轻柔循环环境音（白噪/自然音/舒缓 pad）。两种实现任选其一：
   - 方案 A（推荐，无版权风险）：用 Web Audio API 生成低频 pad/白噪+缓慢起伏（类似现有 useChime 思路扩展为持续循环音）。
   - 方案 B：放一个小体积、可商用免版权的 loop 到 `app/public/sounds/`，用 `<audio loop>` 播放。
   要求：默认开启但音量轻柔；提供一个静音/开关按钮；因浏览器自动播放策略，音频须在用户手势（进入休息即点击进入，属手势）后启动；离开休息页（卸载/完成/提前结束）必须停止音频，无残留。
3. 提示音：保留现有步骤切换提示音与完成 C-E-G 收尾音，可微调更自然，但不破坏现有完成流程（`completeRest` + `unlockAchievement('health_guardian')` 等调用保持不变）。

【不要做】不要改休息完成的数据逻辑/积分/成就调用、不要改路由、不要引入重型音频库（用 Web Audio 或原生 audio 即可）、不要动其它页面/store/native、不要加无关重构。注意音频资源体积要小、确保免版权。

【验收标准】
- `cd app && npx tsc --noEmit` 零错误；`npm run build` 通过。
- 浏览器自测：5 套背景均明显更精美且有柔和动效；进入休息有轻柔背景音乐、可静音；离开页面音频停止；步骤/完成提示音正常；倒计时与引导文字清晰可读。
- 若新增资源，确认体积合理（背景音建议 < 500KB）且来源免版权（在进度文档注明来源/生成方式）。

【交付】按锚点 7.2 写进度文档，第 2 节列出新增资源（若有）及来源，第 4 节贴自测现象。

---

# 第二轮（先不发，待 T1–T5 合并 + 用户确认）

## 【任务 T6（候选）】自定义监控 App 名单
- 现状：监控的 5 个目标 App 在 `appNames.ts: DEFAULT_TARGET_APPS` 硬编码，Personalize 仅只读展示（锚点 5 节 P2）。
- 为什么放第二轮：它会改 `Personalize.tsx` + `App.tsx`（startService 传参）+ `storage.ts`（持久化所选），与 T3/T4 改的文件重叠，必须等第一批合并后再做，避免冲突；且属于“扩功能”，需你确认是否要做、以及交互方式（建议：用已有 `getAppUsageList` 列出近期使用 App，多选勾选保存）。
- 需要你决定：是否做 / 是否限定只能在内置候选里选 / 还是允许从已安装应用任意选。确认后我再补完整提示词。
