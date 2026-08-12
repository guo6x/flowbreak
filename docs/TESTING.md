# TESTING — 测试与验证

> 定义自动化测试层级、CI 范围、真机测试矩阵与证据标准。易变的数字只出现在下方「验证快照」；历史验证记录在 `docs/archive/progress/`（含 `VERIFICATION.md` 的 Beta 1.1 记录）。

## 1. 自动化测试层级

| 层级 | 命令 | 覆盖 |
| ---- | ---- | ---- |
| Frontend | `cd app && npm test`（Vitest） | 页面、store、rest-mode、状态判定等 React/TS 逻辑 |
| Play JVM | `cd app/android && ./gradlew testPlayDebugUnitTest` | Robolectric：状态机、前台追踪、服务恢复、统计等（Play 渠道） |
| Domestic JVM | `./gradlew testDomesticDebugUnitTest` | 同上（Domestic 渠道，含无障碍服务单测） |
| RecoveryIntegration | 包含在上述 JVM 任务中 | 前台服务重建/重启恢复集成测试 |
| Room migrations | 包含在上述 JVM 任务中 | schema 1→2→3 迁移测试 |
| Build/Lint | `./gradlew lintPlayDebug lintDomesticDebug assemblePlayDebug assembleDomesticDebug` | 源码分析 + 双渠道 Debug 构建 |
| Release 构建 | `./gradlew bundlePlayRelease assembleDomesticRelease` | R8、资源压缩、Release Lint Vital（**未签名**产物） |

## 2. CI 做什么 / 不做什么

CI（`.github/workflows/android.yml`）在 master push / PR 时执行：

- 做：`npm ci`、npm audit 门禁（生产依赖 high 级 + 全依赖 critical 级）、`npm test`、`npm run build`、`npx cap sync android`、双渠道单测 + lint + Debug 构建、`assemblePlayDebugAndroidTest`、Release 双渠道构建、R8 mapping 检查、Room schema drift 检查。
- 不做：**不执行 connected instrumentation tests**。`assemblePlayDebugAndroidTest` 只是构建 instrumentation APK，不等于在设备上执行测试。
- 不做：真机验证（厂商后台限制、悬浮窗/无障碍行为、统计误差对照等只能真机做）。

## 3. 真机测试矩阵与证据标准

### 结果标记

| 标记 | 含义 |
| ---- | ---- |
| PASS | 有可复核证据（截图/日志/DB/prefs）且符合 PRODUCT 语义 |
| FAIL | 复现出与 PRODUCT 语义不符的行为 |
| BLOCKED | 环境/系统限制导致不可测（如 `am kill` 对前台服务进程不生效） |
| NOT_RUN | 未执行 |

### 2026-08-12 设备记录（真实设备证据）

- 设备：Redmi Note 13 Pro 5G（`2312DRA50C` / garnet）/ Android 16 / SDK 36 / HyperOS 3.0（`OS3.0.306.0.WNRCNXM`）
- APK：`app-domestic-debug.apk`（com.flowbreak.app.cn，versionCode 2 / versionName 1.1.0），代码基线 `a06a772`
- 外部证据目录：`D:\ai_code\flowbreak-device-evidence\`（reports/、screenshots/、ui/、dumpsys/、db/、logs/）
- 总结果：**FAILED**（`reports/final-report.md`；T01–T42 矩阵）
- 缺陷明细：`KNOWN_ISSUES.md`（FB-P1-01/02/03、FB-P2-01）
- 通过项摘要：Onboarding/权限/目标应用/个性化/Dashboard/暂停恢复、PERCEPTION/COGNITION/BLOCKED 触发、Overlay 专项、休息与后台恢复、锁屏恢复、GRACE 窗口、最近任务移除、进程重建（START_STICKY）、force-stop 语义、系统返回键对照、输入法、紧急使用（含每日一次）、无障碍强阻断（授权/持久化/自动回桌面/overlay 阻断页/次日紧急解锁）、覆盖安装、BLOCKED 粘滞（T40）、重启 T41/T42。

### 证据标准

- 每个 PASS/FAIL 必须有可复核证据：截图、UI dump、logcat 片段、`FlowBreakPrefs.xml`、DB 查询、`dumpsys` 输出之一。
- 几十 MB 的 logcat 不入仓库；只保留 summary 与外部证据路径/报告名。
- 任务完成报告（progress-*）代表执行 AI 的自述，不等同于独立验收结果；独立验收以测试矩阵与证据为准。

### 尚未完成的设备验证

- 小米、OPPO、vivo、华为完整矩阵（权限、后台限制、重启、24 小时稳定性）。
- 与系统数字健康误差 ≤10% 的多设备对照。
- 阻断触发延迟 ≤2s 实测。
- 微信视频号识别真机专项。
- 修复提交（master `99fdcc2` 系列）的 Redmi 复测 R1–R4。

## 4. 验证快照（a06a772）

| 项 | 值 |
| ---- | ---- |
| Frontend (Vitest) | 148 |
| Play JVM | 220 |
| Domestic JVM | 220 |
| RecoveryIntegration | 23 |
| Room migrations | 6 |
| 真机结论 | FAILED（P1 ×3、P2 ×1，见 `KNOWN_ISSUES.md`） |

> 此快照对应 `a06a772`；master 上后续修复提交会改变测试数量，以最新 CI 为准。
