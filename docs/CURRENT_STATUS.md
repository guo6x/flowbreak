# CURRENT_STATUS — 项目当前状态

> 当前工程与发布状态的入口。产品规则见 [PRODUCT](PRODUCT.md)，缺陷台账见 [KNOWN_ISSUES](KNOWN_ISSUES.md)，源码绑定验证见 [TESTING](TESTING.md)。历史报告不认证后继候选。

## 现在可以怎样使用

**仅供明确知情、自愿的小范围工程试用，不是正式稳定版，不是商店发布包。** 可安装的是 Debug 签名 APK；CI 的 unsigned Release APK/AAB 不能直接安装或当作正式分发包。安装、更新和反馈步骤见 [使用指南](USER_GUIDE.md#试用安装更新与反馈)。

- vivo/iQOO 的保护运行时仍明确不支持并安全拒绝启动；不要用额外权限绕过，不宣传已支持全品牌。
- Xiaomi/Redmi 有历史参考证据，不表示当前包或所有同品牌机型已验收。其他 OEM 未验证。
- 已取消固定 24 小时验收：`FIXED_24H_ACCEPTANCE=NOT_REQUIRED`。不设置最低时长，不要求占用日常 Redmi；历史未执行不改成通过。
- 试用包、模拟器或 CI 通过都不关闭真实设备缺陷。**Gate F = FAIL，G/H/I = PENDING；STORE_READY=NO，PRODUCTION_RELEASE_APPROVED=NO。**

本轮可安装包位于仓库外 `D:\AI_code\flowbreak-delivery\20261025-oem-trial-b2e2d01\`：Domestic 为 `FlowBreak-domestic-oem-trial-b2e2d01.apk`，Play 为 `FlowBreak-play-oem-trial-b2e2d01.apk`。两者均为绑定下述 b2e 源码的 Debug 工程试用包，目录附 `trial-manifest.json` / `SHA256SUMS.txt`；文件身份和哈希见 [TESTING](TESTING.md#本轮本地工件与可安装交付)。不要把历史 a15 包或 unsigned Release 当成本轮试用包。

## 源码、分支与云端

| 范围 | 已核实事实 |
| --- | --- |
| 主候选 | [PR #29](https://github.com/guo6x/flowbreak/pull/29)，远端 `fix/fb-p1-08-protection-integrity`；不是已合并的历史 PR #42，未合并 master |
| 本轮运行时代码/测试锚点 | `b2e2d011fce54ac415d8a058a2745c5e71c3bdba`；包含 OEM 后台设置、原生入口容错和首页手动恢复。本轮工件版本 `versionCode=3` / `versionName=trial-oem-b2e2d01`，不是正式发布版本 |
| 本轮修改前已核验云端基线（历史） | `37e1f05ac2adeba9bd72d289a2b208fe18ca84d7` / [Run 40188434455](https://github.com/guo6x/flowbreak/actions/runs/40188434455)：verify SUCCESS，release SKIPPED（PR 策略）；仅认证该历史 SHA，不给本轮源码继承 PASS |
| 后继文档 HEAD / 最新 CI | 本收据为推送前本地核验，b2e 运行时锚点与后继文档 HEAD 分开记录；对应提交的最新 hosted 状态、精确 HEAD 与 Run 链接见 [PR #29 checks](https://github.com/guo6x/flowbreak/pull/29/checks) 和最终 PR 回执。不手工缓存或继承旧 Run，也不把本地构建称为 GitHub CI |
| master | 清理盘点基线 `67d1b7c3ee82ef4f6dd275299d51969869d7db4d`，PR #42 已合并；本轮不向 master 合并任何候选 |
| 仍有独立工作的分支 | [Draft PR #43](https://github.com/guo6x/flowbreak/pull/43) / `integration/flowbreak-closeout-20261024`：含 REST 自动取消失败的 durable pending-marker 补丁，未证明被 PR #29 完全替代，因此保留 |

远端不因“名字旧”而删未合并工作。原工作区与原体验分支中的用户修改不纳入清理提交，也不重置、覆盖或删除。

## 已完成的工程验证

本节记录冻结源码的本地核验，不是未来 SHA 的保证。完整记录、失败后复跑和证据范围集中于 [TESTING](TESTING.md)。

| 检查 | 结果 |
| --- | --- |
| 前端源码 / Web 构建 | 最终 Vitest **396/396**（29 files，maxWorkers=2）；Dashboard **74/74**、Permissions **28/28** 定向通过；本轮 TypeScript/Vite 构建 PASS。未放宽超时或编译门禁 |
| Android 源码验证 / 构建 | Play/Domestic JVM **508/541** 全过，51/52 suites、0 failure/error/skipped；双渠道 Lint 0 error/fatal（44/45 warnings）。最终 Debug、AndroidTest 组装及 unsigned Release 矩阵全部 PASS；Gradle 395 tasks、36m47s，Room schema 无差异。组装不等于执行 instrumentation |
| 品牌 / 来源 / 发布策略 / 隐私静态 | **36/20/8/15** 项通过；**146** 个第一方输入、0 静态违规。来源/发布脚本回归不等于最终二进制核验，静态审计不是运行时网络证明 |
| 签名安全负向回归 | 8/8 PASS；不读取或恢复真实生产密钥，不等于跨机恢复通过 |
| 本轮 Web UI | 绑定 b2e 新 dist 的 UI **59/59 PASS**：45 项 Web 预览 + 14 项模拟桥接（7 场景 × 320/393px），分别仅为 `WEB_PREVIEW_ONLY` / `SYNTHETIC_NATIVE_BRIDGE_ONLY`，不是 Android/OEM 验收 |
| 本轮本地产物 | Web 资产 **51/51** 哈希一致（另有 2 个生成文件）；APK/AAB 的内嵌来源、包名/版本、Manifest、R8、runtime 清单与 unsigned 状态复核 PASS，Release `SHA256SUMS` **7/7** 一致；两渠道 Debug 交付身份与 Debug 证书已核实，未用生产签名 |
| 本轮 AVD | **NOT_COMPLETED / INSTALL_ENVIRONMENT_ERROR**。首份 Play Debug 的 `adb install -r` 返回 `cmd: Failure calling service package: Broken pipe (32)`，本轮未取得 installed APK hash，Play/Domestic 的身份 instrumentation 均未执行。日志 `.tmp/oem-avd-identity-b2e2d01.log`。这不是应用缺陷定论；AVD 的证据范围仍仅 `SIMULATOR_ONLY`，不认证 OEM 核心场景 |
| npm 审计 | 本轮本地显式官方 registry 的 production-high / all-critical 均 **0 vulnerabilities**；默认镜像的历史 404/NOT_IMPLEMENTED 仍为 **BLOCKED/NOT_PASS**。GitHub CI 审计按各自精确 SHA / Run 独立查阅，不由本地结果或历史 CI 继承 |

本轮三项体验改善：两渠道按品牌提供可选后台设置与未知品牌通用指引；原生私有/标准入口失败逐级回退并如实拒绝，API 28+ 标准后台限制保持三态；首页为已配置且仍有开启意图、但原生未生效的保护提供单次手动恢复，预检原生状态，不自动重试，也不以“请求已发出”冒充 ACTIVE。厂商说明与官方来源分层见 [机型兼容参考](OEM_COMPATIBILITY.md)。

独立复审发现的三项新增 P2 均完成 red→green：后台设置先成功再失败不残留成功文案；恢复失败后仍对账更新的暂停意图，补偿停止失败保留双重真实原因；不支持机型缺权限时不再出现授权入口。本轮改动范围内未遗留新增 P0/P1/P2；这不替代真机验收，也不关闭既有已知缺陷。

## 未关闭事项

| 项目 | 状态与剩余条件 |
| --- | --- |
| FB-P1-08 / [Issue #28](https://github.com/guo6x/flowbreak/issues/28) / Gate F | **OPEN_SUPPORTED_SCOPE_RELEASE_BLOCKER / FAIL**。后继 Redmi 报告及保存 APK 已锚定历史源码 `48aaf6a77ca6bae9f17f19e0210f5af3af745126`、APK SHA-256 `ff7225de9626ab20d29dade1fecdbc20b09917ae73f846a550338baad4e7f2c2`，不再称“全部身份缺失”。仍缺约 33 秒失败窗口的完整原生同期因果链，且不是本轮候选真机复验；不臆定根因 |
| FB-P1-05 | **OPEN_UNSUPPORTED_V1_1_0**。vivo/iQOO 后继因果审计缺失败窗口的绝对起止锚点，不能把调度 gap / 分类 / 累计判定为唯一根因；继续 fail closed，不改称已修复 |
| FB-P1-07 / PLATFORM-EXEC-001 | **MITIGATED_ACCEPTED_PLATFORM_LIMITATION**。保留真实历史失败；Android/OEM 不给执行时间期间不保证实时提醒/阻断 |
| FB-P2-02 | **OPEN_NON_BLOCKING_V1_1_0**。recovered usage DB 与 checkpoint durability 非原子；不重开既有 Gate E |
| COMPAT-001 | HyperOS 尽力启动 BlockActivity 可能被拒；强阻断不以其成功为前提，保留兼容观察 |
| [Gate G / #35](https://github.com/guo6x/flowbreak/issues/35) | 跨机器签名恢复 NOT_YET_TESTED，真实低版本→高版本同签名升级 NOT_TESTED；两个独立缺项 |
| [Gate H / #34](https://github.com/guo6x/flowbreak/issues/34)、[#40](https://github.com/guo6x/flowbreak/issues/40) | 发行主体、公开政策 URL、账号、适用资质、最终素材/申报和运行时网络审查未齐备 |
| [Gate I / #36](https://github.com/guo6x/flowbreak/issues/36) | 真实自愿 Beta 尚未开始；整理候选安装包不等于已有试用者或反馈 |

## 历史证据与维护规则

- 2026-10-25 仓库清理：12 个已合并本地旧分支在独立 Git bundle 校验后删除；本地 master 仅快进到现有 origin/master。云端 3 个有效分支保留，未合并 PR。旧 Issue #22（晚到事件）和 #41（体验集成）按修复/证据关闭，#28/34/35/36/40 继续开放并更新。删除一次性/重复文档与两张无引用旧截图，真实验证历史归档保留；详见 [维护审计](docs-audit.md)。
- Redmi R1–R4、iQOO fail-closed、Gate E 支持范围与签名托管均有各自历史证据；详见 [TESTING](TESTING.md)、[RELEASE](RELEASE.md)、[KNOWN_ISSUES](KNOWN_ISSUES.md)。不自动转移为当前包通过。
- 旧 PR、自动化数和历史审计见 [历史目录](archive/progress/README.md)。当前入口不再重复累计旧状态流水账。
- 支持邮箱 `15015091629@163.com` 已获准公开，但收发未验证；公开隐私政策尚未托管。国内办理步骤见 [国内渠道指南](compliance/DOMESTIC_PUBLISHING_GUIDE.md)。
- 本轮在清理基础上改善 OEM 后台设置、原生容错和首页恢复体验，并更新工程试用文档；未操作真实手机、生产签名、注册/付费、tag/release 或商店提交。新工件按源码单独核验，AVD 与 hosted 收据按各自证据记录，不沿用旧包或历史 CI。
