# RELEASE — 发布门禁与发布状态

> 发布门禁是**真正的门禁**：任何一项未通过即不可发布。当前状态见文末「当前发布状态」。
> 历史发布检查表（含旧业务规则）归档于 `docs/archive/progress/RELEASE_CHECKLIST.md`。

## 当前发布状态

**RELEASE BLOCKED**（截至 2026-08-12）

原因：`KNOWN_ISSUES.md` 的 `FB-P1-01`、`FB-P1-02`、`FB-P1-03`、`FB-P2-01` 未关闭（master 上的修复提交尚未真机复测）。只要 P1 未关，就不得出现「ready」。

## 发布门禁清单

### Code

- [ ] 产品行为与 `docs/PRODUCT.md` 一致；发现不一致按「源码违反 PRODUCT = Bug」处理并登记 `KNOWN_ISSUES.md`
- [ ] 无已知 OPEN 的 P1 缺陷（`KNOWN_ISSUES.md` 全 P1 为 RESOLVED 且附复测证据）
- [ ] 无未经验证的「实验性」能力被作为正式功能宣传

### CI

- [ ] `npm test` / `npm run build` / `npx cap sync android` 通过
- [ ] `testPlayDebugUnitTest`、`testDomesticDebugUnitTest`、`lintPlayDebug`、`lintDomesticDebug` 通过
- [ ] `assemblePlayDebug`、`assembleDomesticDebug` 通过
- [ ] Release 构建（`bundlePlayRelease`、`assembleDomesticRelease`）通过，R8 mapping 存在
- [ ] Room schema drift 检查通过（无未提交 schema 变更）
- [ ] npm audit 门禁通过（生产依赖无 high，全依赖无 critical）

### Security / 隐私

- [ ] `PRIVACY.md` 与实际权限/数据行为一致
- [ ] 无密钥/敏感信息入库；release 使用环境变量签名
- [ ] 脱敏诊断导出不含姓名、受限应用包名、具体使用明细
- [ ] 提供公开隐私政策 URL 和支持邮箱（当前未配置）

### Android / 双渠道

- [ ] Play 清单不含 AccessibilityService；国内版包含且默认关闭
- [ ] 两个 applicationId 可同时安装，设置互不污染
- [ ] AAB/APK 签名、安装、覆盖升级测试通过
- [ ] 覆盖安装保留用户数据（Room schema 迁移验证）

### 真机设备矩阵

- [ ] 目标应用统计与系统数字健康误差 ≤10%（多机型）
- [ ] 阻断触发延迟 ≤2s
- [ ] 连续运行 24 小时无时间暴涨、重复通知或 ANR
- [ ] 小米/OPPO/vivo/华为：权限、后台限制、重启恢复实测
- [ ] Redmi 精准复测 R1–R4（FB-P1-01/02/03、FB-P2-01）全部 PASS

### 商店

- [ ] Play：前台服务声明、Data Safety、隐私政策 URL、商店截图、Android Vitals
- [ ] 国内：无障碍用途说明清晰，关闭后不执行强阻断；后台/自启动引导实测
- [ ] 版本号与 `CHANGELOG.md` 一致

## 已删除/修正的旧检查项

- ~~「离开全部目标应用 30 秒后归零」~~：与 `PRODUCT.md` BLOCKED sticky 语义冲突（FB-P1-02），从门禁中移除；目标行为见 PRODUCT。
- ~~「79/80/100/120% 状态边界」~~：修正为 80/100/120%（PERCEPTION/COGNITION/BLOCKED），无 79% 档位。
- 旧「定时提醒」相关检查项：功能已移除，不再作为发布要求。
