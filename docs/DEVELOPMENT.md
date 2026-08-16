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
npm run test:provenance   # node:test — provenance 工具链测试（app/scripts/__tests__）
npm run test:release   # node:test — release 版本策略测试
node scripts/release-version.mjs            # 计算 VERSION_NAME/VERSION_CODE（package.json version 为 Source of Truth）
node scripts/release-version.mjs --tag v1.1.0   # 校验 tag == v<version>，不符 FAIL
npm run build        # tsc + vite build → dist/
node scripts/generate-build-provenance.mjs dist   # 生成 dist/build-provenance.json（需 SOURCE_COMMIT_SHA 等 allowlist env）
npx cap sync android # 同步 dist 与原生工程（构建原生前必须执行）
node scripts/verify-web-asset-sync.mjs dist android/app/src/main/assets/public   # dist→assets SHA-256 全量校验
node scripts/generate-artifact-manifest.mjs --out <dir> --provenance <json> --binaries '<json>' --extra-sums '<json>'   # manifest + SHA256SUMS

# Android 原生（Windows 用 gradlew.bat）
cd app/android
.\gradlew.bat testPlayDebugUnitTest testDomesticDebugUnitTest
.\gradlew.bat lintPlayDebug lintDomesticDebug
.\gradlew.bat assemblePlayDebug assembleDomesticDebug
.\gradlew.bat bundlePlayRelease assembleDomesticRelease   # Release 产物未签名
.\gradlew.bat assemblePlayDebugAndroidTest                # 仅构建 instrumentation APK，不执行设备测试
```

### 构建产物溯源（必读）

APK 同时包含原生 dex 与前端 Web bundle（`app/dist/` 经 `cap sync` 拷贝进 `android/app/src/main/assets`）。前端修改后如果跳过重建，会出现「原生已更新、Web 仍是旧包」的不一致 APK（案例：2026-08-12 16:14 本地 APK 的 dex 含 `__flowbreakHandleBack`，但 JS bundle 缺失该钩子，R4 复测失败；重建后通过）。规则：

1. 前端改动后必须连续执行：`npm run build` → `npx cap sync android` → `gradlew assemble*`（同一工作流一次完成）。
2. 组装产物前确认 assets 中包含新代码特征（例如对钩子字符串 `__flowbreakHandleBack` 做检查）。
3. 产物必须记录构建对应的 Git SHA；对 APK/AAB 计算 SHA-256 并留档。
4. 禁止使用来源不明的旧本地产物做验收/发布（详见 `TESTING.md` 产物溯源、`RELEASE.md` GATE C）。
5. 本地验证 provenance 链：设 `SOURCE_COMMIT_SHA`（= 当前 HEAD）、`VERSION_CODE`、`VERSION_NAME` 环境变量后，按上述「常用命令」顺序执行脚本即可；CI 中的完整校验（source identity、dist→assets、APK/AAB 内部 provenance、aapt2 badging、manifest/SHA256SUMS、upload-artifact）见 `.github/workflows/android.yml`。

### 版本策略（VERSION_POLICY，GATE G）

- versionName 唯一 Source of Truth = `app/package.json` 的 `version`（stable SemVer `X.Y.Z`，无 prerelease/build/leading-zero）。
- versionCode = `MAJOR*1_000_000 + MINOR*1_000 + PATCH`（1.1.0 → 1001000）；MINOR/PATCH 必须 < 1000，总量 ≤ 2100000000；单调且与 CI run_number 无关。
- 正式发布 tag 必须精确 = `v<versionName>`；`vfoo`/`v1`/`v1.1.0-test` 等一律 FAIL。tag 所指 SHA 必须是 origin/master 的 ancestor（workflow 强制）。
- CI verify 的 `ci-<sha>`/VERSION_CODE=1 仅属 unsigned 验证产物语义，保持不动。

### 签名身份与 secret 纪律（GATE G）

- 双渠道分离签名身份：Play `FLOWBREAK_PLAY_KEYSTORE_*`（upload key）、Domestic `FLOWBREAK_DOMESTIC_KEYSTORE_*`（app-signing key）；build.gradle 仅在对应 env 存在时启用该渠道 release 签名，否则 unsigned。
- **禁止**：把 keystore/密码提交 git、写进 provenance/manifest、打进日志、放进 artifact；keystore 只允许解码到 `$RUNNER_TEMP` 并由 `if: always()` 清理。
- 本地机制验证可用 TEST ONLY 密钥（keytool 生成于临时目录，明确 TEST ONLY，不进仓库）。
- 生产密钥（Stage B 首轮）已降级 **SUPERSEDED_PRE_PRODUCTION**（无泄露证据、非安全事故，禁用于正式发行）。**最终生产密钥 = HUMAN-ONLY 密码托管**：产品负责人独立终端用 keytool 交互式生成（命令不含 -storepass/-keypass），密码直接录入其密码管理器；AI 不得生成/读取/保存/接收密码；**禁止再创建任何 password handoff 文件**；AI 仅从 public certificate 独立计算 fingerprint 交叉核对。
- 签名边界：release job 仅 `workflow_dispatch && refs/heads/master` 或 `v*` tag；Environment `production-signing` 部署分支策略（master + v*）由产品负责人 UI 设置。
- 证书 SHA-256 fingerprint 是公开信息，写入 `app/release-signing-policy.json` allowlist（最终身份 fingerprint；CI signed build 强制匹配，换错证书立即 FAIL）。
- 独立备份纪律：最终两把 `.jks` 必须存在与当前电脑物理/逻辑独立的加密备份（离线介质/密码管理器保险库）；仅复制到另一个本地文件夹不算备份；`PRODUCTION_KEY_BACKUP = VERIFIED` 只能由产品负责人确认。

## 3. 代码敏感区（改动前必读）

- 状态机：`app/android/app/src/main/java/com/flowbreak/app/BlockStateMachine.java`（产品语义见 `PRODUCT.md`，改动必须配套单测）
- 前台追踪：`ForegroundUsageDetector.java` / `ForegroundAppTracker.java`（历史缺陷 `FB-P1-01` 已 RESOLVED；改动必须配套单测）
- 前台服务：`FlowForegroundService.java`（2s tick、START_STICKY、休息/紧急/拉回协调）
- 覆盖层：`FlowOverlayController.java`（fallback `BlockActivity` 为尽力而为路径，见 `KNOWN_ISSUES.md#COMPAT-001`）
- 无障碍强阻断（仅 domestic）：`src/domestic/java/com/flowbreak/app/FlowAccessibilityService.java`（HOME + 顶部横幅 + 尽力而为 `tryStartBlockActivity`）
- 返回键桥接：`app/android/app/src/main/java/com/flowbreak/app/MainActivity.java`（onBackPressed → `window.__flowbreakHandleBack`）+ 前端 `app/src/pages/TargetApps.tsx`（脏检查钩子；历史缺陷 `FB-P2-01` 已 RESOLVED）
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
