# docs 文档导航

本文档目录是 FlowBreak 的**当前文档体系**。`docs/archive/` 下的内容全部是历史资料，**不具有当前规范效力**，不得作为当前产品规则、架构或测试状态依据。

## 阅读顺序

第一次使用产品：先读 [使用指南](USER_GUIDE.md)。维护视觉和图标：读 [品牌与界面设计参考](BRAND.md)。这两份分别是面向用户的操作指南与面向开发者的设计参考，不取代产品规范或验收证据。

查找手机后台设置、理解品牌兼容边界：读 [后台设置与机型兼容参考](OEM_COMPATIBILITY.md)。其中的设置引导和自动化覆盖不等于真机验证通过。

第一次接手项目（新 AI / 新开发者）：

1. [`docs/PRODUCT.md`](PRODUCT.md) — 产品是什么、现在应该怎么工作
2. [`docs/ARCHITECTURE.md`](ARCHITECTURE.md) — 当前技术架构与数据流
3. [`docs/CURRENT_STATUS.md`](CURRENT_STATUS.md) — 项目现在在哪一步
4. [`docs/KNOWN_ISSUES.md`](KNOWN_ISSUES.md) — 当前已知缺陷与验证状态
5. [`docs/DEVELOPMENT.md`](DEVELOPMENT.md) — 如何构建、测试、提交

准备测试时：先读 [`docs/TESTING.md`](TESTING.md)。

准备发布时：先读 [`docs/RELEASE.md`](RELEASE.md)；Gate F / `FB-P1-08` 的历史复验方案与当前取证限制见 [`FB-P1-08-FINAL-DEVICE-HANDOFF.md`](FB-P1-08-FINAL-DEVICE-HANDOFF.md)，它不是当前可执行的验收协议。

想先了解国内商店账号和资质怎么办理：读 [国内渠道办理指南](compliance/DOMESTIC_PUBLISHING_GUIDE.md)。面向发行负责人，说明步骤、材料和是否启动的决策点；不是已办理证明，也不要求现在注册、购买或提交。

跨域审查集成风险时：参考 [`INTEGRATION_RISK_MATRIX.md`](INTEGRATION_RISK_MATRIX.md)；它是设计风险索引，不是当前门禁/测试结果的事实源。重复状态的离线 HTML 草稿已移除，避免同时维护多份结论。

做文档变更时：先读 [现行维护审计](docs-audit.md)；体系建立时的历史审计已归档。

## 不同事实类型的 Source of Truth

| 事实类型 | 唯一归属 |
| ---- | ---- |
| 产品目标行为（应该怎么工作） | `docs/PRODUCT.md` |
| 当前代码实际上实现了什么 | 明确选定 SHA 的源码；候选与 master 不混用 |
| 自动化回归是否通过 | 最新 CI / 自动化测试结果（见 `docs/TESTING.md`） |
| 真机/厂商系统上实际上发生什么 | 最新真实设备验收证据（见 `docs/TESTING.md` 设备矩阵） |
| 当前项目是否可以发布 | `docs/CURRENT_STATUS.md` + `docs/KNOWN_ISSUES.md` + `docs/RELEASE.md` |
| 历史背景 | `docs/archive/` |

规则（所有 AI 必须遵守）：

- 自动化测试**不能**覆盖或否定真实设备已经稳定复现的缺陷。
- 源码与真机证据冲突时：源码说明「实现意图/当前实现」，真机证据说明「真实运行结果」；两者的差异应登记为 Bug（`docs/KNOWN_ISSUES.md`），而不是静默修改文档。
- 对于「产品应该怎么工作」的目标行为，`docs/PRODUCT.md` 是唯一规范。如果源码违反 `PRODUCT.md`，这是 Bug，不是自动把 PRODUCT 改成错误源码行为的理由。

## 各文档职责（单一事实源）

| 信息类型 | 唯一归属 |
| ---- | ---- |
| 产品行为规范（现在应该怎么工作） | `docs/PRODUCT.md` |
| 新用户操作与常见问题 | `docs/USER_GUIDE.md`（引用产品规范） |
| 后台设置操作、品牌指引与官方来源 | `docs/OEM_COMPATIBILITY.md`（不替代产品规范或真机证据） |
| 品牌资产与界面设计参考 | `docs/BRAND.md` |
| 技术架构、组件、数据流 | `docs/ARCHITECTURE.md` |
| 项目当前状态、基线、阶段 | `docs/CURRENT_STATUS.md` |
| 自动化测试层级、CI、真机证据标准 | `docs/TESTING.md` |
| 未解决问题（Bug） | `docs/KNOWN_ISSUES.md` |
| 发布门禁与发布状态 | `docs/RELEASE.md` |
| FB-P1-08 / Gate F 历史方案与当前取证限制 | `docs/FB-P1-08-FINAL-DEVICE-HANDOFF.md`（当前诊断合同尚未就绪，不是可执行验收协议或通过证据） |
| 国内渠道账号与资质办理步骤 | `docs/compliance/DOMESTIC_PUBLISHING_GUIDE.md`（发行人操作指南；字段现况引用隐私说明和发布状态） |
| 跨域集成风险 | `docs/INTEGRATION_RISK_MATRIX.md`（风险索引；不取代候选/门禁/测试事实源） |
| 开发环境、命令、提交规范 | `docs/DEVELOPMENT.md` |
| 版本变更历史 | 根目录 `CHANGELOG.md` |
| 产品隐私说明（尚非正式托管政策网页） | 根目录 `PRIVACY.md` |
| 第三方许可 | 根目录 `THIRD_PARTY_NOTICES.md` |
| 历史资料 | `docs/archive/`（不具规范效力） |

## 防过时规则

- 当前 HEAD、测试数量、CI Run ID、APK SHA 等易变事实集中在 `CURRENT_STATUS.md` 和 `TESTING.md` 的验证快照，其他当前文档只引用。已冻结的历史归档可保留原始数值；仓库外每批交付包的版本/hash清单只认证该批工件，不是另一个项目状态源。
- 风险矩阵可记录需要复核的设计风险，但不得固化候选 SHA、CI、产物或门禁的易变结果。候选最终化后应更新 `CURRENT_STATUS.md` / `TESTING.md`，而不是复制一份状态表。
- 同一事实只写一次；新文档发现重复内容时应指向唯一归属文档。
- 仓库文档不依赖任何一台电脑的绝对路径；设备原始证据不入 Git 仓库，由测试工作区外部保存（见 `TESTING.md` 验证快照元数据说明）。
