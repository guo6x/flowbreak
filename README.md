# FlowBreak

A local-first Android digital-wellbeing tool that helps reduce continuous use of short-video and social apps through progressive reminders and optional strong blocking.

一款本地运行的 Android 数字健康工具，通过渐进式提醒和可选强阻断，帮助用户减少连续使用短视频和社交应用的时间。

## 当前阶段

- 处于**发布准备阶段（RELEASE PREPARATION）**（MVP，尚未发布；不是 STORE READY）。
- 2026-08-14 Redmi R1–R4 真机复测：**核心验收通过（PASSED）**，原 P1/P2 缺陷全部 RESOLVED（见 [docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md)）。
- 详细状态与发布门禁见 [docs/CURRENT_STATUS.md](docs/CURRENT_STATUS.md) 与 [docs/RELEASE.md](docs/RELEASE.md)。

## 核心机制

- 选择受限目标应用（默认 5 个，最多 30 个），共享同一个连续使用限额。
- 按连续使用时长占限额百分比渐进干预：**80% → PERCEPTION（轻提醒）→ 100% → COGNITION（强提醒）→ 120% → BLOCKED（全屏阻断）**。
- BLOCKED 后需完成休息（2/3/5 分钟）获得 10 分钟访问窗口；每日一次紧急使用（长按 10 秒，5 分钟窗口）。
- 只统计目标应用时长，数据全本地（SharedPreferences + Room + localStorage），无账号、无广告、无第三方统计。

产品行为规范（唯一规范）：[docs/PRODUCT.md](docs/PRODUCT.md)

## Play / Domestic 差异

| 项目 | Play 版 | 国内版 |
| ---- | ---- | ---- |
| applicationId | `com.flowbreak.app` | `com.flowbreak.app.cn` |
| 无障碍强阻断 | 不包含 | 可选、实验性 |
| 微信视频号识别 | 不支持 | 实验性（依赖可选无障碍服务） |

## 技术栈

- 前端：React 19 / TypeScript / Vite / Zustand / Tailwind CSS v4 / Framer Motion / Capacitor 8
- 原生：Java / Foreground Service（START_STICKY）/ UsageEvents / WindowManager / Room（schema v3）/ SharedPreferences / 可选 AccessibilityService（仅国内版）

架构说明：[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

## 快速开发

```bash
cd app
npm ci
npm run dev        # 前端开发
npm test           # 前端测试
npm run build      # 生产构建
npx cap sync android
cd android
./gradlew testPlayDebugUnitTest testDomesticDebugUnitTest
./gradlew assemblePlayDebug assembleDomesticDebug
```

> Windows 使用 `gradlew.bat`；需要 JDK 21；CI 使用 Node 22.23.1。
> 详见 [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)。

## 文档入口

新接手项目请按 [docs/README.md](docs/README.md) 的阅读顺序阅读。文档目录：`docs/PRODUCT.md`、`docs/ARCHITECTURE.md`、`docs/CURRENT_STATUS.md`、`docs/KNOWN_ISSUES.md`、`docs/TESTING.md`、`docs/RELEASE.md`、`docs/DEVELOPMENT.md`。

## 其他文档

- [PRIVACY.md](PRIVACY.md) — 隐私政策
- [CHANGELOG.md](CHANGELOG.md) — 版本变更记录
- [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) — 第三方许可

## License

当前仓库尚未添加开源许可证。在正式确定许可证前，默认保留所有权利。
