# FlowBreak — Third-Party Notices（主要依赖摘要）

**审查日期：2026-10-12；与分支 `app/package-lock.json` 锁定的 NPM 版本相符。** 本文件是方便用户阅读的直接依赖摘要，**不是穷尽的 SBOM，也不是各依赖许可证原文的替代品**。Android 发布前还需对最终 Play/Domestic runtimeClasspath、打包资源、间接依赖及应附带的许可证原文单独复核。

## 随应用运行的主要组件

| 组件 | npm 锁定版本 | 许可证 |
|---|---|---|
| Capacitor Android / Core | 8.4.3 | MIT |
| React / React DOM | 19.2.8 | MIT |
| React Router (`react-router`) | 8.3.0 | MIT |
| Framer Motion | 12.38.0 | MIT |
| Lucide React | 1.8.0 | ISC |
| Recharts | 3.8.1 | MIT |
| Zustand | 5.0.13 | MIT |
| Recharts 使用的 `react-is` | 19.2.5 | MIT |
| AndroidX AppCompat / AndroidX Core / AndroidX Room | 实际解析版本见 Gradle 运行时清单（Room 显式 2.7.2） | Apache License 2.0 |

## 构建和测试期依赖（非直接视为 Android 运行时 SDK）

| 组件 | npm 锁定版本 | 许可证 |
|---|---|---|
| Capacitor CLI | 8.4.3 | MIT |
| Tailwind CSS | 4.2.2 | MIT |
| Vite | 7.3.6 | MIT |
| TypeScript | 5.8.3 | Apache License 2.0 |
| Vitest | 4.1.11 | MIT |
| Testing Library React | 16.3.2 | MIT |

上述版本仅反映这个锁定文件；重新执行依赖解析或正式构建时应复核，不能拿旧公告替代最终 APK/AAB。构建工具代码通常不直接包含于用户安装的二进制中，但**是否有相应运行时依赖需要通过实际产物确认**。

## 参考项目与未纳入代码范围

- TapBlok（Apache-2.0）：历史文档中仅参考应用选择、阻断状态机与开机恢复思路；本摘要不主张复制其代码。
- ScreenTime（Unlicense）：历史文档中仅参考 UsageEvents 统计和通知思路。
- Reef：历史文档中仅作 UX 结构参考，未声明复制其代码。
- GPL 项目：仅研究交互；任何代码重用都必须另行完成许可证与发行义务审核。

## 发布前仍需核对

- `app/package-lock.json` 中的**全部**直接/间接运行时依赖与许可证；
- Gradle Play/Domestic 的 **runtimeClasspath** 实际解析树、AndroidX 与 Capacitor 间接依赖、资源/字体/图标版权；
- 对要求携带许可证或 NOTICE 的依赖，随发行包或商店可访问页面提供必要声明与许可证文本；
- 根据最终发行二进制的 SHA 与渠道重审，不因为这一页更新就认为 `GATE_H` 已经通过。

**状态：主要依赖摘要已校正；完整版许可证物料与发行方审核仍为 PENDING。**
