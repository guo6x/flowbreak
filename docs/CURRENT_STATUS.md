# CURRENT_STATUS — 项目当前状态

> 这是「项目现在在哪」的唯一当前视图。易变事实（HEAD、测试数、Run ID、APK SHA）只出现在本文档与 `TESTING.md` 的验证快照中。

## 当前状态速览

| 项 | 值 |
| ---- | ---- |
| Last verified date | 2026-08-12（真机） |
| 文档基线 | `a06a772bd0b12bc6a31e78d91a4d634ec7027437` |
| 阶段 | 真机验收阶段（MVP 未发布） |
| CI | 绿（a06a772 基线；master 上后续 4 个修复提交的 CI 结果以最新 CI 为准） |
| 真机验收 | **FAILED — blockers open**（见 `KNOWN_ISSUES.md`） |
| 发布状态 | **RELEASE BLOCKED**（见 `RELEASE.md`） |

## 设备验证（2026-08-12 快照）

- 设备：Redmi Note 13 Pro 5G（`2312DRA50C` / garnet）/ Android 16 / SDK 36 / HyperOS 3.0（`OS3.0.306.0.WNRCNXM`）
- 测试 APK：domestic debug，`com.flowbreak.app.cn`，versionCode 2 / versionName 1.1.0
- 结论：**核心验收暂未通过**。
- 已确认缺陷：`FB-P1-01` 冷启动前台追踪失效、`FB-P1-02` BLOCKED 可被离开 30 秒绕过、`FB-P1-03`（候选）BlockActivity 后台启动被 HyperOS 拒绝、`FB-P2-01` 系统返回键不拦截未保存修改。
- 已真实通过（以外部设备证据 `final-report.md` 为准）：PERCEPTION / COGNITION / BLOCKED 触发、Overlay、紧急使用、每日一次语义、5 分钟 Emergency GRACE、DB `emergency_unlock` 事件、Domestic Accessibility 强阻断（HOME 回桌面）、重启恢复（T41/T42）、覆盖安装。
- 已执行验证但发现缺陷（不列为通过）：Android 系统返回键 → `FB-P2-01`；BLOCKED 离开/恢复语义（离开 30 秒可绕过）→ `FB-P1-02`。T40 验证的精确行为仅为「BLOCKED 期间持续停留在目标应用内无自动解除」，不覆盖离开后的解除语义。

## master 演进说明（重要）

- 文档基线为 `a06a772`；之后 master 已合入 4 个代码修复提交（最新 `99fdcc2`：foreground bootstrap、sticky BLOCKED、domestic BAL 移除、返回键修复）。
- 这些修复**尚未**在 Redmi 真机复测；P1/P2 在复测通过前**保持 OPEN**（`KNOWN_ISSUES.md`）。
- 本文档分支（`docs/consolidate-project-docs`）等待代码修复任务给出 Documentation Delta 后进行第二阶段同步，再合并。

## 自动化测试快照（a06a772 验证快照）

| 层级 | 数量 |
| ---- | ---- |
| Frontend (Vitest) | 148 |
| Play JVM (Robolectric) | 220 |
| Domestic JVM (Robolectric) | 220 |
| RecoveryIntegration | 23 |
| Room migrations | 6 |

> 这些数字属于 a06a772 验证快照，未来代码变化后以最新 CI 为准（master 含修复提交后本机本地结果已增至 Play 244 / Domestic 254，不属于本快照）。

## 当前公开阻塞（Open release blockers）

1. `FB-P1-01` foreground tracker 冷启动失效（核心保护高频路径）
2. `FB-P1-02` BLOCKED 30 秒离开绕过（强阻断无强制力）
3. `FB-P1-03`（候选）BlockActivity 后台启动被 HyperOS 拒绝
4. `FB-P2-01` 系统返回键行为不一致

## 下一里程碑

- 等待代码修复任务的：Documentation Delta、final SHA、CI 结果、Redmi 精准复测 R1–R4。
- 复测通过 → 关闭对应 KNOWN_ISSUES → 更新 RELEASE 门禁 → 第二阶段文档同步 → 合并 docs 分支。
