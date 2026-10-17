# Third-Party Notices

FlowBreak 使用以下随应用运行或构建的主要开源组件。版本以 `app/package-lock.json` 与 Gradle 依赖声明为准：

- Capacitor Android / Core / CLI 8.4.3 — MIT License
- React / React DOM 19.2.8 — MIT License
- React Router 8.3.0 — MIT License
- Framer Motion 12.38.0 — MIT License
- Lucide React 1.8.0 — ISC License
- Recharts 3.8.1 — MIT License
- Zustand 5.0.13 — MIT License
- AndroidX AppCompat 1.7.1 / Core 1.17.0 / Room 2.7.2 — Apache License 2.0
- Tailwind CSS 4.2.2 / Vite 7.3.6 / Vitest 4.1.12 — MIT License（构建 / 测试期依赖）
- TypeScript 5.8.3 — Apache License 2.0（构建期依赖）
- Playwright 1.62.1 — Apache License 2.0（仅网页验收工具，浏览器按其各自许可提供）
- Sharp 0.35.5 — Apache License 2.0（仅品牌资产生成工具；其平台二进制及传递依赖遵循各自附带的许可）

参考项目边界：

- TapBlok（Apache-2.0）：仅参考应用选择、阻断状态机和开机恢复思路。
- ScreenTime（Unlicense）：仅参考 UsageEvents 统计和常驻通知思路。
- Reef：仅作 UX 结构参考，当前实现未复制其代码，因而不将其代码纳入发布物。
- GPL 项目：仅研究交互，不复制实现或代码。

本文件记录了项目直接使用的组件；间接依赖的精确版本由 `app/package-lock.json` 和 Gradle 的解析依赖共同锁定。每次升级依赖时，都必须重新核对许可证及本文件。
