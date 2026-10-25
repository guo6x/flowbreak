# FlowBreak v1.1.0 — 双渠道上架文案、声明脚本和取材清单

**DRAFT_ONLY — 2026-10-12；尚未商店提交，也未获审核批准。** 用户的设备、身份和证书没有用于本文档。所有描述须经最终实测和发行主体确认。

**资料补记（2026-10-25）：** 负责人已指定 `15015091629@163.com` 为对外支持邮箱，未做收发验证或注册账号；公开隐私政策网页地址暂无。国内各渠道开发者账号与资质的办理步骤见 [国内发行指南](DOMESTIC_PUBLISHING_GUIDE.md)，准备资料不等于决定办理或已提交。

## 1. Google Play 商品详情（待发行方选定默认语言）

**App name（≤30 字符）：** FlowBreak

**短说明（≤80 字符）：** 自主选择需要少用的应用，适时休息，了解自己的使用习惯。

**完整说明草稿：**

FlowBreak 是一款帮助你更有意识地使用手机的本地数字健康工具。

你可以自行选择希望减少使用的应用，设定连续使用时间上限。应用在获得必要的 Android 权限、保护服务实际正常运行时，观察所选应用的前台使用情况，并在达到设定条件后进行提醒或阻断。你可以通过短暂休息恢复使用，还可以回顾本机记录的使用趋势、休息完成情况和个人反馈。

**由你自主设置**
- 自己决定哪些应用需要提醒或限制，以及使用时长。
- 在系统设置中自行授权或撤销使用情况访问、悬浮窗和通知等能力。
- 如果 Android/OEM 系统限制了后台运行，提醒与拦截可能延迟或暂时不可用；返回应用后请核对所显示的保护状态和所需权限。应用不保证后台始终运行，也不保证每种中断都会被即时发现或提示。

**注重本地数据**
- 使用记录、设置、休息与反馈主要保存在设备本地，不要求注册账号。
- 支持手动导出本地数据、查看运行诊断和清除本地数据。
- 具体数据处理以正式发布的隐私政策为准。

**使用范围与限制**
FlowBreak 的保护效果受到 Android 系统和手机厂商后台限制的影响，不保证每种机型或所有场景都能实时阻断。部分机型可能暂时不受支持；请在应用中查看权限与运行诊断。此应用是行为管理工具，不提供疾病诊断或治疗。

**不得直接发布的附加保证**：不写“所有安卓设备”、“不可绕过”、“毫秒级精准”、“解决手机成瘾”、“系统后台无法关闭”、“100% 保护”等描述。禁止展示尚未通过真机验证的微信视频号能力为普适能力。

## 2. 国内渠道商品详情

**应用名：** FlowBreak

**一句话说明：** 让自己选择的手机使用规则真正可见，让休息成为使用过程的一部分。

**详情简介：**
FlowBreak 支持由用户选择需要限制的应用、设定使用时长，并在取得相应授权后提醒或阻断连续使用。达到设定条件时，可以按照休息引导恢复使用；还可以回看本机使用统计、休息完成与反馈。数据以设备本地保存为基础，可由用户主动导出或清除。

**权限提示：**
使用情况访问用于识别所选应用与前台时间；悬浮窗用于显示阻断界面；通知及前台服务用于展示正在运行的保护状态。**仅国内版**提供可选“无障碍强阻断”辅助功能：经用户单独知情后，通过窗口切换事件中的应用包名及必要的窗口类名识别目标应用，在阻断条件触发时返回桌面；不会读取或保存页面正文、聊天内容、输入文本。该功能可拒绝，也可在系统设置中随时关闭。实际保护效果取决于手机系统权限与后台限制。

国内渠道最终描述须在法律与应用商店审核要求下审定，不含编造的备案号、版权登记、政府背书或医疗功效。

## 3. Google Play FGS `specialUse` 填报说明草案

**Use case:** A user-configured, persistent digital well-being reminder that observes foreground durations of apps selected by the user (with Usage Access) and presents a visible ongoing status notification while protection is enabled. The foreground task is necessary to deliver timely reminders and screen-blocking cues that depend on changing foreground app activity. The current source is designed to keep product usage data on-device and does not implement uploading it to a FlowBreak backend. This is a source-level statement only: do not submit it as a verified final-binary/network claim until the exact release candidate's merged manifest, SDK/dependency inventory, and runtime network behavior have been reviewed.

**Why does deferring the task matter?** Delaying the task may make a configured time threshold or reminder late or miss it entirely; the on-screen promise must reflect actual detection ability. Delaying tasks is not a critical safety hazard and should not be described as an emergency use case.

**What happens if interrupted?** Android/OEM restrictions can delay or suspend monitoring, so reminders and blocking may be late or unavailable. Check the implementation and verification state in [`CURRENT_STATUS.md`](../CURRENT_STATUS.md) against the exact release candidate before making runtime-health claims. Real-device validation is separate from source review and automated checks; until the final candidate has been independently reviewed and the required device evidence recorded, do not claim that reopening the app always diagnoses or immediately reports every degraded state. Never claim monitoring continued when the OS suspended execution.

**User initiated and perceptible:** The user explicitly selects targets and enables protection; the ongoing foreground notification indicates that monitoring is active. Revoking usage/overlay privileges removes necessary protection capabilities; user-visible status must be accurate.

**Video evidence (NOT RECORDED):** 1. fresh open/configure selected target, 2. explain permission access, 3. user enables protection and sees service notification, 4. demonstrate time-limit transition and active reminder/overlay, 5. pause/stop protection and see notification/service state change, 6. show no support on unsupported devices. Use approved build or controlled simulator, label any simulated sequence; do not submit fictitious UI footage as actual evidence.

FGS subtype manifest currently says “健康提醒：监控连续使用时长”; no corresponding Play policy approval has been issued. Play variant does not request AccessibilityService.

## 4. Domestic Accessibility 单独告知草案（应用内 UI 需单独确认）

**标题：** 选择是否开启无障碍强阻断

**独立说明：** 如果你选择开启，FlowBreak 会通过 Android 无障碍服务监听前台窗口切换事件中的应用包名以及必要的窗口类名（用于识别特定页面，所导出的相关信号仅为本机状态和时间戳）。只有当窗口属于你主动设置的受限应用，且保护达到阻断条件时，才会尝试返回桌面并显示阻断页面。这不是读取屏幕文字的工具；FlowBreak 不会借此读取、保存或上传聊天消息、输入内容或网页正文。你可以拒绝，其他基础保护设置仍可继续；也可以随时在系统无障碍设置中关闭。部分手机可能限制这项功能，不能保证一定阻断。

**确认按钮：** 「我理解，前往系统设置」 / 「暂不开启」。必须在用户主动确认后打开系统设置；不得预勾选、静默开启或用隐私政策链接代替此项告知。此项只是披露/用户选择，不会绕过 Android 系统的无障碍权限界面。

**Play 渠道：** 不显示国内版无障碍开关或相应功能宣传，不在包中声明无障碍 service。

## 5. 图片 / 预览视频素材列表（PENDING_REAL_CAPTURE）

必须与**真实最终构建**一致，不能用设计稿或代码截图冒充应用实拍：

| 类型 | 内容 | 隐私要求 | 现状 |
|---|---|---|---|
| App icon | 最终品牌应用图标与商店规范尺寸 | 不涉及用户数据 | 源素材需由发行方确认 |
| 手机图1 | 首页及保护状态、设备受支持范围 | 模拟数据、无真实目标列表 | 未采集 |
| 手机图2 | 设置自选目标应用、使用限额 | 示例包名、禁止用户的真实私密名单 | 未采集 |
| 手机图3 | 休息引导/计时（需真实内部状态） | 不显示个人资料 | 未采集 |
| 手机图4 | 趋势统计与日反馈 | 假数据明确标注演示；不得伪装用户真实记录 | 未采集 |
| 手机图5 | 隐私数据导出/清除与运行诊断 | 诊断字段脱敏 | 未采集 |
| Play 审核视频 | specialUse FGS 的真实开启/运行/关闭 | 禁止泄露通知中其他应用/联系人内容 | 未录制 |
| 国内渠道可选 | 无障碍单独说明、允许/拒绝和系统页 | 说明文字必须与最终实现一致 | 未录制 |

真实图库格式、尺寸、数量和视频 URL 以拟投放平台当期规则为准，Google 官方资源说明：https://support.google.com/googleplay/android-developer/answer/9866151?hl=en

## 6. 发行方补充字段（不得使用测试占位信息提交）

- 发布主体法定名称/类型/地区：**未提供**。
- 面向公众的支持邮箱：**负责人已提供** — `15015091629@163.com`；尚未执行收发验证，不据此推断手机号、商店账号或主体身份。
- 公开隐私政策 HTTPS 地址：**未配置/未部署**；是网页地址，不是个人住址，可先评估静态托管。
- 备案编号、APP 主办者、版权归属或授权：**未提供 / 待发行条件核验**。
- 商店账号所有权、发行地区和具体渠道：**未核实**。
- 最终商店截图/审核视频与批准结果：**未制作/未提交**。

若任何一项依照对应渠道为硬性要求而缺失，`GATE_H=BLOCKED_OWNER_ACTION`，不允许签署“可上架”。

**官方字段限制：** https://support.google.com/googleplay/android-developer/answer/9859152?hl=zh-Hans
