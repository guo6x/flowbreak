# TESTING — 测试与验证

> 定义自动化测试层级、CI 范围、真机测试矩阵与证据标准。当前门禁和易变 HEAD/CI 状态以 [CURRENT_STATUS.md](CURRENT_STATUS.md) 为准；本文件保留本轮明确绑定到源码 SHA 或 workflow Run 的验证证据。较早的 PR #29、PR #42、体验分支和自动化计数已集中至 [历史验证快照](archive/progress/verification-snapshots-through-20261025.md)；Beta 1.1 记录另见 [VERIFICATION.md](archive/progress/VERIFICATION.md)。

## 现行验收范围（2026-10-25）

负责人已移除固定 24 小时连续运行验收，`FIXED_24H_ACCEPTANCE=NOT_REQUIRED`；稳定性改为日常使用反馈，不设置最低时长，不要求提供日常 Redmi。核心计时/提醒/休息/恢复、安全关闭、状态真实性与隐私仍要复验。以下历史快照中的 `24h NOT_EXECUTED` 不改成 PASS，也不再作为当前待补的固定时长任务。取消要求不关闭 `FB-P1-08` / `FB-P1-05`，不把模拟器结果升级为真机证据。

## 本轮代码、页面与工程复验（2026-10-25）

源码/测试锚点：`a15b54d5e88ea59245a724c3cb29eb4820583ca9`，基于 `edf5def5af41e3daa9eceb318c55b8ada8bfd93f`。后继资料提交不改变运行时代码；本地检查仅证明列明范围，最终推送 HEAD 的 hosted 结果以 [PR #29 checks](https://github.com/guo6x/flowbreak/pull/29/checks) 精确 SHA 为准。历史 Run 不认证新 HEAD。

| 检查 | 本轮结果 |
| --- | --- |
| Vitest / TypeScript / Vite | **336/336 PASS**，28 files；`npm run build` PASS。新增测试包括 support mailto、慢查询单飞/快速 reject 后等待其他查询、无激活/无 API/抛错振动路径。测试环境已有 act/Recharts/AudioContext 提示与真实浏览器 console gate 分开记录，不称测试 stderr 为空。 |
| UI smoke | **45/45 PASS — WEB_PREVIEW_ONLY**；13 路由 × 320/393/1280px，另含导航、合成统计、Web 休息操作与完成态、小屏退出按钮可达性；0 `pageErrors`、0 `consoleErrors`、0 非本地请求、0 失败资源。帮助/隐私的六个视口场景均只包含精确无 query 的支持 mailto。 |
| Play / Domestic JVM | **495/495 / 528/528 PASS**；49/50 suites，JUnit XML 0 failure、0 error、0 skipped。新增 Activity 未附着时的异步成功/拒绝/延迟四项，以及 vivo/iQOO 保存而不调起服务两项。 |
| Lint / Debug / AndroidTest assemble | Play/Domestic Lint 均无 error/fatal，44/45 warnings；两个渠道 Debug 和 AndroidTest APK 均组装 PASS。仅组装不表示 instrumentation 已执行；实际安装执行另记。 |
| Brand / Provenance / Release / Privacy static | **36/36 / 20/20 / 8/8 / 15/15 PASS**；第一方静态审计 143 files / 0 violations，声明 INTERNET；不能据此推出最终二进制零联网。 |
| Portable signing security | **8/8 PASS**；测试密码不入 argv/log、不可信归档器被拒、无安全后端则 fail closed；没有读取或恢复实际生产 vault，不关闭 Gate G。 |
| 官方 npm audit（本地） | 显式 `--registry=https://registry.npmjs.org`，`--omit=dev --audit-level=high` 与 `--audit-level=critical` 都为 **0 vulnerabilities**。依赖文件未因本轮发生修改。 |
| 默认镜像 npm audit（历史环境阻塞） | `https://registry.npmmirror.com` 的 `404 / NOT_IMPLEMENTED` 保持 **BLOCKED / NOT_PASS**；官方 registry 的成功不修复镜像审计端点。 |

回归可信度记录：单飞测试在修复前确实出现 expected 1 / actual 6，普通慢请求与快速 reject+慢 sibling 两种路径均做了 red→green；目标保存测试在门禁修复前确实调起了不支持设备的服务。UI 新鲜度负测拒绝旧 dist，v2 新增 console gate 抓到无手势振动拦截（44 页面断言通过但总结果 FAIL）；修复后 v3 才是 45/45 与 0 console error。最初 TypeScript 对测试中直接删除 Navigator 非可选字段报错，改用 `Reflect.deleteProperty` 后 build 通过，未放宽编译或浏览器门禁。

最终页面证据：`C:\WINDOWS\TEMP\flowbreak-ui-smoke-20261025-final-v3\results.json` 及 53 张截图。320×740 REST 容器 `scrollHeight=767/clientHeight=740`；滚动后按钮 top/bottom 为 `691.69/739.69`、左右 `20/300`，完整可达。保留已有内层滚动，无需布局补丁；初始首屏裁边不记作不可达 P2。截图与合成统计不是 Android/OEM 验收或商店最终取材。

历史离线集成简报曾在 320/393/1280px **3/3 PASS**，不计入产品 45/45 或真机结果；该简报仅重复状态，已在文档清理中移除，原文和修正可从 Git 历史恢复，不作为现行入口继续维护。

本轮 Android 构建曾因 `cap sync` 与 Gradle 同时操作生成目录而失败（EBUSY / missing generated build.gradle），该失败不记 PASS；随后串行同步并重跑双渠道矩阵成功。最终以如下源码绑定工件为准，不能用先前并行组装的 APK 冒充最终工件。

### 最终源码绑定构建与 unsigned 工件

- 源码 `a15b54d5e88ea59245a724c3cb29eb4820583ca9`；本地工程版本 `versionCode=2` / `versionName=ci-local-a15b54d`，不表示正式发布版本。`SOURCE_COMMIT_SHA` 与 `GITHUB_SHA` 均绑定该完整 SHA；只允许资料文档 WIP，不把工作树说成全 clean。
- 顺序执行 Web build → provenance → `cap sync` → Web asset sync，`dist → Android assets` **51/51 SHA-256 一致**（另有 2 个 Capacitor 生成文件）。使用 JDK 21 / Gradle 8.13，最终同批 `testPlayDebugUnitTest testDomesticDebugUnitTest lintPlayDebug lintDomesticDebug assemblePlayDebug assembleDomesticDebug assemblePlayDebugAndroidTest assembleDomesticDebugAndroidTest bundlePlayRelease assembleDomesticRelease` 全部退出 0（395 tasks，5m49s）。JUnit/Lint 最终 XML 的数量与上表一致；Room schema 无新差异。
- 之后串行生成两渠道 `ReleaseRuntimeClasspath` inventory；Play AAB 与 Domestic APK 的内嵌 provenance、从二进制独立读取的包名/版本、合并 Manifest、R8 mapping、runtime 清单和 unsigned 状态均复核 PASS。Play 不含无障碍服务，Domestic 包含；两者均声明 FGS specialUse。没有读取、使用生产签名或恢复实际密钥。
- `artifact-manifest.json` 中两项 binary 均为 `signed=false`；`SHA256SUMS.txt` **7/7 独立复算一致**。本地 verifier 首次因 PowerShell 续行失败；修正本地 helper、显式调用 ZIP 扩展 API，并识别 apksigner 的精确 unsigned 输出后完整通过。未放宽生产源码、编译或发布门禁。

| 本地 unsigned 工件 | SHA-256 |
| --- | --- |
| Play AAB | `17a515266f5c7ff02010688c7a14cf04365ddf4161339c51bbce4ccb0476037f` |
| Domestic APK | `fe9ea1679bda146af6b88b1009437b040278b5b3f8d8c3f25a921983cca8d2de` |
| artifact-manifest.json | `bfd6642c949d75cb38b3240e167ebe9a3ed9fee95212cae37a63fbf3cae1a7e6` |

本地证据目录：`.tmp/local-release-verification/a15b54d5e88ea59245a724c3cb29eb4820583ca9-20261025T062714Z-e5791e36fb8d4cd794c5431f9d8709d3/`。这些不是签名发布包，也不是最终文档 HEAD 的 hosted artifact；最终推送必须独立确认对应 SHA 的云端检查与产物。

### API 36 AVD 安装身份复验（SIMULATOR_ONLY）

- `Medium_Phone_API_36.0`，Android 16 / API 36，显式 serial `emulator-5554`；每次安装、拉包和 instrumentation 前确认 `ro.kernel.qemu=1`、`sys.boot_completed=1`。未枚举或操作真实手机，没有 wipe、uninstall、clear、降级或权限变更。
- 同批最终 Debug / AndroidTest APK 用 `install -r` 安装，Play `com.flowbreak.app` 和 Domestic `com.flowbreak.app.cn` 均成功；只执行 `ApplicationIdentityInstrumentedTest`，两渠道各 **2/2 PASS**（实际包名、FGS 存在且不 exported、Accessibility 仅 Domestic 存在并受正确权限保护）。没有把其他 instrumentation 场景记为已执行。
- `pm path` 唯一定位已安装 base APK 并拉取逐一对账：Play SHA-256 `7a6968de18fa1ae0c15f4c7e1accfaafcb12a27a24ea47f3ebb4e6a16885027c`；Domestic `2d051f404d5347f2b9bea4383428cc9c2159945a209da982f945d1629dd5251c`；均与本地同批源码绑定 Debug APK 相同。这些是 Debug 工件，不与上表 unsigned Release 混用。
- 输出与安装回执保存在 `.tmp/avd-identity-20261025T062717Z-adf4fd4cc8ab494f87c5a4be1981d0d3/`，不入 Git。该检查不证明任意 OEM 的计时、阻断、后台恢复、签名升级或 Beta 结果；**Gate F 仍 FAIL，Gate G/H/I 仍 PENDING**。
- 核验后仅关闭本轮启动的 `emulator-5554`，保留模拟器数据，不停止其他项目进程或 ADB 服务。

### 已推送 HEAD 的独立云端复验快照（2026-10-25）

- 精确源码：`dca4f205412b17cd34d414d879d6857a21b16ae2`；[GitHub Actions Run `40170229748`](https://github.com/guo6x/flowbreak/actions/runs/40170229748) 的 `verify=SUCCESS`、`release=SKIPPED`。后者是 PR 签名策略的预期跳过，不表示完成签名或发布验收。
- 云端 unsigned artifact：`flowbreak-unsigned-dca4f205412b17cd34d414d879d6857a21b16ae2`，[artifact ID `12589874425`](https://github.com/guo6x/flowbreak/actions/runs/40170229748/artifacts/12589874425)。下载后独立复算 `SHA256SUMS.txt` **7/7 匹配**；Play AAB 与 Domestic APK 的内嵌 provenance **2/2 匹配该完整源码 SHA**，版本与清单一致，均为 `signed=false`。
- 该 Run 的 production-high / all-critical npm audit（含回归后的两项复跑）均报告 **0 vulnerabilities**。这是 GitHub CI 对该精确 SHA 的结果，不是本地镜像的审计通过。
- 本快照仅认证该 SHA 和该 Run 的工程范围，不将本地 `a15b54d…` 工件冒充云端包，也不关闭 Gate F/G/H/I。后继文档清理 HEAD 仍须核对其自身成功的 workflow 和 artifact；不能借此历史绿色 Run 认证新 HEAD。

### 文档清理与工程试用交付复核（2026-10-25）

本批不改运行时代码、依赖或 CI。对上述 `a15b54d…` 同批 Debug APK 再次运行 apksigner：两渠道 v2 签名 PASS，证书均为 `C=US, O=Android, CN=Android Debug`，证书 SHA-256 为 `dcabfd7e9af8c996b48475269849ad6185d670bbb93c6212d924db9a24ed11a7`；不是正式证书。独立读取包名、`versionCode=2` / `versionName=ci-local-a15b54d`、最低 API24 和包内 provenance，复制到仓库外日期交付目录后 **2/2 hash 一致**，值与上方 AVD 拉包记录相同。

清理后本地再次复跑：Vitest **336/336**（28 files，单 worker，265.63s）、品牌 **36/36**、Provenance **20/20**、Release policy **8/8**、隐私静态 **15/15** 全部通过；仍是 143 个第一方输入、0 静态违规。全仓 38 份现存 Markdown 的 **121/121 相对链接有效**，Git 暂存差异空白检查通过。独立文档复审发现并修正历史取证方案被误称现行协议、已解决 iQOO 旧门禁被误写为当前支持等表述，未改变缺陷裁定。

交付文件为 `FlowBreak-domestic-trial-a15b54d.apk`、`FlowBreak-play-trial-a15b54d.apk`，另附中文安装/限制/反馈说明、`trial-manifest.json` 和 `SHA256SUMS.txt`；不把 APK 或签名材料加入 Git。此批只是知情自愿工程试用，不是生产签名、真实设备复验、无损升级承诺或 Gate I 通过。

## 历史快照入口

旧 PR #29 首轮、PR #42、体验分支（含早期失败 Run）及 `99fdcc2` 自动化计数集中保存在 [历史验证快照](archive/progress/verification-snapshots-through-20261025.md)，不在本页重复；其中 FAIL、NOT_EXECUTED、SKIPPED 和镜像审计 BLOCKED 仍保留原有含义。下文带日期的 OEM 记录保留为支持范围与证据模型的历史参考，不认证本轮候选。

## 1. 自动化测试层级

| 层级 | 命令 | 覆盖 |
| ---- | ---- | ---- |
| Frontend | `cd app && npm test`（Vitest） | 页面、store、rest-mode、状态判定等 React/TS 逻辑 |
| 品牌一致性 | `cd app && npm run test:brand` | 单一 SVG 派生的浏览器图标、Android launcher/单色图标与启动图；PNG 按像素比较、文本忽略 CRLF 差异 |
| 网页视觉 / 交互 | `cd app && npm run build && npm run test:ui` | Chromium 的逐页身份、三种宽度、导航、统计样本、Web 休息流程、溢出与资源/网络检查；仅 `WEB_PREVIEW_ONLY` |
| Play JVM | `cd app/android && ./gradlew testPlayDebugUnitTest` | Robolectric：状态机、前台追踪、服务恢复、统计等（Play 渠道） |
| Domestic JVM | `./gradlew testDomesticDebugUnitTest` | 同上（Domestic 渠道，含无障碍强阻断回归测试） |
| RecoveryIntegration | 包含在上述 JVM 任务中 | 前台服务重建/重启恢复集成测试 |
| Room migrations | 包含在上述 JVM 任务中 | schema 1→2→3→4 升级与幂等重放测试 |
| Build/Lint | `./gradlew lintPlayDebug lintDomesticDebug assemblePlayDebug assembleDomesticDebug` | 源码分析 + 双渠道 Debug 构建 |
| AndroidTest APK 组装 | `./gradlew assemblePlayDebugAndroidTest assembleDomesticDebugAndroidTest` | 仅组装 Play / Domestic instrumentation APK，不执行 connected tests |
| Release 构建 | `./gradlew bundlePlayRelease assembleDomesticRelease` | R8、资源压缩、Release Lint Vital（**未签名**产物） |

## 2. CI 做什么 / 不做什么

CI（`.github/workflows/android.yml`）在 master push / PR / 手动 dispatch 时执行 verify；签名 release job 仅允许 master dispatch 或发布 tag：

- 做：`npm ci`、npm audit 门禁（生产依赖 high 级 + 全依赖 critical 级）、`npm test`、`npm run test:brand`、`npm run test:provenance`、`npm run test:release`、`npm run test:privacy-static`、签名安全回归、`npm run build`、安装 Chromium 后 `npm run test:ui`、`npx cap sync android`、双渠道单测 + lint + Debug 构建、`assemblePlayDebugAndroidTest` / `assembleDomesticDebugAndroidTest`、Release 双渠道构建、R8 mapping 检查、Room schema drift 检查。
- 网页检查使用一次性合成数据，不连接真实手机；截图与 `results.json` 作为独立 `flowbreak-web-preview-<sourceSha>` artifact 保存（14 天）。即使后续步骤失败也尝试上传已有截图。空态和样本态的页面身份、请求错误均必须通过；截图仍需人工审美复核，不能由退出码代替。
- 做（Artifact Provenance 链，GATE C）：source identity 校验（checkout HEAD = `SOURCE_COMMIT_SHA`、工作区干净）→ 生成 `dist/build-provenance.json` → cap sync → **Web asset sync 校验**（dist 每个文件在 Android assets 中 SHA-256 一致，缺失/mismatch 直接 FAIL）→ Release 构建 → **APK/AAB 内部 provenance 校验**（unzip + `aapt2 dump badging` + AAB 自身 manifest 经 wrapper zip + `aapt2 dump xmltree` 读版本）→ `artifact-manifest.json` + `SHA256SUMS.txt` → upload-artifact（`flowbreak-unsigned-<sourceSha>`，retention 90 天）。
- 不做：**不执行 connected instrumentation tests**。`assemblePlayDebugAndroidTest` / `assembleDomesticDebugAndroidTest` 只是构建两渠道 instrumentation APK（AndroidTest APK successfully ASSEMBLED），**不等于**在设备上执行测试；本页列明的 AVD 身份检查是独立的本地模拟器证据。
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

### 2026-08-14 Redmi R1–R4 复测记录（历史参考设备证据）

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
- 本次 OEM / 核心场景短测在正常路径之外同时做失效注入，例如：
  - 撤销 Usage Access
  - 撤销 Overlay
  - Domestic 关闭 Accessibility
  - service 被系统杀
  - battery saver / OEM 后台限制
  - force-stop 后重新手动打开
- 每项至少验证：A. 实际保护行为；B. 用户界面是否如实反映能力。
- force-stop 语义：force-stop 后**不要求** App 自动复活。正确测试：force-stop → 用户手动重新打开 FlowBreak → UI 不得谎称旧保护状态仍完全有效 → 合法恢复保护。
- 日常使用反馈不仅关注崩溃，也关注「**无 silent protection drift**」（保护能力无无声漂移）；不要求固定连续运行时长，出现异常按实际版本和现象登记。

### v1.1.0 OEM 支持边界与 fail-closed 验收

- Gate D 的 v1.1.0 定义是「声明明确的支持范围 + 对已知不可靠设备 fail closed」，不是把所有尚未测试的 OEM 宣布为支持。
- vivo / iQOO（大小写与空白归一化后）属于 v1.1.0 明确不支持的设备族；Play 与 Domestic 必须一致阻断。
- fail-closed 原因必须是 `UNSUPPORTED_DEVICE_FOR_RELIABLE_MONITORING`，不得退化成电池优化或其他权限提示。
- 已有 active config、目标应用、限额与历史数据必须保留；诊断必须分别报告 `monitoringConfigured` 与 `protectionRuntimeAvailable`，不能把保存过配置伪装成当前保护可用。
- 最小回归覆盖：vivo/iQOO 检测、Xiaomi/Redmi/Samsung/OPPO/Honor 不误判、启动/配置重载/BootReceiver/服务入口均阻断，以及两渠道共用该边界。Redmi 参考设备保持支持证据。

### 尚未完成的设备验证

- 本次兼容候选的真机矩阵（权限、后台限制、计时、重启与恢复）；不预设必须提供某一部 Redmi。当前 vivo/iQOO 仍被代码拒绝启动，`FB-P1-05` 修复前不得宣称已支持；未来结果按实际机型/系统/候选记录，未覆盖品牌不宣称全面兼容。
- 与系统数字健康误差 ≤10% 的多设备对照（UsageStats 精度）。
- 阻断触发延迟 ≤2s 实测（blocking latency）。
- Protection Integrity 核心短测（含上述失效注入）及日常使用反馈；固定 24 小时任务已取消。
- 微信视频号识别真机专项。
- 小规模 Beta。
