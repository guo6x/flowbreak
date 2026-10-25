# 历史验证快照（截至 2026-10-25）

> **历史归档，不代表当前候选或当前门禁通过。** 本文件从现行测试指南移出旧 PR #29、PR #42、体验分支及 2026-08-14 自动化计数，保留各自原始源码、Run、产物和失败边界。当前方法与验证入口见 [TESTING.md](../../TESTING.md)，当前候选及发布状态见 [CURRENT_STATUS.md](../../CURRENT_STATUS.md) 和 [RELEASE.md](../../RELEASE.md)。
> 原记录中的“当前”“本轮”“本页顶部”等只指当时的记录批次，不认证后继源码；旧 `FAIL`、`NOT_EXECUTED`、`SKIPPED`、`BLOCKED` 不改写成 `PASS`。负责人已取消固定 24 小时验收，旧未测仍仅是历史事实。

## PR #29 先前本地与首个推送候选验证快照（2026-10-25；历史基线）

本节固定记录本轮增量之前的源码、测试与产物；“未再变化”“docs-only”等描述仅指该历史批次，不表示本轮未修改运行时代码，也不能覆盖后继 SHA 的独立验证。

- 前序候选代码 `fd9045a3b916486588b93e477935070e640440e5` 之后，推送前远端基线为 `29573ee115ee8ba25120a3b61ce7ae87b0535fde`，Run `40145072882` 只验证该 SHA。本地代码/测试提交为 `0996ca32b8cc471236b8724665dd32a683a72067`，包含 `RestMode.tsx` 超时后原生状态对账/拒绝回执 token 校验，以及 v3→v4 Room 迁移幂等键回填与回归测试。首个推送候选 `1070f920adae83fd44e2e4ee04ca7e5109d8f89b` 的 hosted Run `40151649558` 已全绿：`verify=SUCCESS`、PR release job 按策略 `SKIPPED`；本地运行时代码未再变化。本次文档补记该 Run，不改变运行时代码；新文档版本的 hosted 结论以其自身 SHA 对应的 workflow/artifact 为准。目标分支 `fix/fb-p1-08-protection-integrity`，未合并 master。
- 本地自动化（代码提交 `0996ca3…`）：Vitest **326/326 PASS**（28 files；Windows 单 worker）；TypeScript/Vite build **PASS**；UI smoke **44/44 PASS**（`WEB_PREVIEW_ONLY`）；brand **36/36**；provenance **20/20**；release policy **8/8**；privacy static **15/15**；`Test-SEC-PORTABILITY-001.ps1` **PASS**。
- Android：无过滤条件强制复跑 `testPlayDebugUnitTest` **489/489**、`testDomesticDebugUnitTest` **522/522**，JUnit XML 汇总 0 failure / 0 error / 0 skipped；双渠道 lint 无 error/fatal（Play 44、Domestic 45 warnings），Debug、Play AndroidTest APK 与 unsigned Release build **PASS**。REST 时钟恢复/请求 token 回归、Room v4 v3→v4 历史幂等键迁移（含重复、畸形 metadata）均包含在 JVM 测试中。AndroidTest APK 仅表示组装成功，不代表 instrumentation 已在真机运行。
- 本地产物溯源：source `0996ca32b8cc471236b8724665dd32a683a72067` 的 Web asset sync **50/50 SHA-256 匹配**；Play AAB 与 Domestic APK 内 provenance 均精确绑定该 SHA，`versionCode=1` / `versionName=ci-local-0996ca3` 经 provenance 和二进制 manifest 独立复核；双渠道 R8 mapping、runtime dependency inventory、unsigned `artifact-manifest.json` 与 `SHA256SUMS.txt` 均生成并复核。包只用于本地工程验收，不是签名发布包。
- 本地 unsigned 工件指纹（source `0996ca32b8cc471236b8724665dd32a683a72067`，`versionCode=1` / `versionName=ci-local-0996ca3`，均 `signed=false`）：Play AAB SHA-256 `0f7ec19b59cd7a04ba23f875610a6fede54fe3aec5ee9e98116a68af844f16f8`；Domestic APK SHA-256 `7a2f10317b7b2fc26d356f8bae82bb77503f014dc4233f7b796d88fc75d9aeca`；`artifact-manifest.json` SHA-256 `64a63e8ad847eb6f37ff48a637ec32506942b6d84fc72f4de168b6672b5ed7d3`。`SHA256SUMS.txt` 7/7 条目逐个复算一致；AAB 的 `jarsigner` 与 APK 的 `apksigner` 均确认未签名。这些不是可发布包或 GitHub artifact。
- Hosted unsigned 工件：旧基线 Run `40145072882` 上传 artifact ID `12580044816`（source `29573ee…`）。首个收口候选 Run `40151649558` 上传 artifact ID `12582807009`（source `1070f920…`）；下载后 `artifact-manifest.json`、内部 `build-provenance.json`、两项 binary 均精确绑定该 SHA，`SHA256SUMS.txt` **7/7** 匹配，Play AAB 与 Domestic APK 均 `signed=false`。这是工程构建证据，不是签名/发布验收。
- npm audit 来源必须分开：本地官方 `https://registry.npmjs.org` 的生产依赖 high 与全依赖 critical 审计均 **PASS — 0 vulnerabilities**；本地默认镜像 `https://registry.npmmirror.com` 的两项审计均因 endpoint `404 / NOT_IMPLEMENTED` 而 **BLOCKED / NOT_PASS**。GitHub Actions Run `40151649558` / SHA `1070f920…` 的生产依赖、全依赖 critical 及回归后复跑均报告 **0 vulnerabilities**；早先基线 Run `40145072882` / SHA `29573ee…` 也为 0。Hosted 结果不覆盖本地镜像阻塞。
- 旧 Run `38053414311` 只属于 PR #29 旧 HEAD `b00b9cc75073e77759e28ba5b71f960033cbf1d5`：其生产依赖 audit 对 `@capacitor/android` 受影响版本 8.0.0–8.3.4 报告 1 个 critical（GHSA-rvm3-566m-v7fv），因此 `verify=FAILURE` / `release=SKIPPED`。不得把该旧失败归属到新 SHA；后续 docs-only HEAD 的 hosted 结论以 [PR #29 checks](https://github.com/guo6x/flowbreak/pull/29/checks) 显示的精确 SHA 为准。
- iQOO V2073A / Android 13 当前被 vivo/iQOO 品牌门禁拒绝启动。此前旧候选安装返回 `INSTALL_FAILED_ABORTED: User rejected permissions`，无法仅凭文字判断拒绝来源；安装未完成、未启动或改变权限，既有 `com.flowbreak.app.cn` 未触碰。本轮没有设备安装/运行证据。兼容修复与复验纳入本次收尾，安全拒绝不算支持验证。
- Gate F **仍 FAIL**：历史约 33 秒问题与当前候选核心状态的设备复验未完成；固定 24 小时要求已移除，不再限定必须 Redmi。AVD/JVM/静态审计不等于未执行的设备场景通过。Gate G/H/I 也保持未完成，详见 [RELEASE.md](../../RELEASE.md)。

| 检查 | 结果与边界 |
| ---- | ---- |
| npm audit（官方 registry，本地候选） | **PASS — 0 vulnerabilities**；生产依赖 high 与全依赖 critical 两项门槛 |
| npm audit（配置镜像，本地） | **BLOCKED / NOT_PASS** — `404 / NOT_IMPLEMENTED`，见上文 |
| GitHub CI npm audit（PR #29 收口候选） | Run `40151649558` / SHA `1070f920…`：生产依赖与全依赖 critical（含回归后复跑）均 **0 vulnerabilities**；不是本地镜像审计结果 |
| PR #29 hosted CI（首个收口候选） | Run `40151649558` / SHA `1070f920…`：`verify=SUCCESS`，`release=SKIPPED`（PR 策略）；后续 docs-only HEAD 仍须以 PR checks 的精确 SHA/Run 验证 |
| GitHub CI npm audit（早先基线） | Run `40145072882` / SHA `29573ee…`：生产依赖与全依赖 critical 均 **0 vulnerabilities**；不覆盖本次候选 |
| GitHub CI npm audit（PR #42 历史） | Run `39301143402` 对 source `f7abbad7…` 报告 **0 vulnerabilities**；这是另一 PR/历史 SHA 的独立证据 |
| Android device smoke | **NOT EXECUTED（本候选）** — 旧候选 iQOO 安装曾返回 `INSTALL_FAILED_ABORTED: User rejected permissions`；不推断拒绝来源；未成功安装/启动、未改权限，旧 `.cn` 应用未触碰 |
| Fixed 24h acceptance | **NOT_REQUIRED** — 负责人于 2026-10-25 移除；历史 NOT_EXECUTED 不改成 PASS |
| Protection Integrity / Gate F | **FAIL / NOT_CLOSED** — 核心状态与真实失败仍需复验，不指定必须 Redmi |

## PR #42 历史集成验证快照（2026-10-23；不是当前候选）

- 集成来源：`integration/experience-quality-20261019`；功能修复源 `f7abbad7e900b32db5291cf26754cbd492721948`（更早功能基线 `64d2e42d675b0f6ef757e1084d68d285c1ac4daa`）；合并审查起点为 `origin/master=695b11f0fe68e4260864c638fda51b949c3a04f8`。
- 本次审查修补前最后已验证 HEAD 为 `afe404fe094bb3968f8ef16a372cb09131b8a3f7`，仅文档有变化；hosted verify [Run `39303124166`](https://github.com/guo6x/flowbreak/actions/runs/39303124166) **PASS**，release 按策略 **SKIPPED**。它才是当时的 PR HEAD，不能继续把 `f7abbad7…` 称为当前 HEAD。
- 下表测试与二进制哈希保留为历史功能修复源 `f7abbad7…` / [Run `39301143402`](https://github.com/guo6x/flowbreak/actions/runs/39301143402) 的固定快照。2026-10-23 的审查/REST 恢复逻辑跟进不能复用旧 Run 冒充新验证；新 HEAD 和精确 CI、合并状态以 [PR #42 checks](https://github.com/guo6x/flowbreak/pull/42/checks) 及 [master Actions](https://github.com/guo6x/flowbreak/actions/workflows/android.yml?query=branch%3Amaster) 为准。
- 目标状态仍为 **`INTEGRATION_ENGINEERING_READY / DEVICE_ACCEPTANCE_PENDING`**。常规工程合并不代表正式发布；模拟器、JVM、静态检查和 unsigned 产物都不能关闭真实设备 Gate F。

| 检查 | 结果与边界 |
| ---- | ---- |
| Frontend (Vitest) | **291 / 291 PASS**，27 个测试文件；覆盖设置/受限应用保存后服务动作失败的真实状态对账；Windows 复核使用单 worker，避免并行 fork 启动超时 |
| TypeScript / Vite | **PASS**（`npm run build`） |
| 网页逐页 / 交互 | **44 / 44 PASS**；涵盖窄屏、导航、统计图、Web 休息控制和完成态 |
| 品牌一致性 | **36 / 36 PASS**（`npm run test:brand`） |
| Provenance / Release policy | **20 / 20 PASS**、**8 / 8 PASS** |
| Privacy static | **15 / 15 PASS**；静态网络与 SDK 清单检查通过 |
| Android JVM | Domestic **489 / 489 PASS**；Play **456 / 456 PASS**；当前候选 JUnit XML 汇总为 0 failure / 0 error / 0 skipped（hosted CI 同一命令双渠道执行并通过） |
| 双渠道 Lint | **PASS**；0 error（warnings 仅为既有工具链提示） |
| 双渠道 Debug / unsigned Release | **PASS**；Play AAB 与 Domestic unsigned Release APK 均成功生成，R8/Room/Manifest 检查通过 |
| Web → Android 同步 | **49 / 49 PASS**；Capacitor Web 资产与 Android assets SHA-256 一致 |
| unsigned 产物 provenance | hosted Run `39301143402` / source `f7abbad7…`：[artifact](https://github.com/guo6x/flowbreak/actions/runs/39301143402/artifacts/12211868639)；Play AAB `989af971a7683ba615fca335335c44b51acbf6e96c97fc8ef15aca995c0c796`；Domestic unsigned Release APK `cc8e9f01cb11d41ef58ff25751863496ab47bb36a82e74069216d361d488376e`；`versionCode=1` / `versionName=ci-2a7b01cceecf46031ec5ea04ac43b467fd280453`。本地 `1.1.0` unsigned 产物仅为功能基线/AVD 使用 |
| npm audit（GitHub CI） | **PASS — 0 vulnerabilities**；生产依赖 high、全依赖 critical，以及 build 后回归重跑均返回 `found 0 vulnerabilities` |
| npm audit（本地环境） | **BLOCKED / NOT_PASS**；当前配置的 `https://registry.npmmirror.com` 返回 404/`NOT_IMPLEMENTED`，不能把本地镜像阻塞写成安全审计通过；该状态不覆盖 hosted CI 结果 |
| API 36 AVD 启动与页面 | **受控范围 PASS**：`Medium_Phone_API_36.0` / Android 16 / API 36 / `emulator-5554`；Domestic debug 安装后启动，Onboarding、权限页、个性化、目标应用、首页、统计、个人中心、阻断设置、诊断均已截图 |
| AVD 权限往返 | 初始权限页 **0/2**；Usage Access 与 Display over other apps 系统页可打开并返回，随后仅在模拟器上通过 app-op 受控授予进行后续页面 smoke；未触碰真实手机、未读取签名材料 |
| AVD 诊断与服务 | 诊断页显示服务心跳、监控线程、设备支持、已配置、关键权限、Room v3 和 1 个目标应用；服务 `isForeground=true`。这是模拟器证据，不是 Redmi/HyperOS 或 24h 稳定性证据 |
| 崩溃检查 | 本次筛选日志未见 `FATAL EXCEPTION` / `AndroidRuntime` fatal；WebView variations seed 告警属于系统环境提示 |
| Instrumentation / 真机 / 发布 | 未运行 connected instrumentation；无真实手机/OEM smoke、24h soak、生产签名、商店审核或正式发布 |

AVD 截图与脱敏日志保存在工作区外：`D:\temp\flowbreak-avd-evidence-20261019`。该目录中的截图仅证明指定 API 36 模拟器的受控页面和权限状态，不能转译为 Gate F/G/H/I 通过。

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
