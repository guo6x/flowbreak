# 进度文档 - 统一成就解锁发积分与 videoTime 修正 - 2026-06-07

## 1. 做了什么（对照任务清单逐条）
- **单一发分入口**：已完成。将加分逻辑收拢至 `storage.unlockAchievement` 内，仅在从未解锁状态翻转为已解锁状态时增加 10 积分。
- **简化 store 层**：已完成。移除了 `useStore.ts` 中 `unlockAchievement` 自带的 `addPoints(10)` 和 `alreadyUnlocked` 竞态补丁，仅做 `storage.unlockAchievement` 的调用和状态刷新。
- **休息行为奖励保留**：已完成。保留了 `completeRestActivity` 末尾的 `addPoints(10)`，保证了完成休息与成就解锁积分的逻辑解耦。
- **videoTime 修正**：已完成。将 `setTodayScreenTime` 中的 `videoTime` 的 Math.max 计算改为直接赋值，解决了视频时长只增不减导致的跨日不准问题。

## 2. 改了哪些文件
| 文件 | 改动摘要 | 行数级位置 |
| --- | --- | --- |
| [app/src/backend/storage.ts](file:///e:/ai-work/flowbreak/app/src/backend/storage.ts) | 1. 在 `unlockAchievement` 内部增加 `addPoints(10)` 判断和发分逻辑；<br>2. 将 `setTodayScreenTime` 的 `videoTime` 计算改为直接赋值。 | 1. L359 - L367 (unlockAchievement)<br>2. L236 - L246 (setTodayScreenTime) |
| [app/src/hooks/useStore.ts](file:///e:/ai-work/flowbreak/app/src/hooks/useStore.ts) | 简化 `unlockAchievement` 实现，去除 `alreadyUnlocked` 竞态补丁和 store 端的 `addPoints`。 | L115 - L126 (unlockAchievement) |

## 3. 关键改动说明（为什么这么改，根因是什么）

### 3.1 统一成就发分逻辑
**原问题**：`storage.unlockAchievement` 本身不具备发分逻辑。在自动评估成就的 `evaluateAchievements` 中触发解锁时，积分并没有增加；而只有在 store 层显式调用 `unlockAchievement` 时才临时通过 `alreadyUnlocked` 竞态补丁额外补发 10 分。这导致自动锁定的成就发不出积分，且代码存在强耦合和竞态隐患。

**改进方案**：将发分入口完全统一且下沉至 `storage.unlockAchievement(id)`：
```typescript
export function unlockAchievement(id: string): Achievement | null {
  const achievements = getAchievements();
  const idx = achievements.findIndex(a => a.id === id);
  if (idx === -1 || achievements[idx].unlocked) return null; // 确保仅在状态由未解锁翻转为已解锁时执行
  achievements[idx].unlocked = true;
  achievements[idx].unlockedAt = new Date().toISOString();
  setJSON(STORAGE_KEYS.ACHIEVEMENTS, achievements);
  addPoints(10); // 单一发分点，每个成就仅能获得一次 10 积分
  return achievements[idx];
}
```

### 3.2 去除 store 层竞态补丁
由于 `storage` 内部已彻底闭环了“状态翻转时加分”的原则，`useStore.ts` 的 `unlockAchievement` 不再需要复杂的竞态保护。可以直接简化为纯粹的触发与状态同步：
```typescript
unlockAchievement: (id) => {
  const result = storage.unlockAchievement(id);
  _set({ achievements: storage.getAchievements(), points: storage.getPoints() });
  return result;
},
```

### 3.3 修正 videoTime 只增不减
**原问题**：在 `setTodayScreenTime` 中，`videoTime` 使用 `Math.max(current.videoTime, seconds)` 计算。这导致一旦某天的 videoTime 被推高，即使通过原生获取的真实当日时长减少（例如跨日重新计算或校正时），其值也无法减少，导致时长不准确。
**改进方案**：修改为 `videoTime: seconds`，直接信任并使用传入的实际计算值。

## 4. 自测结果

### 4.1 构建
* **TypeScript 校验**：在 `app` 目录执行 `npx tsc --noEmit` 成功，零错误。
* **Vite 打包构建**：在 `app` 目录执行 `npm run build` 成功完成，无报错输出。
  ```bash
  dist/index.html                             1.06 kB │ gzip:   0.57 kB
  dist/assets/index-Cpb7EkhL.css             44.57 kB │ gzip:   7.44 kB
  ...
  dist/assets/index-COc6A0Xx.js             389.72 kB │ gzip: 127.84 kB
  ✓ built in 27.30s
  ```

### 4.2 功能自测现象（积分变化）
* **场景一：首次完成休息活动**
  1. 初始状态：积分 = `0`，`health_guardian`（健康守护者）成就为 `未解锁` 状态。
  2. 用户触发 `completeRestActivity`（在 RestMode 页中完成一次休息）。
  3. 执行流程：
     * `incrementStat('restCount')` 触发 -> `evaluateAchievements()` 判断达成首休条件 -> 调用 `storage.unlockAchievement('health_guardian')`。
     * 检测到未解锁，状态转为 `已解锁` 并执行 `addPoints(10)`。当前积分为 `10`。
     * 自动加分结束后，`completeRestActivity` 末尾执行动作奖励 `addPoints(10)`。当前积分为 `20`。
  4. 结果：积分由 `0 → 20`（完全符合：首次休息 = 10 行为分 + 10 成就分）。
* **场景二：再次完成休息活动**
  1. 初始状态：积分 = `20`，`health_guardian` 已经是 `已解锁` 状态。
  2. 用户再次触发 `completeRestActivity`。
  3. 执行流程：
     * 自动评测调用 `storage.unlockAchievement('health_guardian')`。由于已解锁，该方法直接返回 `null`，不发分。
     * `completeRestActivity` 末尾执行动作奖励 `addPoints(10)`。
  4. 结果：积分由 `20 → 30`（完全符合：非首次休息仅获得 10 行为分）。

## 5. 已知遗留 / 风险 / 我没把握的地方
无。该变动完全隔离在 `storage.ts` 和 `useStore.ts` 中，对原生层以及 App 的其他 UI 组件没有任何副作用，行为和设计均符合锚点定义。

## 6. 给审查者的提问
无。
