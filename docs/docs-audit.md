# 文档维护审计

维护日期：2026-10-25。面向维护者与集成审查者，用于说明文档职责、清理依据与更新规则；本页不重复维护易变的 HEAD、CI、测试数量或发布门禁结果。

## 1. 读者与职责分层

| 读者 / 目的 | 入口与职责 |
| --- | --- |
| 新用户了解产品与使用边界 | 根 [README](../README.md)；文档导航见 [docs/README.md](README.md) |
| 产品维护者核对目标规则 | [PRODUCT.md](PRODUCT.md)；实际实现以源码为准，不以愿景蓝图推定已支持 |
| 开发者修改与恢复工程 | [ARCHITECTURE.md](ARCHITECTURE.md)、[DEVELOPMENT.md](DEVELOPMENT.md)、[WORKSTATION_RECOVERY.md](WORKSTATION_RECOVERY.md) |
| 测试与问题排查 | [TESTING.md](TESTING.md) 维护绑定源码或产物的验证证据；[KNOWN_ISSUES.md](KNOWN_ISSUES.md) 维护缺陷及证据缺口 |
| 发行与合规决策 | [RELEASE.md](RELEASE.md) 和 [compliance/](compliance/)；隐私事实见 [PRIVACY.md](../PRIVACY.md) |

动态状态集中在 [CURRENT_STATUS.md](CURRENT_STATUS.md)，具体测试证据集中在 [TESTING.md](TESTING.md)。其他页面引用这些入口，不复制易过期的数字。历史来源、当前候选与未验证事项必须分开记录。

## 2. 本轮删除与归档

以下文件经全文审查后移除，均可从 Git 历史恢复；没有删除外部设备日志、截图、签名材料或缺陷证据。

| 已删除文件 | 原因 / 有效信息去向 |
| --- | --- |
| `archive/prompts/FlowBreak 执行提示词（第一批）.md` | 一次性 AI 任务分派已结束，旧执行约束不应作为当前任务；现行协作规则见 DEVELOPMENT |
| `archive/prompts/README.md` | 对应提示词删除后不再有目录说明用途 |
| `archive/progress/project-memory.md` | 重复的 AI 会话/项目流水账；有效规则已并入 PRODUCT、ARCHITECTURE、DEVELOPMENT |
| `archive/progress/RELEASE_CHECKLIST.md` | 全未勾选、无独立验证结果的旧清单；现行发布条件由 RELEASE 统一维护 |
| `INTEGRATION_ACCEPTANCE_DRAFT.html` | 重复状态与测试摘要的视觉草稿，无独立验收结论；减少多份状态副本，保留既有外部截图作为历史展示证据 |
| 根目录 `profile-bottom.png`、`stats-chart.png` | 旧界面遗留截图，已目视确认、全仓无运行时或现行文档引用，仅被历史审计列名；不属于品牌源资产或真实验收证据 |

原 2026-08-12 审计及 2026-08-14 同步记录迁至 [历史审计](archive/progress/docs-audit-20260814.md)，正文保持冻结，不把旧表当作当前文件清单。旧产品设计、构建指纹、验证和缺陷历史继续保留于 [archive/](archive/README.md)，不因过期改写或删除真实结果。

## 3. 必须保留的边界

- 固定 24 小时验收已取消；历史 `NOT_EXECUTED` 不因此变成 `PASS`，也不重新要求借用日常 Redmi。
- AVD 身份检查、自动化与静态审计不替代真机/OEM核心流程、运行时网络或实际签名升级验收；兼容性与未解决缺陷按现行事实源保留。
- 签名密钥、密码、证件与原始设备日志不得加入 Git；清理不授权生产签名、商店发布、真实设备操作或删除外部材料。
- 未合并且含独有提交的本地/云端分支、被工作区引用的分支及用户 WIP 必须保留；不能仅凭名称旧、无活跃 PR 或 CI 通过就删除、合并或覆盖。
- Issue 只在代码与相应证据确实支持时关闭。取消验收时长、归档文档或交付工程候选不能关闭真实缺陷。

## 4. 后续更新规则

代码、依赖、权限、隐私处理、构建或发行条件变化时，同批更新对应职责文档；提交交付后核对实际云端 HEAD 和 CI，再记录结果。每条验证结论注明源码/产物与范围，不用旧绿色记录认证新提交，不把本地镜像审计受阻写成通过。

当前入口保持精简；重复草稿和一次性提示词可以删除，真实验证/缺陷历史则冻结归档。调整路径或删除文件后检查 Markdown/HTML 入站链接和文档索引，避免悬空引用。
