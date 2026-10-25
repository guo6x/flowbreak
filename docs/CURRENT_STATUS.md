# CURRENT_STATUS — 项目当前状态

> 当前工程与发布状态的入口。产品规则见 [PRODUCT](PRODUCT.md)，缺陷台账见 [KNOWN_ISSUES](KNOWN_ISSUES.md)，源码绑定验证见 [TESTING](TESTING.md)。历史报告不认证后继候选。

## 现在可以怎样使用

**仅供明确知情、自愿的小范围工程试用，不是正式稳定版，不是商店发布包。** 可安装的是 Debug 签名 APK；CI 的 unsigned Release APK/AAB 不能直接安装或当作正式分发包。安装、更新和反馈步骤见 [使用指南](USER_GUIDE.md#试用安装更新与反馈)。

- vivo/iQOO 的保护运行时仍明确不支持并安全拒绝启动；不要用额外权限绕过，不宣传已支持全品牌。
- Xiaomi/Redmi 有历史参考证据，不表示当前包或所有同品牌机型已验收。其他 OEM 未验证。
- 已取消固定 24 小时验收：`FIXED_24H_ACCEPTANCE=NOT_REQUIRED`。不设置最低时长，不要求占用日常 Redmi；历史未执行不改成通过。
- 试用包、模拟器或 CI 通过都不关闭真实设备缺陷。**Gate F = FAIL，G/H/I = PENDING；STORE_READY=NO，PRODUCTION_RELEASE_APPROVED=NO。**

## 源码、分支与云端

| 范围 | 已核实事实 |
| --- | --- |
| 主候选 | [PR #29](https://github.com/guo6x/flowbreak/pull/29)，远端 `fix/fb-p1-08-protection-integrity`；不是已合并的历史 PR #42，未合并 master |
| 运行时代码/测试锚点 | `a15b54d5e88ea59245a724c3cb29eb4820583ca9`；本次清理只改文档，不改变该运行时代码 |
| 清理前已核验云端基线 | `dca4f205412b17cd34d414d879d6857a21b16ae2` / [Run 40170229748](https://github.com/guo6x/flowbreak/actions/runs/40170229748)：verify SUCCESS，release SKIPPED（PR 策略）；artifact `12589874425` 已独立复核，详见 TESTING |
| 最新文档 HEAD / CI | 以 [PR #29 checks](https://github.com/guo6x/flowbreak/pull/29/checks) 和 PR 收口回执为准；清理后的提交必须通过自己的 CI，不能继承上行绿色结果 |
| master | 清理盘点基线 `67d1b7c3ee82ef4f6dd275299d51969869d7db4d`，PR #42 已合并；本轮不向 master 合并任何候选 |
| 仍有独立工作的分支 | [Draft PR #43](https://github.com/guo6x/flowbreak/pull/43) / `integration/flowbreak-closeout-20261024`：含 REST 自动取消失败的 durable pending-marker 补丁，未证明被 PR #29 完全替代，因此保留 |

远端不因“名字旧”而删未合并工作。原工作区与原体验分支中的用户修改不纳入清理提交，也不重置、覆盖或删除。

## 已完成的工程验证

本节数字绑定上述运行时代码，不是未来 SHA 的保证。完整命令、指纹、失败后复跑和证据范围集中于 [TESTING](TESTING.md)。

| 检查 | 结果 |
| --- | --- |
| 前端 / 构建 / Web 逐页 | Vitest 336/336；TypeScript/Vite PASS；13 路由、三种宽度 UI 45/45，0 console/page error、0 外部请求。仅 WEB_PREVIEW_ONLY |
| Android | Play/Domestic JVM 495/528 全过；双渠道 Lint 0 error/fatal（44/45 warnings）；双渠道 Debug、AndroidTest 组装、unsigned Release 构建通过 |
| 品牌 / 来源 / 发布策略 / 隐私静态 | 36/20/8/15 项通过；143 个第一方输入无静态违规。不是运行时网络证明 |
| 签名安全负向回归 | 8/8 PASS；不读取或恢复真实生产密钥，不等于跨机恢复通过 |
| 本地产物 / AVD | Web 资产 51/51、unsigned 清单 7/7；API 36 AVD 两渠道各 2/2 installed-identity PASS，拉取包哈希与同批 Debug APK 一致。不是 OEM 核心场景验收 |
| npm 审计 | 本地显式官方 registry 和已列明 GitHub CI 的 production-high/all-critical 均 0 vulnerabilities；本地默认镜像的历史 404/NOT_IMPLEMENTED 仍为 BLOCKED/NOT_PASS |

原生设置/目标应用的持久化成功但重载失败会对账并如实提示；轮询竞态、无 Activity 回调、受限设备重载和无用户激活振动等代码回归已修复。代码复审不替代设备验收，也不表示所有已知 P1/P2 均已解决。

## 未关闭事项

| 项目 | 状态与剩余条件 |
| --- | --- |
| FB-P1-08 / [Issue #28](https://github.com/guo6x/flowbreak/issues/28) / Gate F | **OPEN_SUPPORTED_SCOPE_RELEASE_BLOCKER / FAIL**。历史约 33 秒 REST 异常缺原生同期链证据，当前候选核心状态尚缺真实设备复验；不臆定根因 |
| FB-P1-05 | **OPEN_UNSUPPORTED_V1_1_0**。vivo/iQOO 计时兼容根因未定位，继续 fail closed，不改称已修复 |
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
- 本次仅清理文档、已解决 Issue 和可恢复的合并分支；不操作真实手机、生产签名、注册/付费、tag/release 或商店提交。
