# 知农时 · 验收清单

> 依据 `spec.md` 硬性验收线与 `tasks.md` 交付物清单。勾选 = 已满足并提供证据。

## 一、硬性验收线

- [x] 接口端到端 < 2s（`orchestration/tests/e2e.test.js` 断言）
- [x] 375px 视口无横向滚动（`frontend/tests/pages.test.js` + `common.css` `overflow-x:hidden`）
- [x] 首屏 3 秒出结论（`timeline.html` 首屏 `conclusion-card`，mock/接口返回后立即渲染）
- [x] 天气不可用返回缓存并标注数据时间（`cache.js` + `api.test.js` 降级用例）
- [x] 识别必人工确认、失败回落搜索（`photo-capture.js` + `handleRecognition`）

## 二、架构侧交付物（5 项）

- [x] 时间线 API 与六步编排（`orchestration/server.js`、`scheduler.js`）
- [x] 计算层调度器，零外部依赖（`scheduler.js` 仅按契约调用 6 个导出函数）
- [x] 缓存与降级策略，800ms 超时返缓存并标注数据时间（`cache.js`）
- [x] 华为云部署配置（`deploy/functiongraph.yaml`、`env.example.json`、`README.md`）
- [x] 架构图与选型说明（`docs/architecture.svg`）

## 三、数据侧交付物（5 项）

- [x] 作物农事规程表：5 种作物、每种 ≥ 8 节点、存「播种后第 N 天」、可溯源（`data/crop-schedule.json`）
- [x] 作业敏感度阈值表：4 类作业 × 4 类因子（`data/operation-threshold.json`）
- [x] 节气-农事对应表：24 节气零缺漏（`data/solar-term-farming.json`）
- [x] 销售节日规则表：≥ 6 个销售节点、数据非代码（`data/festival-rule.json`）
- [x] 状态与文案映射表：绿/黄/红一一映射，前端文案唯一来源（`data/status-copy.json`）

## 四、前端侧交付物（4 项）

- [x] 输入表单页：3 字段 + 拍照入口 + 模糊联想，375px 不横滚（`frontend/pages/index.html`）
- [x] 时间线展示页：三层信息轴、节点可展开、实线/虚线（`frontend/pages/timeline.html`）
- [x] 拍照识别与确认组件：拍照 → 3 候选 → 确认 → 失败回落搜索（`frontend/pages/photo-capture.js`）
- [x] 演示脚本与备用录屏占位（`docs/demo-script.md`、`docs/demo-record/`）

## 五、契约与数据治理

- [x] D2/D3/D5 三份契约冻结并登记（`contract/*.json`、`contract/changelog.md`）
- [x] 契约冻结校验（`contract/validate-contracts.js`，破坏性修改将失败）
- [x] 数据集与契约一致性测试（`data/tests/consistency.test.js`）

## 六、测试汇总

| 类别 | 文件 | 结果 |
|---|---|---|
| 计算层单元测试 | `compute/tests/*.test.js` | 通过 |
| 编排层 API / 缓存测试 | `orchestration/tests/*.test.js` | 通过 |
| 前端页面测试 | `frontend/tests/pages.test.js` | 通过 |
| 数据一致性测试 | `data/tests/consistency.test.js` | 通过 |
| 端到端与性能测试 | `orchestration/tests/e2e.test.js` | 通过 |

> 运行：`node --test compute/tests/*.test.js orchestration/tests/*.test.js frontend/tests/*.test.js data/tests/*.test.js`

## 七、待办（非阻塞）

- [ ] 录制真实备用录屏 `docs/demo-record/20261008-v3.mp4`（需真机/演示环境）
- [ ] 真实农户试用反馈（加分项）