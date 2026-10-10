/**
 * W1 本地假数据（上线前删除）— 离线渲染完整时间线与今日结论。
 * 与 contract/api-contract.json 的响应结构保持一致。
 */
window.MOCK_TIMELINE = {
  apiVersion: 'v1',
  dataSource: 'cache',
  dataTime: '2025-04-01T08:00:00.000Z',
  todayConclusion: {
    nodeId: '山东金乡大蒜#5',
    operationType: 'fertilizer',
    status: 'red',
    copy: '不建议今天作业，请参考改期建议',
    certainty: 'predicted',
  },
  nodes: [
    { nodeId: '山东金乡大蒜#1', nodeSeq: 1, daysAfterSowing: 0, date: '2025-03-15', operationType: 'sowing', name: '播种', description: '适期播种，深度一致，覆土镇压', status: 'green', copy: '今天适宜作业，按计划进行', reasons: [], rescheduleAdvice: null, certainty: 'certain' },
    { nodeId: '山东金乡大蒜#2', nodeSeq: 2, daysAfterSowing: 7, date: '2025-03-22', operationType: 'emergence', name: '出苗', description: '检查出苗情况，缺苗补种', status: 'green', copy: '今天适宜作业，按计划进行', reasons: [], rescheduleAdvice: null, certainty: 'certain' },
    { nodeId: '山东金乡大蒜#4', nodeSeq: 4, daysAfterSowing: 120, date: '2025-07-13', operationType: 'irrigation', name: '返青水', description: '返青期浇水，促进恢复生长', status: 'yellow', copy: '条件一般，建议谨慎并关注天气变化', reasons: ['湿度偏差'], rescheduleAdvice: null, certainty: 'predicted', weatherIcon: 'cloudy' },
    { nodeId: '山东金乡大蒜#5', nodeSeq: 5, daysAfterSowing: 140, date: '2025-08-02', operationType: 'fertilizer', name: '返青追肥', description: '返青期追施氮肥，规避降雨与低温', status: 'red', copy: '不建议今天作业，请参考改期建议', reasons: ['降雨不利（怕暴雨把肥冲走）'], rescheduleAdvice: '建议推迟至 2025-08-05', certainty: 'predicted', weatherIcon: 'heavyRain' },
    { nodeId: '山东金乡大蒜#6', nodeSeq: 6, daysAfterSowing: 170, date: '2025-09-01', operationType: 'pesticide', name: '抽薹期打药', description: '防治叶枯病与蒜蛆，避雨避风', status: 'green', copy: '今天适宜作业，按计划进行', reasons: [], rescheduleAdvice: null, certainty: 'predicted', weatherIcon: 'clear' },
    { nodeId: '山东金乡大蒜#8', nodeSeq: 8, daysAfterSowing: 205, date: '2025-10-06', operationType: 'harvest', name: '蒜薹采收', description: '蒜薹适时采收，遇雨提前抢收', status: 'yellow', copy: '条件一般，建议谨慎并关注天气变化', reasons: ['降雨偏差'], rescheduleAdvice: '有雨风险，建议提前抢收', certainty: 'predicted', festivalIcon: 'midAutumn' },
  ],
  reminders: [
    { festivalName: '中秋节', festivalDate: '2025-10-06', windowStart: '2025-09-21', saleWindowDays: 15, reminderCopy: '中秋备货窗口，建议提前 15 天锁定采收与出货' },
  ],
};