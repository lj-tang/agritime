/**
 * W1 本地假数据（上线前删除）— 离线渲染完整时间线与今日结论。
 * 与 contract/api-contract.json 的响应结构保持一致。
 * 时间/任务名称/天气字段以 null 占位，待后端真实数据接入。
 * v1.1：新增 solarTerm / solarTermTip / weatherFactors / suitability / severeWeatherAlert（节点）
 *       与 dateAnchor / applicableCrops / salesCopy（节日提醒），全部保持 null 占位，不造假。
 */
window.MOCK_TIMELINE = {
  apiVersion: 'v1',
  dataSource: 'cache',
  dataTime: null,
  todayConclusion: {
    nodeId: null,
    operationType: null,
    status: null,
    copy: null,
    certainty: null,
  },
  nodes: [
    { nodeId: null, nodeSeq: 1, daysAfterSowing: 0, date: null, operationType: null, name: null, description: null, status: null, copy: null, reasons: [], rescheduleAdvice: null, certainty: null, weatherIcon: null, solarTerm: null, solarTermTip: null, weatherFactors: null, suitability: null, severeWeatherAlert: null },
    { nodeId: null, nodeSeq: 2, daysAfterSowing: 7, date: null, operationType: null, name: null, description: null, status: null, copy: null, reasons: [], rescheduleAdvice: null, certainty: null, weatherIcon: null, solarTerm: null, solarTermTip: null, weatherFactors: null, suitability: null, severeWeatherAlert: null },
    { nodeId: null, nodeSeq: 4, daysAfterSowing: 120, date: null, operationType: null, name: null, description: null, status: null, copy: null, reasons: [], rescheduleAdvice: null, certainty: null, weatherIcon: null, solarTerm: null, solarTermTip: null, weatherFactors: null, suitability: null, severeWeatherAlert: null },
    { nodeId: null, nodeSeq: 5, daysAfterSowing: 140, date: null, operationType: null, name: null, description: null, status: null, copy: null, reasons: [], rescheduleAdvice: null, certainty: null, weatherIcon: null, solarTerm: null, solarTermTip: null, weatherFactors: null, suitability: null, severeWeatherAlert: null },
    { nodeId: null, nodeSeq: 6, daysAfterSowing: 170, date: null, operationType: null, name: null, description: null, status: null, copy: null, reasons: [], rescheduleAdvice: null, certainty: null, weatherIcon: null, solarTerm: null, solarTermTip: null, weatherFactors: null, suitability: null, severeWeatherAlert: null },
    { nodeId: null, nodeSeq: 8, daysAfterSowing: 205, date: null, operationType: null, name: null, description: null, status: null, copy: null, reasons: [], rescheduleAdvice: null, certainty: null, weatherIcon: null, solarTerm: null, solarTermTip: null, weatherFactors: null, suitability: null, severeWeatherAlert: null },
  ],
  reminders: [
    { festivalName: null, festivalDate: null, windowStart: null, saleWindowDays: null, reminderCopy: null, dateAnchor: null, applicableCrops: null, salesCopy: null },
  ],
};