# 契约变更记录（changelog）

> 规则：契约冻结后只追加版本，禁止就地破坏性修改。任何字段新增/变更/废弃都必须在此登记版本号、变更原因、影响范围与处理指引。

## v1 · 初次冻结

| 契约 | 冻结节点 | 版本 | 冻结内容 |
|---|---|---|---|
| `api-contract.json` | D2 | v1 | `POST /api/timeline`、`POST /api/recognition` 的入参/出参/错误码 |
| `compute-contract.json` | D3 | v1 | 6 个导出函数签名：`generateNodes / scoreNodes / resolveSolarTerm / fetchWeatherWindow / recognize / buildFestivalReminders` |
| `data-contract.json` | D5 | v1 | 5 张表：`crop-schedule / operation-threshold / solar-term-farming / festival-rule / status-copy` |

**变更原因**：项目首次基线建立，三份契约按 D2 → D3 → D5 节奏依次冻结，供前后端双线并行开发。

**影响范围**：全项目。冻结后接入层、编排层、计算层、数据层均按 v1 契约实现。

**处理指引**：后续如需变更，追加 v2，并在下表登记；旧版本请求须保持可用（向后兼容）。

## v2 · 数据集扩充与架构增强

| 契约 | 版本 | 变更项 | 兼容性 |
|---|---|---|---|
| `data-contract.json` | v2 | 新增 3 张表 `phenology-stage / dataset-provenance / data-quality-report`；`operation-threshold` 追加 `variable` 字段并扩 `factorType` 枚举（+`soilMoisture`/`light`）；`solar-term-farming` 追加 `keyFarmingPeriod`（必填）/`applicableCrops`；`crop-schedule.source` 升为必填，约束改为「≥20 作物 × ≥12 节点」 | 只追加，v1 五表字段全部保留 |
| `compute-contract.json` | v2 | 新增第 7 个导出模块 `resolvePhenologyStage`（`compute/phenology-resolver.js`）；`scoreNodes`/`resolveSolarTerm` 补充「只追加返回/读取字段」说明 | 只追加，既有 6 个签名逐字不变 |
| `api-contract.json` | v2 | `nodes[]` 追加可选字段 `phenologyStage`/`keyFarmingPeriod`；`contractVersion` 升 v2（对外 `apiVersion` 仍为 v1） | 只追加可选字段，`required` 不变 |

**变更原因**：引入公开农业数据集并扩充数据层，需以追加方式扩展数据表、计算模块与接口字段（需求 5.1–5.6）。

**影响范围**：数据层（新增 3 表、扩充既有 4 表）、计算层（新增物候期解析、因子数据驱动化）、编排层（装配物候期标注，API 响应可选字段）。接入层前端交互与识别模型不受影响。

**兼容性**：全部为追加式演进。既有 5 种作物节点语义不变、既有 API 请求响应结构不变（新增字段均非必填）、既有 6 个计算模块签名不变。旧版本行为保持可用，满足需求 4.5 兼容性与 5.6.2 契约追加式演进。

## 变更登记表

| 版本 | 契约 | 变更项 | 原因 | 影响范围 | 兼容性 |
|---|---|---|---|---|---|
| v1 | 全部 | 初次冻结 | 项目基线 | 全项目 | — |
| v2 | 数据/计算/接口 | 新增 3 表、追加字段与会话枚举、新增物候期模块、追加可选响应字段 | 公开数据集扩充与架构增强 | 数据层/计算层/编排层 | 只追加，旧版本可用 |
## v3 · 多源天气与因子扩充

| 契约 | 版本 | 变更项 | 兼容性 |
|---|---|---|---|
| `data-contract.json` | v3 | `crop-schedule` 追加 `category` 字段（必填，枚举 9 类）；`operation-threshold.factorType` 枚举追加 5 值（airQuality/uvIndex/evapotranspiration/soilTemperature/soilEC，达 11 类）；`dataset-provenance` 追加 `dataSourceType` 字段（必填，枚举 4 值）；约束升级为「≥30 作物 × ≥5 类别」「4 作业 × ≥11 因子」 | 只追加，v2 既有 8 表字段全部保留 |
| `compute-contract.json` | v3 | 新增 3 个导出模块 `fetchRealtimeWeather`（cma-adapter.js）/`fetchPredictedWeather`（pangu-adapter.js）/`orchestrateWeather`（weather-orchestrator.js）；`fetchWeatherWindow`/`scoreNodes` 补充 notes | 只追加，既有 7 个模块签名逐字不变 |
| `api-contract.json` | v3 | `nodes[]` 追加可选字段 `predictSource`/`predictWindow`/`confidence`/`dataSourceType`；`dataSource` 枚举追加 `predicted`；`contractVersion` 升 v3（对外 `apiVersion` 仍为 v1） | 只追加可选字段，`required` 不变 |

**变更原因**：接入气象局实时数据与盘古大模型预测，扩充作物种类与影响因子，需以追加方式扩展数据表、计算模块与接口字段（需求 5.1–5.7）。

**影响范围**：数据层（作物 category、因子 5 新类、来源 dataSourceType）、计算层（3 新模块+天气适配器扩展+阈值打分扩展）、编排层（多源天气编排器接入，API 响应可选预测字段）。接入层前端交互与识别模型不受影响。

**兼容性**：全部为追加式演进。既有 21 种作物节点语义不变、既有 6 类因子阈值判定语义不变、既有 API 请求响应结构不变（新增字段均非必填）、既有 7 个计算模块签名不变、Open-Meteo 接入保留为降级源。旧版本行为保持可用，满足需求 4.5 兼容性与 5.7.1 契约追加式演进。

| v3 | 数据/计算/接口 | 追加 category/dataSourceType/5 因子、新增 3 计算模块、追加可选预测响应字段 | 多源天气与因子扩充 | 数据层/计算层/编排层 | 只追加，v2 全部保留 |