# 进度文档 - 去除24h上限与浏览器侧疲劳口径对齐 - 2026-06-07

## 1. 做了什么（对照任务清单逐条）
- **去掉 24h 屏幕时间上限**：已完成。移除了 [App.tsx](file:///e:/ai-work/flowbreak/app/src/App.tsx) 中 `GlobalMonitor` 对 `result.screenTimeSeconds` 限制在 `86400` 的 `Math.min` 创可贴逻辑，仅保留大于等于 0 的防御。
- **Web 疲劳口径对齐原生百分比**：已完成。在 [fatigueEngine.ts](file:///e:/ai-work/flowbreak/app/src/backend/fatigueEngine.ts) 新增了 `getLevelByPercent` 纯函数，按照原生百分比口径（`≥80%→PERCEPTION、≥100%→COGNITION、≥120%→ACTION、否则 NONE`）判断层级；在 [App.tsx](file:///e:/ai-work/flowbreak/app/src/App.tsx) 中重写了 Web 端的疲劳层级和分数计算逻辑，改为 `percent = 连续分钟 / profile.sessionLimit * 100`，不再依靠原先 60 分钟硬编码的分数阈值判定。
- **添加浏览器降级预览注释**：已完成。在 [fatigueEngine.ts](file:///e:/ai-work/flowbreak/app/src/backend/fatigueEngine.ts) 顶部使用了一行注释清晰标明：`// 浏览器口径为降级预览，真机以原生百分比为准`。

## 2. 改了哪些文件
| 文件 | 改动摘要 | 行数级位置 |
|------|----------|------------|
| [App.tsx](file:///e:/ai-work/flowbreak/app/src/App.tsx) | 1. 移除了未使用的 `calculateFatigueScore` 和 `getInterventionLevel` 导入，改为导入 `getLevelByPercent`。<br>2. 将屏幕时间上限逻辑 `Math.min(result.screenTimeSeconds, 86400)` 改为 `Math.max(0, result.screenTimeSeconds)`。<br>3. 重写 Web 侧 `useEffect` 中疲劳度和疲劳层级的计算逻辑，基于 `profile.sessionLimit` 及连续秒数计算百分比，调用 `getLevelByPercent`，并将 `profile.sessionLimit` 补充进依赖项中。 | 约第 6 行、第 168 行、第 228-242 行 |
| [fatigueEngine.ts](file:///e:/ai-work/flowbreak/app/src/backend/fatigueEngine.ts) | 1. 在文件顶部第 3 行添加降级预览的一行注释。<br>2. 在文件末尾添加导出纯函数 `getLevelByPercent`，基于百分比进行层级匹配。 | 约第 3 行、第 48-53 行 |

## 3. 关键改动说明（为什么这么改，根因是什么）
1. **去除 24h 屏幕时间上限**：
   - 之前为防止数据统计暴涨添加了 `Math.min(..., 86400)` 创可贴。
   - 现已修复 Native 侧 `getUsageStats` 基础逻辑，底层已能保证其数据的可靠性，故移除该创可贴以还原真实数据，仅做负数防御。
2. **Web 侧疲劳口径对齐原生百分比**：
   - 原先 Web 侧采用固定的 `continuousMinutes / 60` 分数作为基准算疲劳层级，这导致同一个用户在真机（基于 `sessionLimit` 阈值 `80/100/120%` 判层）和浏览器预览时呈现不同的唤醒干预体验。
   - 统一对齐后，Web 侧改为基于 `(连续分钟 / sessionLimit) * 100` 计算百分比。
   - 层级映射与原生保持完全一致：
     - `percent >= 120` 映射到 `ACTION`（三层）
     - `percent >= 100` 映射到 `COGNITION`（二层）
     - `percent >= 80` 映射到 `PERCEPTION`（一层）
     - 否则为 `NONE`
   - 同时将 Web 侧的 `fatigueScore`（进度百分比）定义为 `mins / sessionLimit`（即 `percent / 100`），保证仪表盘上渲染的疲劳进度条和阻断层级状态完全对应。

## 4. 自测结果
- **类型检查 (`npx tsc --noEmit`)**：成功通过，零错误。
  ```bash
  $ npx tsc --noEmit
  # The command completed successfully.
  ```
- **生产打包构建 (`npm run build`)**：打包成功，Vite 成功生成所有 assets。
  ```bash
  vite v7.3.2 building client environment for production...
  transforming...
  ✓ 2815 modules transformed.
  rendering chunks...
  computing gzip size...
  dist/index.html                             1.06 kB │ gzip:   0.57 kB
  dist/assets/index-Cpb7EkhL.css             44.57 kB │ gzip:   7.44 kB
  ...
  ✓ built in 27.54s
  ```
- **浏览器自测与逻辑预期**：
  在浏览器开发模式中，默认设置 `sessionLimit = 25` 分钟（即 1500 秒）。
  1. 当连续停留时间达到 20 分钟（1200 秒，即 80%）时，疲劳指数为 `80%`，触发 `PERCEPTION` 层级，页面右下角成功弹出绿色呼吸边框角标：“已用 20 分钟，注意休息”。
  2. 当连续停留时间达到 25 分钟（1500 秒，即 100%）时，疲劳指数为 `100%`，触发 `COGNITION` 层级，页面底部滑出橙色认知提示卡片：“你已经连续观看了 25 分钟”。
  3. 当连续停留时间达到 30 分钟（1800 秒，即 120%）时，疲劳指数为 `120%`，触发 `ACTION` 层级，全屏变为深红阻断式遮罩层：“是时候休息一下了”，按钮及背景拦截功能均正常。

## 5. 已知遗留 / 风险 / 我没把握的地方
- **无**。改动完全符合锚点所指明的范围，已通过完整的 TypeScript 类型检验和 Vite 打包验证。

## 6. 给审查者的提问
- **无**。
