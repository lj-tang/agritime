# 数据资产清单与接线检查

项目里最容易出的一类问题，不是代码写错，而是**数据文件看起来都在、实际没人读**。
2026-11-23 就踩过一次：`data/init.sql` 里躺着 400 种作物 × 8 个节点的规程，
但线上 `/api/crops` 里 `hasProtocol` 只有 13 种——SQL 从没被部署脚本导入，
而后端只读 `backend/crop-protocols.json`。同期 `data/crop-base/crop-schedule.json`
（31 个产区作物的 377 条日程）也只被契约校验脚本引用。

后来 `init.sql` 经生成器接入了 `backend/crop-protocols.json`，
`crop-schedule.json` 的 18 个产区作物也合并进去了（见 `deploy/tools/`）。
为了让"接线"这件事不再靠人记得，加了本清单 + 自动检查。

## 检查方式

```bash
python3 deploy/tools/check-data-assets.py        # 检查，有孤儿数据退出 1
python3 deploy/tools/check-data-assets.py --list # 打印每个文件的消费方
```

判定规则：

1. 在消费方代码（backend/src、backend/test、frontend、miniprogram、shared、deploy 下的脚本与工具）里
   搜到该文件的路径或文件名 → 已接线。
2. 搜不到、但在 `deploy/data-assets.json` 的 `entries` 里登记过 → 已登记（保留有明确理由）。
3. 两者都没有 → 孤儿数据，检查失败，CI 红。
4. 基线里登记了、磁盘上却没有 → 过期条目，同样失败。

`shared/constants/data-contract.json` 与 `validate-contracts.js` 只做"声明/校验"，不算消费方；
否则所有被契约列出的数据文件都会"看起来已接线"，检查就失去意义。

新增数据文件时二选一：给它找到消费方；或确认为生成器输入 / 运维参考 / 契约校验用后，
在 `deploy/data-assets.json` 登记 `status` 与 `note`（写清谁用、为什么保留、什么时候删）。

## 当前资产

### 运行时读取

| 路径 | 内容 | 消费方 |
| --- | --- | --- |
| `backend/crop-protocols.json` | 400 种作物规程（13 套人工 + 387 套导入），3643 个节点 | `ComputeStub.loadProtocols`、`CropRegistry`、时间线 |
| `backend/market-festivals.json` | 节日营销提示 | `ComputeStub.readFestivals` |
| `data/crop-base/properties.json` | 400 种作物属性（生育期、喜温、多年生等） | `CropProperties`、时间线扩季 |
| `data/crop-base/alias.json` | 标准名/俗名/拼音/产区名映射 | `CropRegistry.search`、`protocolIdOf` |
| `data/disease/library.json` | 病害库 | 病害识别接口 |
| `data/geo/places-cn.json` | 中国地名库 | `CnGazetteer` 反查地名 |
| `data/guidance/guidance.json` | 农事指导文案 | `GuidanceService`、`Dose` |
| `data/guidance/solar-term-farming.json` | 节气农事 | `SowAdvisor`、前端 `solar-terms.js` |
| `data/industries/aquaculture/*.json` | 水产种类与成本模型 | `SpeciesCatalog`、`AquaCost`、`IndustryHandler` |
| `data/market/recommend-rules.json` | 市场推荐规则 | `RecommendHandler` |
| `data/voice/dialect-map.json` | 方言映射 | 前端 `dialect.js`（nginx `/data/voice/`） |
| `data/geo-baseline.json` | 地块地理基线 | `TerrainService`、`GeoBaselineTest`、`signoff-baseline.py` |

### 生成器输入 / 运维参考

| 路径 | 说明 |
| --- | --- |
| `data/init.sql` | 400 种作物 × 8 节点的原始规程 SQL；`deploy/tools/gen-protocols-from-initsql.py` 转成 `crop-protocols.json`，`fix-initsql-jobtype.py` 修过 705 行 job_type 错位 |
| `data/crop-base/crop-schedule.json` | 31 个产区作物日程；生成器合并进规程（18 个非人工作物），也是契约校验的数据源 |
| `deploy/data/manual-protocols.json` | 13 套人工规程的单一来源，生成器合并时原样保留 |
| `data/store-mysql.sql` | MySQL 存储表结构，`setup-mysql.sh` / `migrate-to-mysql.py` 用 |

### 已登记但运行时未接入（`deploy/data-assets.json`）

| 路径 | 原因 |
| --- | --- |
| `data/crop-base/phenology-stage.json` | 物候期表，契约 v2 声明，时间线暂未使用（用 properties 推导） |
| `data/guidance/operation-threshold.json` | 阈值当前硬编码在 `ComputeStub.java:922-940`；后续可配置化时接入 |
| `data/guidance/festival-rule.json` | 旧版节日规则，已被 `backend/market-festivals.json` 取代 |
| `data/guidance/status-copy.json` | 状态文案，当前内联在代码里 |
| `data/enums/common-enums.json` | 只被契约校验读取 |
| `data/schema/*.json` | 契约校验用 JSON Schema |
| `data/region/*.json` | 离线数据管线产出的质量报告/来源清单 |

## CI

`.github/workflows/ci.yml` 在每次 push / PR 跑：

1. 后端编译（`javac`）
2. 后端回归测试（`bash backend/test/run.sh`）
3. 前端页面测试（`node frontend/tests/pages.test.js`）
4. 契约校验（`node shared/constants/validate-contracts.js`）
5. 数据资产接线检查（`python3 deploy/tools/check-data-assets.py`）

本地等价命令：Linux/macOS `bash deploy/tools/test-all.sh`，Windows `pwsh -File deploy/tools/test-all.ps1`。
