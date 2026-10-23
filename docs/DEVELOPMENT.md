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
npm run brand:generate   # 修改 SVG 后更新浏览器 / Android 图标与启动图
npm run test:brand       # 检查派生品牌资产没有漂移
npx playwright install chromium   # 首次网页验收时安装浏览器
npm run test:ui       # 独立启动本地 preview，逐页截图 / 交互 / 溢出 / 外网请求检查
node scripts/generate-build-provenance.mjs dist   # 生成 dist/build-provenance.json（需 SOURCE_COMMIT_SHA 等 allowlist env）
npx cap sync android # 同步 dist 与原生工程（构建原生前必须执行）
node scripts/verify-web-asset-sync.mjs dist android/app/src/main/assets/public   # dist→assets SHA-256 全量校验
node scripts/generate-artifact-manifest.mjs --out <dir> --provenance <json> --binaries '<json>' --extra-sums '<json>'   # manifest + SHA256SUMS

# Android 原生（Windows 用 gradlew.bat）
cd android           # 接上方的 app/ 目录；若从仓库根目录开始则 cd app/android
.\gradlew.bat testPlayDebugUnitTest testDomesticDebugUnitTest
.\gradlew.bat lintPlayDebug lintDomesticDebug
.\gradlew.bat assemblePlayDebug assembleDomesticDebug
.\gradlew.bat bundlePlayRelease assembleDomesticRelease   # Release 产物未签名
.\gradlew.bat assemblePlayDebugAndroidTest                # 仅构建 instrumentation APK，不执行设备测试
```

### 网页视觉回归

`npm run test:ui` 要求已经生成 `dist/`，默认使用本地端口 4175；如果端口已占用会失败，不会把未知旧服务当作被测构建。需要明确复用已知服务时用 `npm run test:ui -- --url http://127.0.0.1:<port>`。输出在仓库被忽略的 `.tmp/ui-smoke/`，包括逐页 PNG 与 `results.json`；可用 `--out <path>` 指定输出。

测试使用隔离的浏览器上下文和本地预览配置，检查页面身份、横向溢出、图片、资源加载失败、未处理异常、外网请求与导航，并包含合成统计样本与 Web 休息流程；不读取真实设备、不清除已有用户数据，也不证明原生保护有效。视觉质量仍要逐张看截图，不能仅凭测试退出码判断。品牌调整后先执行 `npm run test:brand`；具体视觉约定见 [BRAND.md](BRAND.md)。

若当前 npm registry 不支持安全审计接口，不要把 404 当作无漏洞；对公开依赖可显式执行 `npm audit --registry=https://registry.npmjs.org`。不要用 `npm audit fix --force` 做未审查的大版本升级。

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
- 生产密钥：首轮 technical dry-run 身份已降级 **SUPERSEDED_PRE_PRODUCTION**（无泄露证据、非安全事故，禁用于正式发行）。最终身份与托管状态以 `app/release-signing-policy.json` 和 [CURRENT_STATUS.md](CURRENT_STATUS.md) 为准。常规开发不得重新生成、替换身份或修改 secrets；身份变更必须另行授权，只从 public certificate 独立核对 fingerprint。
- 签名边界：release job 仅 `workflow_dispatch && refs/heads/master` 或 `v*` tag；Environment `production-signing` 已由产品负责人设置为 `master` + `v*`；当前无 v* tag，0 matching tags 正常。
- 证书 SHA-256 fingerprint 是公开信息，写入 `app/release-signing-policy.json` allowlist（最终身份 fingerprint；CI signed build 强制匹配，换错证书立即 FAIL）。
- Portable signing custody：禁止 Windows DPAPI、Credential Manager-only、当前 SID、TPM-only、BitLocker machine-bound state 或当前电脑专属证书作为正式恢复源。现有 age 托管状态见 policy；仓库的 `tools/New-PortableSigningVault.ps1` 与 `tools/Test-PortableSigningVault.ps1` 当前在接触敏感内容前 fail closed（`BLOCKED_PENDING_SAFE_TOOL`），不得当作可用的创建或恢复工具，也不允许用任意外部 archiver fallback 绕过。
- 独立备份纪律：最终两把 `.jks` 必须存在与当前电脑物理/逻辑独立的加密备份（离线介质/可信个人存储）；仅复制到另一个本地文件夹不算备份。跨机器验收必须在经授权的独立环境按已批准的安全流程执行并留证；上述占位脚本的拒绝测试不算恢复验收。只有实际证据达到 `CROSS_MACHINE_SIGNING_RECOVERY=PASS` 才能关闭该门禁。

## 3. 代码敏感区（改动前必读）

- 状态机：`app/android/app/src/main/java/com/flowbreak/app/BlockStateMachine.java`（产品语义见 `PRODUCT.md`，改动必须配套单测）
- 前台追踪：`ForegroundUsageDetector.java` / `ForegroundAppTracker.java`（历史缺陷 `FB-P1-01` 已 RESOLVED；改动必须配套单测）
- 前台服务：`FlowForegroundService.java`（2s tick、START_STICKY、休息/紧急/拉回协调）
- 覆盖层：`FlowOverlayController.java`（fallback `BlockActivity` 为尽力而为路径，见 `KNOWN_ISSUES.md#COMPAT-001`）
- 无障碍强阻断（仅 domestic）：`src/domestic/java/com/flowbreak/app/FlowAccessibilityService.java`（HOME + 顶部横幅 + 尽力而为 `tryStartBlockActivity`）
- 返回键桥接：`app/android/app/src/main/java/com/flowbreak/app/MainActivity.java`（onBackPressed → `window.__flowbreakHandleBack`）+ 前端 `app/src/pages/TargetApps.tsx`（脏检查钩子；历史缺陷 `FB-P2-01` 已 RESOLVED）
- 统计取数：`NativeFlowPlugin.java` / `NativeFlowStatisticsService.java`（读取 Room 使用聚合与 legacy 兼容汇总；UsageEvents 检测/配对在前台服务链路）
- 数据模型：`FlowDatabase.java`（schema v3；改 schema 必须提供迁移并提交 `app/android/app/schemas/`）
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
