# ARCHITECTURE — 当前技术架构

> 只描述当前（基线 `99fdcc2f6f357e78fb70dd127adedfa31a098a71`）实际存在的架构。历史架构描述一律见 `docs/archive/`。
> 业务状态语义见 `docs/PRODUCT.md`，本文档不重复业务规则。

## 1. 分层总览

```text
┌─────────────────────────────────────────────────────────────┐
│ React 19 + TypeScript + Vite (Capacitor WebView)            │
│  App.tsx / pages / hooks / components                       │
│  localStorage = Web 界面层缓存（UI Projection）               │
└───────────────┬─────────────────────────────────────────────┘
                │ Capacitor 插件调用（@capacitor/core）
┌───────────────▼─────────────────────────────────────────────┐
│ NativeFlowPlugin (Java, Capacitor 原生插件)                  │
│  权限 / 设置 / 状态查询 / 休息结算 / 导出 / 清除 / 诊断        │
└───────────────┬─────────────────────────────────────────────┘
┌───────────────▼─────────────────────────────────────────────┐
│ FlowForegroundService (前台服务, START_STICKY)               │
│  每 2s tick：检测前台 → 累计会话 → 状态机 → 干预/通知/落库     │
└─────────────────────────────────────────────────────────────┘
```

## 2. 组件职责与数据流

### 2.1 前台检测链（Source of Truth：系统 UsageEvents）

```text
UsageStatsManager.queryEvents(游标, now)
        │
        ▼
ForegroundUsageDetector ──► ForegroundAppTracker（内存态）
        │  （游标推进、36h 初始回看、60s bootstrap 重试、异常降级）
        ▼
当前前台包名 foregroundPackage
        │
        ▼
TargetAppClassifier（目标应用判断，channel 相关）
        │
        ▼
BlockStateMachine.update(targetInForeground, pkg, now, limitMs)
```

- `ForegroundUsageDetector`：封装 UsageEvents 游标（首次回看 36 小时，之后增量；bootstrap 重试窗口 60s），异常安全，喂给 `ForegroundAppTracker`。
- `ForegroundAppTracker`：按 **Activity 实例（package + className）** 跟踪前台；仅当前实例的 `MOVE_TO_BACKGROUND` / `ACTIVITY_PAUSED` / `ACTIVITY_STOPPED` 才清空前台，同包旧实例的 PAUSED/STOPPED 不清当前前台（`027af94`；历史缺陷 `FB-P1-01` 已 RESOLVED，见 `KNOWN_ISSUES.md`）。

### 2.2 状态机与累计（Source of Truth：原生）

- `BlockStateMachine`：纯 Java 状态机（IDLE/PERCEPTION/COGNITION/BLOCKED/RESTING/GRACE），阈值 80/100/120%，`LEAVE_RESET_MS=30s` 仅重置尚未进入 BLOCKED 的连续会话；BLOCKED 豁免离开重置（sticky，`9c34fe9`；历史缺陷 `FB-P1-02` 已 RESOLVED，见 `KNOWN_ISSUES.md`）。
- `UsageAccumulator`：单 tick 增量上限 10s，每 15s 批量落库（`USAGE_FLUSH_MS`）。
- `FlowServiceStateStore`：状态快照的 SharedPreferences 持久化（blockState/sessionMs/graceUntil/emergencyUnlockDay 等）。
- `RestSessionManager` / `RestSessionValidator` / `RestCheatTracker`：休息会话的开始、完成校验（原生时间戳）、防作弊。
- `EmergencyUnlockManager`：每日一次紧急解锁（自然日 + 单调时钟 + 60s 时钟偏移容差）。
- `PullbackSessionCoordinator` / `PullbackOutcomeTracker`：休息后 10 分钟观察窗口与「成功拉回」判定。
- `HeartbeatGate`：服务心跳每 30s 落一次，供诊断/恢复判断。

### 2.3 干预呈现（UI Projection）

- `FlowOverlayController`：PERCEPTION/COGNITION 顶部提示条（非阻断）；BLOCKED 全屏遮罩（`TYPE_APPLICATION_OVERLAY`）；无悬浮窗权限或 addView 失败时 fallback `BlockActivity`（尽力而为）。
- `FlowNotificationController`：前台服务常驻通知 + 阶段提醒通知。
- `FlowAccessibilityService`（仅 domestic source set）：业务处理仅接收 `TYPE_WINDOW_STATE_CHANGED` 事件（无障碍配置订阅窗口状态与窗口列表变化，代码只处理窗口状态变化）；BLOCKED + 目标应用在前台 → `GLOBAL_ACTION_HOME` + 无障碍顶部横幅 `BlockedTargetBanner`（`TYPE_ACCESSIBILITY_OVERLAY`，单实例、show 幂等，含「开始休息」入口）+ 尽力而为 `tryStartBlockActivity`（HyperOS 可能拒绝后台启动，失败记 `Log.w` 不静默、不影响强阻断，见 `KNOWN_ISSUES.md#COMPAT-001`）；微信视频号识别（className 关键词 + 60s TTL）。

### 2.4 持久化（Persistence）

| 存储 | 内容 | 谁写 |
| ---- | ---- | ---- |
| SharedPreferences `FlowBreakPrefs` | 设置（targetApps/limitMinutes/restDuration/emergency 开关/strongBlockingEnabled）、状态快照、心跳 | 原生服务/插件 |
| Room `FlowDatabase`（schema v3，迁移 1→2、2→3） | daily_usage、daily_summary、flow_events、progress 等 | 原生服务（前台扫描只在服务内执行） |
| localStorage | Web 界面层 profile/statistics/achievements/reflections 缓存（UI Projection） | React 层 |

- 原生统计/仪表盘/导出统一从 Room 读取；界面不再直接执行使用扫描。
- 统计口径：`NativeFlowStatisticsService` 基于 UsageEvents 配对算法计算「当日目标应用前台总时长」，物理上限为 `now − 今日0点`。

### 2.5 生命周期与恢复

- `FlowForegroundService`：`onStartCommand` 返回 `START_STICKY`；`foregroundServiceType="specialUse"`（运行时显式传入 `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`）；监听 `ACTION_SCREEN_OFF` / `ACTION_SCREEN_ON`（熄屏不累计、会话离开计时）。
- `BootReceiver`：`BOOT_COMPLETED` / `QUICKBOOT_POWERON`，仅在 `serviceConfigured && monitoringEnabled` 时重启服务。
- `MainActivity` / `BlockActivity`：`launchMode="singleTask"`；`BlockActivity` `exported=false`、`excludeFromRecents=true`。
- 系统返回键桥接（`99fdcc2`）：`MainActivity.onBackPressed` 经 `evaluateJavascript` 询问 WebView 全局钩子 `window.__flowbreakHandleBack`；Target Apps 页存在未保存修改时弹「有未保存的修改」确认框并消费按键，无修改时保持系统默认退出。注意：前端改动后必须 `npm run build` → `npx cap sync android` 重新打包，否则 APK 内 Web bundle 缺少新钩子（产物溯源见 `TESTING.md`、`RELEASE.md` GATE C）。
- 服务被杀重建：由 START_STICKY 拉起；休息会话通过持久化时间戳自动完成。

## 3. Play / Domestic source set

- `app/android/app/src/main/`：公共代码与 manifest（服务、接收器、权限、BlockActivity）。
- `app/android/app/src/play/`：Play 渠道资源（strings）。
- `app/android/app/src/domestic/`：国内版 manifest（声明 `FlowAccessibilityService`）、服务实现、无障碍配置与文案。
- 渠道判定：`BuildConfig.CHANNEL`（`play` / `domestic`）。

## 4. 构建与测试链路（详见 `TESTING.md`、`DEVELOPMENT.md`）

- 前端：`npm test`（Vitest）、`npm run build`、`npx cap sync android`。
- 原生：`testPlayDebugUnitTest` / `testDomesticDebugUnitTest`（JVM/Robolectric）、`lintPlayDebug` / `lintDomesticDebug`、`assemblePlayDebug` / `assembleDomesticDebug`、`assemblePlayDebugAndroidTest`（仅构建 instrumentation APK）、Release（`bundlePlayRelease` / `assembleDomesticRelease`，未签名）。
- CI：`.github/workflows/android.yml`（Node 22.23.1、JDK 21 temurin、npm audit 门禁、Room schema drift 检查、R8 mapping 检查）。

## 5. Source of Truth / Persistence / UI Projection / Fallback 汇总

| 角色 | 组件 |
| ---- | ---- |
| Source of Truth | 系统 UsageEvents（前台事实）、`BlockStateMachine`（状态事实）、Room（统计数据事实） |
| Persistence | SharedPreferences + Room（原生）、localStorage（界面层） |
| UI Projection | React 页面、`FlowOverlayController`、`FlowNotificationController` |
| Fallback | 无悬浮窗权限 → `BlockActivity`（尽力而为）；强阻断可见引导 → 无障碍顶部横幅（独立于 `BlockActivity`）；UsageEvents 异常 → tracker 当前状态降级 |
