# DEVELOPMENT — 开发指南

> 给编码 AI / 开发者。当前环境示例仅描述本机现状，不是跨机器标准；构建命令与版本要求是项目要求。

## 1. 环境版本要求（项目要求）

- Node.js 22.23.1（CI 使用；`app/package.json` 中 engines 以仓库为准）
- JDK 21（temurin 21，CI 使用；Gradle 构建文件按 `VERSION_21` 配置）
- Android SDK（CI 使用 `android-actions/setup-android`；本地需 Android SDK Platform + Build Tools）
- 前端包管理：`npm ci`（使用 `app/package-lock.json`）

> 本机环境示例（非项目要求）：`D:\environment\flowbreak-env.ps1` 提供环境变量；本机曾因用户名含中文导致 Gradle Test Worker 类路径问题，使用 ASCII junction 指向 Gradle 缓存解决（见归档 `VERIFICATION.md`）。**这些是当前设备环境实现，不是项目架构要求**，其他机器请按各自环境配置。

## 2. 常用命令

```powershell
# 前端
cd app
npm ci
npm run dev          # 开发服务器
npm test             # Vitest
npm run build        # tsc + vite build → dist/
npx cap sync android # 同步 dist 与原生工程（构建原生前必须执行）

# Android 原生（Windows 用 gradlew.bat）
cd app/android
.\gradlew.bat testPlayDebugUnitTest testDomesticDebugUnitTest
.\gradlew.bat lintPlayDebug lintDomesticDebug
.\gradlew.bat assemblePlayDebug assembleDomesticDebug
.\gradlew.bat bundlePlayRelease assembleDomesticRelease   # Release 产物未签名
.\gradlew.bat assemblePlayDebugAndroidTest                # 仅构建 instrumentation APK，不执行设备测试
```

## 3. 代码敏感区（改动前必读）

- 状态机：`app/android/app/src/main/java/com/flowbreak/app/BlockStateMachine.java`（产品语义见 `PRODUCT.md`，改动必须配套单测）
- 前台追踪：`ForegroundUsageDetector.java` / `ForegroundAppTracker.java`（已知缺陷 `FB-P1-01`）
- 前台服务：`FlowForegroundService.java`（2s tick、START_STICKY、休息/紧急/拉回协调）
- 覆盖层：`FlowOverlayController.java`（fallback `BlockActivity`，见 `FB-P1-03`）
- 无障碍强阻断（仅 domestic）：`src/domestic/java/com/flowbreak/app/FlowAccessibilityService.java`
- 统计取数：`NativeFlowPlugin.java` / `NativeFlowStatisticsService.java`（UsageEvents 配对算法）
- 数据模型：`FlowDatabase.java`（schema v3；改 schema 必须提供迁移并提交 `app/schemas/`）
- 前端全局：`app/src/App.tsx`（GlobalMonitor）、`app/src/hooks/useStore.ts`、`app/src/backend/storage.ts`

## 4. Git 安全规范

- 不在 master 直接做大规模重构；使用独立分支/worktree（如 `docs/consolidate-project-docs`）。
- 禁止：`git reset --hard`、`git clean -fd`、`git add .`/`git add -A`、`git commit -am`、force push。
- 提交范围精确；业务代码、测试、Gradle、CI、依赖、Android 资源与文档变更分开提交。
- 危险操作（删文件、force push 等）先确认；不提交敏感文件。
- 提交信息使用项目现有风格（`fix(scope): ...` / `docs: ...` / `test(...): ...` / `chore(...): ...`）。

## 5. 文档协作规范

- 当前产品规则以 `docs/PRODUCT.md` 为准；文档与源码冲突 → 登记 `docs/KNOWN_ISSUES.md`，不得静默改文档掩盖。
- 新事实只写入唯一归属文档（见 `docs/README.md` 表格），不复制到多处。
- 历史资料放入 `docs/archive/` 且不改写原文；归档 README 统一声明「不具当前规范效力」。
- 易变数字（HEAD、测试数、Run ID、SHA）只进 `CURRENT_STATUS.md` / `TESTING.md` 快照。
- 文档改动后检查相对链接有效性（自检清单见 `docs/docs-audit.md`）。

## 6. 提交要求（编码任务）

- 完成自测并贴关键输出（tsc / build / gradle 结果）。
- 修改了状态机/服务/追踪/统计等核心逻辑时，必须补充或更新对应 JVM 单测；前端逻辑改动更新 Vitest。
- 真机相关改动必须说明复测计划（对应 `KNOWN_ISSUES.md` 的 Next action）。
- 交付信息包含：Documentation Delta（如涉及）、final SHA、CI 结果、真机复测结果。
