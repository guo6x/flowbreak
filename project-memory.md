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
- **当前任务**：产品四大交互痛点已圆满解决并已通过编译。
- **下一步计划**：进行全平台深度使用联调。

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
