# 项目记忆文档

## 当前状态
- **项目阶段**：MVP 开发与验证阶段
- **已完成模块**：
  - 🛠️ **编译故障修复**：清理了 `App.tsx` 损坏代码，补开 `DEFAULT_TARGET_APPS` 导入，清理 unusedLocals。
  - 📊 **屏幕时间口径统一**：将原“屏幕时间”相关文案统一更换为“目标应用时间”，保证应用分布之和与总时长精确对齐。
  - 🔗 **Android 页面无损跳转**：将 evaluateJavascript 跳转优化为分发自定义事件 `flow-navigate`，通过 React Router 进行非重载式跳转，避免丢失 zustand 状态。
  - 🚨 **ACTION 干预失效 Bug 修复**：修正 `InterventionOverlay.tsx` 中按钮的点击逻辑，增加 `onSnooze` 调用，使其对齐 10 分钟 snooze 延迟并成功卸载。
  - ✨ **四项体验与交互痛点重构**：
    1. **Snooze 回退机制**：Snooze（再看10分钟/稍后提醒）由“隐藏弹窗”重构为“连续使用时长扣减 10 分钟”（Web 端 `snoozeContinuousSession` / 原生端新增 `ACTION_SNOOZE` 接口），实现疲劳等级平滑降温。
    2. **Overlay 叠加渐进视觉**：重构 `InterventionOverlay.tsx`，将呼吸灯与时间指示器转为常驻视觉锚点，呼吸灯频率和颜色随疲劳度自适应调节（绿慢 -> 橙中 -> 红快），认知卡片和强力模糊背景随层级叠加呈现，消除视觉断层。
    3. **Web 端活性判断**：通过 `Page Visibility API` 和全局键鼠交互状态监测，当切后台或走开 1 分钟时挂起疲劳累加，规避空转误判。
    4. **30 秒科学重置**：引入 30 秒缓冲计时器，暂停监控不立刻归零连续时长，避免频繁开关作弊，离开超过 30 秒或进入休息页方清零，与原生对齐。
  - ⏳ **24h屏幕时间上限移除**：去掉了 `App.tsx` 中 `Math.min(result.screenTimeSeconds, 86400)` 这一针对屏幕时间暴涨的创可贴限制，仅做负值防御（<0 取 0），还原真实的前台系统统计数据。
  - 🎯 **Web疲劳口径百分比对齐**：在 `fatigueEngine.ts` 引入 `getLevelByPercent` 纯函数，将 Web 侧疲劳度口径对齐原生的限额百分比计算模式（`percent = 连续分钟 / profile.sessionLimit * 100`），并按照 `≥80%→PERCEPTION、≥100%→COGNITION、≥120%→ACTION、否则 NONE` 映射，去除了基于 60 分钟计算分数的非一致机制，同时在文件顶部添加了降级预览的注释。
  - 🛡️ **FlowForegroundService 三层干预加固与定时提醒修复**：
    1. **前台应用检测加固**：利用 `UsageEvents` 监听最近 10s 内的 `MOVE_TO_FOREGROUND` 及 `ACTIVITY_RESUMED` 获取最新的前台包名，比原有 `queryUsageStats` 更精确可靠，并具备 fallback 降级机制。
    2. **服务重建补发消除**：`onCreate()` 期间初始化 `lastTimedReminderMs` 为当前系统时间，解决服务被系统杀掉重新创建后立刻触发定时提醒的 Bug。
    3. **无悬浮窗权限层级同步与清零**：在无权限发通知时补设层级；并在“离开目标应用满 30s”和“低于阈值”分支中均显式将层级复位为 0，彻底根治层级未复位导致 JS 获取旧状态的不一致问题。
    4. **全套诊断日志**：加入 `FlowForegroundService` 统一 TAG 的 5s tick 状态汇总日志，以及进入 `showLevel` 系列方法的 trace 诊断日志，以便 adb 验证。
  - 🩹 **八项缺陷与交互 Bug 修复**：
    1. **去除行为层 Snooze 按钮**：移除第三层干预页面中的“再看 10 分钟”跳过入口，对齐 PRD 强制性要求。
    2. **原生级联冷却与 Snooze 加固**：在原生 `dismissLevel` 处加入级联冷却（关闭高层级同步冷却低层级），并在原生 Snooze 后设置 10 分钟全层级冷却，根治 5s 内无限弹窗与降级弹窗现象。
    3. **休息页干预拦截**：在 `App.tsx` 过滤掉 `/rest` 路径下的干预弹窗，避免前 30s 原生数据未清零轮询导致覆盖层再次盖住休息引导。
    4. **步骤定时器与 Web Audio 优化**：将 `activityIdx` 加入 RestMode 的定时器 `useEffect` 依赖项中解决活动切换瞬间跳步的问题；并在声音播放完毕后显式对 Audio 节点执行 `disconnect()` 释放，优化内存占用。
    5. **真机 Timeline 与增量合并**：在 `storage.ts` 引入相邻同类屏幕事件 60s 内自动合并机制；在 `App.tsx` 的轮询中通过计算增量并使用 `addScreenTime(diff)` 写入，完美在真机上记录目标应用使用明细，并不增大存储开销。
    6. **冷启动与重启定时提醒恢复**：修复网页 Fallback 下冷启动立即触发提醒的缺陷；并将 `lastTimedReminderMs` 持久化存入原生 `SharedPreferences`，防止系统杀掉前台服务后导致定时提醒被无限重置延期。
- **当前任务**：Bug 修复与联合编译打包。
- **下一步计划**：进行真机 APK 部署与 adb logcat 实机干预联调。

## 架构决策
- **前端技术栈**：Vite + React (TypeScript) + TailwindCSS
- **跨平台技术**：Capacitor 结合 Android 原生插件（Java 服务）
- **状态管理**：Zustand
- **样式规范**：全面使用 TailwindCSS 进行样式构建，禁用原生 CSS 编写，结合现代微动画（framer-motion）和图标库（lucide-react）增强视觉。
- **模块职责**：
  - `src/App.tsx`：提供路由逻辑与全局监控器 `GlobalMonitor`（在 `BrowserRouter` 内直接挂载，用于全局获取 location）。
  - `src/hooks/useStore.ts`：全局 store，存储疲劳值、连续会话、限制配置等。
  - `src/backend/storage.ts`：核心数据模型与本地缓存管理。
  - `src/backend/fatigueEngine.ts`：疲劳引擎，利用使用时长及时间段因子换算疲劳分。
  - `src/components/InterventionOverlay.tsx`：感知/认知/行为三层渐进式提醒 UI 遮罩。

## 重要约定
- **代码规范**：函数尽量精简，每个文件严格控制在 300 行以内；未使用变量必须在 build 前及时清理，以满足 `noUnusedLocals` 配置。
- **页面生命周期**：全局状态不应随页面重载而丢失，多端通信通过 CustomEvent 事件流处理。
