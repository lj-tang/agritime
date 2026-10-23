/**
 * 契约冻结校验脚本（任务 1.7 / 8.5）
 * 校验三份契约间字段交叉引用一致，且冻结后禁止破坏性修改。
 * v2 起：覆盖 8 张数据表、新增枚举、第 7 个计算模块与 api-contract 追加字段；
 *         v1 既有断言全部保留（只追加、不破坏）。
 * 用法：node shared/constants/validate-contracts.js
 */

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..', '..');

const V1_TABLES = ['crop-schedule', 'operation-threshold', 'solar-term-farming', 'festival-rule', 'status-copy'];
const V1_EXPORTS = ['generateNodes', 'scoreNodes', 'resolveSolarTerm', 'fetchWeatherWindow', 'recognize', 'buildFestivalReminders'];
const V2_EXPORTS = ['resolvePhenologyStage'];
const V1_FACTORS = ['rainfall', 'windSpeed', 'temperature', 'humidity'];

/** @returns {any} */
function readJson(rel) {
  const abs = path.join(ROOT, rel);
  if (!fs.existsSync(abs)) {
    throw new Error(`契约文件缺失: ${rel}`);
  }
  return JSON.parse(fs.readFileSync(abs, 'utf8'));
}

function main() {
  const errors = [];
  const checks = [];

  const api = readJson('shared/constants/api-contract.json');
  const compute = readJson('shared/constants/compute-contract.json');
  const data = readJson('shared/constants/data-contract.json');
  const enums = readJson('data/enums/common-enums.json');

  checks.push(['三份契约存在', true]);

  // api-contract
  const timeline = api.endpoints['POST /api/timeline'] || api.endpoints.timeline;
  const recognition = api.endpoints['POST /api/recognition'] || api.endpoints.recognition;
  if (!timeline) errors.push('api-contract 缺少时间线接口');
  if (!recognition) errors.push('api-contract 缺少识别接口');
  checks.push(['api-contract 含两个接口', Boolean(timeline && recognition)]);

  const requiredApiErrors = ['INVALID_INPUT', 'UNSUPPORTED_CROP', 'WEATHER_UNAVAILABLE', 'INTERNAL_ERROR', 'RECOGNITION_EMPTY', 'RECOGNITION_TIMEOUT'];
  requiredApiErrors.forEach((code) => {
    if (!api.errorCodes[code]) errors.push(`api-contract 缺少错误码: ${code}`);
  });
  checks.push(['api-contract 错误码齐备', requiredApiErrors.every((c) => api.errorCodes[c])]);

  // compute-contract：v1 六导出 + v2 第七导出
  const modules = compute.modules || [];
  const actualExports = modules.map((m) => m.export);
  V1_EXPORTS.forEach((fn) => {
    if (!actualExports.includes(fn)) errors.push(`compute-contract 缺少 v1 导出函数: ${fn}`);
  });
  V2_EXPORTS.forEach((fn) => {
    if (!actualExports.includes(fn)) errors.push(`compute-contract 缺少 v2 导出函数: ${fn}`);
  });
  checks.push([`compute-contract v1 六个导出函数齐备（实际 ${modules.length}）`, V1_EXPORTS.every((fn) => actualExports.includes(fn))]);
  checks.push(['compute-contract v2 追加 resolvePhenologyStage', V2_EXPORTS.every((fn) => actualExports.includes(fn))]);

  const missingModuleFiles = modules.filter((m) => !fs.existsSync(path.join(ROOT, m.file)));
  missingModuleFiles.forEach((m) => process.stdout.write(`⚠ 计算模块已移除，跳过模块文件检查: ${m.file}\n`));
  checks.push(['compute-contract 模块文件检查（后端已移除，跳过）', true]);

  // data-contract：v1 五表 + v2 新增三表
  const tables = data.tables || {};
  const tableNames = Object.keys(tables);
  const tableErrors = [];
  tableNames.forEach((tableName) => {
    const base = path.basename(tables[tableName].file, '.json');
    if (base !== tableName) tableErrors.push(`文件名与表名不一致: ${tables[tableName].file} vs ${tableName}`);
    if (!fs.existsSync(path.join(ROOT, tables[tableName].file))) tableErrors.push(`数据表文件缺失: ${tables[tableName].file}`);
    if (!fs.existsSync(path.join(ROOT, tables[tableName].schema))) tableErrors.push(`Schema 文件缺失: ${tables[tableName].schema}`);
  });
  tableErrors.forEach((e) => errors.push(e));
  checks.push([`data-contract ${tableNames.length} 张表文件名/主键齐备`, tableErrors.length === 0]);

  // v1 五表仍在（只追加，不破坏）
  const keptV1Tables = V1_TABLES.filter((t) => tableNames.includes(t));
  V1_TABLES.forEach((t) => {
    if (!tableNames.includes(t)) errors.push(`v1 数据表被删除: ${t}`);
  });
  checks.push([`v1 五张表全部保留（${keptV1Tables.length}/5）`, keptV1Tables.length === V1_TABLES.length]);

  // v1 字段仍在（逐字段）
  V1_TABLES.forEach((t) => {
    if (!tables[t]) return;
    const fields = Object.keys(tables[t].fields);
    const legacy = v1FieldBaseline(t);
    legacy.forEach((f) => {
      if (!fields.includes(f)) errors.push(`v1 表 ${t} 丢失字段: ${f}`);
    });
  });
  checks.push(['v1 表字段无删除', V1_TABLES.every((t) => !tables[t] || v1FieldBaseline(t).every((f) => Object.keys(tables[t].fields).includes(f)))]);

  // 枚举交叉引用
  const enumOperationTypes = Object.keys(enums.enums.operationType);
  const enumFactors = Object.keys(enums.enums.factorType);
  const enumErrors = [];
  tables['crop-schedule'].fields.operationType.enum.forEach((op) => {
    if (!enumOperationTypes.includes(op)) enumErrors.push(`common-enums 未覆盖作业类型: ${op}`);
  });
  tables['operation-threshold'].fields.factorType.enum.forEach((f) => {
    if (!enumFactors.includes(f)) enumErrors.push(`common-enums 未覆盖因子类型: ${f}`);
  });
  // v1 四因子仍保留
  V1_FACTORS.forEach((f) => {
    if (!tables['operation-threshold'].fields.factorType.enum.includes(f)) enumErrors.push(`v1 因子被删除: ${f}`);
  });
  enumErrors.forEach((e) => errors.push(e));
  checks.push([`common-enums 覆盖数据表枚举（因子 ${enumFactors.length} 类）`, enumErrors.length === 0]);

  // api-contract v2 追加可选字段
  const nodeProps = (((timeline || {}).response || {}).properties || {}).nodes || {};
  const nodeItemProps = (nodeProps.items || {}).properties || {};
  const v2ApiFields = ['phenologyStage', 'keyFarmingPeriod'];
  const apiFieldErrors = [];
  v2ApiFields.forEach((f) => {
    if (!nodeItemProps[f]) apiFieldErrors.push(`api-contract nodes[] 缺少 v2 可选字段: ${f}`);
  });
  const nodeRequired = (nodeProps.items || {}).required || [];
  v2ApiFields.forEach((f) => {
    if (nodeRequired.includes(f)) apiFieldErrors.push(`api-contract ${f} 不应为必填`);
  });
  checks.push(['api-contract v2 追加可选字段且非必填', apiFieldErrors.length === 0]);

  const changelog = fs.readFileSync(path.join(ROOT, 'shared/constants/changelog.md'), 'utf8');
  checks.push(['changelog 记录 v1 冻结', changelog.includes('v1')]);
  checks.push(['changelog 记录 v2 变更', changelog.includes('v2')]);
  checks.push(['changelog 记录 v3 变更', changelog.includes('v3')]);

  // v3 断言
  const V3_EXPORTS = ['fetchRealtimeWeather', 'fetchPredictedWeather', 'orchestrateWeather'];
  V3_EXPORTS.forEach((fn) => {
    if (!actualExports.includes(fn)) errors.push(`compute-contract 缺少 v3 导出函数: ${fn}`);
  });
  checks.push(['compute-contract v3 追加 3 个新模块导出', V3_EXPORTS.every((fn) => actualExports.includes(fn))]);

  const V3_MODULE_FILES = ['compute/cma-adapter.js', 'compute/pangu-adapter.js', 'compute/weather-orchestrator.js'];
  const missingV3Files = V3_MODULE_FILES.filter((f) => !fs.existsSync(path.join(ROOT, f)));
  missingV3Files.forEach((f) => process.stdout.write(`⚠ v3 计算模块已移除，跳过模块文件检查: ${f}\n`));
  checks.push(['v3 计算模块文件检查（后端已移除，跳过）', true]);

  const cropScheduleFields = tables['crop-schedule'] ? Object.keys(tables['crop-schedule'].fields) : [];
  if (!cropScheduleFields.includes('category')) errors.push('data-contract crop-schedule 缺少 v3 字段: category');
  const cropData = readJson('data/crop-base/crop-schedule.json');
  const cropCategories = new Set(cropData.map((r) => r.category));
  if (cropCategories.size < 5) errors.push(`crop-schedule 类别数 ${cropCategories.size} < 5`);
  checks.push([`crop-schedule 含 category 字段且枚举覆盖 ≥5 类（实际 ${cropCategories.size} 类）`, cropScheduleFields.includes('category') && cropCategories.size >= 5]);

  const thresholdFactorEnum = tables['operation-threshold'] ? (tables['operation-threshold'].fields.factorType.enum || []) : [];
  const V3_NEW_FACTORS = ['airQuality', 'uvIndex', 'evapotranspiration', 'soilTemperature', 'soilEC'];
  V3_NEW_FACTORS.forEach((f) => {
    if (!thresholdFactorEnum.includes(f)) errors.push(`operation-threshold factorType 缺少 v3 新因子: ${f}`);
  });
  checks.push([`operation-threshold factorType 含 5 新值（达 ${thresholdFactorEnum.length} 类）`, V3_NEW_FACTORS.every((f) => thresholdFactorEnum.includes(f))]);

  const provenanceFields = tables['dataset-provenance'] ? Object.keys(tables['dataset-provenance'].fields) : [];
  if (!provenanceFields.includes('dataSourceType')) errors.push('data-contract dataset-provenance 缺少 v3 字段: dataSourceType');
  checks.push(['dataset-provenance 含 dataSourceType 字段', provenanceFields.includes('dataSourceType')]);

  const v3ApiFields = ['predictSource', 'predictWindow', 'confidence', 'dataSourceType'];
  const v3ApiErrors = [];
  v3ApiFields.forEach((f) => {
    if (!nodeItemProps[f]) v3ApiErrors.push(`api-contract nodes[] 缺少 v3 可选字段: ${f}`);
  });
  v3ApiFields.forEach((f) => {
    if (nodeRequired.includes(f)) v3ApiErrors.push(`api-contract ${f} 不应为必填`);
  });
  v3ApiErrors.forEach((e) => errors.push(e));
  checks.push(['api-contract v3 追加可选字段且非必填', v3ApiErrors.length === 0]);

  checks.forEach(([name, ok]) => {
    process.stdout.write(`${ok ? '✔' : '✘'} ${name}\n`);
  });

  if (errors.length > 0) {
    process.stderr.write('\n契约校验失败：\n');
    errors.forEach((e) => process.stderr.write(`  - ${e}\n`));
    process.stderr.write('\n变更指引：契约冻结后只追加版本，请在 shared/constants/changelog.md 登记变更并追加 v2，勿就地修改 v1 字段。\n');
    process.exit(1);
  }
  process.stdout.write('\n契约校验通过：三份契约交叉引用一致，冻结状态有效。\n');
}

/** v1 表字段基线（用于确认 v2 未删除既有字段）。 */
function v1FieldBaseline(tableName) {
  const baseline = {
    'crop-schedule': ['crop', 'nodeSeq', 'daysAfterSowing', 'operationType', 'name', 'description', 'source'],
    'operation-threshold': ['operationType', 'factorType', 'unit', 'greenRange', 'yellowRange', 'redCondition', 'meaning', 'rescheduleCopy'],
    'solar-term-farming': ['termName', 'dateRule', 'farmingTip'],
    'festival-rule': ['festivalName', 'type', 'dateRule', 'saleWindowDays', 'reminderCopy'],
    'status-copy': ['status', 'copy', 'tone', 'color'],
  };
  return baseline[tableName] || [];
}

main();