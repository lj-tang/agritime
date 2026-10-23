# T00 基建与目录规整 · 编码任务规划（t00_directory_migration）

> 本任务规划基于 `t00_directory_migration/spec.md`（REQ-TDM-UBI-001~049，49 条 EARS 需求）与 `design.md`（D-TDM-01~20 关键决策、M-01~M14 迁移映射、25 个改动文件、18 条风险）生成。
> 全局约束继承自 `stage2_frontend`（60 条需求 / D-01~D-12）与 `date_picker_range`（20 条需求 / D-DPR-01~D-DPR-12）：纯原生 JS + 原生 CSS、前端 41/41 测试全绿、4 个核心接口完全兼容、nginx 部署路径不变、无框架/构建工具/第三方库、迁移经 `git mv` 保留历史。
> 项目结构已实地核验：根目录下 `backend/`、`shared/` 不存在（待新建）；`contract/` 含 6 文件；`data/` 含 8 个散落 JSON + `enums/`(1 文件) + `schema/`(8 个 schema 文件)；`frontend/{pages,assets,tests}` 与 `docs/`(5 文件/目录) 结构与 design.md 迁移映射完全一致。

## 任务依赖关系总览

```
组1（建立目录骨架，无依赖）
  ├─→ 组2（契约层迁移）──────────────┐
  ├─→ 组3（数据层重组）──────────────┤
  │                                  ├─→ 组4（更新路径引用）──┐
  └─→ 组5（错误码+数据格式约定）─────→ 组6（接口文档+任务书归档）─┤
                                                              ├─→ 组7（校验与测试）
                                                              │        │
                                                              │        └─→ 组8（部署+git历史验证）
                                                              │                 │
                                                              │                 └─→ 组9（最终审查交付）
```

**关键依赖说明**：
- 组1 是所有后续迁移的前提（`git mv` 要求目标目录已存在）。
- 组4 须在组2 + 组3 均完成后执行（`validate-contracts.js` 路径更新须同时引用新契约路径与新数据路径）。
- 组5 须在组2 完成后执行（`data-format.json` 须与已迁移的 `shared/constants/api-contract.json`、`openapi.yaml` 交叉校验）。
- 组6 须在组5 完成后执行（`docs/api.md` 须引用 `error-codes.json` 中的错误码 name）。

## 1. 建立目标目录骨架

> 对应 REQ-TDM-UBI-001~006、REQ-TDM-UBI-010。
> 依赖：无（首要任务，所有后续迁移的前提）。
> 风险点：R-TDM-08（目标子目录已部分存在），执行时用 `mkdir -p` 容错。

### 1.1 新建 backend/ 顶层目录及四子目录
- [ ] 执行 `mkdir -p backend/api backend/service backend/model backend/middleware`，建立后端骨架四子目录（对应 REQ-TDM-UBI-003）
- [ ] 在 `backend/api/`、`backend/service/`、`backend/model/`、`backend/middleware/` 各创建 `.gitkeep` 文件（对应 REQ-TDM-UBI-006/042）
- [ ] 验收：`backend/` 下存在 api/、service/、model/、middleware/ 四子目录，各含 .gitkeep，无实际后端代码文件

### 1.2 新建 shared/ 顶层目录及两子目录
- [ ] 执行 `mkdir -p shared/utils shared/constants`，建立共享层两子目录（对应 REQ-TDM-UBI-005）
- [ ] 在 `shared/utils/` 创建 `.gitkeep` 文件（对应 REQ-TDM-UBI-023/006）；`shared/constants/` 暂不放 .gitkeep（将由组2 契约文件迁入填充）
- [ ] 验收：`shared/` 下存在 utils/、constants/ 两子目录；utils/ 含 .gitkeep

### 1.3 新建 frontend/components/ 子目录
- [ ] 执行 `mkdir -p frontend/components`，建立公共组件目录预留（对应 REQ-TDM-UBI-010）
- [ ] 在 `frontend/components/` 创建 `.gitkeep` 文件（对应 REQ-TDM-UBI-010）
- [ ] 验收：`frontend/components/` 目录存在且含 .gitkeep，无公共组件文件

### 1.4 新建 data/ 业务子目录
- [ ] 执行 `mkdir -p data/crop-base data/guidance data/region data/industries`，建立数据层四业务子目录（对应 REQ-TDM-UBI-004）
- [ ] 在 `data/industries/` 创建 `.gitkeep` 文件（对应 REQ-TDM-UBI-020）；`data/crop-base/`、`data/guidance/`、`data/region/` 暂不放 .gitkeep（将由组3 数据文件迁入填充）
- [ ] 验收：`data/` 下存在 crop-base/、guidance/、region/、industries/ 四子目录；industries/ 仅含 .gitkeep

### 1.5 提交目录骨架
- [ ] 执行 `git add -A && git commit -m "T00: 建立目标目录骨架（REQ-TDM-UBI-001~006,010）"`，提交骨架建立
- [ ] 验收：`git log --oneline -1` 显示骨架提交；`git status` 无未跟踪的骨架文件

## 2. 契约层迁移至 shared/constants/

> 对应 REQ-TDM-UBI-012~014。
> 依赖：组1（`shared/constants/` 目录已建立）。
> 风险点：R-TDM-07（git mv 须识别为 rename）、R-TDM-12（contract/ 目录须完全清空后删除）。

### 2.1 迁移 6 个契约文件至 shared/constants/
- [ ] 执行 `git mv contract/api-contract.json shared/constants/api-contract.json`（对应 M-01）
- [ ] 执行 `git mv contract/compute-contract.json shared/constants/compute-contract.json`（对应 M-02）
- [ ] 执行 `git mv contract/data-contract.json shared/constants/data-contract.json`（对应 M-03）
- [ ] 执行 `git mv contract/openapi.yaml shared/constants/openapi.yaml`（对应 M-04）
- [ ] 执行 `git mv contract/validate-contracts.js shared/constants/validate-contracts.js`（对应 M-05）
- [ ] 执行 `git mv contract/changelog.md shared/constants/changelog.md`（对应 M-06）
- [ ] 验收：`shared/constants/` 下存在 6 个契约文件，文件名不变；`git status` 显示 6 个文件为 renamed

### 2.2 删除 contract/ 空目录
- [ ] 确认 `contract/` 目录已空（6 文件全部迁出），执行 `rmdir contract/` 或由 git 提交自动清除（对应 REQ-TDM-UBI-046）
- [ ] 验收：项目根目录下不存在 `contract/` 目录

### 2.3 提交契约层迁移
- [ ] 执行 `git add -A && git commit -m "T00: 契约层迁移至 shared/constants/（REQ-TDM-UBI-012~014）"`
- [ ] 验收：`git log --follow shared/constants/api-contract.json` 能查到该文件在 contract/ 时期的提交记录（对应 REQ-TDM-UBI-044）

## 3. 数据层重组

> 对应 REQ-TDM-UBI-015~022。
> 依赖：组1（`data/{crop-base,guidance,region}/` 目录已建立）。
> 风险点：R-TDM-13（data/ 根目录须无散落 JSON 残留）。
> 注意：`data/enums/`(common-enums.json) 与 `data/schema/`(8 个 schema 文件) 保留原位，不迁移（对应 K-04/K-05）。

### 3.1 迁移作物基础数据至 data/crop-base/
- [ ] 执行 `git mv data/crop-schedule.json data/crop-base/crop-schedule.json`（对应 M-07，REQ-TDM-UBI-015）
- [ ] 执行 `git mv data/phenology-stage.json data/crop-base/phenology-stage.json`（对应 M-08，REQ-TDM-UBI-015）
- [ ] 验收：`data/crop-base/` 下存在 crop-schedule.json、phenology-stage.json

### 3.2 迁移农事指导规则至 data/guidance/
- [ ] 执行 `git mv data/status-copy.json data/guidance/status-copy.json`（对应 M-09，REQ-TDM-UBI-016）
- [ ] 执行 `git mv data/operation-threshold.json data/guidance/operation-threshold.json`（对应 M-10，REQ-TDM-UBI-016）
- [ ] 执行 `git mv data/solar-term-farming.json data/guidance/solar-term-farming.json`（对应 M-11，REQ-TDM-UBI-016）
- [ ] 执行 `git mv data/festival-rule.json data/guidance/festival-rule.json`（对应 M-12，REQ-TDM-UBI-016）
- [ ] 验收：`data/guidance/` 下存在 status-copy.json、operation-threshold.json、solar-term-farming.json、festival-rule.json

### 3.3 迁移地理与环境数据至 data/region/
- [ ] 执行 `git mv data/data-quality-report.json data/region/data-quality-report.json`（对应 M-13，REQ-TDM-UBI-017）
- [ ] 执行 `git mv data/dataset-provenance.json data/region/dataset-provenance.json`（对应 M-14，REQ-TDM-UBI-017）
- [ ] 验收：`data/region/` 下存在 data-quality-report.json、dataset-provenance.json

### 3.4 验证 data/ 根目录无散落 JSON 并提交
- [ ] 审查 `data/` 根目录，确认仅含子目录（crop-base/、guidance/、region/、industries/、enums/、schema/），无散落 .json 文件（对应 REQ-TDM-UBI-021/047）
- [ ] 确认 `data/enums/common-enums.json` 与 `data/schema/` 下 8 个 schema 文件位置不变（对应 REQ-TDM-UBI-018/019，K-04/K-05）
- [ ] 执行 `git add -A && git commit -m "T00: 数据层重组（REQ-TDM-UBI-015~022）"`
- [ ] 验收：`git status` 显示 8 个数据文件为 renamed；`data/` 根目录无散落 .json

## 4. 更新迁移文件路径引用

> 对应 REQ-TDM-UBI-049，关键决策 D-TDM-12（validate-contracts.js 豁免逐字节一致）、D-TDM-13（data-contract.json 豁免逐字节一致）。
> 依赖：组2（契约文件已迁至 `shared/constants/`）+ 组3（数据文件已迁至 `data/` 子目录）。
> 风险点：R-TDM-01/R-TDM-02（路径引用与逐字节一致约束冲突，以 D-TDM-12/13 豁免处理）。
> 豁免范围：仅限路径字符串更新，不涉及校验逻辑/契约语义变更。

### 4.1 更新 validate-contracts.js 内 6 处路径引用
- [ ] 在 `shared/constants/validate-contracts.js` 第 32 行，将 `'contract/api-contract.json'` 改为 `'shared/constants/api-contract.json'`（对应 D-TDM-12）
- [ ] 在 `shared/constants/validate-contracts.js` 第 33 行，将 `'contract/compute-contract.json'` 改为 `'shared/constants/compute-contract.json'`
- [ ] 在 `shared/constants/validate-contracts.js` 第 34 行，将 `'contract/data-contract.json'` 改为 `'shared/constants/data-contract.json'`
- [ ] 在 `shared/constants/validate-contracts.js` 第 130 行，将 `'contract/changelog.md'` 改为 `'shared/constants/changelog.md'`
- [ ] 在 `shared/constants/validate-contracts.js` 第 149 行，将 `'data/crop-schedule.json'` 改为 `'data/crop-base/crop-schedule.json'`
- [ ] 在 `shared/constants/validate-contracts.js` 第 183 行（stderr 文案），将 `contract/changelog.md` 改为 `shared/constants/changelog.md`
- [ ] 确认第 35 行 `'data/enums/common-enums.json'` 不变（enums/ 保留原位）；确认 ROOT 计算 `path.resolve(__dirname, '..')` 仍指向项目根目录（`shared/constants/` 与 `contract/` 均为根下二级目录，ROOT 不变）
- [ ] 验收：仅 6 处路径字符串变更，校验逻辑无修改；`node shared/constants/validate-contracts.js` 正常执行无路径错误

### 4.2 更新 data-contract.json 内 8 张表 file 字段
- [ ] 在 `shared/constants/data-contract.json` 中，将 `crop-schedule` 表的 `"file": "data/crop-schedule.json"` 改为 `"file": "data/crop-base/crop-schedule.json"`（对应 D-TDM-13）
- [ ] 将 `phenology-stage` 表的 `"file": "data/phenology-stage.json"` 改为 `"file": "data/crop-base/phenology-stage.json"`
- [ ] 将 `operation-threshold` 表的 `"file": "data/operation-threshold.json"` 改为 `"file": "data/guidance/operation-threshold.json"`
- [ ] 将 `solar-term-farming` 表的 `"file": "data/solar-term-farming.json"` 改为 `"file": "data/guidance/solar-term-farming.json"`
- [ ] 将 `festival-rule` 表的 `"file": "data/festival-rule.json"` 改为 `"file": "data/guidance/festival-rule.json"`
- [ ] 将 `status-copy` 表的 `"file": "data/status-copy.json"` 改为 `"file": "data/guidance/status-copy.json"`
- [ ] 将 `dataset-provenance` 表的 `"file": "data/dataset-provenance.json"` 改为 `"file": "data/region/dataset-provenance.json"`
- [ ] 将 `data-quality-report` 表的 `"file": "data/data-quality-report.json"` 改为 `"file": "data/region/data-quality-report.json"`
- [ ] 确认各表 `schema` 字段不变（schema/ 保留原位，如 `"schema": "data/schema/crop-schedule-schema.json"`）
- [ ] 验收：仅 8 处 file 字段路径变更，字段定义/类型/约束等契约语义无修改

### 4.3 提交路径引用更新
- [ ] 执行 `git add -A && git commit -m "T00: 更新 validate-contracts.js 与 data-contract.json 路径引用（REQ-TDM-UBI-049，D-TDM-12/13 豁免）"`
- [ ] 验收：`node shared/constants/validate-contracts.js` 输出"契约校验通过"，无错误

## 5. 新增全局错误码与数据格式约定

> 对应 REQ-TDM-UBI-025~031。
> 依赖：组2（`shared/constants/` 已建立，契约文件已迁入，用于引用既有错误码与字段格式）。
> 风险点：R-TDM-09（错误码与既有接口冲突）、R-TDM-10（数据格式约定与契约矛盾）、R-TDM-18（敏感信息泄露）。

### 5.1 新建全局错误码定义文件
- [ ] 在 `shared/constants/` 下新建 `error-codes.json`，JSON 格式，含 `success`、`clientErrors`、`serverErrors` 三个数组（对应 REQ-TDM-UBI-025，D-TDM-08）
- [ ] `success` 数组含 `SUCCESS`（code=0, name="SUCCESS", message="操作成功", httpStatus=200）
- [ ] `clientErrors` 数组含：`PARAM_INVALID`（400）、`UNAUTHORIZED`（401）、`FORBIDDEN`（403）、`NOT_FOUND`（404）、`METHOD_NOT_ALLOWED`（405）、`UNSUPPORTED_CROP`（400）、`RECOGNITION_EMPTY`（400）、`RECOGNITION_TIMEOUT`（408），每项含 code/name/message/httpStatus 四字段（对应 REQ-TDM-UBI-026/027）
- [ ] `serverErrors` 数组含：`INTERNAL_ERROR`（500）、`WEATHER_UNAVAILABLE`（502）、`SERVICE_UNAVAILABLE`（503）、`UPSTREAM_TIMEOUT`（504），每项含四字段
- [ ] 确认覆盖既有 6 个错误码（INVALID_INPUT、UNSUPPORTED_CROP、WEATHER_UNAVAILABLE、INTERNAL_ERROR、RECOGNITION_EMPTY、RECOGNITION_TIMEOUT）语义不变（对应 REQ-TDM-UBI-028，D-TDM-19）；确认无敏感信息（对应 spec 4.3，R-TDM-18）
- [ ] 验收：`error-codes.json` 含三类枚举，每项四字段齐备，既有错误码语义兼容

### 5.2 新建数据格式约定文件
- [ ] 在 `shared/constants/` 下新建 `data-format.json`，JSON 格式（对应 REQ-TDM-UBI-029，D-TDM-09）
- [ ] 定义 `dateFormat: "YYYY-MM-DD"`、`dateTimeFormat: "YYYY-MM-DDTHH:mm:ssZ"`（ISO 8601）
- [ ] 定义 `latitudeRange: { "min": -90, "max": 90 }`、`longitudeRange: { "min": -180, "max": 180 }`
- [ ] 定义 `cropIdEnum: ["dry_garlic", "wheat_winter", "corn_summer", "rice", "soybean"]`（与 `crop-map.js` 5 个 cropId 一致）
- [ ] 定义 `statusEnum: ["green", "yellow", "red"]`（适宜/谨慎/不宜）
- [ ] 交叉校验 `data-format.json` 与 `shared/constants/api-contract.json`、`shared/constants/openapi.yaml` 字段格式一致，无矛盾（对应 REQ-TDM-UBI-031，D-TDM-20，R-TDM-10）；确认无敏感信息
- [ ] 验收：`data-format.json` 覆盖日期/经纬度/cropId/status 四类格式，与契约一致

### 5.3 提交错误码与数据格式约定
- [ ] 执行 `git add -A && git commit -m "T00: 新增全局错误码与数据格式约定（REQ-TDM-UBI-025~031）"`
- [ ] 验收：`shared/constants/error-codes.json` 与 `shared/constants/data-format.json` 存在且结构合法

## 6. 新增接口文档与任务书归档

> 对应 REQ-TDM-UBI-032~036。
> 依赖：组2（契约已迁至 `shared/constants/openapi.yaml`，用于参数对齐）+ 组5（`error-codes.json` 已建立，用于错误码引用）。
> 风险点：R-TDM-18（文档不得回显密钥/服务器内部路径）。

### 6.1 新建接口文档 docs/api.md
- [ ] 在 `docs/` 下新建 `api.md`，Markdown 格式，按接口分节描述 4 个核心接口（对应 REQ-TDM-UBI-032，D-TDM-10）
- [ ] 为 `GET /api/timeline`（时间线）编写：路径、方法、请求参数表（crop/sowDate/lat/lon/location 的名/类型/必填/取值范围）、响应结构、错误码引用、请求/响应示例
- [ ] 为 `POST /api/recognition`（拍照识别）编写：路径、方法、请求参数（图片数据）、响应结构（识图候选）、错误码引用、示例
- [ ] 为 `POST /api/chart`（图表数据）编写：路径、方法、请求参数、响应结构、错误码引用、示例
- [ ] 为 `GET /health`（健康检查）编写：路径、方法、响应结构、示例
- [ ] 各接口错误码引用 `shared/constants/error-codes.json` 中的错误码 name，不自行编造（对应 REQ-TDM-UBI-034/039）
- [ ] 参数定义与 `shared/constants/openapi.yaml` 一致（对应 REQ-TDM-UBI-033）
- [ ] 文档末尾附 REST 规范说明（资源命名用名词复数、HTTP 方法语义化、错误返回统一 JSON 结构 `{code, message, data}`）（对应 REQ-TDM-UBI-035/038）
- [ ] 确认无真实密钥或服务器内部路径细节（对应 spec 4.3.2，REQ-TDM-UBI-035 禁止项，R-TDM-18）
- [ ] 验收：`docs/api.md` 含 4 个核心接口完整文档 + REST 规范说明，与契约一致

### 6.2 新建任务书归档 docs/taskbook.md
- [ ] 在 `docs/` 下新建 `taskbook.md`，Markdown 格式，归档功能扩展任务书 V3.0 的 T00 任务内容（对应 REQ-TDM-UBI-036，D-TDM-11）
- [ ] 内容含：任务编号 T00、目标目录结构（frontend/backend/data/shared/docs 五层分离）、验收标准
- [ ] 确认内容与任务书原文一致，不篡改（对应 REQ-TDM-UBI-036 禁止项）
- [ ] 验收：`docs/taskbook.md` 含 T00 任务描述、目标目录结构与验收标准

### 6.3 提交接口文档与任务书归档
- [ ] 执行 `git add -A && git commit -m "T00: 新增接口文档与任务书归档（REQ-TDM-UBI-032~036）"`
- [ ] 验收：`docs/api.md` 与 `docs/taskbook.md` 存在；`docs/` 既有 5 文件/目录（acceptance.md、architecture.svg、data-sources.md、demo-record/、demo-script.md）位置与内容不变（对应 D-TDM-18）

## 7. 迁移后校验与测试

> 对应 REQ-TDM-UBI-011/048/049。
> 依赖：组4（路径引用已更新）+ 组6（文档已新增）。
> 风险点：R-TDM-04（前端测试因路径变更失败）、R-TDM-13（data/ 根目录残留散落 JSON）、R-TDM-14（industries/ 误放数据）、R-TDM-15（backend/ 误放代码）。

### 7.1 运行前端测试验证
- [ ] 执行 `node --test frontend/tests/pages.test.js`，确认 41 项断言全部通过（对应 REQ-TDM-UBI-011/048）
- [ ] 若测试失败，执行 `git revert` 回退迁移，排查路径引用断裂原因，修正后重新迁移（对应 spec 5.2.3 异常场景，R-TDM-04）
- [ ] 确认 `frontend/tests/pages.test.js` 文件内容无修改（对应 REQ-TDM-UBI-049 禁止项，`git diff` 审查无变更）
- [ ] 验收：测试输出 41/41 全绿，无失败；测试文件内容逐字节不变

### 7.2 运行契约校验脚本验证
- [ ] 执行 `node shared/constants/validate-contracts.js`，确认脚本正常执行输出"契约校验通过"（对应 REQ-TDM-UBI-049）
- [ ] 若校验失败，排查 `validate-contracts.js` 第 32/33/34/130/149/183 行路径引用与 `data-contract.json` 8 处 file 字段是否已正确更新
- [ ] 验收：契约校验通过，无路径错误，无契约不一致

### 7.3 审查目录结构合规性
- [ ] 审查项目根目录，确认顶层目录恰为 frontend/、backend/、data/、shared/、docs/、deploy/ 及元数据目录（.git/、.codeartsdoer/），无散落的非目标顶层目录（对应 REQ-TDM-UBI-001/045）
- [ ] 确认 `contract/` 目录已删除（对应 REQ-TDM-UBI-046）
- [ ] 确认 `data/` 根目录仅含子目录（crop-base/、guidance/、region/、industries/、enums/、schema/），无散落 .json 文件（对应 REQ-TDM-UBI-021/047）
- [ ] 确认各新建空目录含 .gitkeep：frontend/components/、backend/{api,service,model,middleware}/、data/industries/、shared/utils/（对应 REQ-TDM-UBI-006）
- [ ] 确认 `data/industries/` 仅含 .gitkeep，无实际数据文件（对应 REQ-TDM-UBI-020，R-TDM-14）
- [ ] 确认 `backend/` 各子目录仅含 .gitkeep，无实际后端代码（对应 REQ-TDM-UBI-042，R-TDM-15）
- [ ] 验收：目录结构完全符合目标五层分离，无散落文件

## 8. 部署兼容性与 git 历史验证

> 对应 REQ-TDM-UBI-040~044。
> 依赖：组7（目录结构已合规）。
> 风险点：R-TDM-05（nginx 部署路径失效）、R-TDM-06（后端引用 data/ 路径）、R-TDM-07（git mv 未识别为 rename）。

### 8.1 验证部署兼容性
- [ ] 确认 `frontend/pages/` 下 index.html、timeline.html、photo-capture.js 位置不变，nginx root `/agritime/frontend/pages` 路径仍有效（对应 REQ-TDM-UBI-040，R-TDM-05）
- [ ] 确认未触碰服务器后端 Java 服务 `/opt/crop-calendar`（对应 REQ-TDM-UBI-041）；确认项目内 `backend/` 仅为骨架预留（对应 REQ-TDM-UBI-042）
- [ ] 确认 `deploy/` 目录及 3 文件（env.example.json、functiongraph.yaml、README.md）位置与内容不变（对应 spec 7.3，D-TDM-17）
- [ ] 验收：nginx 部署路径不变，后端服务不受影响，deploy/ 不变

### 8.2 验证 git 历史保留
- [ ] 执行 `git log --follow shared/constants/api-contract.json`，确认能查到该文件在 contract/ 时期的提交记录（对应 REQ-TDM-UBI-044）
- [ ] 执行 `git log --follow shared/constants/validate-contracts.js`，确认能查到迁移前历史
- [ ] 执行 `git log --follow data/crop-base/crop-schedule.json`，确认能查到该文件在 data/ 根目录时期的提交记录
- [ ] 审查 `git status`，确认所有迁移文件显示为 renamed 而非 deleted+added（对应 REQ-TDM-UBI-043，R-TDM-07）
- [ ] 验收：所有迁移文件 git 历史完整保留，迁移表现为 rename

### 8.3 验证文件内容一致性
- [ ] 审查 `git diff`，确认除 `shared/constants/validate-contracts.js`（6 处路径引用更新）与 `shared/constants/data-contract.json`（8 处 file 字段更新）外，其余迁移文件内容逐字节一致（对应 REQ-TDM-UBI-013/022，D-TDM-12/13 豁免）
- [ ] 确认 `frontend/` 下全部文件（pages/、assets/、tests/）位置与内容逐字节不变（对应 REQ-TDM-UBI-007/008/009，D-TDM-16）
- [ ] 确认 `data/enums/common-enums.json` 与 `data/schema/` 下 8 个 schema 文件内容不变（对应 REQ-TDM-UBI-018/019）
- [ ] 验收：迁移文件内容一致性符合预期（仅 2 文件有路径引用更新，其余逐字节不变）

## 9. 最终审查与交付确认

> 对应 REQ-TDM-UBI-045~047、spec 7.5 交付任务。
> 依赖：组8（所有验证通过）。
> 风险点：R-TDM-17（迁移不可回滚，已通过 git 提交保障可 git revert）。

### 9.1 需求覆盖审查
- [ ] 审查 49 条 EARS 需求（REQ-TDM-UBI-001~049）是否全部由任务覆盖，无遗漏
- [ ] 确认 14 条迁移映射（M-01~M-14）全部执行完毕
- [ ] 确认 11 个新建文件（N-01~N-11）全部创建完毕
- [ ] 确认 7 个保留不变项（K-01~K-07）全部保持不变
- [ ] 验收：需求覆盖矩阵完整，无遗漏

### 9.2 风险点复查
- [ ] 复查 R-TDM-01~18 共 18 条风险，确认缓解措施已落实（对应 design.md 2.6 节）
- [ ] 确认 `mock-data.js` 注释中旧路径 `contract/api-contract.json` 保留不动（R-TDM-03，注释非代码引用，不影响运行；spec 7.3 要求前端文件内容不变）
- [ ] 确认 `compute-contract.json` 内部 `compute/*.js` 路径引用不变（R-TDM-11，后端模块已移除，校验脚本第 65/144 行跳过检查）
- [ ] 确认目录层级深度未超 4 层（R-TDM-16，最深路径为 3 层如 `shared/constants/validate-contracts.js`）
- [ ] 验收：18 条风险缓解措施全部落实

### 9.3 交付与推送
- [ ] 确认所有迁移提交信息含 T00 任务编号（对应 spec 7.5 交付任务第 1 条）
- [ ] 执行 `git push` 推送至 GitHub 仓库 `lj-tang/agritime`（对应 spec 7.5 交付任务第 1 条）
- [ ] 确认项目可同步至目标服务器 115.175.44.27 的 `/agritime`，nginx root 路径仍有效（对应 spec 7.5 交付任务第 2 条）
- [ ] 最终运行 `node --test frontend/tests/pages.test.js` 确认 41/41 全绿（对应 spec 7.5 交付任务第 3 条）
- [ ] 验收：代码已推送，服务器部署兼容，前端测试全绿，T00 任务交付完成