# docs/archive/progress — 历史进度与验证记录

这些文件为历史任务进度报告/验证记录，**仅为历史记录**，不得作为当前测试状态或验收结论依据。

- 任务完成报告（progress_report*、progress-*、walkthrough）代表当时执行 AI 的自述，**不等同于独立验收结果**。
- 历史报告中的「无遗留问题」「完全解决」「最终完成」等表述**不能覆盖**后来的真机证据（2026-08-12 Redmi 真机结论：核心验收 FAILED；该轮发现的 P1/P2 已于 2026-08-14 R1–R4 复测后 RESOLVED，见 `KNOWN_ISSUES.md`）。
- 当前验证状态以 `docs/TESTING.md` 与 `docs/CURRENT_STATUS.md` 为准；外部真机证据目录：`D:\ai_code\flowbreak-device-evidence\`。

| 文件 | 性质 | 现行信息去向 |
| ---- | ---- | ---- |
| [docs-audit-20260814.md](docs-audit-20260814.md) | 2026-08-12 文档审计及 2026-08-14 同步原文，已冻结 | 当前维护规则 → [docs-audit.md](../../docs-audit.md) |
| [verification-snapshots-through-20261025.md](verification-snapshots-through-20261025.md) | 旧 PR29、PR42、体验分支与早期自动化的 SHA 绑定快照 | 当前证据与方法 → [TESTING.md](../../TESTING.md) |
| `VERIFICATION.md` | Beta 1.1 验证记录（2026-07-17） | 测试层级/证据标准 → `TESTING.md`；状态 → `CURRENT_STATUS.md` |
| `progress_report.md` | T1 进度报告（2026-06-07） | 历史记录 |
| `progress_report_fatigue_alignment.md` | T3 进度报告（2026-06-07） | 历史记录 |
| `progress_report_usage_events.md` | T2 进度报告（2026-06-07） | 统计算法 → `ARCHITECTURE.md` |
| `progress-achievements-unification.md` | T4 进度报告（2026-06-07） | 历史记录 |
| `walkthrough.md` | 休息页美化进度文档 | 历史记录 |

2026-10-25 清理后不再保留重复的 `project-memory.md` 和全未勾选的 `RELEASE_CHECKLIST.md`；当前规范见 `docs/PRODUCT.md`、`docs/ARCHITECTURE.md`、`docs/DEVELOPMENT.md` 与 `docs/RELEASE.md`，原文仍可从 Git 历史恢复。
