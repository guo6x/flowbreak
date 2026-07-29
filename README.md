# FlowBreak

A local-first Android app that helps reduce continuous use of short-video and social apps through progressive reminders and optional strong blocking.

一款本地运行的 Android 数字健康工具，通过渐进式提醒和可选强阻断，帮助用户减少连续使用短视频和社交应用的时间。

---

## 当前项目状态

- 处于 MVP / Beta 验证阶段。
- 主要支持 Android。
- 数据完全保存在本地（SharedPreferences + Room）。
- 不要求注册账号。
- 不接入广告和第三方统计 SDK。
- 尚未正式发布到应用商店。
- 部分厂商后台运行能力仍需要真机验证。

## 核心功能

- 选择需要限制的目标应用（最多 30 个）。
- 统计目标应用使用时间。
- 连续使用达到阈值后的渐进式提醒（80% / 100% / 120%）。
- 完成休息后获得 10 分钟访问窗口。
- 每日一次紧急使用（5 分钟）。
- 眼部放松、拉伸和呼吸休息引导。
- 本地统计、效果验证和每日反馈。
- 本地数据导出、脱敏诊断和数据清除。
- Play 版与国内版双渠道构建。

## 工作机制

状态机按连续使用时长占共享限额的百分比推进：

```text
IDLE
→ PERCEPTION（达到 80%）
→ COGNITION（达到 100%）
→ BLOCKED（达到 120%）
→ RESTING
→ GRACE（完成休息后开放 10 分钟）
```

补充说明：

- 离开全部目标应用满 30 秒后，连续会话会重置。
- 所有用户选择的目标应用共享同一个连续使用限额。
- 当前判定依据主要是连续使用时长，不是生理疲劳检测。

## Play 版与国内版区别

| 项目            | Play 版              | 国内版                    |
| --------------- | ------------------- | ---------------------- |
| applicationId   | `com.flowbreak.app` | `com.flowbreak.app.cn` |
| 使用情况访问        | 支持                  | 支持                     |
| 悬浮窗提醒         | 支持                  | 支持                     |
| 无障碍强阻断        | 不包含                 | 可选、实验性                 |
| 微信视频号识别       | 不支持细分               | 依赖可选无障碍服务              |

明确事项：

- Google Play 版不声明 `AccessibilityService`。
- 国内版无障碍功能仅监听窗口切换事件，不读取页面内容。
- 微信视频号识别属于实验性能力，可能受微信版本变化影响。

## 技术架构

前端：

- React 19
- TypeScript
- Vite
- Zustand
- Tailwind CSS v4
- Framer Motion
- Capacitor 8

Android 原生：

- Java
- Foreground Service
- UsageEvents / UsageStatsManager
- WindowManager
- Room
- SharedPreferences
- 可选 AccessibilityService（仅国内版）

## 项目目录

```text
flowbreak/
├── app/
│   ├── src/                  React 前端
│   ├── android/              Android 原生工程
│   ├── package.json
│   └── README.md
├── .github/workflows/        GitHub Actions
├── CHANGELOG.md
├── PRIVACY.md
├── RELEASE_CHECKLIST.md
├── VERIFICATION.md
├── THIRD_PARTY_NOTICES.md
└── README.md
```

## 本地开发

开发服务器：

```bash
cd app
npm ci
npm run dev
```

生产构建：

```bash
cd app
npm ci
npm run build
```

同步 Android：

```bash
cd app
npx cap sync android
```

Android Debug 构建：

```bash
cd app/android
./gradlew assemblePlayDebug assembleDomesticDebug
```

单元测试：

```bash
cd app/android
./gradlew testPlayDebugUnitTest testDomesticDebugUnitTest
```

> Windows 下请使用 `gradlew.bat` 代替 `./gradlew`。
> 构建需要 JDK 21（CI 使用 temurin 21）。

## 权限说明

| 权限       | 用途                          |
| -------- | --------------------------- |
| 使用情况访问   | 识别前台应用并计算所选应用的连续使用时长        |
| 悬浮窗      | 在达到阻断条件时显示覆盖页               |
| 通知       | 显示状态和提醒                     |
| 前台服务     | 保持检测运行                      |
| 开机启动     | 重启后恢复监控                     |
| 电池优化设置   | 降低部分厂商系统杀死后台检测的概率           |
| 无障碍服务    | 仅国内版、用户主动开启，监听窗口切换以执行强阻断    |

提醒：

FlowBreak 需要较多系统权限才能稳定识别前台应用和执行提醒，请仅从可信来源安装。不保证所有厂商设备都能 100% 后台稳定运行。

## 隐私

- 数据本地处理，不上传使用明细。
- 不要求登录。
- 支持完整数据导出（设置、使用聚合、每日统计、事件和进度）。
- 支持脱敏诊断导出（不含姓名、受限应用包名或具体使用明细）。
- 支持清除全部本地数据。

详见 [PRIVACY.md](PRIVACY.md)。

## 当前限制

- 尚未完成大规模真实用户验证。
- 不同 Android 厂商的后台限制可能影响服务稳定性。
- 使用时间统计仍需要更多机型与系统数字健康进行误差对照。
- 无障碍和悬浮窗行为可能因系统版本不同而不同。
- 微信视频号识别依赖页面和 Activity 结构，版本更新可能导致失效。
- 当前不属于医疗产品。
- "休息效果"指标主要是本地短期行为记录，不代表治疗或长期健康改善。

## 验证状态

已完成：

- TypeScript / Vite 构建。
- Android Java 单元测试（状态阈值、跨应用共享会话、离开 30 秒、锁屏、访问窗口、前台事件、原生休息校验、效果判定边界）。
- 双渠道 Debug 构建与 R8 Release 构建流程。
- Room v2 到 v3 迁移测试。
- Android 16 模拟器启动验证。
- 红米设备覆盖安装验证。

仍未完成：

- 小米、OPPO、vivo、华为完整矩阵测试。
- 24 小时稳定性验证。
- 与系统数字健康误差不超过 10% 的多设备验证。
- 正式商店签名与发布审核。

详见 [VERIFICATION.md](VERIFICATION.md)。

## 相关文档

- [PRIVACY.md](PRIVACY.md) - 隐私政策
- [CHANGELOG.md](CHANGELOG.md) - 版本变更记录
- [VERIFICATION.md](VERIFICATION.md) - Beta 验证记录
- [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) - 发布检查表
- [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) - 第三方许可

## License

当前仓库尚未添加开源许可证。在正式确定许可证前，默认保留所有权利。
