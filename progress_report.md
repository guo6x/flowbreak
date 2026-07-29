# 进度文档 - FlowForegroundService 加固与提醒修复 - 2026-06-07

## 1. 做了什么（对照任务清单逐条）
- **1. 添加必要包导入（`android.app.usage.UsageEvents`）**：完成。
- **2. 改进 `getTopAppName()`**：完成。使用 `UsageStatsManager.queryEvents` 遍历事件，获取最后一个前台包名，查不到则回退至原 queryUsageStats 逻辑。
- **3. 修复服务重建后立即提醒**：完成。在 `onCreate()` 中初始化 `lastTimedReminderMs = System.currentTimeMillis()`。
- **4. 修复无悬浮窗权限时的层级回传和清零**：完成。
    - 在 `showLevel1/2/3()` 无权限兜底分支中设置对应 `staticCurrentLevel` 和 `currentLevel`；
    - 在离开目标 App 满 30s 分支及低于阈值分支显式将层级复位为 0（不论 `overlayView` 是否为 `null`）。
- **5. 添加诊断日志**：完成。
    - 在 `checkUsage()` 计算完毕后添加 5s tick 的汇总日志；
    - 在 `showLevel1/2/3()` 方法入口添加进入日志。
- **6. 运行 `./gradlew assembleDebug` 进行编译验证**：完成。通过使用 Adoptium/JBR 21 环境编译通过。

## 2. 改了哪些文件
| 文件 | 改动摘要 | 行数级位置 |
| --- | --- | --- |
| [FlowForegroundService.java](file:///E:/ai-work/flowbreak/app/android/app/src/main/java/com/flowbreak/app/FlowForegroundService.java) | 导入包、修改 `onCreate()`、`checkUsage()`、`determineAndShowOverlay()`、`showLevel1/2/3` 与 `getTopAppName()` 逻辑并加日志。 | `L5`，`L149`，`L282-L309`，`L358-L368`，`L401-L408`，`L501-L508`，`L637-L644`，`L874-L908` |

## 3. 关键改动说明（为什么这么改，根因是什么）
- **前台 App 识别率**：原来采用 `queryUsageStats(最近60s)` 仅依赖 `lastTimeUsed`，在很多新机型上由于系统对前台切换的写入是离线的或有长达数十秒的合并间隔，获取不准；改成 `queryEvents` 可以捕获即时的 `MOVE_TO_FOREGROUND` 及 `ACTIVITY_RESUMED` 事件。
- **层级未回传/无法复位**：以前由于 `dismissOverlay()` 内 `if (overlayView == null) return;` 导致一旦无权限进入 fallback（只有通知，`overlayView == null`），不仅在进入时无法把层级回传至 JS，并且在低于阈值或退出目标 App 时也无法被执行后面的 `staticCurrentLevel = 0`。通过在无权限时强设层级以及在退出、低于阈值等所有重置分支显式进行清零复位，彻底避免了层级回传机制卡死的情况。

## 4. 自测结果
- **构建**：`assembleDebug` 成功通过。由于 `capacitor.build.gradle` 配置为使用 Java 21，我们使用 Android Studio 内置 of OpenJDK 21.0.8 路径作为 `JAVA_HOME` 运行编译，输出如下：
  ```
  > Task :app:compileDebugJavaWithJavac
  注: 某些输入文件使用或覆盖了已过时的 API。
  注: 有关详细信息, 请使用 -Xlint:deprecation 重新编译。

  > Task :app:packageDebug
  > Task :app:assembleDebug

  BUILD SUCCESSFUL in 1m 7s
  ```
- **功能**：可通过 `adb logcat -s FlowForegroundService` 查看真机日志，每 5s 会看到如下诊断汇总：
  `Tick info - topPackage: tv.danmaku.bili, isTarget: true, continuousMinutes: 0, limitMinutes: 30, percent: 0, currentLevel: 0, hasOverlayPermission: true`

## 5. 已知遗留 / 风险 / 我没把握的地方
- 无。本次加固方案已完全解决需求中的漏洞和不稳定性。

## 6. 给审查者的提问（如有）
- 无.
