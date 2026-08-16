# TESTING — 测试与验证

> 定义自动化测试层级、CI 范围、真机测试矩阵与证据标准。易变的数字只出现在下方「验证快照」；历史验证记录在 `docs/archive/progress/`（含 `VERIFICATION.md` 的 Beta 1.1 记录）。

## 1. 自动化测试层级

| 层级 | 命令 | 覆盖 |
| ---- | ---- | ---- |
| Frontend | `cd app && npm test`（Vitest） | 页面、store、rest-mode、状态判定等 React/TS 逻辑 |
| Play JVM | `cd app/android && ./gradlew testPlayDebugUnitTest` | Robolectric：状态机、前台追踪、服务恢复、统计等（Play 渠道） |
| Domestic JVM | `./gradlew testDomesticDebugUnitTest` | 同上（Domestic 渠道，含无障碍强阻断回归测试） |
| RecoveryIntegration | 包含在上述 JVM 任务中 | 前台服务重建/重启恢复集成测试 |
| Room migrations | 包含在上述 JVM 任务中 | schema 1→2→3 迁移测试 |
| Build/Lint | `./gradlew lintPlayDebug lintDomesticDebug assemblePlayDebug assembleDomesticDebug` | 源码分析 + 双渠道 Debug 构建 |
| Release 构建 | `./gradlew bundlePlayRelease assembleDomesticRelease` | R8、资源压缩、Release Lint Vital（**未签名**产物） |

## 2. CI 做什么 / 不做什么

CI（`.github/workflows/android.yml`）在 master push / PR 时执行：

- 做：`npm ci`、npm audit 门禁（生产依赖 high 级 + 全依赖 critical 级）、`npm test`、`npm run test:provenance`、`npm run build`、`npx cap sync android`、双渠道单测 + lint + Debug 构建、`assemblePlayDebugAndroidTest`、Release 双渠道构建、R8 mapping 检查、Room schema drift 检查。
- 做（Artifact Provenance 链，GATE C）：source identity 校验（checkout HEAD = `SOURCE_COMMIT_SHA`、工作区干净）→ 生成 `dist/build-provenance.json` → cap sync → **Web asset sync 校验**（dist 每个文件在 Android assets 中 SHA-256 一致，缺失/mismatch 直接 FAIL）→ Release 构建 → **APK/AAB 内部 provenance 校验**（unzip + `aapt2 dump badging` + AAB 自身 manifest 经 wrapper zip + `aapt2 dump xmltree` 读版本）→ `artifact-manifest.json` + `SHA256SUMS.txt` → upload-artifact（`flowbreak-unsigned-<sourceSha>`，retention 90 天）。
- 不做：**不执行 connected instrumentation tests**。`assemblePlayDebugAndroidTest` 只是构建 instrumentation APK（AndroidTest APK successfully ASSEMBLED），**不等于**在设备上执行测试。
- 不做：真机验证（厂商后台限制、悬浮窗/无障碍行为、统计误差对照等只能真机做）。
- 历史缺口（已关闭）：Run `31577669420`（HEAD = `99fdcc2`）当时**未持久上传 artifacts**——「CI build success」≠「仍可从该 Run 下载正式 artifact」。该缺口已由 GATE C 实现关闭（artifact 已持久上传并实际下载复核）。

## 3. 真机测试矩阵与证据标准

### 结果标记

| 标记 | 含义 |
| ---- | ---- |
| PASS | 有可复核证据（截图/日志/DB/prefs）且符合 PRODUCT 语义 |
| FAIL | 复现出与 PRODUCT 语义不符的行为 |
| BLOCKED | 环境/系统限制导致不可测（如 `am kill` 对前台服务进程不生效） |
| NOT_RUN | 未执行 |

### 2026-08-14 Redmi R1–R4 复测记录（当前设备证据）

- 设备：Redmi Note 13 Pro 5G（`2312DRA50C` / garnet）/ Android 16 / SDK 36 / HyperOS 3.0（`OS3.0.306.0.WNRCNXM`）
- 被测 APK：domestic debug（com.flowbreak.app.cn，versionCode 2 / versionName 1.1.0）。复测前在代码工作区 `99fdcc2` 上重新执行前端 build → Capacitor sync → Android assemble 生成；该重建解决了此前旧 Web bundle 混入问题（`__flowbreakHandleBack` 缺失）。正式 Artifact Provenance 链已由 `RELEASE.md` GATE C 建立并通过（2026-08-15）。
- 报告：External device evidence: `reports/R1-R4-retest-2026-08-14.md`、`reports/final-report.md`（第二轮结论）
- 总结果：**PASSED**（R1 ×3 / R2 / R3 ×3 / R4 全部 PASS）
- R1 冷启动前台追踪 ×3：MainActivity→BrowserActivity 同包 Activity 切换事件形态完全复现，sessionMs 1:1 连续增长、不再卡 0。
- R2 BLOCKED sticky：同一真实 BLOCKED 下离开 29s/31s/64s/约 2min 重进均仍 BLOCKED，sessionMs 未被 30 秒规则重置；completeRest → GRACE 10min 正常；Emergency 长按约 11s → GRACE 5min，emergencyUnlockDay 更新、DB emergency_unlock 正常记录。
- R3 HyperOS 强阻断 ×3：真实 BLOCKED → 打开目标 App → Accessibility 立即执行 HOME → 回到桌面 → TYPE_ACCESSIBILITY_OVERLAY 顶部横幅可见且含「开始休息」入口；横幅单实例、重复命中不堆叠、非目标 App 正常使用、无全手机锁死、连续三次重进均继续阻断；核心强阻断不依赖 BlockActivity 成功启动。
- R4 系统返回键：物理 Back 与手势 Back 在未保存修改时均弹「有未保存的修改」且应用不退；无修改保持系统默认退出。
- Smoke：IDLE→PERCEPTION→COGNITION→BLOCKED→RESTING→GRACE 全链路走通；force-stop 后 20s 无自动重启（语义正确）；覆盖安装保留 prefs/DB/目标应用/权限数据。
- 遗留观察（非阻塞）：`tryStartBlockActivity` 仍可能产生 `MIUILOG Permission Denied Activity` 系统日志（R3 轮次 4 条），横幅兜底使其不影响功能（`KNOWN_ISSUES.md#COMPAT-001`）。

### 2026-08-12 首轮验收记录（历史快照，不再代表当前状态）

- 设备：Redmi Note 13 Pro 5G（`2312DRA50C` / garnet）/ Android 16 / SDK 36 / HyperOS 3.0（`OS3.0.306.0.WNRCNXM`）
- APK：`app-domestic-debug.apk`（com.flowbreak.app.cn，versionCode 2 / versionName 1.1.0），代码基线 `a06a772`
- 当时结论：**FAILED**（`reports/final-report.md` 第一轮；T01–T42 矩阵）——P1 ×3、P2 ×1，缺陷明细见 `KNOWN_ISSUES.md` 各条目历史。
- 通过项摘要（第一轮已 PASS，第二轮未推翻）：Onboarding/权限/目标应用/个性化/Dashboard/暂停恢复、PERCEPTION/COGNITION/BLOCKED 触发、Overlay 专项、休息与后台恢复、锁屏恢复、GRACE 窗口、最近任务移除、进程重建（START_STICKY）、force-stop 语义、输入法、紧急使用（含每日一次）、覆盖安装、重启 T41/T42。
- 该快照发现的缺陷（FB-P1-01/02/03、FB-P2-01）已全部 RESOLVED（2026-08-14 R1–R4）。

### 证据标准

- 每个 PASS/FAIL 必须有可复核证据：截图、UI dump、logcat 片段、`FlowBreakPrefs.xml`、DB 查询、`dumpsys` 输出之一。
- 几十 MB 的 logcat 不入仓库；只保留 summary 与外部证据路径/报告名。
- 任务完成报告（progress-*）代表执行 AI 的自述，不等同于独立验收结果；独立验收以测试矩阵与证据为准。

### 构建产物溯源（Build Artifact Provenance）证据标准

- APK 同时包含原生 dex 与前端 Web bundle；两者必须来自**同一个 Git SHA**。不能只说「这个 APK 是最新的」，必须能证明 Native code、Web assets、BuildConfig / version metadata 来自同一 Git SHA。
- 复测暴露案例（2026-08-12 16:14 本地 APK）：dex 已含 R4 新代码（`__flowbreakHandleBack`），但 JS bundle 仍是旧版 → 钩子 undefined → 返回键直退应用无对话框。**这不是 R4 产品 Bug 本身**，而是产物溯源问题；重新执行 `npm build` → `cap sync` → `assemble` 后问题解决。
- 验收/发布用产物要求：
  1. 前端 build
  2. cap sync
  3. Android build
  4. 同一工作流连续完成
  5. 产物记录 Git SHA
  6. 对 APK / AAB 计算 SHA-256 并留档
  7. release artifact 不允许使用来源不明的旧本地产物
- 验证技巧：组装前确认 assets 中存在新代码特征字符串（如钩子名 `__flowbreakHandleBack`）。

### 已实现的 provenance 工具链（GATE C，PR #1 实测通过）

- 脚本（`app/scripts/`，Node 内置模块，无新增依赖；测试 `npm run test:provenance` 20/20、`npm run test:release` 8/8）：
  - `generate-build-provenance.mjs`：npm build 后生成 `dist/build-provenance.json`（allowlist 字段，缺 SHA/非法 SHA/非法版本号即 FAIL，绝不 dump 环境变量/secret）。
  - `verify-web-asset-sync.mjs`：cap sync 后校验 dist 每个文件在 Android web assets 中 SHA-256 一致（缺失/mismatch 即 FAIL），并校验 provenance 的 sourceGitSha/versionCode/versionName 与本轮声明一致。
  - `generate-artifact-manifest.mjs`：生成 `artifact-manifest.json`（binaries 的 filename/size/sha256）与 `SHA256SUMS.txt`（binaries + 双渠道 mapping + manifest 自身；manifest 不含自身 hash，无递归）。
- CI（`.github/workflows/android.yml`）：
  - PR 事件 checkout 固定到 `SOURCE_COMMIT_SHA`（PR head SHA）；manifest 区分 `sourceGitSha`（实际构建源码）与 `workflowSha`（github.sha，PR 为 merge SHA），并记录 `prHeadSha`——临时 merge commit 不冒充 source commit。
  - APK 版本独立读取：`aapt2 dump badging`；AAB 版本独立读取：其自身 `base/manifest/AndroidManifest.xml` 经 wrapper zip 由 `aapt2 dump xmltree` 解析（AGP 8.13 对 AAB 不产出 output-metadata.json，已注明）。
  - 产物命名：`flowbreak-unsigned-<sourceSha>`（UNSIGNED / TEST ONLY）；tag 签名发布复用同一链路（signing 属 GATE G）。
- 独立复核流程（验收标准）：下载 artifact ZIP → 解压 → `sha256sum -c SHA256SUMS.txt`（或 Get-FileHash）→ manifest sourceGitSha = 期望 SHA → 从 APK/AAB 内读取 `build-provenance.json` 的 sourceGitSha 与版本一致性。
- 实测记录：PR #1 CI Run `31900444404` verify SUCCESS；artifact `flowbreak-unsigned-ea53a22136dd58663291581763eb3614c4b10ba6` 下载后 5/5 校验一致，APK/AAB 内部 provenance sourceGitSha 均 = PR head SHA。

### 版本与签名验证（GATE G Stage A，2026-08-16）

- **版本策略（VERSION_POLICY）**：versionName 唯一 Source of Truth = `app/package.json` version（stable SemVer，无 prerelease/build）；versionCode = `MAJOR*1_000_000 + MINOR*1_000 + PATCH`（1.1.0 → 1001000；MINOR/PATCH < 1000，总量 ≤ 2100000000），确定性、单调、与 CI run_number 无关；正式 tag 必须精确 = `v<versionName>`（`vfoo`/`v1`/`v1.1.0-test` 一律 FAIL）。`scripts/release-version.mjs` 输出 `VERSION_NAME`/`VERSION_CODE` 供 GITHUB_ENV；release job 构建前还校验 tag 指向的 SHA 是 origin/master 的 ancestor（`TAG_ON_MASTER_LINE=PASS`）。
- **签名身份（SIGNING_IDENTITY_DECISION = SEPARATE）**：Play = upload key（`FLOWBREAK_PLAY_*`），Domestic = app-signing key（`FLOWBREAK_DOMESTIC_*`）；build.gradle 按 flavor 条件启用，env 缺失时保持 unsigned（CI verify 不受影响）。
- **签名验证（CI release job）**：Domestic APK → `apksigner verify --verbose --print-certs`（Verifies + signer 数 + cert SHA-256）；Play AAB → `jarsigner -verify`（self-signed upload key 属正常，不用 `-strict`）+ `keytool -printcert -jarfile` 提取 cert SHA-256；两者与 `app/release-signing-policy.json`（仅公开信息，无 secret）allowlist 对照，不匹配 FAIL；tag 生产发布要求 allowlist 已 provision，dry-run 允许未 provision 但记录 warning。
- **signed artifact provenance**：signed AAB/APK + mappings + build-provenance + artifact-manifest（含 signed/signingRole/certificateSha256）+ SHA256SUMS 一并上传；命名 `flowbreak-signed-v<version>-<sha>`（dry-run 前缀 `flowbreak-signed-dry-run-`）；`if: always()` 清理 `$RUNNER_TEMP` keystore。
- **TEST ONLY 密钥纪律**：生产密钥不存在（`SIGNING_ASSET_STATUS = NONE`）。本地机制验证使用临时 TEST ONLY 密钥（`keytool` 生成于临时目录、明确 TEST ONLY、不进仓库、不作为正式 fingerprint）；GitHub CI 的 signed dry-run 在 secrets 缺失时清晰失败，**不**降级用 debug key 冒充 production signing。
- **本地实测（TEST ONLY 密钥，2026-08-16）**：signed 构建 PASS；apksigner Verifies（v2，1 signer，cert = TEST domestic fingerprint）；jarsigner exit 0；keytool cert = TEST play fingerprint；badging versionCode=1001000/versionName=1.1.0 与 verifier 输出一致；manifest signed 证据 + sums 复核 0 失败。
- **尚未执行（需生产密钥）**：真机 install/upgrade（当前无设备连接）、生产 fingerprint allowlist、`PRODUCTION_KEY_BACKUP = VERIFIED`——GATE G 保持 PENDING。

### Protection Integrity（UI promise ≤ 实际保护能力）

- 原则：**界面不能显示「保护中」，而实际关键能力已经失效**。UI 承诺必须 ≤ 实际保护能力。
- 当前作为 TESTING / RELEASE ACCEPTANCE PRINCIPLE 执行（不是马上做大规模新功能）。
- 未来 OEM / 24h 验证时，正常路径测试同时做失效注入，例如：
  - 撤销 Usage Access
  - 撤销 Overlay
  - Domestic 关闭 Accessibility
  - service 被系统杀
  - battery saver / OEM 后台限制
  - force-stop 后重新手动打开
- 每项至少验证：A. 实际保护行为；B. 用户界面是否如实反映能力。
- force-stop 语义：force-stop 后**不要求** App 自动复活。正确测试：force-stop → 用户手动重新打开 FlowBreak → UI 不得谎称旧保护状态仍完全有效 → 合法恢复保护。
- 未来 24h 测试：不仅验证「不崩」，还要验证「**无 silent protection drift**」（保护能力无无声漂移）。

### 尚未完成的设备验证

- 多 OEM 真机矩阵（Xiaomi / Redmi、OPPO / OnePlus、vivo / iQOO、Honor / Huawei：权限、后台限制、重启、24 小时稳定性）。
- 与系统数字健康误差 ≤10% 的多设备对照（UsageStats 精度）。
- 阻断触发延迟 ≤2s 实测（blocking latency）。
- 24h stability + Protection Integrity（含上述失效注入）。
- 微信视频号识别真机专项。
- 小规模 Beta。

## 4. 验证快照（99fdcc2，2026-08-14）

| 项 | 值 |
| ---- | ---- |
| Frontend (Vitest) | **151 / 151 PASS**（14 test files） |
| Play JVM | **244 PASS** |
| Domestic JVM | **254 PASS**（比 Play 多出的测试含新的 Accessibility 强阻断回归测试） |
| RecoveryIntegration | **23**（@Test；仍是 23，不是 26） |
| Room migrations | **6 / 6** |
| CI | Run `31577669420` / verify Job `94053353542` **SUCCESS**（HEAD = `99fdcc2`） |
| 真机结论 | **PASSED**（R1–R4，2026-08-14；P0 = 0、P1 = 0） |

> 历史快照（`a06a772`，2026-08-12）：Frontend 148 / Play JVM 220 / Domestic JVM 220 / RecoveryIntegration 23 / Room 6，真机结论 FAILED。仅作历史对比，不得当作当前数字。
