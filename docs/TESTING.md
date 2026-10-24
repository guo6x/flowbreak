# TESTING — 测试与验证

> 定义自动化测试层级、CI 范围、真机测试矩阵与证据标准。易变的数字只出现在下方「验证快照」；历史验证记录在 `docs/archive/progress/`（含 `VERIFICATION.md` 的 Beta 1.1 记录）。

## 当前集成验证来源（2026-10-24 核对，历史计数与产物不转移）

- 当前 master 基线：`67d1b7c3ee82ef4f6dd275299d51969869d7db4d`（PR #42 merge）；运行时代码 SHA：`8cf1d653fe34d01096130322199ed4f4d6623039`（Android REST 作弊取消持久化失败时 fail closed，并补齐回归测试）；PR #43 的最新完整候选验证 HEAD：`a66013d4f24bd6e282eda0904f6c735afcfa0446`（其上收口状态文档）。仍开放的 PR #29 保护完整性分支快照为 `b00b9cc75073e77759e28ba5b71f960033cbf1d5`，各 SHA 证据不互相转移。
- 最新完整候选 hosted verify [Run `40039686623`](https://github.com/guo6x/flowbreak/actions/runs/40039686623) **PASS**，release 按策略 **SKIPPED**；运行时代码 SHA 的上一轮 [Run `40033048741`](https://github.com/guo6x/flowbreak/actions/runs/40033048741) 与 master 基线 [Run `39928579175`](https://github.com/guo6x/flowbreak/actions/runs/39928579175) 仍单独保留。旧 Run/产物仍只归属原始 SHA。
- 目标状态为 **`INTEGRATION_ENGINEERING_READY / DEVICE_ACCEPTANCE_PENDING`**；常规工程合并不代表正式发布；模拟器、JVM、静态检查和 unsigned 产物都不能关闭真实设备 Gate F。

> 下表中的自动化数字与审计结论均来自完整候选验证 HEAD `a66013d4…` / Run `40039686623`；运行时代码 SHA `8cf1d653…` / Run `40033048741` 作为前一阶段代码验证历史保留。

| 检查 | 结果与边界 |
| ---- | ---- |
| Frontend (Vitest) | **296 / 296 PASS**，27 个测试文件；覆盖设置/受限应用保存后服务动作失败的真实状态对账；Windows 复核使用单 worker，避免并行 fork 启动超时 |
| TypeScript / Vite | **PASS**（`npm run build`） |
| 网页逐页 / 交互 | **44 / 44 PASS**；涵盖窄屏、导航、统计图、Web 休息控制和完成态 |
| 品牌一致性 | **36 / 36 PASS**（`npm run test:brand`） |
| Provenance / Release policy | **20 / 20 PASS**、**8 / 8 PASS** |
| Privacy static | **15 / 15 PASS**；静态网络与 SDK 清单检查通过 |
| Android JVM | Domestic **489 / 489 PASS**；Play **456 / 456 PASS**；JUnit XML 汇总为 0 failure / 0 error / 0 skipped（完整候选 hosted CI 双渠道执行并通过） |
| 双渠道 Lint | **PASS**；0 error（warnings 仅为既有工具链提示） |
| 双渠道 Debug / unsigned Release | **PASS**；Play AAB 与 Domestic unsigned Release APK 均成功生成，R8/Room/Manifest 检查通过 |
| Web → Android 同步 | **50 / 50 PASS**；Capacitor Web 资产与 Android assets SHA-256 一致 |
| unsigned 产物 provenance | 完整候选 Run `40039686623` 的 unsigned provenance **PASS**，sourceGitSha = `a66013d4…`；artifact `flowbreak-unsigned-a66013d…`（ID `12539036479`）仅为 TEST ONLY。Play AAB SHA-256 `97ebb2efa…`；Domestic unsigned APK SHA-256 `8bb6c5cb…`。运行时代码 SHA `8cf1d653…` 的 artifact 仍保留在 Run `40033048741` |
| npm audit（GitHub CI，生产依赖 high） | **PASS — 0 vulnerabilities**（完整候选 Run `40039686623`） |
| npm audit（GitHub CI，全依赖 critical） | **PASS**（完整候选 Run `40039686623`）；开发依赖仍有 Vite high advisory，不能写成全依赖零漏洞 |
| npm audit（本地镜像） | **BLOCKED / NOT_PASS**；当前配置的 `https://registry.npmmirror.com` 返回 404/`NOT_IMPLEMENTED`，不覆盖 hosted CI 结果 |
| API 36 AVD 启动与页面 | **受控范围 PASS**：`Medium_Phone_API_36.0` / Android 16 / API 36 / `emulator-5554`；Domestic debug 安装后启动，Onboarding、权限页、个性化、目标应用、首页、统计、个人中心、阻断设置、诊断均已截图 |
| AVD 权限往返 | 初始权限页 **0/2**；Usage Access 与 Display over other apps 系统页可打开并返回，随后仅在模拟器上通过 app-op 受控授予进行后续页面 smoke；未触碰真实手机、未读取签名材料 |
| AVD 诊断与服务 | 诊断页显示服务心跳、监控线程、设备支持、已配置、关键权限、Room v3 和 1 个目标应用；服务 `isForeground=true`。这是模拟器证据，不是 Redmi/HyperOS 或 24h 稳定性证据 |
| 崩溃检查 | 本次筛选日志未见 `FATAL EXCEPTION` / `AndroidRuntime` fatal；WebView variations seed 告警属于系统环境提示 |
| 2026-10-24 USB 实体机 smoke | **PASS_UNSUPPORTED_SCOPE_ONLY**：vivo `V2073A` / Android 13 / SDK 33；现有已签名 Domestic v1.1.0（APK SHA-256 `2ee7d214…`，来源 `2af5e93…`）的 Dashboard/Diagnostics 显示设备不支持、保护能力不可用、未发现 FlowBreak 保护 FGS；force-stop 后重新打开恢复，未见 fatal crash。此结果不代表 PR #43 候选包 |
| 候选安装与 Gate F | **NOT_EXECUTED / STILL OPEN**：PR #43 最新 Domestic APK 为 unsigned，`adb install -r` 返回 `INSTALL_PARSE_FAILED_NO_CERTIFICATES`；未卸载或清除现有签名包。connected instrumentation、候选 APK 真机 smoke、24h soak、生产签名、商店审核和正式发布仍未完成 |

AVD 截图与脱敏日志保存在工作区外：`D:\temp\flowbreak-avd-evidence-20261019`。该目录中的截图仅证明指定 API 36 模拟器的受控页面和权限状态，不能转译为 Gate F/G/H/I 通过。

实体机本轮原始截图、UI dump、logcat、包信息和安装失败输出保存在工作区外：`D:\temp\flowbreak-device-evidence-20261024`。本轮只使用已安装签名包做兼容性/失效保护边界复验；未使用生产签名，也未将 unsigned 候选强行安装。该设备属于 v1.1.0 明确 fail-closed 的 vivo/iQOO 范围，不能关闭 Issue #28 / `FB-P1-08` 或 24 小时稳定性门禁。

## 本轮本地验证快照（2026-10-18，默认设置 / 隐私分享 / 权限页回归）

- 分支：`product/experience-polish-20261018`；本轮源码与测试提交：`154a7cfab7da1c7924ae1acb53acd63a931c2626`。文档快照在后续单独提交，不改变 App 运行时源码。
- 下表只记录对应源码提交的本地工具链，不代替 hosted Actions 结果或签名发布包验收；本次 hosted run 单独记录如下。
- GitHub Actions 随后已在提交 `147a64567f56290c3b8c0dce0aa934dd2c3b494f` 执行 [run 39095168568](https://github.com/guo6x/flowbreak/actions/runs/39095168568)：`verify` job **PASS**，覆盖前端、UI smoke、双渠道 JVM/Lint、Debug、unsigned Release 与 provenance；体验分支上的签名 `release` job **SKIPPED**。这是 unsigned CI 证据，不是签名或商店验收。
- 原工作区 `D:\AI_code\flowbreak` 中的两处 Java 未提交修改不属于本分支。未读取或操作生产签名材料，没有合并或发布。

| 检查 | 结果与边界 |
| ---- | ---- |
| Frontend (Vitest) | **263 / 263 PASS**，25 个测试文件，无失败或跳过 |
| TypeScript / Vite | **PASS**（`npm run build`） |
| 网页逐页 / 交互 | **44 / 44 PASS**；13 条路由 × 320/393/1280px，加导航和合成统计、Web 休息控制/完成态 |
| 品牌一致性 | **36 / 36 PASS**（`npm run test:brand`） |
| Provenance / Release policy | **20 / 20 PASS**、**8 / 8 PASS** |
| Android JVM | Domestic **412 / 412 PASS**；Play **382 / 382 PASS**；强制重跑，0 failure / 0 error / 0 skipped |
| 双渠道 Lint | **PASS**；Domestic 0 errors / 45 warnings，Play 0 errors / 44 warnings |
| Web → Android 同步 | `npx cap sync android` **PASS**；随后两个渠道 Debug APK 均成功组装 |
| Domestic Debug APK | 5,186,928 bytes；SHA-256 `41de12d1eecbcd3778d72bafca3d0824d5d8fb720d17638e5e180464ffd4c64a` |
| Play Debug APK | 5,180,646 bytes；SHA-256 `dafc7391129c6db0e20153e90e170faad9799f95b791be52662fc91e2c138290` |
| API 36 AVD | **有限范围 PASS**：`Medium_Phone_API_36.0` / Android 16 / API 36；确认 `ro.boot.qemu=1`、`sys.boot_completed=1`，设备命令均使用 `-s emulator-5554` |
| AVD 原生权限 / 默认值 | Domestic APK `install -r` 成功并保留数据；偏好文件在安装前后只有 `migrationVersion=1`，无 `strongBlockingEnabled` 键。Accessibility 为 `null`，Usage Access 与 Overlay 未授权，保护服务未启动 |
| AVD 页面 / 返回 | Onboarding 正常渲染；跳过后权限页稳定显示 0/2。打开系统“使用情况访问”页再返回，仍为 0/2；未点击授权、未 wipe、未 `pm clear`、未卸载 |
| 崩溃检查 | 未见 `AndroidRuntime` fatal；WebView 有一条系统 variations seed 签名告警，不影响页面渲染 |
| Instrumentation / 真机 / 发布 | 没有运行 connected instrumentation、真实手机/OEM smoke、24h soak 或生产签名/发布 |

截图证据保存在工作区外：`D:\temp\flowbreak-avd-evidence-20261018\flowbreak-final-154a7cf-onboarding.png`、`flowbreak-final-154a7cf-permissions.png`、`flowbreak-final-154a7cf-settings-return.png`。这些截图是指定 AVD 的界面证据，不是 Redmi/OEM 真机验收。网页预览截图仍标记为 `WEB_PREVIEW_ONLY`。

Gradle 复核命令（cwd `app/android`）：

```powershell
./gradlew.bat --no-daemon --console=plain --rerun-tasks :app:testDomesticDebugUnitTest :app:testPlayDebugUnitTest
./gradlew.bat --no-daemon --console=plain :app:lintDomesticDebug :app:lintPlayDebug
./gradlew.bat --no-daemon --console=plain :app:assembleDomesticDebug :app:assemblePlayDebug
```

## 较早本地验证快照（2026-10-18，体验与品牌初始版本）

> 以下数字与 APK 属于前一份快照 `b7c40d2`，保留作历史对照；本页顶部的跟进快照代表当前体验分支验证。

- 分支：`product/experience-polish-20261018`；起点：`48aaf6a77ca6bae9f17f19e0210f5af3af745126`。
- 代码与测试提交：`b7c40d27e48b2ccf8e55f388ebedb3f17186c02d`。本表记录这份代码的本地检查；后续文档与 CI 维护未改变 App 运行时源码。云端结果必须单独核对 [Android Actions](https://github.com/guo6x/flowbreak/actions/workflows/android.yml) 的 run、source SHA 和结论，不把本表当作 hosted CI 的 PASS。
- 原工作区两处 Java 未提交修改未纳入本分支。没有操作真实用户数据、安装 APK、调用生产签名、合并分支或执行发布。

| 检查 | 结果与边界 |
| ---- | ---- |
| 全新依赖安装 | 普通 `npm ci --no-fund --no-audit` **PASS**，不依赖 legacy-peer-deps 配置 |
| Frontend | **262 / 262 PASS**，24 个文件，无跳过 |
| Provenance / Release policy | **20 / 20 PASS**、**8 / 8 PASS** |
| 品牌一致性 | **36 项 PASS**；同一 SVG 派生，浏览器与原生资源均在范围内 |
| 网页逐页 / 交互 | **44 项 PASS**，13 条路由 × 320/393/1280px，加导航和合成统计、Web 休息控制/完成态；无横向溢出、破图、资源失败、未处理异常或意外外网请求 |
| TypeScript / Vite | **PASS**（`npm run build`） |
| npm audit | 官方 registry 的全依赖、low 及以上门槛：**0 known vulnerabilities**（仅本次解析依赖的审计快照，不是永久安全保证） |
| dist → Android web assets | **48 / 48 文件 SHA-256 一致**；额外 2 个 Capacitor runtime 文件允许存在；provenance source SHA 为上方代码提交 |
| Play / Domestic JVM | **377 / 377 PASS**、**406 / 406 PASS**；无失败、错误或跳过 |
| 双渠道 Lint / Debug | **PASS**；Lint 为 0 error、Play 44 / Domestic 45 个 warning，不宣称零警告；最终网页同步后再次构建成功 |
| Instrumentation APK | `assemblePlayDebugAndroidTest` **ASSEMBLED**，没有执行 connected tests |
| Portable signing security regression | `TARGETED_TESTS=PASS`；secret 不进进程参数/日志，外部 archiver 被拒绝，创建/恢复工具 fail closed；**不是生产密钥恢复测试** |
| 新构建设备 / 签名 / 发布 | **NOT_EXECUTED**；未执行 connected instrumentation、Redmi smoke、24h 稳定性或生产发布 |

逐页 PNG、引导三步、统计空态/样本、休息退出确认与完成态已人工复核；本地证据在被 Git 忽略的 `.tmp/ui-smoke/`（`results.json` 明确标识 `WEB_PREVIEW_ONLY`）。样本记录只存在于一次性浏览器上下文，不是真实健康数据，也不作为原生保护有效的证据。云端 verify 会独立重跑网页检查并保存该 run 的截图。

本地 Debug 包（`versionCode=1` / `versionName=local-b7c40d2`，仅用于工程验证，不是签名发布候选）内部 `assets/public/build-provenance.json` 均已读取确认 source SHA 等于上方代码提交：

- Play APK SHA-256：`deceb59895097578c48828393b44d6e3286070777300ea67a672fe53fda8208a`。
- Domestic APK SHA-256：`d80fa7e5698dc7e4b9960f24148f2f0cdc1f96893fecf44054a42f365879613e`。
- 包位置：`app/android/app/build/outputs/apk/<channel>/debug/`，本地构建目录不入 Git。内部 provenance 校验与 dist→assets 校验是构建证据，不是安装运行证据。

在这份较早的 `b7c40d2` 快照中，没有关闭既有 Gate F/G/H/I，也没有将旧 Redmi 或 signed 证据改写成此分支的实机 PASS。签名工具文档中的旧能力描述已按当时的 fail-closed 源码修正；`FB-P2-03` 当时仍是待修复差异，后续处理与新证据见本页顶部跟进快照。

首次云端运行 [39062202344](https://github.com/guo6x/flowbreak/actions/runs/39062202344)（源码 `bbd95b23d70eea8ac786f0ecdbfaafa5d23015a4`）在 SDK 准备阶段失败：固定版本 setup-android 的默认值请求了 Google 已不再提供的 `tools` 包。后续产品测试均为 **SKIPPED，不是 PASS**。CI 随后显式请求 `platform-tools`，其余平台与 build-tools 由 Gradle 按项目声明解析；签名条件、密钥和发布门禁没有更改。修复后以新提交的独立 run 为准，不能复用该失败 run 冒充验证成功。

第二次运行 [39062409715](https://github.com/guo6x/flowbreak/actions/runs/39062409715)（源码 `353c3b2c26f6096f00630d3ea03b7514314a15e5`）通过 SDK、依赖、单测、品牌、安全回归和 Web build，网页检查仍为 **FAIL / 0 checks**：Vite 给端口插入 ANSI 样式，导致就绪字符串匹配超时。本地已用相同彩色日志环境复现「HTTP 200，但原字符串不匹配」；修复仅对就绪日志去除控制字符，仍保留端口空闲预检、strictPort、子进程状态与 HTTP 校验。修复后在 `CI=true` / `FORCE_COLOR=1` 且未设置 `NO_COLOR` 的本地环境重跑，**44 / 44 PASS**（`.tmp/ui-color/`）。这两次失败均保留原 run，不将其后跳过的 Android 步骤计为通过。

## 1. 自动化测试层级

| 层级 | 命令 | 覆盖 |
| ---- | ---- | ---- |
| Frontend | `cd app && npm test`（Vitest） | 页面、store、rest-mode、状态判定等 React/TS 逻辑 |
| 品牌一致性 | `cd app && npm run test:brand` | 单一 SVG 派生的浏览器图标、Android launcher/单色图标与启动图；PNG 按像素比较、文本忽略 CRLF 差异 |
| 网页视觉 / 交互 | `cd app && npm run build && npm run test:ui` | Chromium 的逐页身份、三种宽度、导航、统计样本、Web 休息流程、溢出与资源/网络检查；仅 `WEB_PREVIEW_ONLY` |
| Play JVM | `cd app/android && ./gradlew testPlayDebugUnitTest` | Robolectric：状态机、前台追踪、服务恢复、统计等（Play 渠道） |
| Domestic JVM | `./gradlew testDomesticDebugUnitTest` | 同上（Domestic 渠道，含无障碍强阻断回归测试） |
| RecoveryIntegration | 包含在上述 JVM 任务中 | 前台服务重建/重启恢复集成测试 |
| Room migrations | 包含在上述 JVM 任务中 | schema 1→2→3 迁移测试 |
| Build/Lint | `./gradlew lintPlayDebug lintDomesticDebug assemblePlayDebug assembleDomesticDebug` | 源码分析 + 双渠道 Debug 构建 |
| Release 构建 | `./gradlew bundlePlayRelease assembleDomesticRelease` | R8、资源压缩、Release Lint Vital（**未签名**产物） |

## 2. CI 做什么 / 不做什么

CI（`.github/workflows/android.yml`）在 master push / PR / 手动 dispatch 时执行 verify；签名 release job 仅允许 master dispatch 或发布 tag：

- 做：`npm ci`、npm audit 门禁（生产依赖 high 级 + 全依赖 critical 级）、`npm test`、`npm run test:brand`、`npm run test:provenance`、`npm run test:release`、签名安全回归、`npm run build`、安装 Chromium 后 `npm run test:ui`、`npx cap sync android`、双渠道单测 + lint + Debug 构建、`assemblePlayDebugAndroidTest`、Release 双渠道构建、R8 mapping 检查、Room schema drift 检查。
- 网页检查使用一次性合成数据，不连接真实手机；截图与 `results.json` 作为独立 `flowbreak-web-preview-<sourceSha>` artifact 保存（14 天）。即使后续步骤失败也尝试上传已有截图。空态和样本态的页面身份、请求错误均必须通过；截图仍需人工审美复核，不能由退出码代替。
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

### 2026-09-08 v1.1.0 PR #20 final-scope acceptance（GATE D）

- Merge / master：PR #20 已合并，master SHA = `2af5e93e88687cdb8b7903f270ae67380773a200`；合并后的 master verify Run `34227077948` **SUCCESS**。
- Signed candidate：workflow dispatch Run `34227560491` 的 verify 与 release 均 **SUCCESS**；artifact = `flowbreak-signed-dry-run-v1.1.0-2af5e93e88687cdb8b7903f270ae67380773a200`；Domestic APK SHA-256 = `2ee7d2147b8d19a4f83e45dd85bc5eb3ea61504ab87e875260fedcdc1b0b1875`；Domestic fingerprint = `8d69d1786ea63b05ff3b8d1f5a78266a2fa1eda7b823af0c295cbfdc10e77f20`。
- iQOO 设备：vivo `V2073A` / OriginOS 13.5 / Android 13 / SDK 33；对 `com.flowbreak.app.cn` 执行 `adb install -r` 成功，未清除或卸载数据。已有目标应用（5 个）、限额（15 分钟）及 Room v3 数据仍可见。
- Fail-closed 结果：`unsupportedDevice=true`、`protectionRuntimeAvailable=false`；Dashboard 与 Diagnostics 均如实显示设备不支持，启动保护控件不可用；Diagnostics 显示保护服务未检测到心跳，未观察到活动保护 FGS。`IQOO_FAIL_CLOSED=PASS`，`IQOO_SUPPORTED=NO`。
- Accessibility sanity：已启用 FlowBreak Accessibility；`tv.danmaku.bili` 在短时前台检查中保持可用，未观察到 HOME 动作、`BlockedTargetBanner` 或 `BlockActivity`。本阶段没有执行计时，因此 `S6–S13=NOT_RUN`，也没有重新调查 `FB-P1-05` 根因。
- Redmi：当前未连接 Redmi，未执行新 signed build smoke；既有 Redmi R1–R4 外部证据仍为 **PASS**，因此 `REDMI_SUPPORTED_REFERENCE=PASS`、`REDMI_NEW_BUILD_SMOKE=NOT_EXECUTED`。
- 安全边界：production keys、GitHub signing secrets、age vault、USB backup、signing policy 均未修改。原始设备证据仍保存在仓库外：`D:\AI_code\flowbreak-device-evidence\iqoo-signed-sanity\2026-09-08-final-scope-acceptance\`。

### 2026-09-11 Gate E supported-scope evidence model

Gate E 的当前结论区分健康运行时、执行间隔恢复和 Android/OEM 无执行时间三种语义；本节不把历史等价性证据写成一次新的完整物理复测。

- `E1_USAGE_ACCOUNTING = PASS_CARRIED_FORWARD_WITH_EQUIVALENCE`：依据为此前有效的 Redmi 真机 PASS（`54f6c54a313e96fb374514b2bb4bac8bfd689545`）与当前 master 的 normal-path equivalence。
- `E2A_HEALTHY_RUNTIME_BLOCKING_LATENCY = PASS_CARRIED_FORWARD_WITH_EQUIVALENCE`：依据为此前签名 Redmi 约 `361s` 进入 BLOCKED 的证据（`c79fa0...`）与当前 master 的 normal-path equivalence。
- `E2B_EXECUTION_GAP_RECOVERY = PASS_CURRENT_SIGNED_DEVICE_INTEGRATION`：当前签名 `ccacb6738a44d3bc08da9a4ceac75766316b2521` 已在 Redmi 上实际走过 reconciliation path，并恢复非零 machine session。
- `TEST_A_RECOVERY_SIGNAL = PASS`。
- `TEST_A_ACCOUNTING_PRECISION = INCONCLUSIVE`：精确 foreground transition timing 与 after-usage export 不完整，因此不能称为 exact accounting PASS；没有据此证明 overcount。
- `REALTIME_ENFORCEMENT_DURING_OS_EXECUTION_SUSPENSION = NOT_GUARANTEED_PLATFORM_LIMITATION`：当 Android/OEM 不给进程或 worker 执行时间时，实时 overlay 与状态迁移不属于 v1.1.0 保证。

当前 master 没有独立重新执行完整的 E1/E2 physical test sequence；本 Gate E 结论是 documented supported-scope evidence carry-forward，加上当前 signed execution-gap recovery integration。

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

### 版本与签名验证（GATE G，更新至 2026-09-08）

- **版本策略（VERSION_POLICY）**：versionName 唯一 Source of Truth = `app/package.json` version（stable SemVer，无 prerelease/build）；versionCode = `MAJOR*1_000_000 + MINOR*1_000 + PATCH`（1.1.0 → 1001000；MINOR/PATCH < 1000，总量 ≤ 2100000000），确定性、单调、与 CI run_number 无关；正式 tag 必须精确 = `v<versionName>`（`vfoo`/`v1`/`v1.1.0-test` 一律 FAIL）。`scripts/release-version.mjs` 输出 `VERSION_NAME`/`VERSION_CODE` 供 GITHUB_ENV；release job 构建前还校验 tag 指向的 SHA 是 origin/master 的 ancestor（`TAG_ON_MASTER_LINE=PASS`）。
- **签名身份（SIGNING_IDENTITY_DECISION = SEPARATE）**：Play = upload key（`FLOWBREAK_PLAY_*`），Domestic = app-signing key（`FLOWBREAK_DOMESTIC_*`）；build.gradle 按 flavor 条件启用，env 缺失时保持 unsigned（CI verify 不受影响）。
- **签名验证（CI release job）**：在 decode/build signing 前读取 `app/release-signing-policy.json`；`provisioningStatus != PROVISIONED` 输出 `SIGNING_POLICY_NOT_PROVISIONED` 并立即 fail closed，两个 certificate fingerprint 还必须是合法的 64 位 lowercase SHA-256。之后才执行 Domestic APK `apksigner verify --verbose --print-certs`、Play AAB `jarsigner -verify` + `keytool -printcert -jarfile` 与 allowlist 对照；不允许 debug key、旧 fingerprint 或自动生成 key fallback。
- **signed artifact provenance**：signed AAB/APK + mappings + build-provenance + artifact-manifest（含 signed/signingRole/certificateSha256）+ SHA256SUMS 一并上传；命名 `flowbreak-signed-v<version>-<sha>`（dry-run 前缀 `flowbreak-signed-dry-run-`）；`if: always()` 清理 `$RUNNER_TEMP` keystore。
- **TEST ONLY 密钥纪律**：Stage A 的本地机制验证曾使用临时 TEST ONLY 密钥（keytool 生成于临时目录、不进仓库、不作为正式 fingerprint）；CI signed dry-run 在 secrets 缺失时清晰失败，**不**降级用 debug key 冒充 production signing。
- **生产身份当前状态**：首轮 technical dry-run（Run `31934183213`）仅证明签名工程链路可用，身份已标记 **SUPERSEDED_PRE_PRODUCTION**，禁止用于正式发行。最终身份已在 primary workstation 生成；当前 policy 为 `PROVISIONED`，两个最终 public certificate fingerprint 已写入 allowlist。
- **CI signing credentials**：旧 identity 的 8 个 secrets 已清理；最终 identity 对应的 8 个 production signing secrets 已由 owner provision。secret values 未读取、未写入仓库，也未进入 artifact 或日志。
- **Portable vault 工具边界**：仓库中的 `tools/New-PortableSigningVault.ps1` / `tools/Test-PortableSigningVault.ps1` 当前在触碰密钥、policy、剪贴板或 secret 前 fail closed（`BLOCKED_PENDING_SAFE_TOOL`），不提供创建/解密/验收能力；外部 archiver fallback 也被拒绝。已建立的 age 托管记录来自 `app/release-signing-policy.json` 和独立证据，不是这些占位脚本的执行结果。安全回归只证明拒绝路径，不证明密钥恢复成功。
- **当前剩余项**：`v1.1.0` 正式 tag / store release 尚未执行；portable vault 本地恢复与 off-machine backup 已 PASS，但 `CROSS_MACHINE_SIGNING_RECOVERY=NOT_YET_TESTED`，因此 GATE G 仍保持 `PENDING_CROSS_MACHINE_RECOVERY`。

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

### v1.1.0 OEM 支持边界与 fail-closed 验收

- Gate D 的 v1.1.0 定义是「声明明确的支持范围 + 对已知不可靠设备 fail closed」，不是把所有尚未测试的 OEM 宣布为支持。
- vivo / iQOO（大小写与空白归一化后）属于 v1.1.0 明确不支持的设备族；Play 与 Domestic 必须一致阻断。
- fail-closed 原因必须是 `UNSUPPORTED_DEVICE_FOR_RELIABLE_MONITORING`，不得退化成电池优化或其他权限提示。
- 已有 active config、目标应用、限额与历史数据必须保留；诊断必须分别报告 `monitoringConfigured` 与 `protectionRuntimeAvailable`，不能把保存过配置伪装成当前保护可用。
- 最小回归覆盖：vivo/iQOO 检测、Xiaomi/Redmi/Samsung/OPPO/Honor 不误判、启动/配置重载/BootReceiver/服务入口均阻断，以及两渠道共用该边界。Redmi 参考设备保持支持证据。

### 尚未完成的设备验证

- 受支持范围内其他 OEM 的真机矩阵（Xiaomi / Redmi 之外的 OPPO / OnePlus、Honor / Huawei 等：权限、后台限制、重启、24 小时稳定性）；vivo / iQOO 不属于 v1.1.0 支持运行时矩阵，保留为 `FB-P1-05` 开放兼容性问题。
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
| 真机结论 | **PASSED**（R1–R4，2026-08-14；P0 = 0、supported-scope release-blocking P1 = 0；不包含后续 `FB-P1-05`） |

> 历史快照（`a06a772`，2026-08-12）：Frontend 148 / Play JVM 220 / Domestic JVM 220 / RecoveryIntegration 23 / Room 6，真机结论 FAILED。仅作历史对比，不得当作当前数字。
