# 知农时 后端乙 调用契约文档

| 项 | 值 |
|----|----|
| 版本 | v1.0 |
| 日期 | 2026-10-09 |
| 模块 | 后端乙 - 计算与规则层（com.zhinongshi.calc） |
| 对接方 | 后端甲（编排层）、前端 |

> 本文档定义后端乙 6 大模块的入参、出参 JSON 样例与枚举含义，供后端甲调用与前端联调使用。
> 后端乙不写 Controller，所有方法由后端甲注入 Bean 后直接调用。

---

## 1. 统一枚举

### 1.1 适宜度 suitability
| 值 | 含义 |
|----|------|
| `GREEN` | 适宜，可按计划执行 |
| `YELLOW` | 谨慎，需关注但可执行 |
| `RED` | 建议调整（推迟/抢收） |

### 1.2 数据来源 dataSource
| 值 | 含义 |
|----|------|
| `real` | 实时接口数据 |
| `cache` | 降级缓存数据（接口超时/网络异常，**由后端甲填充**） |
| `none` | 完全无数据 |

### 1.3 作业类型 jobType
| 值 | 含义 |
|----|------|
| `SPRAY` | 打药 |
| `FERTILIZE` | 施肥 |
| `IRRIGATE` | 灌溉 |
| `HARVEST` | 采收 |

### 1.4 识别状态 recognizeStatus
| 值 | 含义 |
|----|------|
| `SUCCESS` | 识别成功，待人工确认 |
| `LOW_CONFIDENCE` | 置信度不足，请手动搜索 |
| `DEGRADED` | 识别异常降级，回落搜索框 |

### 1.5 错误码 errorCode
| 码 | 含义 |
|----|------|
| `null`/`0` | 成功 |
| `4001` | 作物规程数据缺失 |
| `4002` | 日期格式非法 |
| `4003` | 天气接口超时（已降级标记 cache） |
| `4004` | 无可用气象数据 |
| `4005` | 识别置信度不足 |
| `4006` | 识别服务异常降级 |
| `5001` | 内部计算异常 |

---

## 2. 统一结果包装

所有顶层返回对象包含固定字段：
```json
{
  "errorCode": null,
  "message": "success"
}
```
- 成功：`errorCode=null`，`message` 为 "success" 或业务提示。
- 失败/降级：`errorCode` 为错误码，`message` 为人类可读说明。
- **异常不外抛**，由后端甲编排层处理 HTTP 响应码。

---

## 3. 模块1 节点生成器 CropScheduleGenerator

**Bean**: `CropScheduleGenerator`
**方法**: `generateSchedule(String cropId, LocalDate sowingDate)`

### 入参
| 参数 | 类型 | 说明 |
|------|------|------|
| cropId | String | 作物品种标识 |
| sowingDate | LocalDate | 播种日期(yyyy-MM-dd) |

### 出参 JSON
```json
{
  "cropId": "C001",
  "cropName": "水稻",
  "sowingDate": "2026-04-10",
  "nodeCount": 8,
  "nodes": [
    {
      "nodeId": 1,
      "jobType": "SPRAY",
      "jobTypeName": "打药",
      "daysAfterSowing": 30,
      "executeDate": "2026-05-10",
      "description": "分蘖期病虫害防治"
    }
  ],
  "errorCode": null,
  "message": "success"
}
```
### 错误场景
| 场景 | errorCode | message |
|------|-----------|---------|
| cropId 不存在 | 4001 | 作物规程数据缺失 |
| sowingDate 为 null | 4002 | 日期格式非法 |

### 动态规程方法（v2.0 增强：支持任意作物）

**方法**: `generateScheduleFromProcedures(String cropId, String cropName, List<CropProcedure> procedures, LocalDate sowingDate)`

> 用于 AI 识别到数据库中无预设规程的作物时，由后端甲通过大模型生成该作物农事规程后传入。
> 不查数据库，纯计算换算绝对日期——**识别到什么作物就处理什么作物，不限种类**。

#### 入参
| 参数 | 类型 | 说明 |
|------|------|------|
| cropId | String | 作物品种标识 |
| cropName | String | 作物名称 |
| procedures | List&lt;CropProcedure&gt; | 农事规程列表（大模型生成，每条含 jobType/daysAfterSowing/description） |
| sowingDate | LocalDate | 播种日期(yyyy-MM-dd) |

#### 出参 JSON
```json
{
  "cropId": "D001",
  "cropName": "火龙果",
  "sowingDate": "2026-03-01",
  "nodeCount": 3,
  "nodes": [
    {"nodeId":1,"jobType":"FERTILIZE","jobTypeName":"追肥","daysAfterSowing":15,"executeDate":"2026-03-16","description":"苗期追肥"},
    {"nodeId":2,"jobType":"SPRAY","jobTypeName":"打药","daysAfterSowing":40,"executeDate":"2026-04-10","description":"花期病虫害防治"},
    {"nodeId":3,"jobType":"HARVEST","jobTypeName":"采收","daysAfterSowing":120,"executeDate":"2026-06-29","description":"果实采收"}
  ],
  "errorCode": null,
  "message": "success"
}
```
#### 错误场景
| 场景 | errorCode | message |
|------|-----------|---------|
| procedures 为空/null | 4001 | 作物规程数据缺失 |
| sowingDate 为 null | 4002 | 日期格式非法 |

---

## 4. 模块2 天气适配器 WeatherAdapter

**Bean**: `WeatherAdapter`
**方法**: `fetchWeather(double longitude, double latitude, LocalDate startDate, LocalDate endDate)`

> **缓存边界**：本层只标记 dataSource，**不操作缓存介质**。缓存读写由后端甲完成。

### 入参
| 参数 | 类型 | 说明 |
|------|------|------|
| longitude | double | 经度 |
| latitude | double | 纬度 |
| startDate | LocalDate | 窗口起 |
| endDate | LocalDate | 窗口止 |

### 出参 JSON（real）
```json
{
  "longitude": 116.40,
  "latitude": 39.90,
  "startDate": "2026-05-10",
  "endDate": "2026-05-16",
  "dataSource": "real",
  "dailyWeather": [
    { "date": "2026-05-10", "rainfall": 2.5, "avgTemperature": 22.3, "windSpeed": 3.5 }
  ],
  "errorCode": null,
  "message": "success"
}
```
### 降级（cache）
```json
{
  "dataSource": "cache",
  "dailyWeather": [],
  "errorCode": 4003,
  "message": "天气接口超时，已标记缓存来源，缓存数据由编排层填充"
}
```
### 无数据（none）
```json
{
  "dataSource": "none",
  "dailyWeather": [],
  "errorCode": 4004,
  "message": "无可用气象数据"
}
```

---

## 5. 模块3 作业敏感度打分 RuleScoreService

**Bean**: `RuleScoreService`
**方法**: `scoreJob(JobType jobType, WeatherWindowDTO weatherWindow, LocalDate originalDate)`

> 7 天窗口业务含义：`[originalDate-3, originalDate+3]`。
> 判定取窗口内**最差一天**，不取平均值。

### 入参
| 参数 | 类型 | 说明 |
|------|------|------|
| jobType | JobType | 作业类型 |
| weatherWindow | WeatherWindowDTO | 7 天气象窗口 |
| originalDate | LocalDate | 原计划执行日期 |

### 出参 JSON（RED 建议推迟）
```json
{
  "jobType": "SPRAY",
  "suitability": "RED",
  "originalDate": "2026-05-10",
  "suggestedDate": "2026-05-13",
  "worstDay": "2026-05-12",
  "riskReasons": [
    "2026-05-12 日降雨量 15.2mm 超过打药阈值"
  ],
  "errorCode": null,
  "message": "建议推迟作业"
}
```

### suggestedDate 推算规则
| 状态 | suggestedDate |
|------|---------------|
| RED（推迟） | worstDay + 1 天 |
| RED（采收抢收） | 首个降雨风险日 - 1 天（可能早于 originalDate，代表立刻抢收） |
| GREEN / YELLOW | originalDate |

---

## 6. 模块4 节气计算 SolarTermUtil

**类**: `SolarTermUtil`（可 new，纯本地无网络）
**方法**: `calculateSolarTerms(int year)`

### 入参
| 参数 | 类型 | 说明 |
|------|------|------|
| year | int | 年份 |

### 出参 JSON
```json
{
  "year": 2026,
  "solarTerms": [
    { "order": 1, "name": "立春", "date": "2026-02-04" },
    { "order": 4, "name": "春分", "date": "2026-03-20" },
    { "order": 22, "name": "冬至", "date": "2026-12-22" },
    { "order": 23, "name": "小寒", "date": "2027-01-05" },
    { "order": 24, "name": "大寒", "date": "2027-01-20" }
  ],
  "errorCode": null,
  "message": "success"
}
```
> 小寒、大寒落次年。误差 ≤1 天。断网可算。

---

## 7. 模块5 销售节日 FestivalRuleService

**Bean**: `FestivalRuleService`
**方法**: `queryFestivals(LocalDate windowStart, LocalDate windowEnd, LocalDate referenceDate)`

### 入参
| 参数 | 类型 | 说明 |
|------|------|------|
| windowStart | LocalDate | 窗口起（可 null） |
| windowEnd | LocalDate | 窗口止（可 null） |
| referenceDate | LocalDate | 农事节点日期（计算剩余天数基准，可 null） |

### 出参 JSON
```json
{
  "referenceDate": "2026-05-10",
  "festivals": [
    {
      "festivalName": "端午节",
      "festivalDate": "2026-06-19",
      "daysRemaining": 40,
      "remark": "粽子叶销售节点"
    }
  ],
  "errorCode": null,
  "message": "success"
}
```
> **跨年处理**：12 月节点遇次年 1-2 月节日时，festivalDate 按次年构造，daysRemaining 为正。
> referenceDate 为 null 时 daysRemaining 为 null。

---

## 8. 模块6 作物识别 CropRecognizeAdapter

**Bean**: `CropRecognizeAdapter`
**方法1**: `adaptRecognizeResult(List<CropCandidate> candidates, double confidenceThreshold)`
> 纯排序版本（向后兼容，不查库校验品种是否存在规程）

**方法2**: `adaptRecognizeResultWithValidation(List<CropCandidate> candidates, double confidenceThreshold)`
> 带品种校验版本（推荐使用）：查库校验识别品种是否有农事规程，无规程时按编辑距离推荐相近品种

### 入参
```json
candidates: [
  { "cropId": "C001", "cropName": "水稻", "confidence": 0.92 },
  { "cropId": "C002", "cropName": "小麦", "confidence": 0.71 }
]
confidenceThreshold: 0.8
```

### 出参 JSON（SUCCESS，品种规程存在）
```json
{
  "recognizeStatus": "SUCCESS",
  "needManualConfirm": true,
  "procedureExists": true,
  "topCandidate": { "cropId": "C001", "cropName": "水稻", "confidence": 0.92 },
  "candidates": [
    { "cropId": "C001", "cropName": "水稻", "confidence": 0.92 },
    { "cropId": "C002", "cropName": "小麦", "confidence": 0.71 }
  ],
  "similarCrops": [],
  "promptMessage": "请人工确认识别结果",
  "errorCode": null,
  "message": "success"
}
```

### 出参 JSON（PROCEDURE_NOT_FOUND，品种无规程，已推荐相近）
```json
{
  "recognizeStatus": "PROCEDURE_NOT_FOUND",
  "needManualConfirm": true,
  "procedureExists": false,
  "topCandidate": { "cropId": "X001", "cropName": "稗草", "confidence": 0.88 },
  "candidates": [{ "cropId": "X001", "cropName": "稗草", "confidence": 0.88 }],
  "similarCrops": [
    { "cropId": "C003", "cropName": "玉米", "confidence": 0.33 },
    { "cropId": "C004", "cropName": "大豆", "confidence": 0.25 },
    { "cropId": "C002", "cropName": "小麦", "confidence": 0.20 }
  ],
  "promptMessage": "未找到「稗草」的农事规程，以下为相近作物，请选择确认",
  "errorCode": 4007,
  "message": "识别作物无农事规程，已推荐相近品种"
}
```
> **前端交互**：收到 PROCEDURE_NOT_FOUND 时，展示 similarCrops 列表供用户选择确认；
> 用户选择后，用所选 cropId 调用 `CropScheduleGenerator.generateSchedule(cropId, sowingDate)` 生成节点。

### 低置信度
```json
{
  "recognizeStatus": "LOW_CONFIDENCE",
  "needManualConfirm": true,
  "procedureExists": false,
  "topCandidate": null,
  "candidates": [],
  "similarCrops": [],
  "promptMessage": "识别可信度不足，请手动搜索选择作物",
  "errorCode": 4005,
  "message": "识别置信度不足"
}
```

### 异常降级
```json
{
  "recognizeStatus": "DEGRADED",
  "needManualConfirm": true,
  "procedureExists": false,
  "topCandidate": null,
  "candidates": [],
  "similarCrops": [],
  "promptMessage": "识别服务异常，请使用搜索框选择作物",
  "errorCode": 4006,
  "message": "识别服务异常降级"
}
```
> **needManualConfirm 恒为 true**，本模块不自动选定作物。
> **多品种支持**：系统不限定作物种类，识别到什么品种就处理什么品种；
> 品种无规程时按编辑距离推荐相近品种 top 3（similarCrops），前端展示选择确认界面。

---

## 9. 后端甲编排示例

```java
// 1. 生成农事节点
CropScheduleDTO schedule = cropScheduleGenerator.generateSchedule("C001", sowingDate);

// 2. 取气象（7天窗口）
WeatherWindowDTO weather = weatherAdapter.fetchWeather(lon, lat, originalDate.minusDays(3), originalDate.plusDays(3));
if (weather.getDataSource() == DataSource.CACHE) {
    // 后端甲用缓存介质填充 weather.dailyWeather
    weather.setDailyWeather(cacheService.load(lon, lat, originalDate));
}

// 3. 打分
RuleScoreDTO score = ruleScoreService.scoreJob(JobType.SPRAY, weather, originalDate);

// 4. 节气
SolarTermYearDTO terms = solarTermUtil.calculateSolarTerms(2026);

// 5. 销售节日
FestivalWindowDTO festivals = festivalRuleService.queryFestivals(windowStart, windowEnd, originalDate);

// 6. 作物识别（带品种校验，推荐使用）
CropRecognizeDTO recognize = cropRecognizeAdapter.adaptRecognizeResultWithValidation(candidates, 0.8);
// 若 recognize.recognizeStatus == "SUCCESS"：
//   用 recognize.topCandidate.cropId 调用 generateSchedule 生成节点
// 若 recognize.recognizeStatus == "PROCEDURE_NOT_FOUND"：
//   方式A：前端展示 recognize.similarCrops 供用户选择相近品种，用所选 cropId 调用 generateSchedule
//   方式B（AI动态规程）：后端甲用大模型为该作物生成农事规程，调用 generateScheduleFromProcedures
//     List<CropProcedure> aiProcedures = llmService.generateCropProcedure(recognize.topCandidate.cropName);
//     CropScheduleDTO schedule = cropScheduleGenerator.generateScheduleFromProcedures(
//         recognize.topCandidate.cropId, recognize.topCandidate.cropName, aiProcedures, sowingDate);
```

---

## 10. 缓存职责划分

| 动作 | 责任层 |
|------|--------|
| 调外部天气接口 | 后端乙 WeatherAdapter |
| 标记 dataSource | 后端乙 |
| 缓存写入/读取/过期 | **后端甲** |
| 用缓存数据填充降级结果 | **后端甲** |

---

## 11. 业务硬性底线

> 宁可返回缓存数据并标注来源，绝不编造虚假气象、农事数据；无数据时明确标记 `dataSource:"none"`，不能返回空的虚假时间线。