/**
 * 作物标识映射共享模块（中文名 ↔ cropId）
 * 供 index.html 与 timeline.html 共享，避免内联重复导致漂移。
 * 纯浏览器脚本，无任何第三方依赖。
 */
(function () {
  var CROP_IDS = ['dry_garlic', 'wheat_winter', 'corn_summer', 'rice', 'soybean'];

  var CROPS = [
    { name: '山东金乡大蒜', cropId: 'dry_garlic', lat: 35.07, lng: 116.31, city: '山东省济宁市金乡县' },
    { name: '河南新乡小麦', cropId: 'wheat_winter', lat: 35.30, lng: 113.93, city: '河南省新乡市' },
    { name: '黑龙江绥化玉米', cropId: 'corn_summer', lat: 46.65, lng: 126.99, city: '黑龙江省绥化市' },
    { name: '湖北荆州水稻', cropId: 'rice', lat: 30.33, lng: 112.24, city: '湖北省荆州市' },
    { name: '河南开封大豆', cropId: 'soybean', lat: 34.75, lng: 114.30, city: '河南省开封市' },
    { name: '山东寿光番茄', cropId: null, lat: 36.86, lng: 118.79, city: '山东省潍坊市寿光市' }
  ];

  var CROP_KEYWORDS = {
    '大蒜': 'dry_garlic',
    '蒜': 'dry_garlic',
    '小麦': 'wheat_winter',
    '玉米': 'corn_summer',
    '水稻': 'rice',
    '稻': 'rice',
    '大豆': 'soybean',
    '黄豆': 'soybean'
  };

  function normalize(name) {
    return (typeof name === 'string' ? name : '').trim();
  }

  function isCropId(value) {
    return CROP_IDS.indexOf(value) !== -1;
  }

  /**
   * 按中文名精确查询作物条目。
   * @param {string} name 作物中文名
   * @returns {object|undefined}
   */
  function getCropByName(name) {
    var n = normalize(name);
    if (!n) return undefined;
    for (var i = 0; i < CROPS.length; i += 1) {
      if (CROPS[i].name === n) return CROPS[i];
    }
    return undefined;
  }

  /**
   * 中文名 → cropId 解析。
   * 顺序：合法 cropId 透传 → 精确匹配 → 包含匹配（取最长名）→ 关键词命中 → null。
   * @param {string} name 作物中文名或 cropId
   * @returns {string|null}
   */
  function resolveCropId(name) {
    var n = normalize(name);
    if (!n) return null;
    if (isCropId(n)) return n;
    var exact = getCropByName(n);
    if (exact && exact.cropId) return exact.cropId;
    var best = null;
    for (var i = 0; i < CROPS.length; i += 1) {
      var c = CROPS[i];
      if (!c.cropId) continue;
      if (c.name.indexOf(n) !== -1 || n.indexOf(c.name) !== -1) {
        if (!best || c.name.length > best.name.length) best = c;
      }
    }
    if (best) return best.cropId;
    for (var kw in CROP_KEYWORDS) {
      if (Object.prototype.hasOwnProperty.call(CROP_KEYWORDS, kw) && n.indexOf(kw) !== -1) {
        return CROP_KEYWORDS[kw];
      }
    }
    return null;
  }

  /**
   * 构造接口 crop 参数：有匹配用 cropId，无匹配回退中文名，兜底首个 cropId，保证非空。
   * @param {string} name 作物中文名
   * @returns {string}
   */
  function buildCropParam(name) {
    var n = normalize(name);
    var id = resolveCropId(n);
    if (id) return id;
    if (n) return n;
    return CROPS[0].cropId;
  }

  window.CropMap = {
    CROP_IDS: CROP_IDS,
    CROPS: CROPS,
    CROP_KEYWORDS: CROP_KEYWORDS,
    getCropByName: getCropByName,
    resolveCropId: resolveCropId,
    buildCropParam: buildCropParam
  };
})();