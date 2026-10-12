# FlowBreak v1.1.0 — GATE G 无手机发布工程核对与剩余项

**2026-10-12 — `NON_DEVICE_PREPARED`; `GATE_G=PENDING_CROSS_MACHINE_RECOVERY`; 正式发布未获批准。**

## 本次可从 GitHub 和 CI 确认的事实

- [x] 版本源：`app/package.json` 当前 `1.1.0`；release-version 工具与固定 SemVer/versionCode 约束已有单元测试，版本算法 `1.1.0 → 1001000`。
- [x] 生产签名 policy 中配置了公开证书指纹与两个渠道的不同用途；文件只含 public certificate SHA-256，不包含 keystore 或口令。
- [x] 旧签名身份标注为技术演示用途，禁止用于产品发行；`release-signing-policy.json` 里 final identity 标记为 `PROVISIONED`。
- [x] CI 正常 PR/master `verify` 构建的 APK/AAB 都是 **UNSIGNED / TEST ONLY**。受限制的 signing release job 只允许在已审核 workflow_dispatch/master 或 v* tag 事件中运行；不会对普通 PR 直接提供密钥。
- [x] existing `tools/Test-PortableSigningVault.ps1` 和 `tools/PortableSigningVault.Security.ps1` 用于核验 vault 恢复与证书指纹；此前的记录只证明同机恢复、离机备份完成，**没有独立的跨机器恢复证据**。
- [x] `GHSA-rvm3-566m-v7fv` Critical 安全修复 PR31 已合入 master，提交 `f9ef7a1f0cbcb3925eb87cd9bd16032428054fde`。合并后 master `verify` 成功状态必须单独核实并附具体 run，不能仅引用 PR CI。
- [x] PR29 REST 防作弊修复尚未通过 Gate F 实机验证，明确不允许以当前状态签发正式 tag/Release。

## 不使用手机即可执行或预审的工作

1. **源代码 SHA 冻结。** 审核准确的候选 SHA，确认它包含已验证的安全升级、需要的功能修复和构建文件；签名/审计不能用不同 SHA 的证据互相替代。
2. **隔离式 CI 构建**：`npm ci`、双范围 npm audit、前端/Vitest、溯源测试、双 flavor Gradle JVM/lint、unsigned Debug/Release 产物、R8 mapping、Room schema、manifest 和包内 provenance，全程不读取生产 secrets。
3. **证书校验设计**：Play upload 与 Domestic app signing 指纹从允许列表只读，不创建/替换任何生产 key；生产签名检查含 `apksigner verify --print-certs` 或 `jarsigner/keytool` 交叉核对。
4. **临时签名负向测试**：可在 CI 临时目录生成 *TEST ONLY* key 并核验脚本拒绝错误指纹、错误归档器与不安全 argv；与真实跨机器恢复绝不混同。
5. **密钥保护边界**：不要把 age vault、口令、恢复信息、GitHub Actions secret values、证书私钥或 Keystore 内容写入 issue、PR、日志、云备份测试文件或应用诊断。
6. **确定责任和顺序**：发布命令只在 Gate F/G/H/I 全部达标、发行主体明确授权后执行；绝不因为 CI 全绿就创建正式 v1.1.0 tag、触发 production-signed release 或商店上架。

## 无法从仓库/CI 代替完成的事项

| 条件 | 当前状态 | 完成标准与证据 |
|---|---|---|
| `CROSS_MACHINE_SIGNING_RECOVERY` | **NOT_YET_TESTED** | 在真正独立的授权机器/环境，以 owner 保管的加密 vault/恢复方式恢复；仅验证公开证书指纹与 release policy 两渠道一致；绝不暴露私钥。必须有经核验的实际执行记录。 |
| `ORIGINAL_LAPTOP_RECOVERY` | **NOT_YET_TESTED** | 在规定的不同/还原环境实测，原设备备份/恢复路径对最终身份有效。 |
| `TRUE_VERSION_UPGRADE` | **NOT_TESTED** | 合法较低的最终生产签名版本，升级到较高同签名版本，保留数据，并验证持久化业务状态。旧版仅覆盖重装的证据不足以证明版本升级。 |
| `FINAL_SIGNED_REPLACEMENT_DEVICE` | **NOT_EXECUTED_THIS_CANDIDATE** | 确认发布候选 exact SHA + 签名证书 + 不降级 versionCode 后经允许设备安装，再进行 Gate F 物理行为验收。 |
| `FINAL_TAG` / `STORE_RELEASE` | **DENIED_PENDING_GATES** | 所有 gate 真正关闭并获负责人签字，不提前 tag。 |

**一个潜在工程约束：** v1.1.0 的 versionCode 由 SemVer 确定。已有设备若安装了相同 versionCode，可能执行同版覆盖并保留数据，但这不等于 `TRUE_VERSION_UPGRADE`；要测合法版本升级必须有实际较低且同身份签名的可升级基线，并明确发行版本策略，不能私自制造“测试专用正式生产版本”并把它当公开发布记录。

## 发布决策

`GATE_G_NO_PHONE_DOCUMENTATION = PREPARED`

`GATE_G_REVIEWED_PRODUCTION_ARTIFACT = NOT_APPROVED`

`CROSS_MACHINE_SIGNING_RECOVERY = NOT_YET_TESTED`

`GATE_G = PENDING_CROSS_MACHINE_RECOVERY`

`PRODUCTION_RELEASE = NOT_APPROVED`

上述状态不得因新的文档、PR CI、模拟器或测试签名结果而被升级为 PASS。实际证据记录应更新 `docs/RELEASE.md` 和 `docs/CURRENT_STATUS.md`，并附执行时间、工作站类别及可公开的证书指纹/命令结果摘要，不收集口令。
