# FlowBreak v1.1.0 — Gate H 发布合规准备与证据矩阵

**调查/文件准备日期：2026-10-12 · 状态：DOCUMENTATION_PREPARED / STORE_SUBMISSION_NOT_APPROVED。**

本文件是审查用工作底稿，不构成法律意见或商店审批。**GATE H 仍为 PENDING。** 开发/自动化能完成的事实核查与文案草拟与必须由发行主体提交的资质、商店后台操作严格分开；任何未证实的事实保留为 **UNVERIFIED**。

## 1. 从代码提取、待最终打包复核的发行边界

| 项 | 代码事实与证据 | 申报判断 |
|---|---|---|
| 应用 | FlowBreak v1.1.0，Android Play 与 Domestic 双渠道；`app/package.json`、渠道 Manifest | CONFIRMED_SOURCE（非商店事实） |
| 核心用途 | 用户选择应用，基于 Android 使用情况事件估算前台时长，超时提示/拦截，经休息恢复访问；统计存设备本地；`docs/PRODUCT.md`、`PRIVACY.md` | CONFIRMED_SOURCE；不宣传系统限制下的实时绝对保证 |
| 账号/广告/云同步 | 当前公开隐私声明为不设账号、不接广告/第三方统计、不主动向服务器上传本地使用数据；`PRIVACY.md` | SOURCE_CLAIM；最终 AAB/APK 的 SDK 行为和网络动态检查 **PENDING** |
| 本地使用信息 | 受限应用包名、聚合使用时长、休息和阻断事件、用户选择的日反馈、积分、成就、设置；Room/SharedPreferences | ON_DEVICE_PROCESSING，未证实离端传输；用户可主动导出完整 JSON |
| 脱敏诊断 | 原生 `getDiagnostics/shareDiagnostics` 显示心跳、状态机、Permission、计数、restCheatEvidence；不导出目标包名/UsageEvents 明细；可能包含设备环境与精确诊断时间 | 不能将“脱敏”等同“不可关联”或“绝对无敏感信息”；用户选择分享后接收方可能获得数据 |
| Android Manifest | 通用 INTERNET、PACKAGE_USAGE_STATS、SYSTEM_ALERT_WINDOW、RECEIVE_BOOT_COMPLETED、FOREGROUND_SERVICE、FOREGROUND_SERVICE_SPECIAL_USE、POST_NOTIFICATIONS、VIBRATE；`app/android/app/src/main/AndroidManifest.xml` | 声明与实际用途逐项复核；INTERNET 存在不等于确有数据上传 |
| FGS | `FlowForegroundService` 声明 `specialUse`，manifest 的 subtype 字符串为“健康提醒：监控连续使用时长” | 须提供 Play 特殊用途说明、用户可感知通知、演示视频、任务推迟/中断影响；**PLAY_APPROVAL=UNVERIFIED** |
| Accessibility | **仅 Domestic manifest** 声明 FlowAccessibilityService；Play manifest 应当完全不包含该 service。实际 service 读取窗口切换事件中的 `packageName` 与必要的 `className`；针对微信视频号从类名派生 `wechatInVideoChannel` 布尔值并在 SharedPreferences 保留检测时间戳（不获取页面文字、聊天内容或输入文本） | 国内版须独立明确披露窗口类名及本地派生信号；不能把辅助阻断表述为残障辅助工具；最终两渠道 manifest CI 审核 |
| 设备支持边界 | v1.1.0 Redmi/Xiaomi 为已测参考；vivo/iQOO 明确 fail-closed 不支持；其他 OEM 无全面支持承诺 | 商店文案不得宣称支持全部 Android 手机/后台始终运行 |
| 隐私政策 | 仓库 `PRIVACY.md` 已有中文文稿，但**公开可访问 URL 与公开支持邮箱均缺失** | 必须补齐发行主体核验、公开 URL 和联系方式，再填 Console |
| 合规资质 | APP 主办者、账号/实体、版权授权、备案编号、渠道材料均未由当前仓库提供 | **OWNER_REQUIRED**；不得伪造备案号、应用资质、主体或审核截图 |

仅代码审查不能证明某个第三方 SDK 或生成后的 APK 从不联网。正式 Data safety 表需要结合 **最终 AAB 实际依赖扫描 + 运行时离端流量验证 + 所有分发渠道实际 SDK 清单** 确认。

## 2. Google Play 材料与决定

### Data safety（草案，不可直接当作已确认表单）

- 关键定义：Google Play 将“收集”定义为将数据从设备传送到设备外；只在设备上访问与处理且不离端的数据，不按「已收集」报告。**前提是最终安装包不存在 SDK/联网例外。**
- **暂拟工作假设：** FlowBreak 基础保护数据只在设备上处理，故 *collected = No* 可能成立；但是 APP 声明 INTERNET、用户主动导出/分享、WebView 或三方 SDK 的传输行为必须单独核实。不存在最终网络证据前，不签字确认“无收集/无分享”。
- 用户主动调用 Android 分享面板是重要边界。Google 官方定义还讨论“设备上向另一 App 转交”及用户明确发起的转交例外；需按实际实现、Play Console 问答判断，不得直接将“主动分享”误报为“自动向服务端收集”。
- **数据访问事实**：使用情况、用户所选目标包、设置和反馈保存在本地；至少在隐私政策与权限前置说明中解释访问、使用、保存及用户控制方式，即使 Data safety 不要求标为离端收集。
- 流量复核建议：最终 Play AAB clean install 的离端连接目的地/SDK清单、导出数据、崩溃日志、WebView及运行时; 与实际 Play 版本/最终 SHA 绑定证据。用户手机不需要用于静态代码扫描；真实流量验证可由授权测试环境或模拟器完成（模拟器不替代 Gate F 真机）。
- Google Play 隐私政策须提供可公开访问、不要求登录的链接，并且在应用内可找到该政策；最终应包括开发者/主体联系、访问的数据、用途、共享/留存、用户删除方法、政策更新时间。

### 前台服务声明（specialUse）

- 当前声明目的：在用户主动启用后，持续观察用户选择的应用使用情况，提示长时间使用并维持正在运行的保护状态通知。
- 不能承诺“永久后台运行/永不被系统杀死”；Android/OEM 限制会使前台服务延迟、中断。**当前 master 的 Dashboard 可仍基于监控意图显示“监控中”，并未证明实时 worker/heartbeat 健康提示。** 该完整性属于未合并的 PR #29 和 Gate F 物理验收，因此提交 FGS 声明前不得把“每次恢复时自动准确报告故障”作为已交付能力。
- Play Console 中要解释为何这项任务需要前台服务、任务延迟或中断的实际影响，并提供操作演示视频（由**真实可发行包/模拟器录屏**，不能将静态图冒充视频）。
- `specialUse` 是需 Google Play 审查的类型，不因为项目的 Manifest/CI PASS 就代表获批。
- Google Play 版应维持**无 AccessibilityService 申报**，与实际打包 Manifest 对照。

### Store listing / App content

- 填写 app name、short/full description、分类标签、联系方式、隐私政策 URL、内容分级、目标受众等实际表单；姓名、电话/邮箱不得伪造。
- 官方帮助列明应用名称最多 **30 字符**，简短说明最多 **80 字符**，完整说明最多 **4,000 字符**；商店需要符合要求的图标、实际应用截图和必要预览资源。
- 文案与截图必须体现实际已实现功能。禁止夸大“100% 拦截”“所有设备兼容”“后台绝不失效”“解决沉迷症”等未经支持的主张。见 `STORE_CONTENT_DRAFT.md`。
- Play 声明/视频和页面截图仍需上传 Console，**未执行**。

## 3. 国内渠道合规

- 官方工信部 `工信部信管〔2023〕105号` 规定**在境内从事互联网信息服务的 APP 主办者**按其条款履行 APP 备案；公布备案号、公开备案编号等要求适用时应遵守；网络接入/分发平台需核验。FlowBreak 属纯本地功能是否及如何适用、拟发行渠道所要求的备案范围，应由主办者/渠道按实际部署经营方式正式核对。**不能仅凭离线工具属性就自行断言豁免，也不能擅称已备案。**
- 主办者类型（个人/企业）、实名及注册地址、拟投放平台、应用包名、签名证书、网站/域名/托管及实际分发地区、版权归属等均需发行方确定，形成 `OWNER_REQUIRED` 材料。
- Domestic Accessibility 行为的用途：仅监听当前窗口包名，在被选目标达到阻断条件时辅助返回桌面；不读页内文本或按键；用户可在设置中拒绝/撤销。需突出权限说明并取得用户主动操作。**若 UI 尚无独立显著说明，不得宣称已实现合规授权。**
- 需要隐私政策公开地址与客服渠道、应用基本信息、权限逐项用途清单、截图/录屏、SDK清单、同主体版权授权及按发行渠道要求准备申请。ICP备案/APP备案编号只有获得确认后才进入 UI 与商店页面，不得预置假号码。
- 无障碍能力与隐私用途以实际 manifest + service 代码为准，最终出包之后复查；Domestic 与 Play 使用不同声明，不互相借用资质。

## 4. 责任与审核阻塞矩阵

| 工作项 | 已完成的无手机工作 | 当前状态 | 下一事件 |
|---|---|---|---|
| 规则/代码对照及申报草案 | 读取现有 Manifest、隐私政策、发行机制；本文件与商店文案 | PREPARED | 非手机的安全/法务复核 |
| Google Play Data safety 草表 | 本地/离端判定准则及可复查清单 | DRAFT_UNVERIFIED | 最终 AAB 网络/SDK证据后人工提交 |
| FGS `specialUse` 说明 | 理由与中断影响及录屏脚本 | DRAFT_UNVERIFIED | 录制/上传真实视频、Play 审查 |
| 用户隐私政策 | 现有政策已写，发布审核差异清单 | REVIEW_REQUIRED | 发行方提供公开联系方式与 URL |
| Store listing 文案与画面清单 | 文稿与逐屏取材说明 | DRAFT | 合法商店后台与真实截图 |
| 国内版无障碍披露 | 两个 UI 入口已在 PR #33 中使用共享独立告知及明确选择，含窗口类名用途 | IMPLEMENTED_IN_PR_PENDING_CI_REVIEW | 通过 CI/独立复审后合并；不等于国内商店审核批准 |
| APP 备案、主体、版权、资质 | 官方要求与待提交材料清单 | OWNER_REQUIRED | 发行主体/渠道决定并提供真实证据 |
| 商店账号、审核结果 | 无权代替用户核实账号、支付或申请 | NOT_EXECUTED | 发行方授权并完成申报/审批 |

## 5. 官方依据（2026-10-12 检索）

- Google Play Data safety 规则：https://support.google.com/googleplay/android-developer/answer/10787469?hl=en
- Google Play User Data / privacy policy：https://support.google.com/googleplay/android-developer/answer/10144311?hl=en
- Google Play FGS 声明：https://support.google.com/googleplay/android-developer/answer/13392821?hl=en
- Android `specialUse` 类别与 subtype：https://developer.android.com/develop/background-work/services/fgs/service-types
- Google Play Accessibility 说明：https://support.google.com/googleplay/android-developer/answer/10964491?hl=en
- Google Play 上架字段/限制：https://support.google.com/googleplay/android-developer/answer/9859152?hl=zh-Hans
- Google Play 截图/图标资源：https://support.google.com/googleplay/android-developer/answer/9866151?hl=en
- 工信部 APP 备案官方通知：https://www.miit.gov.cn/jgsj/xgj/wjfb/art/2023/art_dd783a581c9644a4aee10afa582811db.html

**裁决：`GATE_H_DOCUMENTATION = PREPARED`; `GATE_H_STORE_APPROVAL = NOT_EXECUTED`; `GATE_H = PENDING`.** 不应将审查准备、代码检查或政策研究等同于发行许可。
