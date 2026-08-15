# Changelog

## Unreleased

- 新增近 7 天效果验证：记录休息后回刷、成功拉回、回刷时长和每日主观反馈，不用积分替代真实效果。
- 完成休息后创建独立的 10 分钟观察窗口；未回刷，或回刷后主动离开全部目标应用满 30 秒，记为成功拉回。
- 新增运行诊断与脱敏诊断导出，包含版本、渠道、服务心跳、权限和本地记录数量，不包含姓名、受限应用名称或使用明细。
- Room 数据库升级至 v3，保留已有数据并新增效果结果和每日反馈字段。
- 统一原生前台服务、Room 和界面统计：应用使用扫描只在前台服务执行，统计/仪表盘/导出从本地数据库读取。
- 休息计时改为原生时间戳校验，完整休息后才会写入 10 分钟访问窗口；重复提交不会重复奖励。
- 修复锁屏、解锁、离开目标应用 30 秒后的连续会话边界（30 秒重置仅适用于尚未进入 BLOCKED 的连续会话），并批量写入使用时长以降低存储压力。
- 删除未真正由 Android 原生调度的“定时提醒”入口，以及未经验证的疲劳准确率宣称。
- Play 渠道不再提供“整个微信=视频号”的阻断选项；国内版明确标注微信视频号强阻断为实验性能力。
- 数据导出现在包含全部使用聚合、每日统计、事件、进度与本地界面缓存；清除会先停止服务再删除数据库和缓存。

### Fixed（2026-08-14 Redmi R1–R4 复测关闭）

- BLOCKED session bypass after leaving targets：BLOCKED 状态豁免 30 秒离开重置，普通离开目标应用不再解除阻断，只能通过完成正式休息或每日一次紧急使用退出（`9c34fe9`，R2 真机 PASS）。
- cold-start / same-package foreground tracking：前台追踪改为按 Activity 实例跟踪 + bootstrap 60 秒回看重试，冷启动同包 Activity 跳转不再清空前台、sessionMs 不再卡 0（`027af94`，R1 ×3 PASS）。
- Domestic strong-block visible fallback on HyperOS：强阻断不再依赖 BlockActivity 成功启动，新增无障碍顶部横幅（TYPE_ACCESSIBILITY_OVERLAY）独立可靠入口（`3600d97`，R3 ×3 PASS）。
- Target Apps Android system Back unsaved-change handling：系统返回键与手势返回在未保存修改时弹「有未保存的修改」确认框，无修改保持默认退出（`99fdcc2`，R4 PASS）。

### Testing（2026-08-14 验证快照）

- Redmi Note 13 Pro 5G R1–R4 revalidation **PASS**（Android 16 / HyperOS 3.0，代码基线 `99fdcc2`）。
- frontend **151**（14 test files，151/151 PASS）。
- Play JVM **244**。
- Domestic JVM **254**（含新的 Accessibility 强阻断回归测试）。
- RecoveryIntegration 23 @Test；Room migrations 6/6。
- CI：Run `31577669420`（verify Job `94053353542`）SUCCESS。

## 1.0.0

- 新增受限应用选择，最多 30 个应用共享连续使用限额。
- 新增 IDLE、PERCEPTION、COGNITION、BLOCKED、RESTING、GRACE 状态机。
- 完整休息后获得 10 分钟访问窗口；每日可选一次 5 分钟紧急使用。
- 新增 Play 软阻断和国内版无障碍强阻断渠道。
- 新增 Room 本地统计、数据迁移、导出和清除。
- 新增双渠道构建、R8、签名环境变量和 GitHub Actions。
- 移除高级版和“即将上线”入口。
