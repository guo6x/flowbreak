# docs 文档导航

本文档目录是 FlowBreak 的**当前文档体系**。`docs/archive/` 下的内容全部是历史资料，**不具有当前规范效力**，不得作为当前产品规则、架构或测试状态依据。

## 阅读顺序

第一次接手项目（新 AI / 新开发者）：

1. [`docs/PRODUCT.md`](PRODUCT.md) — 产品是什么、现在应该怎么工作
2. [`docs/ARCHITECTURE.md`](ARCHITECTURE.md) — 当前技术架构与数据流
3. [`docs/CURRENT_STATUS.md`](CURRENT_STATUS.md) — 项目现在在哪一步
4. [`docs/KNOWN_ISSUES.md`](KNOWN_ISSUES.md) — 当前已知缺陷与验证状态
5. [`docs/DEVELOPMENT.md`](DEVELOPMENT.md) — 如何构建、测试、提交

准备测试时：先读 [`docs/TESTING.md`](TESTING.md)。

准备发布时：先读 [`docs/RELEASE.md`](RELEASE.md)。

做文档变更时：先读 [`docs/docs-audit.md`](docs-audit.md) 了解本体系如何建立。

## 不同事实类型的 Source of Truth

| 事实类型 | 唯一归属 |
| ---- | ---- |
| 产品目标行为（应该怎么工作） | `docs/PRODUCT.md` |
| 当前代码实际上实现了什么 | 当前已合并源码 |
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
| 技术架构、组件、数据流 | `docs/ARCHITECTURE.md` |
| 项目当前状态、基线、阶段 | `docs/CURRENT_STATUS.md` |
| 自动化测试层级、CI、真机证据标准 | `docs/TESTING.md` |
| 未解决问题（Bug） | `docs/KNOWN_ISSUES.md` |
| 发布门禁与发布状态 | `docs/RELEASE.md` |
| 开发环境、命令、提交规范 | `docs/DEVELOPMENT.md` |
| 版本变更历史 | 根目录 `CHANGELOG.md` |
| 隐私政策 | 根目录 `PRIVACY.md` |
| 第三方许可 | 根目录 `THIRD_PARTY_NOTICES.md` |
| 历史资料 | `docs/archive/`（不具规范效力） |

## 防过时规则

- 当前 HEAD、测试数量、CI Run ID、APK SHA 等易变事实**只允许**出现在 `CURRENT_STATUS.md` 和 `TESTING.md` 的验证快照中，其他文档只引用不复制。
- 同一事实只写一次；新文档发现重复内容时应指向唯一归属文档。
- 仓库文档不依赖任何一台电脑的绝对路径；设备原始证据不入 Git 仓库，由测试工作区外部保存（见 `TESTING.md` 验证快照元数据说明）。
