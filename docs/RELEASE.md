# RELEASE — 发布门禁与发布状态

> 本文定义发行门禁与操作边界。当前候选、精确 HEAD/CI 和门禁裁定见 [CURRENT_STATUS.md](CURRENT_STATUS.md)；源码、测试及工件证据见 [TESTING.md](TESTING.md)。历史记录不认证后继候选。

## 当前状态与试用边界

**RELEASE PREPARATION · NOT APPROVED**；`STORE_READY=NO`，`PRODUCTION_RELEASE_APPROVED=NO`。本次为文档与仓库清理，运行时沿用 TESTING 记载的既有代码源，不是新一轮运行时代码修复；未因此合并 master、进行生产签名、创建 tag 或发布应用。

- 负责人已取消固定 24 小时验收：`FIXED_24H_ACCEPTANCE=NOT_REQUIRED`。采用日常反馈，不设置最低使用时长，也不要求提供日常 Redmi；历史 NOT_EXECUTED 不改成 PASS。
- Gate F 仍 **FAIL / NOT_CLOSED**，Gate G/H/I 仍 **PENDING**。取消时长要求不关闭真实缺陷，也不授权正式发布。
- **Debug 签名 APK 可供知情、自愿参与者小范围工程试用**；必须同时说明已知问题、设备范围、数据风险和退出方式，不能宣传为稳定正式版或商店批准版本。
- Debug 与正式生产证书是不同身份，不能覆盖正式证书签名的安装；同包名证书冲突时停止，不通过卸载、清数据或降级绕过。后续更新须核对包名、渠道、证书、版本和来源，不能承诺未经验证的无损升级。
- **unsigned APK 不能直接安装**，Play AAB 也不是供普通用户直接点击安装的 APK；CI unsigned Release 工件只作工程验证，不作为本次试用安装包。
- 试用设置、支持范围与反馈方式见 [USER_GUIDE.md](USER_GUIDE.md)；准备试用包不等于已有真实 Beta 或 Gate I 通过。

### 安装前的最小核对

- Play 包名为 `com.flowbreak.app`，Domestic 为 `com.flowbreak.app.cn`；分发时说明渠道，不能把同名图标当作同一应用身份。
- 工程试用更新也须同证书、合法版本和正确来源；安装冲突交由负责人处理，不要求试用者卸载既有应用或冒险清除数据。
- 试用者可随时关闭保护或撤销权限；完整导出不是已经支持导入或换机恢复的承诺。

## 门禁总览

| Gate | 内容 | 当前证据边界 |
| --- | --- | --- |
| A | 核心自动化 | 已有本地与 hosted 验证；后继 HEAD 必须使用自己的 CI |
| B | 指定历史 P1/P2 真机复测 | 历史 Redmi R1–R4 PASS；当前候选真机 smoke NOT_EXECUTED |
| C | 产物来源与受控构建 | 已有源码绑定 unsigned 验证；不等于签名发布包验收 |
| D | 支持范围与 fail-closed | 历史 PASS_SUPPORTED_SCOPE；vivo/iQOO 仍不支持保护 |
| E | 使用计时、阻断延迟与平台边界 | 历史 PASS_SUPPORTED_SCOPE_WITH_DOCUMENTED_PLATFORM_LIMITATION |
| F | Protection Integrity 与核心场景 | **FAIL**；Issue #28 / FB-P1-08 仍开放 |
| G | 签名、版本与可发行构建 | **PENDING**；跨机器恢复、真实版本升级均未完成 |
| H | 商店与合规材料 | **PENDING**；发行人资料及最终申报/审核未完成 |
| I | 自愿小规模 Beta | **PENDING / NOT_STARTED**；准备工程试用包不是已开始验证 |

正式发布须全部 release-blocking gates 通过，并具备相应授权。B/D/E 只保留各历史来源的裁定，不等于本候选重新通过；不得将 AVD、JVM、静态扫描或安全拒绝启动当作真机/OEM 验收。

## Gate A / B — 自动化与历史指定缺陷复测

- A 覆盖前端、品牌、UI smoke、Provenance、Release policy、隐私静态检查、签名安全回归，以及双渠道 JVM/Lint/Debug/unsigned Release、Room schema drift、R8 mapping。
- CI 仅组装 `assemblePlayDebugAndroidTest` 与 `assembleDomesticDebugAndroidTest`，不执行 connected instrumentation。另行记录的 AVD 身份测试仍只是对应模拟器证据。
- npm 审计按来源分开：本地官方 registry 与 GitHub CI 已验证结果见 TESTING；默认 `registry.npmmirror.com` 的 404/NOT_IMPLEMENTED 仍是 **BLOCKED / NOT_PASS**，不被官方或云端成功覆盖。
- 最终推送 HEAD 必须核对自身 [PR #29 checks](https://github.com/guo6x/flowbreak/pull/29/checks) 与 artifact；旧绿色 Run、文档-only 父提交或相同运行时代码都不能替代当前 HEAD 的 CI。
- B 的历史 Redmi R1–R4 关闭了当时指定的 FB-P1-01/02/03、FB-P2-01；不表示新候选、全部 P1/P2 或所有机型已通过。原始设备事实和后续偏差见 [KNOWN_ISSUES.md](KNOWN_ISSUES.md)。

## Gate C — 产物来源与受控构建

“受控、可重复执行”不是逐字节确定性构建：provenance 包含构建时间，同一 SHA 重建可能有不同哈希。

1. CI checkout 精确 `SOURCE_COMMIT_SHA`，确认 HEAD 一致且工作区干净；PR head 源码与 workflow/临时 merge SHA 分别记录，不混用。
2. 同批顺序执行 Web build → 生成 provenance → `cap sync` → Android 构建；不得让同步与 Gradle 同时改写生成目录。
3. dist 每个文件须在 Android assets 中存在且 SHA-256 一致；只允许明确的 Capacitor 额外运行时文件，缺失或 mismatch 失败。
4. 从 Domestic APK 与 Play AAB 内独立读取 provenance、包名与版本；APK 用 `aapt2 dump badging`，AAB 读取其自身 manifest（wrapper ZIP + `aapt2 dump xmltree`），不借用其他渠道元数据。
5. 保留双渠道 R8 mapping、Release runtime dependency inventory、`artifact-manifest.json` 与 `SHA256SUMS.txt`，下载后逐项独立复算；依赖树不等于完整 SBOM 或许可证结论。
6. provenance 仅采用 allowlist 字段，不包含 token、密码或 keystore；manifest 区分源码、workflow、渠道、版本和签名角色。
7. hosted 工件必须绑定该 Run 的精确 source SHA；来源不明、旧 Web bundle、其他提交的 APK 或本地未提交代码不得用于验收/发行。

CI verify 的 `flowbreak-unsigned-<sourceSha>` 为 UNSIGNED / TEST ONLY（保留 90 天），网页截图 artifact 为 WEB_PREVIEW_ONLY（保留 14 天）。PR 签名 release job 的 SKIPPED 是策略结果，不是签名验收 PASS。

当前与历史指纹只保存在 [TESTING.md](TESTING.md) 及其 [历史验证归档](archive/progress/verification-snapshots-through-20261025.md)，本页不重复旧 SHA、测试计数或 Run 流水。

## Gate D / E — 支持范围与 Android 执行边界

- D 的支持参考来自既有 Xiaomi/Redmi 设备证据，不保证所有 Redmi 或未测 OEM。vivo/iQOO 因 FB-P1-05 明确不支持启用保护，Play 与 Domestic 必须一致 fail closed。
- 激活、设置/目标配置重载、开机恢复和服务入口均使用 `UNSUPPORTED_DEVICE_FOR_RELIABLE_MONITORING`；保留目标、限额、历史数据和已保存配置，不用更多权限引导绕过。
- 配置已保存、权限已授予、服务有心跳与运行时保护可用是不同事实，诊断与界面必须分别表达。安全拒绝启动只能证明拒绝路径，不等于该 OEM 兼容成功。
- E 保留历史 E1 使用计时及 E2A 健康运行时延迟的等价性沿用、E2B 签名设备间隙恢复证据；没有重新执行完整 E1/E2 物理测试的事实不得改写。
- `TEST_A_ACCOUNTING_PRECISION=INCONCLUSIVE`，恢复信号 PASS 不等于精确计时 PASS，也没有据此证明 overcount；原证据范围见 TESTING。
- Android/OEM 不给进程或 worker 执行时间时，实时覆盖层与状态迁移不受保证。历史 replay 仅在 checkpoint 和 UsageEvents 足以可靠重建时恢复；不足则 INCOMPLETE，不制造时长。
- 本版本不声称修复 HyperOS freezer 或保证 Android 永不挂起。COMPAT-001 的后台启动限制与无障碍横幅兜底按 KNOWN_ISSUES 记录，不能扩大为所有配置下的可靠性承诺。

## Gate F — Protection Integrity 与核心复验：FAIL

- Issue #28 / `FB-P1-08` 是支持范围内发布阻塞项。历史真实设备显示 Bilibili 前台约 33 秒时 REST 页仍倒计时，但缺少同期原生状态、tick、分类器和取消判定证据，根因仍 UNDETERMINED。
- 当前代码回归不证明历史设备根因已修复；先定位兼容性和状态问题，再在获授权、确实可运行候选的设备上定向复验。不固定要求哪部 Redmi，当前被拒绝启动的 iQOO 不算已执行核心验证。
- 核心原则：**UI promise ≤ actual protection capability**。正常流程之外注入 Usage Access/Overlay 撤销、可选 Accessibility 关闭、服务被杀、省电/OEM 后台限制等，分别核对实际行为与界面真实性。
- force-stop 不要求自动复活；用户手动重开后不得谎称旧保护仍完整有效，应走合法恢复。检查无时间暴涨、重复通知、ANR 或 silent protection drift，停止/恢复可用。
- 核心短测、隐私和日常反馈仍需完成，不再要求专门占机一天；固定时长的取消不改变历史失败。
- 历史复验方案与当前取证限制见 [FB-P1-08 设备交接](FB-P1-08-FINAL-DEVICE-HANDOFF.md)。其诊断合同尚未就绪，不是当前可执行验收协议；不能为满足旧方案扩张诊断分享白名单。AVD 不替代厂商调度或真实保护场景。

## Gate G — 签名、版本与可发行构建：PENDING

### 两个独立未完成项

| 项目 | 当前状态 | 关闭所需证据 |
| --- | --- | --- |
| 跨机器签名恢复 | `CROSS_MACHINE_SIGNING_RECOVERY=NOT_YET_TESTED` | 在真正独立、获授权环境恢复原最终身份，并核对两渠道公开证书与 policy；原笔记本恢复仍未测 |
| 真实版本升级 | `TRUE_VERSION_UPGRADE=NOT_TESTED_NO_VALID_LOWER_FINAL_SIGNED_BUILD` | 实际较低版本、同一最终证书产物升级到较高版本，核对数据与持久状态；不得私造“正式低版本”充数 |

其中一项通过不关闭另一项。本地恢复、off-machine 备份、同版 `install -r`、signed install/replacement 和数据保留是历史范围证据，不是本候选已签名或真实升级通过；详见 [Gate G 交接](release/GATE_G_NO_PHONE_HANDOFF.md)。

### 身份、工作流与秘密纪律

- Play 使用独立 upload key，Domestic 使用 app-signing key；最终公开证书指纹以 `app/release-signing-policy.json` allowlist 为准。旧 technical dry-run 身份为 SUPERSEDED_PRE_PRODUCTION，禁止正式发行。
- 已有最终身份、最终 Environment secrets、age 本地托管/恢复、owner 密码管理器交接及 off-machine 备份记录，不授权本次读取密钥、替换身份或重新签名；不得重新生成密钥冒充恢复。
- release job 仅允许 `workflow_dispatch && refs/heads/master` 或合法 `v*` tag；dispatch 有显式 ref 守卫，非 master 签名访问必须拒绝。Environment 部署策略保持 master branch + v* tag。
- 构建前 policy 必须 PROVISIONED，两渠道指纹为合法 64 位 lowercase SHA-256，否则 `SIGNING_POLICY_NOT_PROVISIONED` / 验证失败；不得回退 debug key、旧身份或自动生成密钥。
- `FLOWBREAK_PLAY_*` / `FLOWBREAK_DOMESTIC_*` 只按各自 flavor 启用；环境变量缺失保持 unsigned，不影响 verify。keystore 仅在 `$RUNNER_TEMP` 解码并 `if: always()` 清理。
- 密码不进 child argv、日志、Git、provenance 或 artifact；keytool 使用短生命周期子进程环境变量引用。不得 dump 环境变量或把密钥/证件/私人设备日志入库。
- 可移植托管不能依赖当前 SID、DPAPI、Credential Manager-only、TPM-only、机器绑定 BitLocker 状态或单机证书；同机另一个文件夹不算独立备份。
- 经批准的既有 age v1.3.2 托管记录不等于仓库恢复工具可用；`New-PortableSigningVault.ps1` / `Test-PortableSigningVault.ps1` 在敏感操作前 fail closed，不能用 PATH、Adobe-bundled、batchpass 或任意归档器绕过。

### 版本、签名与产物核验

- 正式 versionName 唯一来源为 `app/package.json`，必须 stable SemVer `X.Y.Z`，无 prerelease/build/leading-zero；`release-version.mjs` 校验版本与 tag，发行前另行核对 CHANGELOG 一致性。
- versionCode 为 `MAJOR*1_000_000 + MINOR*1_000 + PATCH`，MINOR/PATCH < 1000、总量 ≤ 2100000000，独立于 CI run_number。unsigned 工程版本不得冒充正式升级版本。
- 正式 tag 必须精确 `v<versionName>`，且所指源码在 origin/master 祖先链；不能以任意 v* 字符串绕过版本校验，当前任务不创建 tag。
- Domestic APK 用 `apksigner verify --verbose --print-certs`；Play AAB 用 `jarsigner -verify` 与 `keytool -printcert -jarfile`，实际证书必须精确匹配 allowlist。
- 最终核对 Play/Domestic applicationId、渠道服务声明、独立设置、版本、证书和包内 Web 资源。Play 不含 AccessibilityService；Domestic 声明可选服务，默认偏好关闭不代表系统授权。
- signed artifact 包含 AAB/APK、mapping、provenance、manifest、SHA256SUMS；manifest 写入 signed/signingRole/certificateSha256，命名 `flowbreak-signed-v<version>-<sha>`，dry-run 使用明确前缀。
- 安装/升级和数据保留验证须使用真实授权设备及可追溯签名产物；模拟器 debug 覆盖安装不能替代最终签名升级。Gate G 不包含商店 Data Safety 等合规审批。

## Gate H — 商店与合规材料：PENDING

发行人应集中确认下列材料；准备草稿不等于已完成认证、备案、申报或平台审核：

- 真实发行主体、类型、地区及适用资格；各商店开发者/发行者账号不是 FlowBreak 用户账号或 GitHub 账号。
- 可公开访问的 HTTPS 隐私政策 URL。政策地址是网页地址，不是要求公开个人住址；实名认证/备案地址仅经相应官方流程提交，不强制购买域名或服务器。
- 公开支持邮箱 `15015091629@163.com` 已获授权使用，收发未验证；不编造运营主体、邮箱可达性、留存期限或响应 SLA。
- 最终包的权限用途、合并 Manifest、运行时依赖/许可证及真实网络行为核查；Android 声明 INTERNET，静态零违规不是运行时零流量证明。
- Google Play Data Safety、FGS specialUse 声明及适用的平台质量/Android Vitals 核查；对应渠道的 Accessibility 独立告知/用途说明、国内后台与自启动引导说明。
- 图标、最终渠道构建截图、必要演示、商店描述与版本说明；Web 预览和模拟器截图不能自动当作最终商店取材或健康效果证明。
- 按实际主体/渠道核实 APP 备案、版权/软著等适用资质、审核材料和分发要求；不把未经实时核实的规则当作已确认法律结论。

隐私最终核对必须区分完整导出、设备内运行时诊断、可见健康摘要和脱敏分享：完整导出可含全部现存 SharedPreferences；本机 `runtimeTracking` 可含 `restCheatEvidence`，脱敏分享白名单排除整个 `runtimeTracking`。邮件由外部服务/收件端处理，不是自动上传；细节以 [PRIVACY.md](../PRIVACY.md) 为准。

实际材料入口：[Gate H 准备核对表](compliance/GATE_H_READINESS_20261012.md)、[国内渠道办理指南](compliance/DOMESTIC_PUBLISHING_GUIDE.md)、[政策草稿](compliance/PRIVACY_PUBLICATION_DRAFT.md)、[商店文案草稿](compliance/STORE_CONTENT_DRAFT.md)、[网络与 SDK 静态审计](compliance/NETWORK_SDK_STATIC_AUDIT_20261012.md)。无最终发行人/包/账号确认时不代填提交、不付费、不虚构资质。

## Gate I — 自愿小规模试用与反馈：PENDING

- 邀请知情、自愿参与者日常使用，不设最低使用时长，不要求交出日常手机，也不把尚无参与者的计划写成真实 Beta 已完成。
- 每次反馈绑定实际包来源、版本、机型、系统与可复现现象；优先收集最少信息，完整记录/导出只在知情且必要时私下处理，不公开证件、密码、签名材料或使用时间线。
- 出现计时、阻断、休息或保护状态失真时登记缺陷并提供安全停止方法，先保证用户能正常使用设备；实际反馈不是 Gate F 的自动 PASS。
- 达到可复核的核心场景与实际小规模反馈证据后，分别更新 I 与相关缺陷；商店发布仍须满足其余门禁及明确授权。

## 发布决策前的最终核对

1. 核对当前精确 HEAD 的 CI、工件来源、版本/渠道、签名与校验和；不得复用历史绿色记录认证新 HEAD。
2. 确认 KNOWN_ISSUES 中支持范围内 release-blocking 问题已按证据处理；非阻塞风险、未测机型和实验性微信视频号能力如实披露。
3. 保留产品规则：80/100/120%，30 秒 reset 只适用于 pre-BLOCKED；BLOCKED sticky 只能经正式休息、合法紧急使用或明确监控/配置生命周期转换退出。不恢复已移除的定时提醒要求。
4. 当前 Gate F FAIL、G/H/I PENDING，不能正式发布；常规工程合并单独决策，不等于设备或发布验收。工程试用包与最终生产发行物必须分开标识。
5. 同批汇总代码/文档/材料/反馈及外部依赖后普通推送；是否合并、正式签名、创建 tag、商店提交分别遵守授权，不由清理任务自动触发。
