/**
 * 地块监测设备与读数（用户自选接入）
 *
 * 为什么要有这一层：地块分析里的土壤墒情、鱼塘的水温溶氧，目前都是模型推算的。
 * 农户只要在塘边插一根溶氧仪、或者装个几十块的温湿度探头，实测值就比模型准得多。
 * 但也有人什么都不装 —— 所以设备是**可选的**：没接设备就继续用模型估算并标注清楚，
 * 接了设备就用实测值覆盖，并在界面上标明"实测"。
 *
 * 支持三种数据来源：
 *   manual —— 用户手抄仪表读数（最现实，先支持这个）
 *   api    —— 用户填一个返回 JSON 的地址，前端定时拉（预留，后端可加代理）
 *   mqtt   —— 预留位，展示"未接入"，不假装能连
 */
(function () {
  var KEY = 'agritime.sensors.v1';
  var READ_LIMIT = 60; // 每台设备只留最近 60 条，localStorage 不会无限膨胀

  var KINDS = {
    soil_moisture: { label: '土壤墒情', unit: 'm³/m³', field: 'moisture', min: 0, max: 1 },
    soil_temp: { label: '土壤温度', unit: '℃', field: 'temp', min: -20, max: 60 },
    water_temp: { label: '水温', unit: '℃', field: 'temp', min: 0, max: 45 },
    do: { label: '溶解氧', unit: 'mg/L', field: 'do', min: 0, max: 20 },
    ph: { label: 'pH', unit: '', field: 'ph', min: 0, max: 14 },
    ammonia: { label: '氨氮', unit: 'mg/L', field: 'ammonia', min: 0, max: 5 },
    weather: { label: '小型气象站', unit: '℃', field: 'temp', min: -40, max: 60 },
    camera: { label: '摄像头', unit: '', field: '', min: 0, max: 0 }
  };

  var SOURCES = {
    manual: '手动录入',
    api: '网络接口',
    mqtt: 'MQTT（预留）'
  };

  function read() {
    try {
      var raw = localStorage.getItem(KEY);
      var arr = raw ? JSON.parse(raw) : [];
      return Array.isArray(arr) ? arr : [];
    } catch (e) {
      return [];
    }
  }

  function write(list) {
    try {
      localStorage.setItem(KEY, JSON.stringify(list));
    } catch (e) {
      // 存储满了就丢掉最老的读数，再试一次
      list.forEach(function (d) {
        if (d.readings && d.readings.length > 10) d.readings = d.readings.slice(-10);
      });
      try { localStorage.setItem(KEY, JSON.stringify(list)); } catch (e2) { /* 放弃 */ }
    }
  }

  function uid() {
    return 's_' + Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
  }

  function list(plotId) {
    var all = read();
    return plotId ? all.filter(function (d) { return d.plotId === plotId; }) : all;
  }

  function add(plotId, device) {
    var all = read();
    var d = {
      sensorId: uid(),
      plotId: plotId,
      kind: device.kind || 'soil_moisture',
      name: device.name || '',
      source: device.source || 'manual',
      url: device.url || '',
      readings: [],
      createdAt: new Date().toISOString()
    };
    all.push(d);
    write(all);
    return d;
  }

  function remove(sensorId) {
    write(read().filter(function (d) { return d.sensorId !== sensorId; }));
  }

  function addReading(sensorId, value, at) {
    var all = read();
    var d = null;
    all.forEach(function (x) { if (x.sensorId === sensorId) d = x; });
    if (!d) return null;
    var v = parseFloat(value);
    if (!isFinite(v)) return null;
    d.readings = d.readings || [];
    d.readings.push({ at: at || new Date().toISOString(), value: v });
    if (d.readings.length > READ_LIMIT) d.readings = d.readings.slice(-READ_LIMIT);
    write(all);
    return d;
  }

  /** 某类设备的最新一条读数，没有返回 null。 */
  function latest(plotId, kind) {
    var best = null;
    list(plotId).forEach(function (d) {
      if (d.kind !== kind || !d.readings || !d.readings.length) return;
      var last = d.readings[d.readings.length - 1];
      if (!best || last.at > best.at) best = last;
    });
    return best;
  }

  /** 把实测读数整理成接口参数，只带真正测到的项。 */
  function measuredParams(plotId) {
    var p = {};
    var m = latest(plotId, 'soil_moisture');
    if (m) p.soilMoisture = m.value;
    var st = latest(plotId, 'soil_temp');
    if (st) p.soilTemp = st.value;
    var wt = latest(plotId, 'water_temp');
    if (wt) p.waterTemp = wt.value;
    var d = latest(plotId, 'do');
    if (d) p.dissolvedOxygen = d.value;
    var ph = latest(plotId, 'ph');
    if (ph) p.ph = ph.value;
    var nh = latest(plotId, 'ammonia');
    if (nh) p.ammonia = nh.value;
    return p;
  }

  /** 读数是否新鲜：超过 24 小时提示"可能过期"，避免拿旧数据当现状。 */
  function isStale(reading, hours) {
    if (!reading || !reading.at) return true;
    var h = hours || 24;
    return (Date.now() - new Date(reading.at).getTime()) > h * 3600 * 1000;
  }

  window.SensorsStore = {
    KINDS: KINDS,
    SOURCES: SOURCES,
    list: list,
    add: add,
    remove: remove,
    addReading: addReading,
    latest: latest,
    measuredParams: measuredParams,
    isStale: isStale
  };
})();
