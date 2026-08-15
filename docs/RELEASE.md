# RELEASE — 发布门禁与发布状态

> 发布门禁是**真正的门禁**：任何一项 release-blocking gate 未通过即不可发布。当前状态见文末「当前发布状态」。
> 历史发布检查表（含旧业务规则）归档于 `docs/archive/progress/RELEASE_CHECKLIST.md`。

## 当前发布状态

**RELEASE PREPARATION**（截至 2026-08-14，Redmi R1–R4 复测通过）

- 原 RELEASE BLOCKED 的原因（开放 P1）已消除：`FB-P1-01`、`FB-P1-02`、`FB-P1-03`、`FB-P2-01` 全部 RESOLVED（`KNOWN_ISSUES.md`），P0 = 0、P1 = 0。
- 但 **RELEASE PREPARATION ≠ RELEASE APPROVED**：GATE C 已于 2026-08-15 通过（unsigned CI artifact provenance pipeline）；GATE D–I 仍 PENDING，当前不是 STORE READY，更不是 PRODUCTION RELEASE APPROVED。

## 发布门禁总览

| Gate | 内容 | 状态 |
| ---- | ---- | ---- |
| GATE A | Core automated validation | **PASS** |
| GATE B | Redmi targeted P1/P2 revalidation | **PASS** |
| GATE C | Artifact provenance / reproducible release build | **PASS** |
| GATE D | OEM compatibility matrix | **PENDING** |
| GATE E | Usage accounting accuracy / blocking latency | **PENDING** |
| GATE F | 24h stability + Protection Integrity | **PENDING** |
| GATE G | Signing / Versioning / Publishable Build | **PENDING** |
| GATE H | Store / Compliance Readiness | **PENDING** |
| GATE I | Small-scale Beta | **PENDING** |

最终发布条件：**所有 release-blocking gates PASS**。

### GATE A — Core automated validation：PASS

- CI Run `31900444404`（PR #1，verify Job `95050260127`）**SUCCESS**，HEAD = `ea53a22136dd58663291581763eb3614c4b10ba6`（最新全矩阵验证）。
- 基线运行 `31577669420`（verify Job `94053353542`）SUCCESS，HEAD = `99fdcc2f6f357e78fb70dd127adedfa31a098a71`。
- frontend 151/151（14 test files）+ provenance 18/18（`npm run test:provenance`）；Play JVM 244；Domestic JVM 254；RecoveryIntegration 23（仍是 23，不是 26）；Room migrations 6/6。
- `assemblePlayDebugAndroidTest` = AndroidTest APK successfully ASSEMBLED，**不等于** instrumentation tests executed（真机验证归 GATE B/D/F）。

### GATE B — Redmi targeted P1/P2 revalidation：PASS

- 2026-08-14 Redmi Note 13 Pro 5G（Android 16 / HyperOS 3.0）R1–R4 全部 PASS，基线 `99fdcc2`。
- 关闭：`FB-P1-01`（R1 ×3）、`FB-P1-02`（R2）、`FB-P1-03`（R3 ×3）、`FB-P2-01`（R4）。
- 证据：External device evidence: `reports/R1-R4-retest-2026-08-14.md`、`reports/final-report.md`（第二轮结论）。

### GATE C — Artifact provenance / reproducible release build：PASS

**ARTIFACT_PROVENANCE 要求（7 条已全部实现，PR #1 真实验证）**：

1. 前端 build ✓
2. cap sync ✓
3. Android build ✓
4. 同一工作流连续完成 ✓
5. 产物记录 Git SHA ✓
6. 对 APK / AAB 计算 SHA-256 ✓
7. release artifact 不允许使用来源不明的旧本地产物 ✓（CI 干净 checkout + source identity 校验）

已建立的链路（`.github/workflows/android.yml` + `app/scripts/`，详见 `TESTING.md`）：

- CI checkout 按 `SOURCE_COMMIT_SHA` 固定（PR = head SHA，master push = pushed SHA），构建前校验 `git rev-parse HEAD` 等于该 SHA 且工作区干净（`SOURCE_IDENTITY=PASS`）。
- `npm run build` → 生成 `dist/build-provenance.json`（allowlist 字段：schemaVersion / sourceGitSha / workflowSha / gitRef / runId / runNumber / workflow / buildUtc / versionCode / versionName / prHeadSha；**不含任何 secret/token/keystore 信息**）→ `npx cap sync android`。
- Web 资产同步校验：dist 内每个文件必须在 Android web assets 中存在且 SHA-256 一致（缺失或 mismatch 直接 CI FAIL）；允许 Capacitor 额外运行时文件。
- 最终二进制内部校验：Domestic APK 与 Play AAB 内 `build-provenance.json` 存在且 sourceGitSha = 本轮 source SHA；Domestic APK 版本经 `aapt2 dump badging` 独立读取对照；Play AAB 版本经其自身打包的 `base/manifest/AndroidManifest.xml`（wrapper zip + `aapt2 dump xmltree`）独立读取对照（AGP 8.13 对 AAB 不产出 output-metadata.json，已在 workflow 注明）。
- `artifact-manifest.json`（binaries 的 filename/size/sha256）+ `SHA256SUMS.txt`（binaries + 双渠道 mapping + manifest 自身）。
- GitHub Actions artifact `flowbreak-unsigned-<sourceSha>` 持久上传（retention 90 天）；PR / master verify 产物明确 **UNSIGNED / TEST ONLY**，不冒充可发布产物；tag 签名发布（GATE G 范围）复用同一 provenance 链（signing 设计未改）。

GATE C PASS 的范围（重要）：

- **unsigned CI artifact provenance pipeline 已验证**：PR #1 CI Run `31900444404` verify SUCCESS；artifact 可下载、SHA256SUMS 复核一致、manifest sourceGitSha = PR head SHA、APK/AAB 内部 provenance 一致、版本信息与二进制独立读取一致。
- **signed publishable build 仍属于 GATE G**；STORE READY 仍为 NO。

历史缺口（已关闭，保留记录）：本地曾出现「native 已更新、Web bundle 仍旧」的不一致 APK；旧 CI Run `31577669420` 未持久上传 artifacts。

### GATE D — OEM compatibility matrix：PENDING

- Xiaomi / Redmi、OPPO / OnePlus、vivo / iQOO、Honor / Huawei 等产品族：权限、后台限制、重启恢复、24h 稳定性实测。
- 同时决定 `COMPAT-001`（HyperOS best-effort BlockActivity 后台启动被拒）的处理方向：删除 `tryStartBlockActivity` / OEM 条件化 / 保留 best-effort。

### GATE E — Usage accounting accuracy / blocking latency：PENDING

- UsageStats 精度对照：与系统数字健康误差 ≤10%（多机型）。
- blocking latency 对照：阻断触发延迟 ≤2s 实测。

### GATE F — 24h stability + Protection Integrity：PENDING

- 24h 连续运行：无时间暴涨、重复通知、ANR；且**无 silent protection drift**（保护能力无声漂移）。
- Protection Integrity 原则（`TESTING.md`）：**UI promise ≤ actual protection capability**。正常路径测试同步做失效注入：撤销 Usage Access / 撤销 Overlay / 关闭 Accessibility / service 被杀 / battery saver / OEM 后台限制 / force-stop 后手动重开；逐项验证 A. 实际保护行为 + B. UI 如实反映能力。
- force-stop 语义：不要求自动复活；手动重开后 UI 不得谎称旧保护状态仍完全有效，应走合法恢复。

### GATE G — Signing / Versioning / Publishable Build：PENDING

只负责**工程发行产物**：

- release signing
- keystore / CI secret strategy
- versionName
- versionCode
- channel / version consistency
- signed Play AAB
- signed / 最终 Domestic APK（按实际发行策略）
- upgrade / install verification
- release artifact metadata
- CHANGELOG / version consistency

**不由** GATE G 负责：Data Safety、Accessibility declaration、商店文案、隐私政策 URL、商店截图、合规申报材料（归 GATE H）。

### GATE H — Store / Compliance Readiness：PENDING

负责商店与合规发行材料：

- Google Play Data Safety
- FGS declaration（前台服务声明）
- privacy policy URL
- support contact
- screenshots / store listing
- Accessibility disclosure（如果对应渠道存在）
- Domestic permission-use explanations（国内版权限用途说明）
- APP 备案 / 版权 / 主体等经过确认的发行资质
- 渠道审核材料
- distribution compliance

> Distribution compliance research is in progress and will be incorporated after source verification.（合规研究完成后并入；当前不提前创建未经最终审查的 COMPLIANCE 文档，不把未经确认的研究内容写成 CONFIRMED 项目事实。）
> 本门禁只调整「职责归属」，不提前把任何 PENDING 改为 PASS。

### GATE I — Small-scale Beta：PENDING

- 小规模 Beta 验证（在商店正式发行前）。

## 细项清单

### Code

- [x] 产品行为与 `docs/PRODUCT.md` 一致（当前基线 `99fdcc2` 已通过 R1–R4 复测，与 PRODUCT 目标语义一致；不一致按 Bug 登记）
- [x] 无已知 OPEN 的 P1 缺陷（`FB-P1-01/02/03`、`FB-P2-01` 全部 RESOLVED 且附复测证据；P0 = 0、P1 = 0）
- [ ] 无未经验证的「实验性」能力被作为正式功能宣传（微信视频号识别仍未真机专项验证）

### CI

- [x] `npm test` / `npm run build` / `npx cap sync android` 通过（Run 31577669420）
- [x] `testPlayDebugUnitTest`（244）、`testDomesticDebugUnitTest`（254）、lint 双渠道通过
- [x] `assemblePlayDebug`、`assembleDomesticDebug`、`bundlePlayRelease`、`assembleDomesticRelease` 通过，R8 mapping 存在
- [x] Room schema drift 检查通过（migrations 6/6，无未提交 schema 变更）
- [x] npm audit 门禁通过（生产依赖无 high，全依赖无 critical）
- [x] 构建产物持久上传/归档并可复核（GATE C，PASS：`flowbreak-unsigned-<sha>` artifact，retention 90 天，已实际下载复核）
- [x] provenance 工具链测试 `npm run test:provenance` 18/18
- [x] Web asset sync 校验（dist → Android assets SHA-256 全量一致，缺失/mismatch 即 FAIL）
- [x] APK/AAB 内部 provenance 校验（sourceGitSha + 版本独立读取对照）

### Security / 隐私

- [ ] `PRIVACY.md` 与实际权限/数据行为一致（发布前最终核对）
- [ ] 无密钥/敏感信息入库；release 使用环境变量签名
- [ ] 脱敏诊断导出不含姓名、受限应用包名、具体使用明细
- [ ] 提供公开隐私政策 URL 和支持邮箱（当前未配置）

### Android / 双渠道

- [x] Play 清单不含 AccessibilityService；国内版包含且默认关闭
- [ ] 两个 applicationId 可同时安装，设置互不污染（待发布版最终验证）
- [ ] AAB/APK 签名、安装、覆盖升级测试通过（待签名产物）
- [x] 覆盖安装保留用户数据（Room schema 迁移验证；真机覆盖安装 PASS）

### 真机设备矩阵

- [x] Redmi 精准复测 R1–R4（FB-P1-01/02/03、FB-P2-01）全部 PASS（GATE B）
- [ ] 目标应用统计与系统数字健康误差 ≤10%（多机型，GATE E）
- [ ] 阻断触发延迟 ≤2s（GATE E）
- [ ] 连续运行 24 小时无时间暴涨、重复通知或 ANR，且无 silent protection drift（GATE F）
- [ ] Xiaomi / Redmi、OPPO / OnePlus、vivo / iQOO、Honor / Huawei：权限、后台限制、重启恢复实测（GATE D）

### 商店

- [ ] Play：前台服务声明、Data Safety、隐私政策 URL、商店截图、Android Vitals（GATE H）
- [ ] 国内：无障碍用途说明清晰，关闭后不执行强阻断；后台/自启动引导实测（GATE H）
- [ ] 版本号与 `CHANGELOG.md` 一致（GATE G）

## 已删除/修正的旧检查项

- ~~「离开全部目标应用 30 秒后归零」~~：与 `PRODUCT.md` BLOCKED sticky 语义冲突（FB-P1-02），从门禁中移除；现行规则：30 秒 reset 仅适用于尚未进入 BLOCKED 的连续会话，BLOCKED 只能通过正式休息 / 合法紧急使用 / 明确的监控配置生命周期转换退出。
- ~~「79/80/100/120% 状态边界」~~：修正为 80/100/120%（PERCEPTION/COGNITION/BLOCKED），无 79% 档位。
- 旧「定时提醒」相关检查项：功能已移除，不再作为发布要求。

## 下一步路线（建议排序）

1. release signing / versioning 基础准备（GATE G）
2. 多 OEM 真机矩阵（GATE D）
3. UsageStats 精度对照（GATE E）
4. blocking latency 对照（GATE E）
5. 24h stability + Protection Integrity（GATE F）
6. 小规模 Beta（GATE I）
7. 商店正式发行准备（GATE H）
