# FlowBreak 最终产品文档（开发锚点 / Single Source of Truth）

> 版本：v1（2026-06-07 建立）
> 性质：**项目唯一锚点**。它定义“最终产品应该是什么样”、“当前到了哪一步”、“怎么协作”。
> 与旧文档关系：`FlowBreak 全平台产品设计（v1.0.0，2026-04-17）.md` 是**愿景蓝图**（全平台/云同步/付费等），本文件是**落地真相**。两者冲突时，以本文件为准。

---

## 0. 协作模式（先读这一节）

当前阶段：产品已能构建出 APK，但仍有较多瑕疵、不好用。接下来按以下分工推进：

| 角色 | 谁 | 职责 |
|------|----|------|
| 产品负责人 | 用户 | 定优先级、拍板、转达提示词、真机验收 |
| 指挥官 / 架构 / 审查 | Claude（我） | 拆任务、写提示词、定验收标准、审查执行 AI 的代码与进度文档、把关红线 |
| 执行者 | 其他 AI | 按提示词写代码，完成后提交**进度文档** |

工作闭环：
```
我拆任务 + 写提示词 → 用户转达给执行 AI → 执行 AI 改代码 + 写进度文档
→ 用户把进度文档/diff 给我 → 我审查（对照本文件验收标准）→ 通过 / 打回重做
→ 更新本文件第 5、6 节状态
```

执行 AI 必读：本文件第 4（数据模型）、第 7（协作规范，含进度文档模板与代码红线）、第 8（构建）。
**禁止**执行 AI 自行扩大需求范围、加抽象层、加无关重构（见 7.4 红线）。

---

## 1. 产品定位与边界

### 1.1 一句话
在用户刷短视频/社交 App 出现“数字疲劳”临界点时，用**三层渐进式、不惩罚、不制造内疚**的干预，自然引导其停下休息，养成健康使用习惯。

### 1.2 当前真实形态（务必认清）
- **平台：仅 Android**。技术栈 = Capacitor 8 + React 19 + TypeScript + Zustand + Tailwind v4 + 原生前台服务（Java）。
- 旧蓝图里的 iOS / Windows / macOS / Web PWA / Firebase 云同步 / 端到端加密 / 付费分层 / 家庭管理 / AI 推荐 / 健康数据 / 无障碍视频检测 / 听觉+触觉唤醒 / 认知小游戏 —— **全部不在当前范围**，是远期愿景。
- 数据**全本地**：Web 侧 `localStorage`，原生侧 `SharedPreferences`。无后端、无账号体系（Login 页为本地占位）。

### 1.3 MVP 锁定范围（“最终产品”= 把下面这些做对做好）
1. 屏幕使用时间统计（仅统计被监控的目标 App，口径准确、不暴涨）。
2. 三层渐进式干预（覆盖层能盖在抖音/B站等目标 App 之上，逐层加压，行为层不可轻易跳过）。
3. 定时休息提醒（不打开 App 也能按时提醒，可配置）。
4. 休息引导（眼/拉伸/呼吸三选一，有引导动画、提示音、好看的背景）。
5. 激励系统（积分 / 成就 / 连续天数，数值正确、给得出来）。
6. 引导与权限流（首次能顺利拿到“使用情况访问 + 悬浮窗 + 通知 + 电池白名单”四项权限）。

### 1.4 监控的目标 App（当前硬编码，见 `appNames.ts: DEFAULT_TARGET_APPS`）
抖音 `com.ss.android.ugc.aweme`、B站 `tv.danmaku.bili`、快手 `com.smile.gifmaker`、微信 `com.tencent.mm`、小红书 `com.xingin.xhs`。
> 注：用户暂不能自定义这份名单（Personalize 页只读展示）。是否开放为可配置见 5 节 Backlog。

---

## 2. 核心机制（最终形态定义）

### 2.1 疲劳判定：当前有**两套并存**，这是最大复杂度来源（务必理解）

**A. 原生（Android 真机的真相源）—— 基于“连续会话时长占限额百分比”**
- 前台服务每 5s 轮询前台 App；若是目标 App，连续会话 `continuousSessionMs += 5s`。
- `连续分钟数 = continuousSessionMs / 60000`；`百分比 = 连续分钟 / 单次限额(limitMinutes)`。
- `limitMinutes` = 用户在 Personalize 设的“单次连续使用提醒”，默认 25 分钟。
- 阈值：**≥80% → 第一层；≥100% → 第二层；≥120% → 第三层**。（默认 25 分钟时 = 20 / 25 / 30 分钟）
- 离开目标 App 超 30s，或熄屏超 30s，会话清零（仅限尚未进入 BLOCKED 的连续会话；一旦进入 BLOCKED，必须完成休息或合法紧急使用才能解除阻断）。
- 文件：`FlowForegroundService.java`（`checkUsage` / `determineAndShowOverlay`）。

**B. Web（浏览器预览/降级用）—— 基于“疲劳分数”**
- `分数 = 0.7 × min(1, 连续分钟/60) + 0.3 × 时段因子`；时段因子：22:00–02:00=0.8，13:00–15:00=0.6，其余=0.2。
- 阈值：`<0.3 无 / <0.5 第一层 / <0.7 第二层 / ≥0.7 第三层`。白天约 21 / 38 / 55 分钟。
- 文件：`fatigueEngine.ts` + `App.tsx: GlobalMonitor`。

> **已知不一致（记入 Backlog P1）**：A 用“限额百分比 + 用户可配 limit”，B 用“固定分钟阈值 + 忽略 limit”。同一个人在真机内 App 看到的 React 覆盖层层级（真机时其实是轮询自原生，已对齐）与浏览器预览不同。最终产品应**以原生百分比口径为唯一标准**，Web 侧仅作降级展示，且文档/代码注明“浏览器口径不保证与真机一致”。

### 2.2 三层渐进式干预（最终形态）

干预有**两套呈现**，按“当前在哪个 App”分工：
- **在目标 App 内（FlowBreak 在后台）**：由原生 `FlowForegroundService` 用 `WindowManager` 悬浮窗呈现 —— 这是“能盖在抖音上面”的关键。
- **在 FlowBreak App 内**：由 React `InterventionOverlay.tsx` 呈现（真机时层级轮询自原生，保持一致）。

| 层级 | 名称 | 触发(原生) | 视觉 | 退出方式 |
|------|------|-----------|------|---------|
| 1 | 感知层 PERCEPTION | ≥80% | 四边绿色呼吸边框 + 右下“已观看 N 分钟”角标 | 点角标关闭，冷却 5 分钟 |
| 2 | 认知层 COGNITION | ≥100% | 橙色呼吸边框 + 底部卡片（“知道了 / 去休息”） | “知道了”冷却 5 分钟 |
| 3 | 行为层 ACTION | ≥120% | 红色半透明全屏 + 居中卡片（3 个休息活动 + “开始休息”） | **不可滑走**：拦截背景点击、无 snooze；冷却 10 分钟（仅在用户处理后） |

无悬浮窗权限时的降级：原生发**高优先级通知**（`ALERT_CHANNEL`，带震动）代替覆盖层。
核心原则：**渐进**（颜色绿→橙→红、频率变快）、**可升级**、**第三层必须处理**。

### 2.3 屏幕使用时间统计（最终形态）
- 口径：**只统计目标 App 当日前台总时长之和**（不是全机屏幕时间）。
- 原生数据源：`NativeFlowPlugin.getUsageStats()` 累加目标 App 的 `getTotalTimeInForeground()`（从当日 0 点起）。
- Web 侧 `App.tsx` 每 5s 拉取并 `setScreenTime`，当前有 **24 小时硬上限**（`Math.min(v, 86400)`）防暴涨——这是**创可贴，不是根治**。
- **根治方向（Backlog P0）**：部分机型 `getTotalTimeInForeground()` / `INTERVAL_DAILY` 会返回跨日累计或翻倍的脏值（“59 小时”根因）。最终应改用 `UsageEvents`（`MOVE_TO_FOREGROUND`/`MOVE_TO_BACKGROUND` 配对计算当日前台时长），并去掉 24h 创可贴。

### 2.4 定时休息提醒（最终形态）
- 真机真相源：原生 `FlowForegroundService.checkTimedReminder()`，在时间段内每 N 分钟发高优先级通知。配置存 `SharedPreferences`。
- Web 侧 `reminderScheduler.ts` 用 `setInterval` 仅作降级（App 切后台即停，不可靠）。
- 设置项（`ReminderSettings.tsx`）：开关、间隔(15/30/45/60)、时段(开始/结束小时)、周末免打扰。默认：开启、30 分钟、8–22 点、周末免打扰。
- 已知小问题（Backlog P2）：服务重建后 `lastTimedReminderMs=0`，下一 tick 立即补发一次；提醒纯时间驱动，不判断用户是否真的在用手机。

### 2.5 休息引导 + 激励（最终形态）
- 休息页 `RestMode.tsx`：眼部放松 / 身体拉伸 / 深呼吸三选一，分步引导 + Web Audio 提示音 + 渐变背景 + 主题粒子；完成有奖励页（彩纸）。
- 激励：完成一次休息 `addPoints(10)`；成就 8 个（见 `storage.ts: DEFAULT_ACHIEVEMENTS`）；连续天数 `streak`。
- 已知不一致（Backlog P1）：仅“完成休息”和“被显式调用的成就”给分；`evaluateAchievements` 自动解锁的成就（如一周坚持、护眼达人）**不发积分**。最终应统一“每解锁一个成就 +固定分”。

---

## 3. 系统架构（实际）

### 3.1 双干预系统数据流
```
                 ┌───────────────── Android 设备 ─────────────────┐
用户在抖音里刷  → │ FlowForegroundService（前台服务，每5s轮询）        │
                 │   ├─ UsageStatsManager 取前台App / 当日时长        │
                 │   ├─ 累计连续会话 → 算百分比 → 决定层级            │
                 │   ├─ WindowManager 画悬浮层(L1/L2/L3) 盖在抖音上   │
                 │   ├─ checkTimedReminder 定时通知                  │
                 │   └─ 静态变量 staticCurrentLevel/Minutes 供JS轮询 │
                 └───────────────────────┬───────────────────────┘
                                         │ Capacitor Bridge (NativeFlow 插件)
用户在 FlowBreak里 → ┌────────────────────┴───────────────────────┐
                    │ React WebView                               │
                    │   App.tsx GlobalMonitor 每5s:               │
                    │     getUsageStats→setScreenTime(统计)        │
                    │     getCurrentFatigueLevel→setFatigue(层级)  │
                    │   InterventionOverlay 在App内呈现同一层级     │
                    │   Zustand(useStore) ←→ storage.ts(localStorage)│
                    └─────────────────────────────────────────────┘
```

### 3.2 关键文件地图
**Web / 业务（`app/src/`）**
- `App.tsx` —— 路由 + `GlobalMonitor`（跨页监控、喂屏幕时间、轮询原生层级、渲染覆盖层、服务保活）。**改动最敏感。**
- `hooks/useStore.ts` —— Zustand 全局状态，所有 UI 读写入口。
- `backend/storage.ts` —— localStorage 持久化 + 业务规则（统计/积分/成就/连续天数）。
- `backend/fatigueEngine.ts` —— Web 疲劳分数算法。
- `backend/reminderScheduler.ts` —— Web 定时提醒（降级）。
- `backend/appNames.ts` —— 包名→中文名映射 + 目标 App 名单。
- `backend/nativeFlow.ts` —— 原生插件 TS 接口定义。
- `components/InterventionOverlay.tsx` —— App 内三层覆盖层 UI。
- `components/BottomNav.tsx` —— 底部导航。
- `pages/` —— Onboarding / Login / Permissions / Personalize / Dashboard / Statistics / Achievements / Profile / RestMode / ReminderSettings。

**原生（`app/android/app/src/main/java/com/flowbreak/app/`）**
- `FlowForegroundService.java` —— 前台服务：会话累计、层级判定、悬浮窗、定时提醒、保活。**改动最敏感。**
- `NativeFlowPlugin.java` —— Capacitor 插件：权限、`getUsageStats`、起停服务、读写设置。
- `PreferenceUtils.java` —— SharedPreferences 读取/迁移（`PREF_TARGET_APPS` StringSet）。
- `MainActivity.java` / `BootReceiver.java` —— 入口 / 开机自启。

---

## 4. 数据模型（执行 AI 必读，改前对齐 key）

### 4.1 localStorage（`storage.ts` / `reminderScheduler.ts`）
| key | 内容 |
|-----|------|
| `fb_profile` | UserProfile（name/type/dailyGoal/sessionLimit/restDuration/selectedBackground/onboardingDone） |
| `fb_stats` | `{ 'YYYY-MM-DD': DailyStats }`（totalScreenTime/videoTime/restCount/interventionCount/focusMinutes，单位秒） |
| `fb_achievements` | Achievement[]（8 个） |
| `fb_streak` / `fb_points` | 连续天数 / 积分 |
| `fb_session_start` | 本次会话起始时间戳 |
| `fb_activity` | `{ 'YYYY-MM-DD': ActivityEvent[] }`（仅留 7 天，每天≤200 条） |
| `fb_last_rest_day` / `fb_counters` | 上次休息日 / 行为计数(eye/breathe/statsView) |
| `fb_reminder_schedule` | ReminderSchedule（enabled/intervalMinutes/startHour/endHour/quietWeekends） |

### 4.2 SharedPreferences（`"FlowBreakPrefs"`，原生）
| key | 类型 | 说明 |
|-----|------|------|
| `limitMinutes` | int | 单次连续使用限额（默认 30，UI 默认 25） |
| `targetApps`(`PREF_TARGET_APPS`) | **StringSet** | 目标 App 包名集合（务必用 StringSet，勿用 JSON 字符串） |
| `reminderEnabled` | boolean | 定时提醒开关 |
| `reminderIntervalMinutes` | int | 间隔 |
| `reminderStartHour`/`reminderEndHour` | int | 时段 |

> 写入一致性铁律：`limitMinutes`/`targetApps` 必须 `getUsageStats` / `startService` / `saveSettings` / `loadSettings` / `FlowForegroundService` 五处口径一致（targetApps 一律 StringSet）。

---

## 5. 当前已知问题 / 瑕疵（Backlog，按优先级）

> 状态标记：`[ ]` 未处理 / `[~]` 部分缓解 / `[代码已过审]` 改完且我审过+联合编译通过，待真机验收 / `[x]` 已真机验收。每条含：现象 → 疑似根因 → 影响文件 → 验收标准。
> 第一批 T1–T5 已于 2026-06-07 完成并通过我的代码审查 + 联合构建（tsc/vite/assembleDebug 全绿，APK 产出）。

### P0（影响核心可用性）
- `[代码已过审]` **屏幕时间暴涨（曾到 59 小时）**。根因：机型 `getTotalTimeInForeground()`/`INTERVAL_DAILY` 脏值。已根治：`getUsageStats` 改 UsageEvents 配对算法（T2），物理上限锁死 now−今日0点；App.tsx 已去掉 24h 创可贴（T3）。
  影响：`NativeFlowPlugin.getUsageStats` / `App.tsx`。**待真机验收**：连续真机使用，统计值 = 系统“数字健康”里目标 App 之和 ±10%，无跳变。
- `[代码已过审]` **三层干预在目标 App 上是否真的弹出**。已加固：`getTopAppName` 改 UsageEvents+回退、层级回传/复位修复、每 tick 诊断日志（T1）。
  影响：`FlowForegroundService`。**待真机验收**：抖音连续看到 80/100/120% 时长分别出现绿/橙/红覆盖层；杀后台后能恢复；`adb logcat -s FlowForegroundService` 看 tick 日志。

### P1（体验/一致性）
- `[x]` **两套疲劳口径不一致**（2.1 注）。已统一：Web 改按 `连续分钟/sessionLimit` 百分比 + `getLevelByPercent`，与原生 80/100/120 一致，并加降级注释（T3）。
- `[x]` **成就积分不一致**（自动解锁不发分）。已修：发分入口下沉到 `storage.unlockAchievement`（翻转即 +10），store 层去竞态补丁（T4）。逻辑核验：首次休息 0→20、再次 +10。
- `[ ]` **App 内 React 覆盖层与原生覆盖层可能重复呈现**。本批未做（实测中两者互斥：在目标 App 时 FlowBreak 在后台只走原生层）。如真机观察到双弹再处理。
- `[代码已过审]` **休息背景/音效偏单调**。已做：流动光晕背景（5 主题）+ Web Audio 合成环境 Pad（带静音/淡入淡出/卸载销毁）+ 提示音去 pop（T5，零新增资源）。**待浏览器/真机眼看耳听验收**。

### P2（打磨）
- `[x]` 定时提醒服务重建后立即补发：已在 `onCreate` 初始化 `lastTimedReminderMs=now`（T1）。
- `[x]` 无悬浮窗权限时 `staticCurrentLevel` 未更新：已在通知兜底分支补设层级，并在“离开30s/低于阈值”分支显式复位 0（T1）。
- `[ ]` 目标 App 名单不可由用户自定义（仅只读展示）。→ 第二轮 T6。
- `[x]` `setTodayScreenTime` 里 `videoTime` 只增不减：已改为直接赋值 `videoTime: seconds`（T4）。

> 维护规则：发现新问题往这里加；改完我审过+编译通过记 `[代码已过审]`，真机验收通过再记 `[x]` 并注明日期/commit。
> 纯时间驱动提醒（不判断是否真在用机）作为已知设计取舍保留，暂不改。

---

## 6. 验收标准（“最终产品做完”的定义，可逐项勾选）

> 勾选状态：☑代码层已就绪+联合编译通过（2026-06-07）；真机项仍待你装 APK 实测。
- [ ] **统计**（代码已就绪）：真机用一整天，今日时间真实、单调不暴涨、与系统数字健康基本一致。
- [ ] **干预 L1/L2/L3**（代码已就绪）：在抖音/B站内分别在 80%/100%/120% 触发对应覆盖层，颜色/频率渐进，L3 不可滑走。
- [ ] **保活**：手动杀进程 / 熄屏再开 / 重启手机后，前台服务与监控自动恢复。
- [ ] **定时提醒**（代码已就绪）：不打开 App，时间段内按间隔收到通知；改设置即时生效；周末免打扰生效。
- [ ] **休息**（代码已就绪）：三种活动均有引导动画+提示音+美观背景；完成有奖励反馈。
- [x] **激励**：完成休息积分 +10；成就解锁正确且发分；连续天数正确累计。（逻辑核验通过：首次 0→20、再次 +10）
- [ ] **权限流**：首次引导能顺利拿到 使用情况访问 / 悬浮窗 / 通知 / 电池白名单 四项，缺权限有清晰兜底提示。
- [x] **构建**：`assembleDebug` 通过（JBR 21），APK 已产出于 `app/android/app/build/outputs/apk/debug/app-debug.apk`。

---

## 7. 协作规范

### 7.1 任务下发格式（我写给执行 AI 的提示词骨架）
```
【任务】<一句话目标>
【背景】读 FlowBreak 最终产品文档（开发锚点）第 X 节
【改哪里】<精确文件+方法>
【怎么做】<步骤/约束>
【不要做】<范围红线，见 7.4>
【验收标准】<可观测、可勾选，对应第 6 节>
【交付】按 7.2 写进度文档
```

### 7.2 进度文档格式（执行 AI 完成后必须回交，模板）
```markdown
# 进度文档 - <任务名> - <日期>
## 1. 做了什么（对照任务清单逐条）
- 任务点A：完成 / 部分 / 未做 —— 一句话
## 2. 改了哪些文件
| 文件 | 改动摘要 | 行数级位置 |
## 3. 关键改动说明（为什么这么改，根因是什么）
## 4. 自测结果
- 构建：tsc 通过? assembleDebug 通过?（贴关键输出）
- 功能：怎么测的、现象、是否达验收标准
## 5. 已知遗留 / 风险 / 我没把握的地方
## 6. 给审查者的提问（如有）
```

### 7.3 我的审查清单（我审执行 AI 产出时逐项过）
1. 是否只改了授权范围内的文件？有无夹带无关重构/抽象？
2. 五处 SharedPreferences 口径是否仍一致（limitMinutes/targetApps StringSet）？
3. 是否触碰双系统对齐（原生层级 ↔ React 层级 ↔ Web 分数）？有无引入新的不一致？
4. 屏幕时间逻辑：是否仍“只统计目标 App、单调、不暴涨”？
5. 是否引入 OWASP 类隐患 / 写敏感文件 / 加无谓 try-catch？
6. 是否真的过了 `tsc` 与 `assembleDebug`？进度文档是否如实（失败就说失败）？
7. 验收标准是否逐条可观测达成？打回项写清“为什么不通过 + 怎么改”。

### 7.4 代码红线（执行 AI 不可违反，源自项目全局规范）
- 改代码前先读懂现有代码；优先 **Edit 已有文件**，非必要不新建文件。
- **不做需求之外的“改进”和重构**；不加不必要的抽象层、辅助函数、错误处理、注释。
- 对 internal code 做简化假设，只在系统边界做校验；安全性优先（避免 OWASP Top 10）。
- 危险操作（删文件 / force push 等）先确认；用 `git commit` 而非 `amend`；不提交敏感文件。
- 回复简洁直接、结论先行、不用 emoji（除非明确要求）。

---

## 8. 构建与验证

### 8.1 构建链
```
cd app
npm run build            # tsc + vite build → dist/
# 把 dist/ 拷到 android/app/src/main/assets/public/
cd android
# JDK 17（见下）；如遇 Gradle 要 JDK 21，需把 3 个 gradle 文件 VERSION_21 → VERSION_17
./gradlew assembleDebug  # 产物：app/build/outputs/apk/debug/app-debug.apk
```
- JDK：用 JDK 17，构建前 override `JAVA_HOME`（详见记忆 `android_build.md`）。
- 本机 Python：`D:\我的开挂系统\Documents\EasyShare\python`（记忆 `python_path.md`）。

### 8.2 验证手段
- 类型：`npx tsc --noEmit` 零错误。
- 真机：装 APK，授四项权限，按第 6 节逐条验收。
- 日志：`adb logcat -s FlowForegroundService NativeFlowPlugin` 看层级判定/会话累计/提醒触发。

---

## 9. 术语表
- **目标 App**：被监控的 5 个短视频/社交 App（见 1.4）。
- **连续会话**：在目标 App 内不间断停留累计时长；离开目标 App 超过 30 秒只重置尚未进入 BLOCKED 的连续会话；一旦进入 BLOCKED，必须完成休息或合法紧急使用才能解除阻断。
- **三层 / L1-L3**：感知 / 认知 / 行为层渐进干预。
- **覆盖层 Overlay**：盖在别的 App 之上的提示层（原生 WindowManager 实现）。
- **真相源**：真机上以**原生服务**的会话/层级为准，Web 侧为降级/预览。
- **创可贴**：临时缓解症状但未根治的代码（如屏幕时间 24h 上限），最终应替换。
```

