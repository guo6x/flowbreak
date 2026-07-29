# FlowBreak Beta 验证记录

验证日期：2026-07-17（Beta 1.1 重新打包）

## 已通过

- `npm run build`：TypeScript 与 Vite 生产构建通过。
- 浏览器闭环：首次引导、权限继续、受限应用保存、仪表盘每日反馈、反馈刷新持久化、效果验证页与脱敏诊断导出通过，浏览器控制台无错误。
- `testPlayDebugUnitTest`、`testDomesticDebugUnitTest`：状态阈值、跨应用共享会话、离开 30 秒、锁屏、访问窗口、前台事件、原生休息校验，以及休息后未回刷/回刷后离开 30 秒/持续回刷失败等效果判定边界通过。
- `lintAnalyzePlayDebug`、`lintAnalyzeDomesticDebug`：离线源码分析通过。
- `assemblePlayDebug`、`assembleDomesticDebug`：通过；两个 APK 均通过 v2 签名验证。
- `bundlePlayRelease`、`assembleDomesticRelease`：R8、资源压缩和 Release Lint Vital 通过。
- 渠道清单：Play 包名为 `com.flowbreak.app` 且不包含 `FlowAccessibilityService`；国内版包名为 `com.flowbreak.app.cn` 且包含该服务。
- Android 16 模拟器：两个渠道可同时安装，数据目录分别为 `/data/user/0/com.flowbreak.app` 与 `/data/user/0/com.flowbreak.app.cn`，两者冷启动均无 FATAL。
- 红米真机：从 v1.0.0（versionCode 1、Room v2）保留数据覆盖安装至 v1.1.0（versionCode 2），Room 数据库完成 v2→v3 打开与 WAL checkpoint，冷启动按 App PID 过滤无 FATAL、SQLiteException 或 IllegalStateException；当前已安装最新国内调试包。
- Beta 1.1 版本与渠道：两个 APK 均为 versionCode 2 / versionName 1.1.0；Play 清单不含无障碍服务，国内版包含；两个 Debug APK 均通过 APK Signature Scheme v2 验证。

## 当前产物

- Play Debug APK：`app/android/app/build/outputs/apk/play/debug/app-play-debug.apk`
  - SHA-256：`D775A23FDA78144A91734BFE3B652BD5316E1DDBC9A5FB32E09B842C780A2D6B`
- 国内 Debug APK：`app/android/app/build/outputs/apk/domestic/debug/app-domestic-debug.apk`
  - SHA-256：`D818D17FDF815FEED39360A9AE9A31287984C672188A5CF8045C95F49EE14449`

## 公开发布前仍需外部完成

- 配置正式签名密钥和 GitHub Actions secrets；本地 Release 产物在未提供密钥时按设计为未签名产物。
- 提供公开隐私政策 URL 和支持邮箱，完成 Play 前台服务声明与 Data Safety。
- 在小米、OPPO、vivo、华为矩阵完成权限、后台限制、重启和 24 小时稳定性测试。
- 以系统数字健康为对照，实测目标应用统计误差不超过 10%，并实测阻断触发延迟不超过 2 秒。

说明：本机离线执行完整 Debug Lint 时，AndroidTest Lint Model 缺少未缓存的 `androidx.test.ext:junit:1.3.0`、`espresso-core:3.7.0` 等依赖；两渠道 `lintAnalyze*Debug` 和两渠道 Release Lint Vital 均已通过，CI 联网环境保留完整 `lintPlayDebug` 与 `lintDomesticDebug` 门禁。本机用户名含中文会使 Gradle Test Worker 类路径失真，验证时使用指向同一缓存的 ASCII junction `E:\gradle-home`，随后两渠道全量单测通过。
