# FlowBreak App

FlowBreak 的应用主目录。项目总览见仓库根目录 [README.md](../README.md)。

## 技术栈

- 前端：React 19 + TypeScript + Vite + Zustand + Tailwind CSS + Framer Motion
- Android：Capacitor 8 + Java 原生模块（前台服务、UsageStats、WindowManager、Room、可选 AccessibilityService）

## Android 工程位置

Android 原生工程位于 [`android/`](android/)，包含：

- `app/src/main/` - 共享主源码与 Manifest
- `app/src/domestic/` - 国内版专属（FlowAccessibilityService）
- `app/src/play/` - Play 渠道资源
- `app/src/test/` - Java 单元测试

## 常用命令

开发服务器：

```bash
npm ci
npm run dev
```

生产构建：

```bash
npm ci
npm run build
```

同步到 Android：

```bash
npx cap sync android
```

Android Debug 构建：

```bash
cd android
./gradlew assemblePlayDebug assembleDomesticDebug
```

单元测试：

```bash
cd android
./gradlew testPlayDebugUnitTest testDomesticDebugUnitTest
```

逐页网页预览检查（先构建）：

```bash
npx playwright install chromium
npm run build
npm run test:ui
npm run test:brand
```

`test:ui` 默认启动并停止独立的本地预览服务器，输出写入被 Git 忽略的 `../.tmp/ui-smoke/`。它只验证浏览器界面，不执行 Android 真机保护。更多命令见 [开发指南](../docs/DEVELOPMENT.md)，品牌资产见 [设计参考](../docs/BRAND.md)。

> Windows 下请使用 `gradlew.bat`。构建需要 JDK 21。
