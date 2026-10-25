# 后台设置与机型兼容参考

本文面向安装 FlowBreak 试用的 Android 用户，帮助查找后台设置、理解兼容性提示。首次安装和日常操作见 [使用指南](USER_GUIDE.md)；本文不替代产品规范或真机验收证据。

**有品牌引导，不等于该品牌已经验证兼容。** 两个 Android 渠道均提供后台设置引导；vivo / iQOO 仍拒绝开启保护。Redmi / Xiaomi 只有历史参考证据，不是当前候选的本轮通过证明。Gate F 仍为 FAIL，G / H / I 为 PENDING；最新结果以 [当前状态](CURRENT_STATUS.md)、[已知问题](KNOWN_ISSUES.md) 和 [测试标准](TESTING.md) 为准。

仅支持可以安装并运行 Android APK 的系统。HarmonyOS NEXT 等非 Android 系统不支持本项目 APK；不能凭“华为”品牌名称推定支持。

## 操作：查看和调整后台设置

1. 在 FlowBreak 权限页展开「提升后台稳定性（可稍后设置）」。两项必需权限仍是使用情况访问与悬浮窗；后台设置是可选项。
2. 查看该设备的「后台设置」说明，再点「设置」。应用会尽力打开厂商入口；不可用时回退到 FlowBreak 应用详情，再回退到系统设置首页。
3. 如果打开的页面不是后台管理，按下表在系统设置中搜索。只调整 FlowBreak 对应的选项；没有该选项时不必反复授权。
4. 完成后返回应用，点「重新检测权限」。可读取的标准 Android 权限会刷新；厂商白名单无法可靠读取，后台设置项仍要求用户在系统中确认。
5. 按日常使用场景试用；如出现计时停滞、提醒延迟或返回目标应用后仍在休息倒计时，先关闭保护，保留问题发生时间、机型、系统版本和应用版本，并按 [使用指南](USER_GUIDE.md) 分享脱敏诊断。不要把不可靠的保护当成已经生效。

**打开设置不代表已经授权，也不代表保护服务能一直运行。** 省电、强行停止、系统调度和厂商清理仍可能中断保护；通知存在也不能单独证明计时正常。设置入口全部不可达时会如实报错，可手动在系统应用列表中查找 FlowBreak。

| 品牌 / 系统 | 手动查找建议 | 建议的证据边界 |
| ---- | ---- | ---- |
| 小米 / 红米 / 黑鲨 | 搜索「后台自启动」或「自启动管理」；在 FlowBreak 应用信息的电池 / 省电策略中，如有「无限制」可按需选择 | 通用搜索建议；本轮未取得当前系统具体路径的官方正文 |
| 华为（Android 兼容系统） | 搜索「应用启动管理」，对 FlowBreak 关闭自动管理并允许后台活动；电池优化中如有对应项，选择「不允许优化」 | 华为官方正文可核实这些设置方向；名称随版本变化，不证明 FlowBreak 已通过真机验收 |
| 荣耀 | 搜索「应用启动管理 / Launch」，对 FlowBreak 关闭自动管理并允许后台活动；电池优化中如有对应项，选择「不允许优化」 | 荣耀官方正文可核实这些设置方向；不是所有 MagicOS 版本的路径保证 |
| OPPO / 一加 / realme | FlowBreak 应用信息 → 电池使用情况 / 耗电管理，查找允许后台活动；系统如有自启动开关，再按需开启 | 当前路径是通用建议；realme 旧官方 FAQ 另有近期任务下滑锁定说明，不认证当前所有系统的电池路径 |
| 三星 | 「电池和设备维护 → 电池 → 后台使用限制」，从休眠 / 深度休眠中移出 FlowBreak；如有「从不休眠的应用」可加入 | 三星官方正文可核实此设置方向；休眠名单与 Android 电池优化豁免不是同一开关 |
| 魅族 / 华硕 / 中兴 / 努比亚 / 联想 / 摩托罗拉 / 乐视及其他品牌 | 系统应用列表 → FlowBreak → 电池 / 后台运行，避免选择「受限制」；另有自启动、后台管理或休眠名单时再搜索 | 通用建议；未核实这些品牌当前系统的专用路径 |
| vivo / iQOO | 不继续开启更多权限；本版本不在这些设备上启动保护 | 保留拒绝保护边界，不能通过调整开关解除 |

不要求 root、清除应用或系统管理器数据、ADB 修改、关闭系统安全保护，或交出日常使用的手机。也不要求固定 24 小时验收。调整单个应用的电池策略可能增加耗电；不愿调整时可以保持可选设置关闭。

### 标准“后台受限”状态是什么意思

Android API 28 及以上可通过 `ActivityManager.isBackgroundRestricted()` 读取用户是否对该应用开启了标准后台限制。FlowBreak 暴露三种快照：

| 状态 | 能说明什么 | 不能说明什么 |
| ---- | ---- | ---- |
| `restricted` | 本次读取时标准后台限制已开启，应用显示警告和设置建议 | 不能单凭此值确定所有计时问题的根因 |
| `not_restricted` | 本次读取时该标准限制未开启 | 不证明厂商白名单、自启动、休眠策略均已放行，也不保证不会被挂起 |
| `unknown` | 系统低于 API 28、服务不可用或读取失败，无法判断 | 不能当成“未受限”或“保护正常” |

此状态与电池优化豁免、厂商后台设置及保护服务健康是不同维度，不新增一项必需权限。

### 无障碍与侧载提示

Play 版不提供无障碍强阻断；Domestic 版为可选实验性能力，必须先阅读应用内独立说明，再由用户到系统设置授权。后台设置引导可用于两个渠道，不能据此推导 Play 也有无障碍服务。

Android 13 及以上可能对侧载应用显示「受限设置」。只有确认安装来源、信任开发者并理解权限用途后，才按系统提示决定是否允许；不要求关闭全局安全保护，也不以可选无障碍作为使用产品的强制条件。官方安全说明见下方来源。

## 参考：实现、自动化与真机证据分别是什么

下表的“自动化覆盖”描述源码中测试的对象，不表示这些测试已在手机上运行。最新执行结果与工件绑定见 [TESTING.md](TESTING.md)，不能将新增测试或设置文案改动用作旧真机问题的结案证据。

| 品牌 | 适配实现 | 自动化覆盖范围 | 真机证据边界 |
| ---- | ---- | ---- | ---- |
| Xiaomi / Redmi / Blackshark | 品牌手动指引、尽力而为的厂商候选入口与标准回退 | 品牌映射、候选入口、两渠道界面及非授权状态 | Redmi / Xiaomi 仅历史参考；Blackshark 未验证；均不是当前候选本轮 PASS |
| vivo / iQOO | 原生与界面拒绝开启保护，不继续引导权限 | 厂商识别、运行时拒绝及权限页阻断 | 历史失败边界仍保留，不宣称已恢复支持 |
| Huawei / Honor | 品牌指引、候选入口与标准回退；仅 Android 兼容系统 | 品牌映射与候选入口 | 未真机验证 |
| OPPO / OnePlus / realme（含 Oplus 标识） | 品牌指引、候选入口与标准回退 | 品牌映射与候选入口 | 未真机验证 |
| Samsung | 休眠名单指引、候选入口与标准回退 | 休眠文案、品牌映射与候选入口 | 未真机验证 |
| Meizu / Asus / ZTE / Nubia | 通用后台指引、品牌候选入口与标准回退 | 品牌映射与候选入口 | 未真机验证 |
| Lenovo / Motorola / Letv / Leeco | 通用后台指引、品牌候选入口与标准回退 | 品牌映射与候选入口 | 未真机验证 |
| 未知品牌 | 通用应用后台指引，不依赖私有入口 | 未知 / 空品牌回退、权限页通用入口 | 未真机验证；未被拒绝不等于已验证支持 |

自动化对应源码：[`oemBackgroundGuide.test.ts`](../app/src/utils/__tests__/oemBackgroundGuide.test.ts)、[`Permissions.test.tsx`](../app/src/pages/__tests__/Permissions.test.tsx)、[`Dashboard.test.tsx`](../app/src/pages/__tests__/Dashboard.test.tsx)、[`AutoStartTargetTest.java`](../app/android/app/src/test/java/com/flowbreak/app/AutoStartTargetTest.java)、[`NativeFlowPermissionManagerTest.java`](../app/android/app/src/test/java/com/flowbreak/app/NativeFlowPermissionManagerTest.java)、[`NativeFlowSettingsNavigationTest.java`](../app/android/app/src/test/java/com/flowbreak/app/NativeFlowSettingsNavigationTest.java)、[`NativeFlowBackgroundRestrictionTest.java`](../app/android/app/src/test/java/com/flowbreak/app/NativeFlowBackgroundRestrictionTest.java)。页面合成场景见 [`oem-guidance-fixture.mjs`](../app/scripts/oem-guidance-fixture.mjs)。这些测试检查提示、分支逻辑或模拟环境，不提供厂商系统的执行调度保证。

## 参考：官方来源与核实范围

以下公开网页于 **2026-10-25** 核验。这里只确认网页能支持的设置或安全说明，不构成厂商对 FlowBreak 的认证；地区、机型和系统版本不同，菜单可能不同。

### 已取得正文并核对内容

| 官方来源 | 本次可核实的内容 |
| ---- | ---- |
| [Android：Doze 与 App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby) | 电池优化豁免是部分豁免，仍有系统限制；普通设置入口与直接申请豁免的政策边界 |
| [Android：ActivityManager.isBackgroundRestricted](https://developer.android.com/reference/android/app/ActivityManager#isBackgroundRestricted()) | API 28 起读取用户开启的标准后台限制，不是厂商保活接口 |
| [Android：受限设置](https://support.google.com/android/answer/12623953?hl=en) | Android 13 起敏感设置的安全提示；仅对信任的开发者、理解用途后再决定允许 |
| [华为：无法在后台运行应用](https://consumer.huawei.com/en/support/content/en-us00428704/) | App launch / 应用启动管理、Run in background、电池优化与近期任务锁定；仍有后台调度限制 |
| [荣耀：后台应用被强制关闭](https://www.honor.com/global/support/content/en-us00428704/) | Launch 自动管理、允许后台与电池优化；官方亦指出系统仍可能关闭后台应用 |
| [三星：Sleeping apps on your Galaxy device](https://www.samsung.com/us/support/answer/ANS10003442/) | 休眠 / 深度休眠 / 从不休眠名单及「后台使用限制」入口 |
| [realme：官方 FAQ](https://www.realme.com/in/support/faq) | 旧 FAQ 中“近期任务界面下滑至出现锁图标”的锁后台动作；未核实当前机型电池 / 自启动的精确路径 |

部分厂商文章还列出清数据等排障动作，本文不采用。不要为了后台运行清除应用或系统管理器数据。

### 仅核实官方入口，未核实专用后台路径

| 官方入口 | 本轮限制 |
| ---- | ---- |
| [Xiaomi 官方支持 FAQ](https://www.mi.com/global/support/faq/) | 能访问官方入口，未取得匹配当前系统的后台自启动专文；上方仅给通用搜索建议 |
| [OPPO 官方支持](https://support.oppo.com/en/) | 具体条目依赖动态页面，未取得后台 / 电池路径正文；不作为精确步骤认证 |
| [OnePlus 官方用户手册入口](https://service.oneplus.com/global/user-manual) | 取得手册入口，未核实具体机型手册的后台管理正文；不认证通用建议适用于所有 OxygenOS / ColorOS |

其他品牌尚未核实官方专用路径。新增品牌证据时应记录准确机型、系统、渠道、APK 来源与复现步骤，并更新 [TESTING.md](TESTING.md) 的设备证据；只新增映射或引用一篇厂商文章，不能把矩阵改成“真机通过”。
