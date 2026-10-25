# CURRENT_STATUS — 项目当前状态

> 本文是当前门禁、候选 SHA 与云端状态的单一事实源；`TESTING.md` 保存绑定到明确源码/Run 的验证证据，`RELEASE.md` 说明发布门禁及限制。HEAD 与 hosted CI 会变化；PR 检查页是云端最新 SHA 和结果的权威来源。
> 2026-08-12 的「真机验收 FAILED / RELEASE BLOCKED」快照已降级为历史记录（见下方「历史状态」），不再代表当前状态。

## 本次验收与交付决定（2026-10-25）

- 产品负责人已取消固定 24 小时连续运行验收，`FIXED_24H_ACCEPTANCE=NOT_REQUIRED`；改为日常使用反馈，不要求最低使用时长或交出日常 Redmi。过去的 `NOT_EXECUTED` 保留历史含义，不改为 PASS。
- 代码、兼容性、复验、签名核验、商店材料和试用反馈在同一批收尾，不以“后续再做”代替完成。详细执行范围及发行人外部依赖见 [RELEASE.md](RELEASE.md)；当前没有因此关闭任何真实 P1、获得签名/设备验收或商店批准。
- 本轮工程收尾前的已提交基线为 `edf5def5af41e3daa9eceb318c55b8ada8bfd93f`；[Run `40152689683`](https://github.com/guo6x/flowbreak/actions/runs/40152689683) 已复查为 SUCCESS。该 CI 只对应该 SHA，不覆盖本轮代码和文档改动；最新候选以 [PR #29 checks](https://github.com/guo6x/flowbreak/pull/29/checks) 的精确 HEAD 为准。
- 发行资料补记：负责人已指定 `15015091629@163.com` 为公开支持邮箱，未进行收发验证；公开政策网页地址、法定发行主体与具体商店账号仍未确认。已准备 [国内渠道办理指南](compliance/DOMESTIC_PUBLISHING_GUIDE.md) 供负责人决定是否办理；没有注册、付费、备案或商店提交。

## 本轮工程收口（2026-10-25）

本轮代码与测试提交：`a15b54d5e88ea59245a724c3cb29eb4820583ca9`。文档后继提交不改变该运行时代码；包含文档的最终推送 HEAD 必须使用自己的 CI 和 artifact，不能借用下方历史绿色 Run。当前目标仍是 [PR #29](https://github.com/guo6x/flowbreak/pull/29)，不是已合并的历史 PR #42。

| 项目 | 本轮结果与证据边界 |
| --- | --- |
| 原生一致性与回调 | `saveTargetApps` 与普通设置共用重载能力门禁，不支持设备保存规则不再错误调起服务；REST 完成/取消回调由主 Looper 分发，不依赖已分离的 Activity。保留持久化成功但重载失败时的原生对账和真实提示。 |
| 轮询与首次体验 | 原生状态单飞轮询等待全部查询结束，失败时丢弃整轮快照，停止后不落迟到结果；补齐慢查询、快速失败与恢复回归。休息页不再在浏览器无用户激活时调用振动；正常操作后及旧 Android WebView 的尽力反馈保持。 |
| 联系与隐私 | 帮助/隐私页共用支持卡片，纯 `mailto:15015091629@163.com`，不自动附带诊断/记录；公开政策文本与自愿邮件处理边界同步。邮箱收发仍未验证。 |
| 前端与页面 | Vitest **336/336**（28 files）；TypeScript/Vite **PASS**；最新构建 UI smoke **45/45 PASS**，13 路由、320/393/1280px，0 console/page error、0 外部请求、0 资源失败。320px 休息页底部按钮经内层滚动完整可达，未修改产品布局。均为 `WEB_PREVIEW_ONLY`。 |
| Android 自动化 | Play JVM **495/495**、Domestic JVM **528/528**，0 failure/error/skipped；双渠道 Lint 0 error/fatal（44/45 warnings）；双渠道 Debug 与 AndroidTest APK 组装成功。替换了错误的模板包名测试，CI 同时组装两渠道 AndroidTest。 |
| 品牌 / 隐私 / 发布机制 | 品牌 **36/36**、Provenance **20/20**、Release policy **8/8**、Privacy static **15/15**；143 个第一方源码输入无静态违规，仍是 `NOT_RUNTIME_NETWORK_PROOF`。Portable signing security **8/8 PASS**，不表示真实密钥恢复通过。 |
| npm 审计 | 本地显式官方 registry 的 production-high 与 all-critical 均 **0 vulnerabilities**；默认镜像历史 `404 / NOT_IMPLEMENTED` 仍 **BLOCKED / NOT_PASS**。GitHub CI 结果另按精确 SHA/Run 记录，不互相替代。 |
| 精确产物 / 模拟器 | 绑定源码 `a15b54d…` 的双渠道 unsigned Release、包内 provenance/独立版本、R8 mapping、runtime 清单与 **7/7 SHA-256 PASS**，Web 资产 **51/51** 一致；API 36 AVD 两渠道各 **2/2 installed-identity PASS**，拉取安装包与本地 Debug APK 哈希一致。详见 [TESTING.md](TESTING.md)；仅为模拟器身份/渠道声明检查，不是 OEM 核心行为或 Gate F 验收。 |
| 独立复审 | 全部 PR 差异及本轮增量已分工只读复审；发现的 Activity 回调和重载门禁问题已修复，新增低风险轮询竞态也补了 red→green 回归。没有据此关闭历史设备缺陷。 |
| 未关闭风险 | `FB-P1-05` 根因未定位，vivo/iQOO 继续 fail-closed；`FB-P1-08` 缺少可归因的同期设备链证据，Gate F **FAIL**。已登记的 `FB-P2-02` 非原子 checkpoint/DB 持久化风险仍 OPEN_NON_BLOCKING，不声称全项目已无 P2。Gate G/H/I **PENDING**。 |

原始 UI 旧构建快照和 v2 振动报错结果保留；只有 `C:\WINDOWS\TEMP\flowbreak-ui-smoke-20261025-final-v3` 是本轮最终页面证据。未操作真实手机、生产密钥、账号注册或商店提交；原主工作区的用户 WIP 未纳入本分支。

## PR #29 先前已验证基线（2026-10-25；历史快照，不覆盖本轮增量）

前序代码修复提交为 `fd9045a3b916486588b93e477935070e640440e5`（父提交 `0cc7a0636102fdd0c0ef1f9c3f4c428cf8a69ee3`）；其后推送的基线 `29573ee115ee8ba25120a3b61ce7ae87b0535fde` 对应 Run `40145072882`。当前本地代码与测试提交为 `0996ca32b8cc471236b8724665dd32a683a72067`，包含 REST 启动超时继续对账、按原生拒绝回执与当前请求 token 安全退出，以及 v3→v4 pullback 历史幂等键回填。本轮首个推送候选 SHA `1070f920adae83fd44e2e4ee04ca7e5109d8f89b` 的 GitHub Actions Run `40151649558` **verify=SUCCESS**、PR `release=SKIPPED`；生产依赖 high 与全依赖 critical npm audit（含回归后的复跑）均报告 **0 vulnerabilities**。Run 还构建并上传了 unsigned artifact ID `12582807009`，其 source SHA 与 `1070f920…` 一致，7/7 SHA-256、内嵌 provenance 均复核通过。本文本轮后续仅更新验证文档，不改运行时代码；该文档提交的最终 HEAD/CI 仍以 [PR #29 checks](https://github.com/guo6x/flowbreak/pull/29/checks) 显示的精确 SHA 为准。目标分支为 `fix/fb-p1-08-protection-integrity`，**未合并 master**。旧 HEAD `b00b9cc75073e77759e28ba5b71f960033cbf1d5` 的 Run `38053414311` 因旧版 `@capacitor/android` critical（GHSA-rvm3-566m-v7fv）失败；该结果只属于旧 SHA。候选历史含 `77c1e749` 对当时 `origin/master=67d1b7c` 的普通分支内合并。原工作目录 `D:\AI_code\flowbreak` 未清理或覆盖。

| 项 | 该轮历史验证状态 |
| ---- | ---- |
| 本轮改动 | 修复设置/目标应用已持久化但服务重载失败时的原生状态对账与真实错误；保护 REST 回放和 Pullback 统计的幂等恢复；诊断读取 Room 实际数据库版本；告知弹窗键盘焦点约束；Overlay 迟到回调隔离；BEGIN_REST 单调时钟恢复与同 token 终态收据保护；RESTING 时钟超时后继续对账、拒绝回执安全退出；v3→v4 回填历史 pullback 幂等键。 |
| 本地自动化（当前隔离候选工作树） | Vitest **326/326**（28 files）；TypeScript/Vite build PASS；UI smoke **44/44**（WEB_PREVIEW_ONLY）；品牌 **36/36**；Provenance **20/20**；Release policy **8/8**；Privacy static **15/15**；Portable signing security regression PASS。 |
| Android（当前隔离候选工作树） | Play JVM **489/489**、Domestic JVM **522/522**，JUnit 汇总 0 failure/error/skipped；双渠道 lint 无 error/fatal（Play 44、Domestic 45 warnings）；双渠道 Debug、Play AndroidTest APK、Play AAB 与 Domestic unsigned Release APK 构建 PASS。AndroidTest APK 只是组装结果，不代表 instrumentation 真机执行。Room schema v4、v3→v4 幂等键回填及重复/畸形事件迁移均由 JVM 覆盖。 |
| 本地产物溯源 | 代码提交 `0996ca32b8cc471236b8724665dd32a683a72067` 的 provenance 与 `SOURCE_COMMIT_SHA` 完整匹配，`dist → Android assets` **50/50 SHA-256 匹配**；Play AAB 与 Domestic APK 的内嵌 provenance/版本均复核通过，双渠道 R8 mapping 与 runtime dependency inventory 齐全，unsigned manifest/SHA-256 清单 **7/7 匹配**。本地工件 `signed=false` 且不是发布包。Hosted artifact `12582807009`（Run `40151649558`、source `1070f920…`）的 provenance、unsigned 状态与 7/7 SHA-256 也复核通过。 |
| npm audit（本地官方 registry） | **PASS — 0 vulnerabilities**（`https://registry.npmjs.org`，生产依赖 high 与全依赖 critical 两项门槛）。 |
| npm audit（本地镜像） | **BLOCKED / NOT_PASS** — `https://registry.npmmirror.com` 的审计端点返回 404/`NOT_IMPLEMENTED`；不得写成 0 漏洞或 PASS，也不覆盖官方 registry 的独立结果。 |
| GitHub CI（首个推送候选） | Run `40151649558` / SHA `1070f920…`：`verify=SUCCESS`、`release=SKIPPED`（PR 策略）；两轮 production-high/all-critical npm audit 均 **0 vulnerabilities**，web preview、Android 双渠道 JVM/Lint/构建、Release provenance 与 manifest 检查全通过。后续文档-only HEAD 的状态以 [PR #29 checks](https://github.com/guo6x/flowbreak/pull/29/checks) 为准。 |
| iQOO V2073A / Android 13 | 当前代码仍按 vivo/iQOO 品牌拒绝启动；兼容修复纳入本次收尾。此前旧候选安装返回 `INSTALL_FAILED_ABORTED: User rejected permissions`；不推断拒绝来源，安装未完成，未启动或改变权限，既有 `com.flowbreak.app.cn` 未触碰。本轮候选未在设备上安装或运行；当前安全拒绝不算兼容成功。 |
| Gate F | **FAIL / NOT_CLOSED** — 候选核心场景复验未完成，历史约 33 秒异常仍未重现/排根因。固定 24 小时验收已取消；不再指定必须 Redmi，但当前被拒绝启动的 iQOO 和受控自动化均不能据此记为设备通过。 |
| Gate G | **PENDING** — 跨机器签名恢复 `NOT_YET_TESTED`，真实 lower-version → current-version 签名升级 `NOT_TESTED`；本候选没有签名发布包。 |
| Gate H / I | **PENDING** — 支持邮箱已由负责人提供但收发未验证；发行主体、公开政策 URL、账号、适用资质与最终商店素材未齐备，真实小规模 Beta 尚未开始。 |
| 发布边界 | `STORE_READY=NO`；`PRODUCTION_RELEASE_APPROVED=NO`。不合并 master、不使用生产签名、不创建正式 tag 或发布应用。 |

当前工程验证不代表设备验收或正式发布批准。本轮没有安装或操作真实手机。Gate F 保留核心场景与状态真实性复验，不再包含固定 24 小时任务，也不要求提供日常 Redmi；未来设备结论须绑定实际可运行的兼容候选、机型、系统和源码，不把安全拒绝、AVD 或自动化写成真机兼容通过。

## 体验分支历史快照（非当前候选）

以下记录仅描述独立体验分支及其各自验证范围，不代表当前集成工作树或最终候选通过：

| 项 | 历史证据 |
| ---- | ---- |
| 分支 | `product/experience-polish-20261018` |
| 本地验证代码与测试提交 | `154a7cfab7da1c7924ae1acb53acd63a931c2626`（文档快照随后单独提交，不改变 App 运行时源码） |
| 本地自动化 | **PASS（该分支）** — Frontend 263、Play JVM 382、Domestic JVM 412；网页 44 项、品牌 36 项、provenance 20 项、release policy 8 项；双渠道 Lint 0 error、Debug 构建通过（详见 [TESTING.md](TESTING.md)） |
| GitHub Actions | **PASS（该分支的验证提交）** — [run 39095168568](https://github.com/guo6x/flowbreak/actions/runs/39095168568)，验证 SHA `147a64567f56290c3b8c0dce0aa934dd2c3b494f`；unsigned verify job 完成，签名 release job 按策略跳过 |
| API 36 AVD | **PASS（范围有限）** — `Medium_Phone_API_36.0` / Android 16；Domestic debug 覆盖安装后可进入权限页，状态为 0/2；从系统“使用情况访问”设置返回后仍为 0/2；未授予权限、未启动保护服务。截图只作为该分支的受控证据 |
| 范围 | 视觉系统、矢量品牌、应用内帮助、统计与失败状态、设置草稿和原生读取保护、诊断分享白名单、Domestic 默认偏好、权限轮询、品牌与网页检查 |
| 新构建真机 / OEM / 发布验收 | **NOT_EXECUTED**；无真实手机；未调用生产签名、未合并、未发布。上述 AVD 不关闭 Gate D/E/F |
| `FB-P2-03` | Domestic 默认值缺陷已随 PR #42 合并至 master 并按台账规则标记 RESOLVED；JVM + API 36 AVD 是该缺陷的针对性证据，不代表 Redmi/OEM 或 Gate F 验收 |

## 既有发布基线速览（2026-09-11，历史验证范围）

下表保留原发布基线的门禁与实机证据，不代表本轮体验分支的最新 master 状态或新包验收。表内问题计数也是该基线当时的结论。

## 2026-10-12 历史发布快照（仅供时间线参考）

以下是当日的发布状态记录；其中 PR #33 的“审查/CI 中”等状态已过时。当前 Issue/PR 与工程集成来源以本文开头及所链接的云端记录为准。

| 项 | 当前可验证状态 |
| --- | --- |
| master | `f9ef7a1f0cbcb3925eb87cd9bd16032428054fde`，PR #31 Capacitor 安全升级已合并；master [CI 38205826590](https://github.com/guo6x/flowbreak/actions/runs/38205826590) **SUCCESS** |
| PR #29 | `b00b9cc75073e77759e28ba5b71f960033cbf1d5`，OPEN，原生 REST 防作弊修复候选和取证文件仍未合并 |
| Gate F | **FAIL**：已报告约 33 秒 Bilibili 前台使用时 REST 页面仍显示倒计时；当时摘要没有足够原生诊断确定根因。新候选无设备复验，24h 稳定性未执行 |
| PR #32 | 验证专用 PR，**CLOSED/UNMERGED**；[CI 38053274608](https://github.com/guo6x/flowbreak/actions/runs/38053274608) 只能证明模拟链与构建成功 |
| PR #33 | 无手机 Gate H/G 准备 + 国内无障碍独立告知；当时仍在审查/CI，后于 2026-10-12 合并；不代表已上架 |
| Gate G | **PENDING_CROSS_MACHINE_RECOVERY**；真实版本升级另需实测 |
| Gate H | **PENDING**；草案和官方依据已形成，公开身份/邮箱/URL、截图、审核、备案等未完成 |
| Gate I | **PENDING**；真实 Beta 未完成 |
| 发布 | `RELEASE_PREPARATION`，**STORE_READY=NO / PRODUCTION_RELEASE_APPROVED=NO** |

下方“当前状态速览”保留的是旧版本设备与已签名构建历史证据，不得用它替代本节的 2026-10-12 事实。


| 项 | 值 |
| ---- | ---- |
| Last verified date | **2026-09-11** |
| Gate E verified behavior baseline | `ccacb6738a44d3bc08da9a4ceac75766316b2521` |
| Master CI | **PASS** — Run `34569928299` |
| Signed candidate | **PASS** — Run `34570309806`（verify + release） |
| Signed artifact | `flowbreak-signed-dry-run-v1.1.0-ccacb6738a44d3bc08da9a4ceac75766316b2521`（ID `10187690618`） |
| Signed Domestic APK SHA256 | `24bcff9dbe365c5cbd0ee65c00999b904850885ee9472f542be3c05f1dfd29a1` |
| Domestic certificate fingerprint | `8d69d1786ea63b05ff3b8d1f5a78266a2fa1eda7b823af0c295cbfdc10e77f20` |
| 阶段 | **RELEASE PREPARATION**（不是 STORE READY，更不是 PRODUCTION RELEASE APPROVED） |
| 支持参考设备 | **Xiaomi / Redmi**；R1–R4 **PASS** |
| 新 Redmi signed smoke | **NOT_EXECUTED** |
| 当前 P0 | **0** |
| Supported-scope release-blocking P1 | **0** |
| `FB-P1-05` | **P1 / OPEN_UNSUPPORTED_V1_1_0**（vivo/iQOO runtime 不支持，fail closed） |
| 发布状态 | 该历史基线当时记录为 **GATE A/B/C PASS；GATE D PASS_SUPPORTED_SCOPE；GATE E PASS_SUPPORTED_SCOPE_WITH_DOCUMENTED_PLATFORM_LIMITATION；GATE F/G/H/I PENDING**。2026-10-18 当前状态为 Gate F **FAIL**、Gate G/H/I **PENDING**（见本文开头） |

## 既有基线的支持设备范围与最终范围验收

- 已验证支持参考：**Xiaomi / Redmi**。Redmi Note 13 Pro 5G（`2312DRA50C` / garnet）/ Android 16 / SDK 36 / HyperOS 3.0（`OS3.0.306.0.WNRCNXM`）的 R1–R4 全部 PASS；该参考证据不等同于本次新 signed build smoke。
- 被测 APK：domestic debug（`com.flowbreak.app.cn`）。复测前在代码工作区 `99fdcc2` 上重新执行前端 build → Capacitor sync → Android assemble 后生成；该重建解决了此前旧 Web bundle 混入问题。正式 Artifact Provenance 链已由 `RELEASE.md` GATE C 建立并通过（2026-08-15，PR #1 实测）。
- 复测报告（外部设备证据工作区）：`reports/R1-R4-retest-2026-08-14.md`、`reports/final-report.md`（第二轮结论）
- `REDMI_R1_R4=PASS`
- `REDMI_NEW_BUILD_SMOKE=NOT_EXECUTED`
- **R1 冷启动前台追踪 ×3 PASS** → `FB-P1-01` RESOLVED（修复 `027af94`）：MainActivity→BrowserActivity 同包跳转事件形态复现，sessionMs 1:1 连续增长、不再卡 0。
- **R2 BLOCKED sticky PASS** → `FB-P1-02` RESOLVED（修复 `9c34fe9`）：离开 29s/31s/64s/约 2min 重进均仍 BLOCKED，sessionMs 未被 30 秒规则重置；completeRest → GRACE 10min 正常；Emergency 长按约 11s → GRACE 5min，emergencyUnlockDay 更新、DB emergency_unlock 正常记录。
- **R3 HyperOS 强阻断 ×3 PASS** → `FB-P1-03` RESOLVED（修复 `3600d97`）：BLOCKED → 打开目标 App → Accessibility 立即 HOME → 顶部横幅（TYPE_ACCESSIBILITY_OVERLAY）可见且含「开始休息」入口；横幅单实例不堆叠、非目标应用正常、无全手机锁死、连续三次重进均继续阻断；核心强阻断不依赖 BlockActivity 启动成功。
- **R4 系统返回键 PASS** → `FB-P2-01` RESOLVED（修复 `99fdcc2`）：物理 Back 与手势 Back 在未保存修改时均弹「有未保存的修改」，应用不退出；无修改保持系统默认退出。
- Smoke：IDLE→COGNITION→BLOCKED→RESTING→GRACE 全链路走通；force-stop 语义正常（不自动复活）；覆盖安装数据保留。

### vivo/iQOO v1.1.0 支持边界

- `vivo V2073A` / `OriginOS 13.5` / Android 13 / SDK 33：最终范围验收已完成。
- `IQOO_SUPPORTED=NO`
- `IQOO_FAIL_CLOSED=PASS`
- `unsupportedDevice=true`、`protectionRuntimeAvailable=false`；UI 与 Diagnostics 如实显示不支持，保护启动入口不可用，未观察到活动保护 FGS。
- Accessibility 启用后，B 站 `tv.danmaku.bili` 短时保持可用，未观察到 HOME、`BlockedTargetBanner` 或 `BlockActivity`。
- 该结论只适用于本次验证的设备与系统版本，不泛化为所有 vivo、iQOO 或 OriginOS 版本。`FB-P1-05` 仍为 P1 OPEN，不标记为 RESOLVED；v1.1.0 明确排除 vivo/iQOO runtime 支持并 fail closed。

### 其他 OEM

- **UNVERIFIED / BETA SCOPE**。其他 OEM 不因未命中当前 fail-closed 集合而自动获得 v1.1.0 支持声明。

## 既有发布基线自动化验证（历史）

- Gate E verified behavior baseline `ccacb6738a44d3bc08da9a4ceac75766316b2521` 的自动化验证：**PASS**。
- Master verify：Run `34569928299` **PASS**。
- Signed workflow：Run `34570309806` verify + release **PASS**。
- Signed artifact：`flowbreak-signed-dry-run-v1.1.0-ccacb6738a44d3bc08da9a4ceac75766316b2521`，ID `10187690618`。
- 精确 Frontend / Android JVM 套件数量以 `TESTING.md` 为准；下方旧计数仅保留为历史记录。

## 既有发布基线的 Gate E 支持范围决策

- `GATE_E = PASS_SUPPORTED_SCOPE_WITH_DOCUMENTED_PLATFORM_LIMITATION`。
- `E1_USAGE_ACCOUNTING = PASS_CARRIED_FORWARD_WITH_EQUIVALENCE`：此前有效 Redmi 真机 PASS + 当前实现的 normal-path equivalence。
- `E2A_HEALTHY_RUNTIME_BLOCKING_LATENCY = PASS_CARRIED_FORWARD_WITH_EQUIVALENCE`：此前签名 Redmi 约 `361s` BLOCKED 证据 + 当前实现的 normal-path equivalence。
- `E2B_EXECUTION_GAP_RECOVERY = PASS_CURRENT_SIGNED_DEVICE_INTEGRATION`：当前签名 `ccacb6738a44d3bc08da9a4ceac75766316b2521` 在 Redmi 上实际执行 reconciliation path，并恢复非零 machine session。
- Android/OEM execution suspension：`NOT_GUARANTEED_PLATFORM_LIMITATION`。进程或 worker 没有执行时间时，不保证实时 overlay 或状态迁移；这不是 HyperOS suspension 已修复的声明。
- `FB-P1-07 = MITIGATED_ACCEPTED_PLATFORM_LIMITATION`：原始 P1 失败与严重性保留，缓解与可恢复影响已记录，残余实时调度边界不属于 v1.1.0 保证。
- `FB-P2-02 = OPEN_NON_BLOCKING_V1_1_0`：recovered usage DB write 与 replay checkpoint durability 非 atomic；不重新打开 Gate E。
- Test A：`TEST_A_RECOVERY_SIGNAL = PASS`；`TEST_A_ACCOUNTING_PRECISION = INCONCLUSIVE`。本次 Gate E closeout 未独立重跑完整 E1/E2 physical test sequence。

## 历史自动化快照（99fdcc2，2026-08-14）

| 层级 | 数量 |
| ---- | ---- |
| Frontend (Vitest) | **151 / 151 PASS**（14 test files） |
| Play JVM (Robolectric) | **244 PASS** |
| Domestic JVM (Robolectric) | **254 PASS**（比 Play 多出的测试包含新的 Accessibility 强阻断回归测试） |
| RecoveryIntegration | 23 @Test（**仍是 23，不是 26**） |
| Room migrations | **6 / 6** |

> `assemblePlayDebugAndroidTest` = AndroidTest APK **successfully ASSEMBLED**，**不等于** instrumentation tests 已在真机执行。真机验证以外部设备证据为准（`TESTING.md`）。

## 已关闭的 P1/P2（历史保留）

- `FB-P1-01` 前台追踪冷启动失效 → **RESOLVED**（R1 ×3，修复 `027af94`）
- `FB-P1-02` BLOCKED 30 秒离开绕过 → **RESOLVED**（R2，修复 `9c34fe9`）
- `FB-P1-03`（候选）BlockActivity 后台启动被 HyperOS 拒绝 → **RESOLVED**（R3 ×3，修复 `3600d97`）
- `FB-P2-01` 系统返回键不拦截未保存修改 → **RESOLVED**（R4，修复 `99fdcc2`）

`FB-P2-03` 已通过针对性 JVM + API 36 AVD 验证，并随 PR #42 合入 master；[KNOWN_ISSUES.md](KNOWN_ISSUES.md) 已将该项标记为 RESOLVED。该关闭仅针对 Domestic 默认值一致性，不把 AVD 结果升级为真机/OEM 验收或 Gate F 结论。

完整历史（原现象、root cause、fix SHA、自动化回归覆盖、设备证据）保留在 `KNOWN_ISSUES.md` 的 Resolved 区。

## 当前未解决问题

1. **`FB-P1-08`（P1 / OPEN_SUPPORTED_SCOPE_RELEASE_BLOCKER；Issue #28 OPEN）**：Protection Integrity 可能在必要运行能力缺失或状态过期时仍呈现保护有效。历史设备摘要记录 Bilibili 前台约 33 秒时 REST 画面仍在倒计时；没有相应原生状态/tick/分类器/取消证据，因此根因未定。它阻塞 Gate F；模拟器与代码侧测试不关闭此门禁。
2. **`FB-P1-05`（P1 / OPEN_UNSUPPORTED_V1_1_0）**：vivo/iQOO native tick path 的原始兼容性缺陷未解决。v1.1.0 不支持 vivo/iQOO runtime，而是通过 `UNSUPPORTED_DEVICE_FOR_RELIABLE_MONITORING` fail closed；不得降级 severity，也不得标记为 RESOLVED。
3. **`COMPAT-001`（NON-BLOCKING / COMPATIBILITY OBSERVATION，OPEN OBSERVATION）**：HyperOS 仍可能拒绝尽力而为的 `tryStartBlockActivity` 后台启动，但强阻断已不依赖它。
4. **`FB-P1-07 / PLATFORM-EXEC-001`（P1 / MITIGATED_ACCEPTED_PLATFORM_LIMITATION）**：原始 Redmi execution gap 仍是历史有效失败；PR #26 提供历史恢复缓解，实时 enforcement 在 OS/OEM 不给执行时间期间不属于 v1.1.0 保证，不阻塞当前修订后的 Gate E 支持范围。
5. **`FB-P2-02`（P2 / OPEN_NON_BLOCKING_V1_1_0）**：recovered usage DB write 与 replay checkpoint durability 非 atomic；不重新打开 Gate E。
6. **Gate F = FAIL**：Issue #28 / `FB-P1-08` 仍开放，Protection Integrity 未关闭；固定 24 小时验收已移除，历史未执行不改称通过。
7. **Gate G = PENDING**：`CROSS_MACHINE_SIGNING_RECOVERY=NOT_YET_TESTED`，且 `TRUE_VERSION_UPGRADE=NOT_TESTED`；这是两个独立未完成项。
8. **Gate H = PENDING**：商店/合规材料和发行方资料尚未齐备或提交。
9. **Gate I = PENDING / NOT_STARTED**：小规模 Beta 未开始。

> 历史 RELEASE ENGINEERING GAP（产物溯源）已关闭：曾出现「本地 APK 原生 dex 已更新、Web bundle 仍旧」的不一致产物与「CI 未持久上传 artifacts」两个缺口，已由 GATE C 实现并实测关闭（`RELEASE.md` GATE C = PASS，`TESTING.md` 产物溯源）。

## 既有签名托管记录与 GATE G

下列状态沿用已记录的独立托管证据，本轮未操作密钥或重新执行恢复。仓库内 PortableSigningVault 创建/恢复脚本目前是 fail-closed 占位工具，不应把它们的安全拒绝测试等同于恢复 PASS。

CURRENT_PC_ROLE = `PRIMARY_WORKSTATION`

FINAL_SIGNING_IDENTITY = `GENERATED`

GITHUB_FINAL_SIGNING_SECRETS = `PROVISIONED`

PORTABLE_VAULT = `LOCAL_ENCRYPTED_AGE_VAULT`

LOCAL_RESTORE = `PASS`

OFF_MACHINE_BACKUP = `PASS`

CROSS_MACHINE_SIGNING_RECOVERY = `NOT_YET_TESTED`

GATE_G = `PENDING_CROSS_MACHINE_RECOVERY`

SIGNED_INSTALL = `PASS`

SIGNED_REPLACEMENT = `PASS`

DATA_PRESERVATION = `PASS`

TRUE_VERSION_UPGRADE = `NOT_TESTED_NO_VALID_LOWER_FINAL_SIGNED_BUILD`

## 发布状态边界（重要）

- **RELEASE PREPARATION ≠ STORE READY ≠ PRODUCTION RELEASE APPROVED**。
- 已有历史证据：**GATE A / B / C / D / E（各自 SHA 与支持范围内）**；不自动继承为新候选验收。Gate E 的 `PASS_SUPPORTED_SCOPE_WITH_DOCUMENTED_PLATFORM_LIMITATION` 表示健康运行时与执行间隔恢复证据可接受，同时明确 Android/OEM 无执行时间期间不保证实时 enforcement。已知不可靠的 vivo/iQOO 在 v1.1.0 明确不支持且 fail closed。
- 仍需完成：**GATE F / G / H / I**。其中 Gate F 当前为 **FAIL**，Gate G/H/I 为 **PENDING**；详细事实见本文开头与 `RELEASE.md`。
- `FB-P1-05` 仍为 **P1 / OPEN_UNSUPPORTED_V1_1_0**；由于 vivo/iQOO 在 v1.1.0 明确不支持并 fail closed，它不阻塞 Gate D 或当前 v1.1.0 release scope，也不是 Gate E 问题。
- `FB-P1-07` 仍为 **P1 / MITIGATED_ACCEPTED_PLATFORM_LIMITATION**；在修订后的执行调度边界下，它不阻塞 Gate E。`FB-P2-02` 为 non-blocking follow-up。

## 历史状态（2026-08-12 快照，不再代表当前）

- 2026-08-12：physical-device acceptance **FAILED**，RELEASE BLOCKED due open P1（`FB-P1-01/02/03`、`FB-P2-01` OPEN）。
- 该快照的详细记录保留在 `KNOWN_ISSUES.md` 的 Resolved 历史与 `TESTING.md` 的首轮记录中；旧结论不得冒充当前状态。

## 本次收尾路线

按 [RELEASE.md](RELEASE.md) 的“本次完整收尾”统一执行：兼容与核心问题修复、完整工程复验、已有签名与升级/恢复核验、最终商店素材及发行字段、实际日常试用反馈一并汇总；不再把其中事项归为以后处理。固定 24 小时验收已取消，日常 Redmi 不作为前置设备。真实发行人资料、独立恢复环境、账号和平台审批等尚缺外部条件仍据实列明，不用文档声明代替实际完成。
