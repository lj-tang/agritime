/**
 * 渔业板块（T09）：品类 → 放养日 → 生产日历。
 *
 * 品种与规程的唯一来源是 /api/industry/*（`/species` 返回 50 个品种，`/timeline` 返回节点）。
 * 下面的 SPECIES_50 / SS 表只是**接口不可用时的离线兜底**，不能当成第二套品种库：
 * 接口一旦可用就以接口为准，避免两边口径打架。
 *
 * 水温适宜度是后端用气温近似的，页面上必须原样保留那句说明，不能把近似值讲成实测。
 */
(function () {
  'use strict';

  var API = '/api/industry';
  var form = document.getElementById('industry-form');
  if (!form) return;

  var speciesSel = document.getElementById('industry-species');
  var dateInput = document.getElementById('industry-date');
  var locInput = document.getElementById('industry-location');
  var result = document.getElementById('industry-result');

  // 当前下拉里的品种（接口优先，兜底表其次），换品种时用它刷新介绍
  var currentSpecies = [];

  var CITY_COORDS = {
    '湖北省荆州市': [30.33, 112.24],
    '江苏省苏州市': [31.30, 120.60],
    '广东省广州市': [23.13, 113.26],
    '山东省济宁市': [35.07, 116.31]
  };

  function todayISO() {
    var d = new Date();
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
  }

  function coordsOf(location) {
    if (window.__markerLatLon) return [window.__markerLatLon.lat, window.__markerLatLon.lon];
    return CITY_COORDS[location] || [30.33, 112.24];
  }

  var SPECIES_50 = [
    ['grass_carp','草鱼','淡水鱼'],['common_carp','鲤鱼','淡水鱼'],['crucian_carp','鲫鱼','淡水鱼'],
    ['silver_carp','鲢鱼','淡水鱼'],['bighead_carp','鳙鱼','淡水鱼'],['tilapia','罗非鱼','淡水鱼'],
    ['blunt_snout_bream','团头鲂','淡水鱼'],['black_carp','青鱼','淡水鱼'],['catfish','鲶鱼','淡水鱼'],
    ['yellow_catfish','黄颡鱼','淡水鱼'],['snakehead','乌鳢','淡水鱼'],['mandarin_fish','鳜鱼','淡水鱼'],
    ['loach','泥鳅','淡水鱼'],['swamp_eel','黄鳝','淡水鱼'],['freshwater_bass','淡水鲈','淡水鱼'],
    ['dace','鲮鱼','淡水鱼'],['topmouth_culter','翘嘴鲌','淡水鱼'],['channel_catfish','斑点叉尾鮰','淡水鱼'],
    ['tench','丁桂','淡水鱼'],['wuchang_bream','长春鳊','淡水鱼'],['large_yellow_croaker','大黄鱼','海水鱼'],
    ['flounder','牙鲆','海水鱼'],['turbot','大菱鲆','海水鱼'],['tongue_sole','半滑舌鳎','海水鱼'],
    ['sea_bass','海鲈','海水鱼'],['red_seabream','真鲷','海水鱼'],['black_seabream','黑鲷','海水鱼'],
    ['pufferfish','河豚','海水鱼'],['grouper','石斑鱼','海水鱼'],['cobia','军曹鱼','海水鱼'],
    ['mitten_crab','大闸蟹','蟹类'],['swimming_crab','梭子蟹','蟹类'],['mud_crab','青蟹','蟹类'],
    ['white_shrimp','南美白对虾','虾类'],['giant_freshwater_prawn','罗氏沼虾','虾类'],['freshwater_shrimp','青虾','虾类'],
    ['black_tiger_shrimp','斑节对虾','虾类'],['chinese_shrimp','中国对虾','虾类'],['crayfish','小龙虾','虾类'],
    ['scallop','扇贝','贝类'],['oyster','牡蛎','贝类'],['clam','蛤蜊','贝类'],
    ['mussel','贻贝','贝类'],['blood_clam','泥蚶','贝类'],['ark_clam','毛蚶','贝类'],
    ['sea_cucumber','海参','海参'],['soft_shell_turtle','中华鳖','爬行类'],['bullfrog','牛蛙','两栖类'],
    ['snail','田螺','软体类'],['kelp','海带','藻类']
  ];

  function ensure50(list) {
    var ids = {}; list.forEach(function (s) { ids[s.id] = true; });
    SPECIES_50.forEach(function (a) {
      if (!ids[a[0]]) list.push({ id: a[0], name: a[1], category: a[2], stockUnit: '苗种', stockSeason: '春放', tips: '', stages: [] });
    });
  }

  function loadSpecies() {
    var xhr = new XMLHttpRequest();
    xhr.open('GET', API + '/species', true);
    xhr.timeout = 10000;
    xhr.onload = function () {
      try {
        var body = JSON.parse(xhr.responseText);
        var list = (body.data && body.data.species) || [];
        ensure50(list);
        renderSpecies(list);
      } catch (e) {
        renderSpeciesFallback();
      }
    };
    xhr.onerror = xhr.ontimeout = function () {
      renderSpeciesFallback();
    };
    xhr.send();
  }

  function renderSpecies(list) {
    speciesSel.innerHTML = '';
    list.forEach(function (s) {
      var opt = document.createElement('option');
      opt.value = s.id;
      opt.textContent = s.name + '（' + s.category + '）';
      speciesSel.appendChild(opt);
    });
    currentSpecies = list;
    if (list.length) showIntro(list[0]);
  }

  function renderSpeciesFallback() {
    speciesSel.innerHTML = '';
    var list = [];
    SPECIES_50.forEach(function (a) {
      var opt = document.createElement('option');
      opt.value = a[0];
      opt.textContent = a[1] + '（' + a[2] + '）';
      speciesSel.appendChild(opt);
      list.push({ id: a[0], name: a[1], category: a[2], waterTempMin: 15, waterTempMax: 30, stockSeason: '春放' });
    });
    currentSpecies = list;
    showIntro(list[0]);
  }

  function showIntro(s) {
    result.innerHTML = '';
    var box = document.createElement('div');
    box.className = 'industry-empty';
    box.textContent = '选好品类和放养日期后，会按阶段生成生产日历。当前品类：'
      + (s.name || '') + '，适宜水温 ' + (s.waterTempMin || '—') + '~' + (s.waterTempMax || '—') + '℃，'
      + (s.stockSeason || '') + '。';
    result.appendChild(box);
  }

  function statusLabel(status) {
    if (status === 'ok') return '适宜';
    if (status === 'caution') return '需注意';
    return '无数据';
  }

  var SS = {
    grass_carp:[180,15,30,[['放苗',0,'选择健康草鱼苗，3%食盐水浸泡5分钟消毒','水温差不超过2℃'],['驯食青饲料',0.02,'逐步驯化摄食黑麦草、苏丹草等青饲料','少量多次观察摄食'],['青饲料投喂',0.08,'日投喂青饲料量为体重的20-30%','早晚各投喂一次'],['水质调控',0.17,'检测溶氧pH氨氮，草鱼耗氧较高保持溶氧>5mg/L','注意浮头现象'],['转食配合饲料',0.33,'逐步转为配合饲料，日投喂量3-5%','过渡期3-5天'],['强化育肥',0.67,'增加蛋白含量促进快速增重','水温适宜时加大投喂'],['起捕上市',1,'达到1.5斤以上商品规格，停料1天后起捕','选择晴朗天气操作']]],
    common_carp:[150,10,28,[['放苗',0,'选择健康鲤鱼苗，食盐水消毒','鲤鱼适应性强'],['驯食投喂',0.03,'驯化摄食配合饲料，日投喂量2-4%','杂食性易驯化'],['日常投喂',0.10,'正常投喂，根据水温调整投喂量','水温低时减少投喂'],['越冬管理',0.30,'北方需越冬，保持冰下水深1.5米以上','定期打冰眼补氧'],['恢复投喂',0.50,'开春水温回升后恢复投喂','逐步增加投喂量'],['强化育肥',0.75,'加大投喂促进育肥','蛋白含量28-32%'],['起捕上市',1,'达到1斤以上商品规格起捕','选择适宜天气']]],
    crucian_carp:[120,8,28,[['放苗',0,'选择健康鲫鱼苗，消毒后放养','鲫鱼耐低温适应性强'],['投喂',0.04,'日投喂量2-3%，少量多餐','鲫鱼摄食温和'],['日常管理',0.15,'定期检测水质，保持溶氧充足','鲫鱼耐低氧'],['分塘稀养',0.33,'检查生长差异分塘调整密度','操作轻柔'],['育肥',0.60,'强化投喂促进快速生长','水温适宜时增加投喂'],['起捕上市',1,'达到0.5斤以上规格起捕','选择晴朗天气']]],
    silver_carp:[150,15,30,[['放苗',0,'选择健康鲢鱼苗，食盐水消毒','鲢鱼喜上层水体'],['肥水培藻',0.05,'施有机肥培育浮游植物天然饵料','水色保持嫩绿色'],['水质管理',0.15,'监测藻相变化防止倒藻','透明度25-30cm'],['追肥调控',0.30,'定期追肥维持藻类密度','少量多次施肥'],['中期管理',0.50,'检查生长和鳃部健康','注意寄生虫防治'],['育肥',0.75,'保持肥水促进快速生长','水温适宜时生长快'],['起捕上市',1,'达到1斤以上规格起捕','统货起捕']]],
    bighead_carp:[160,15,30,[['放苗',0,'选择健康鳙鱼苗，消毒后放养','鳙鱼喜中上层水体'],['培饵',0.05,'施有机肥培育浮游动物天然饵料','轮虫水蚤为主'],['水质管理',0.15,'监测浮游动物密度','水色茶褐色为宜'],['追肥',0.30,'定期追肥维持浮游动物密度','动物性饵料为主'],['中期管理',0.50,'检查生长和鳃部','注意锚头鳋防治'],['育肥',0.75,'保持充足天然饵料','水温适宜生长快'],['起捕上市',1,'达到1.5斤以上规格起捕','统货起捕']]],
    tilapia:[120,20,35,[['放苗',0,'选择健康罗非鱼苗，水温20℃以上放养','热带鱼不耐低温'],['驯食投喂',0.03,'驯化摄食配合饲料，日投喂量3-5%','罗非鱼贪食'],['日常投喂',0.10,'正常投喂，注意控量防过食','日投2-3次'],['水温监控',0.25,'监测水温，低于15℃需准备越冬','罗非鱼适温20-35℃'],['越冬准备',0.40,'水温下降前加强育肥或转移越冬','温室或深水越冬'],['育肥',0.70,'强化投喂促进快速增重','蛋白含量28-32%'],['起捕上市',1,'达到0.8斤以上规格起捕','选择晴朗天气']]],
    blunt_snout_bream:[120,12,28,[['放苗',0,'选择健康团头鲂苗，消毒放养','团头鲂草食为主'],['投喂牧草',0.04,'投喂黑麦草、苏丹草等牧草','日投草量体重的15-20%'],['水质管理',0.15,'保持溶氧充足水质清新','团头鲂对溶氧要求高'],['转食颗粒',0.30,'逐步转为配合饲料投喂','日投喂量2-4%'],['育肥',0.65,'强化投喂促进快速生长','蛋白含量28%'],['起捕上市',1,'达到0.8斤以上规格起捕','选择适宜天气']]],
    black_carp:[200,15,28,[['放苗',0,'选择健康青鱼苗，消毒放养','青鱼肉食性较大个体'],['投喂螺蛳',0.03,'投喂鲜活螺蛳蚌类','青鱼喜食底栖贝类'],['驯食配合料',0.10,'逐步驯化摄食配合饲料','过渡期7-10天'],['日常投喂',0.25,'日投喂量2-4%，蛋白含量35%以上','青鱼需高蛋白'],['水质管理',0.40,'保持水深水质良好','定期换水'],['育肥',0.70,'强化投喂促进快速增重','注意肠炎防治'],['起捕上市',1,'达到3斤以上规格起捕','选择晴朗天气']]],
    catfish:[140,15,30,[['放苗',0,'选择健康鲶鱼苗，消毒放养','鲶鱼底栖喜暗环境'],['驯食投喂',0.04,'驯化摄食配合饲料，日投喂量3-5%','傍晚投喂为主'],['暗环境管理',0.15,'保持适当遮光，鲶鱼畏强光','可在池中放水草遮荫'],['水质管理',0.30,'定期换水保持底质清洁','鲶鱼耗氧较低'],['育肥',0.65,'强化投喂促进快速生长','蛋白含量32-36%'],['起捕上市',1,'达到1斤以上规格起捕','选择傍晚操作']]],
    yellow_catfish:[150,15,28,[['放苗',0,'选择健康黄颡鱼苗，消毒放养','黄颡鱼底栖群居'],['驯食高蛋白',0.03,'驯化摄食高蛋白配合饲料','蛋白含量38-42%'],['日常投喂',0.10,'日投喂量3-5%，傍晚为主','黄颡鱼夜行性'],['水质管理',0.25,'保持溶氧充足水质清新','对溶氧要求较高'],['分级管理',0.45,'检查生长差异分级养殖','避免大小互残'],['育肥',0.70,'强化投喂促进增重','注意肠炎防治'],['起捕上市',1,'达到0.3斤以上规格起捕','选择傍晚操作']]],
    snakehead:[180,18,32,[['放苗',0,'选择健康乌鳢苗，消毒放养','乌鳢凶猛需单养'],['投喂活饵',0.03,'投喂活饵鱼或冰鲜鱼','日投喂量4-6%'],['驯食冰鲜鱼',0.08,'驯化摄食冰鲜鱼和配合饲料','过渡期5-7天'],['防互残管理',0.20,'注意大小差异防互残','及时分级分离'],['水质管理',0.35,'定期换水保持水质','乌鳢耐低氧'],['育肥',0.70,'强化投喂促进快速增重','蛋白含量40%以上'],['起捕上市',1,'达到1.5斤以上规格起捕','选择适宜天气']]],
    mandarin_fish:[180,15,28,[['放苗',0,'选择健康鳜鱼苗，消毒放养','鳜鱼凶猛肉食'],['投喂活饵鱼',0.03,'投喂活饵鱼（鲮鱼、麦穗鱼等）','活饵充足是关键'],['驯食死饵',0.10,'逐步驯化摄食死饵和冰鲜鱼','驯食难度较大'],['水质管理',0.25,'保持溶氧充足水质优良','鳜鱼对水质要求高'],['中期管理',0.45,'检查摄食和生长情况','注意寄生虫病'],['育肥',0.70,'保证充足饵料促进增重','活饵+冰鲜搭配'],['起捕上市',1,'达到1斤以上规格起捕','选择适宜天气']]],
    loach:[100,10,28,[['放苗',0,'选择健康泥鳅苗，消毒放养','泥鳅耐低氧适应性强'],['投喂',0.05,'驯化摄食配合饲料，日投喂量2-4%','杂食性易饲养'],['底质管理',0.20,'保持底质软泥丰富有机质','泥鳅喜底栖钻泥'],['水质管理',0.40,'定期换水保持水质清洁','泥鳅耐低氧但需清洁'],['育肥',0.70,'强化投喂促进快速生长','蛋白含量28-32%'],['起捕上市',1,'达到0.1斤以上规格起捕','笼捕或干池起捕']]],
    swamp_eel:[150,15,30,[['放苗',0,'选择健康黄鳝苗，消毒放养','黄鳝底栖喜洞穴'],['投喂',0.04,'驯化摄食蚯蚓、冰鲜鱼等','日投喂量3-5%'],['洞穴管理',0.15,'设置竹筒或人工洞穴供栖息','黄鳝喜穴居'],['水质调控',0.30,'保持水位稳定水质清洁','水深10-20cm为宜'],['育肥',0.65,'强化投喂高蛋白饲料','蛋白含量35-40%'],['起捕上市',1,'达到0.3斤以上规格起捕','笼捕为主']]],
    freshwater_bass:[160,15,25,[['放苗',0,'选择健康淡水鲈苗，消毒放养','淡水鲈肉食性'],['驯食冰鲜鱼',0.03,'驯化摄食冰鲜鱼和配合饲料','过渡期7天'],['日常投喂',0.10,'日投喂量3-5%，蛋白含量38%','定时定点投喂'],['水质管理',0.25,'保持溶氧充足水质清新','对溶氧要求较高'],['中期管理',0.45,'检查生长和健康状况','注意烂鳃病防治'],['育肥',0.70,'强化投喂促进快速增重','冰鲜+配合料搭配'],['起捕上市',1,'达到1斤以上规格起捕','选择适宜天气']]],
    dace:[140,18,32,[['放苗',0,'选择健康鲮鱼苗，消毒放养','鲮鱼暖水性不耐低温'],['水温适应',0.04,'放养后适应环境，保持水温18℃以上','鲮鱼适温18-32℃'],['投喂',0.10,'驯化摄食配合饲料，日投喂量2-4%','杂食性'],['越冬管理',0.30,'水温低于15℃需越冬保护','深水或温室越冬'],['恢复投喂',0.50,'水温回升后恢复正常投喂','逐步增加投喂量'],['育肥',0.75,'强化投喂促进增重','蛋白含量28-32%'],['起捕上市',1,'达到0.5斤以上规格起捕','选择适宜天气']]],
    topmouth_culter:[160,15,28,[['放苗',0,'选择健康翘嘴鲌苗，消毒放养','翘嘴鲌性情活跃'],['大水面投喂',0.04,'需大水面养殖，投喂配合饲料','日投喂量3-5%'],['水质管理',0.15,'保持大水体溶氧充足','翘嘴鲌需高溶氧'],['分级管理',0.35,'及时分级避免互残','生长差异大'],['育肥',0.65,'强化投喂促进快速生长','蛋白含量35%以上'],['起捕上市',1,'达到1斤以上规格起捕','选择适宜天气']]],
    channel_catfish:[180,15,30,[['放苗',0,'选择健康斑点叉尾鮰苗，消毒放养','引进品种适应性强'],['驯食投喂',0.03,'驯化摄食配合饲料，日投喂量2-4%','杂食性易驯化'],['日常投喂',0.10,'正常投喂，蛋白含量30-34%','日投2次'],['水质管理',0.20,'定期换水保持水质清洁','斑点叉尾鮰耐低氧'],['中期管理',0.40,'检查生长和健康状况','注意肠败血症防治'],['育肥',0.70,'强化投喂促进快速增重','增加投喂频次'],['起捕上市',1,'达到1.5斤以上规格起捕','选择适宜天气']]],
    tench:[150,10,25,[['放苗',0,'选择健康丁桂苗，消毒放养','丁桂底栖耐寒'],['投喂',0.04,'驯化摄食配合饲料，日投喂量2-4%','杂食性'],['日常管理',0.15,'定期检测水质保持清洁','丁桂适应性强'],['越冬',0.35,'水温下降前加强育肥','丁桂耐寒可自然越冬'],['恢复摄食',0.55,'开春后恢复投喂促进生长','逐步增加投喂'],['育肥',0.75,'强化投喂促进增重','蛋白含量28%'],['起捕上市',1,'达到1斤以上规格起捕','选择适宜天气']]],
    wuchang_bream:[130,12,28,[['放苗',0,'选择健康长春鳊苗，消毒放养','长春鳊草食为主'],['投喂水草',0.04,'投喂水草和颗粒饲料','日投草量体重的10-15%'],['水质管理',0.15,'保持溶氧充足水质清新','长春鳊对溶氧敏感'],['育肥',0.50,'强化投喂配合饲料促进生长','日投喂量2-4%'],['起捕上市',1,'达到0.8斤以上规格起捕','选择适宜天气']]],
    large_yellow_croaker:[200,15,28,[['放苗',0,'选择健康大黄鱼苗，淡水浴消毒','大黄鱼海水网箱养殖'],['网箱适应',0.03,'放养后适应网箱环境','注意防风浪'],['投喂',0.08,'驯化摄食冰鲜鱼和配合饲料','日投喂量2-4%'],['防风浪管理',0.20,'风浪大时减少投喂加固网箱','大黄鱼怕风浪'],['水质监控',0.35,'监测盐度温度溶氧','海水网箱依赖水流交换'],['育肥',0.70,'强化投喂促进增重','蛋白含量38-42%'],['起捕上市',1,'达到1斤以上规格起捕','选择风平浪静天气']]],
    flounder:[240,12,22,[['放苗',0,'选择健康牙鲆苗，淡水浴消毒','牙鲆底栖海水鱼'],['底栖适应',0.02,'放养后适应底栖环境','注意底质清洁'],['投喂冰鲜鱼',0.05,'驯化摄食冰鲜鱼和配合饲料','日投喂量2-3%'],['水温监控',0.15,'保持水温12-22℃适宜范围','牙鲆适低温'],['中期管理',0.35,'检查生长和体色变化','注意白化病防治'],['育肥',0.70,'强化投喂促进增重','蛋白含量40%以上'],['起捕上市',1,'达到1.5斤以上规格起捕','选择适宜天气']]],
    turbot:[240,12,20,[['放苗',0,'选择健康大菱鲆苗，消毒放养','大菱鲆冷水性工厂化养殖'],['工厂化适应',0.02,'适应工厂化循环水环境','水温控制12-20℃'],['投喂',0.05,'驯化摄食配合饲料，日投喂量1-3%','蛋白含量42-46%'],['水温控制',0.15,'严格控制水温12-20℃','大菱鲆对水温敏感'],['分级管理',0.30,'定期分级避免互残','生长差异大'],['中期管理',0.50,'监测水质和摄食状况','注意肠炎防治'],['育肥',0.75,'强化投喂促进增重','高蛋白高脂肪饲料'],['起捕上市',1,'达到2斤以上规格起捕','工厂化出池']]],
    tongue_sole:[270,15,25,[['放苗',0,'选择健康半滑舌鳎苗，消毒放养','半滑舌鳎底栖生长慢'],['底栖适应',0.02,'适应底栖环境，注意底质','舌鳎喜埋于沙中'],['驯食',0.05,'驯化摄食配合饲料难度较大','耐心驯食7-14天'],['日常投喂',0.15,'日投喂量2-3%，蛋白含量42%','舌鳎摄食量少'],['水质管理',0.30,'保持水质清洁溶氧充足','对水质要求高'],['中期管理',0.50,'检查生长和健康状况','生长较慢需耐心'],['育肥',0.75,'强化投喂促进增重','保证营养充足'],['起捕上市',1,'达到1.5斤以上规格起捕','选择适宜天气']]],
    sea_bass:[180,15,25,[['放苗',0,'选择健康海鲈苗，盐度适应','海鲈可淡水驯化'],['盐度适应',0.03,'逐步降低盐度适应淡水或半咸水','海鲈广盐性'],['驯食投喂',0.08,'驯化摄食冰鲜鱼和配合饲料','日投喂量3-5%'],['日常投喂',0.20,'正常投喂，蛋白含量38%','定时定点投喂'],['水质管理',0.35,'保持溶氧充足水质清新','海鲈对溶氧要求高'],['育肥',0.70,'强化投喂促进快速增重','冰鲜+配合料搭配'],['起捕上市',1,'达到1.5斤以上规格起捕','选择适宜天气']]],
    red_seabream:[200,15,28,[['放苗',0,'选择健康真鲷苗，淡水浴消毒','真鲷海水网箱或池塘'],['网箱适应',0.03,'适应网箱或池塘环境','注意盐度稳定'],['投喂',0.08,'驯化摄食配合饲料和冰鲜鱼','日投喂量2-4%'],['水温管理',0.20,'保持水温15-28℃适宜范围','真鲷适温范围广'],['中期管理',0.40,'检查生长和健康状况','注意寄生虫防治'],['育肥',0.70,'强化投喂促进增重','蛋白含量38-42%'],['起捕上市',1,'达到1斤以上规格起捕','选择适宜天气']]],
    black_seabream:[200,15,28,[['放苗',0,'选择健康黑鲷苗，消毒放养','黑鲷杂食适应性强'],['池塘适应',0.03,'适应池塘或网箱环境','黑鲷适应性强'],['投喂',0.08,'驯化摄食配合饲料，日投喂量2-4%','杂食性易饲养'],['水质管理',0.20,'定期监测水质保持清洁','黑鲷耐粗养'],['育肥',0.60,'强化投喂促进生长','蛋白含量32-36%'],['起捕上市',1,'达到0.8斤以上规格起捕','选择适宜天气']]],
    pufferfish:[240,15,25,[['放苗',0,'选择健康河豚苗，消毒放养','河豚有毒操作需防护'],['安全适应',0.02,'放养后适应环境，注意防逃','河豚遇险膨胀需空间'],['投喂',0.05,'驯化摄食配合饲料和冰鲜鱼','日投喂量2-4%'],['防逃管理',0.15,'加固网箱防逃，河豚会咬网','检查网衣完好'],['水质管理',0.30,'保持水质清洁溶氧充足','注意水温变化'],['育肥',0.70,'强化投喂促进增重','蛋白含量38%'],['起捕上市',1,'达到1斤以上规格起捕','操作需戴手套防护']]],
    grouper:[200,20,30,[['放苗',0,'选择健康石斑鱼苗，消毒放养','石斑鱼暖水性海水鱼'],['网箱适应',0.03,'适应网箱环境，注意盐度稳定','石斑鱼喜岩礁'],['投喂鲜杂鱼',0.05,'投喂鲜杂鱼和配合饲料','日投喂量3-5%'],['水温监控',0.15,'保持水温20-30℃适宜范围','石斑鱼适高温'],['中期管理',0.35,'检查生长和摄食状况','注意神经坏死病防治'],['育肥',0.70,'强化投喂促进增重','蛋白含量40%以上'],['起捕上市',1,'达到1.5斤以上规格起捕','选择适宜天气']]],
    cobia:[180,20,30,[['放苗',0,'选择健康军曹鱼苗，消毒放养','军曹鱼暖水性生长快'],['网箱适应',0.03,'适应网箱环境，需大网箱','军曹鱼体型大游速快'],['高蛋白投喂',0.08,'投喂高蛋白配合饲料和鲜杂鱼','日投喂量3-5%'],['快速育肥',0.30,'军曹鱼生长快强化投喂','蛋白含量42%以上'],['水质管理',0.50,'保持大水体交换良好','军曹鱼耗氧高'],['起捕上市',1,'达到5斤以上规格起捕','选择适宜天气']]],
    mitten_crab:[180,10,25,[['放苗',0,'选择肢体完整活力强的扣蟹','大闸蟹洄游性需半咸水'],['投喂',0.04,'投喂配合饲料和鲜杂鱼，日投喂量3-5%','傍晚投喂为主'],['蜕壳管理',0.12,'蜕壳期增加钙质保持水位稳定','大闸蟹多次蜕壳'],['水质调控',0.25,'定期换水保持溶氧充足pH7.5-8.5','水草覆盖50%以上'],['育肥增重',0.55,'增加动物性饵料促进膏黄积累','水温下降时调整投喂'],['起捕上市',1,'蟹膏饱满后起捕','选择低温天气运输']]],
    swimming_crab:[120,15,28,[['放苗',0,'选择健康梭子蟹苗，消毒放养','梭子蟹海水养殖'],['投喂贝类',0.05,'投喂贝类和杂鱼，日投喂量5-8%','梭子蟹食量大'],['蜕壳管理',0.15,'蜕壳期增加钙质保持环境稳定','梭子蟹多次蜕壳'],['水质管理',0.30,'定期换水保持盐度稳定溶氧充足','海水蟹需盐度15-30‰'],['育肥',0.60,'强化投喂促进增重','增加动物性饵料'],['起捕上市',1,'达到规格后起捕','选择适宜天气']]],
    mud_crab:[150,18,32,[['放苗',0,'选择健康青蟹苗，消毒放养','青蟹暖水性海水蟹'],['投喂',0.04,'投喂贝类和杂鱼，日投喂量4-6%','傍晚投喂为主'],['蜕壳管理',0.13,'蜕壳期增加钙质保持水位稳定','青蟹多次蜕壳'],['水温监控',0.30,'保持水温18-32℃适宜范围','青蟹适高温'],['育肥',0.60,'强化投喂促进膏黄积累','增加动物性饵料'],['起捕上市',1,'蟹膏饱满后起捕','选择适宜天气']]],
    white_shrimp:[90,23,32,[['放苗',0,'选择健康虾苗，盐度适应放养','南美白对虾淡水化生长快'],['投喂',0.06,'少量多餐日投喂量3-5%','虾类摄食快'],['蜕壳期',0.20,'蜕壳频繁期补充钙磷','保持水质稳定'],['水质管理',0.40,'定期换水10-20%保持溶氧>5mg/L','氨氮<0.5mg/L'],['起捕上市',1,'达到规格后起捕','选择清晨或傍晚']]],
    giant_freshwater_prawn:[120,22,32,[['放苗',0,'选择健康虾苗，水温22℃以上放养','罗氏沼虾淡水虾需越冬'],['投喂',0.05,'投喂配合饲料，日投喂量3-5%','少量多餐'],['蜕壳期',0.17,'蜕壳期补充钙磷保持水质稳定','罗氏沼虾蜕壳频繁'],['越冬准备',0.40,'水温低于20℃需准备越冬','温室或深水越冬'],['育肥',0.70,'强化投喂促进增重','蛋白含量32%'],['起捕上市',1,'达到规格后起捕','选择清晨操作']]],
    freshwater_shrimp:[80,15,28,[['放苗',0,'选择健康青虾苗，消毒放养','青虾淡水养殖'],['投喂',0.06,'投喂配合饲料，日投喂量3-5%','少量多餐'],['水质管理',0.30,'定期换水保持溶氧充足','青虾对溶氧敏感'],['育肥',0.60,'强化投喂促进生长','蛋白含量30%'],['起捕上市',1,'达到规格后起捕','选择清晨操作']]],
    black_tiger_shrimp:[120,22,32,[['放苗',0,'选择健康虾苗，盐度适应放养','斑节对虾海水养殖'],['盐度适应',0.04,'逐步适应养殖盐度','斑节对虾广盐性'],['投喂',0.08,'投喂配合饲料，日投喂量3-5%','少量多餐'],['蜕壳期',0.20,'蜕壳期补充钙磷保持水质稳定','蜕壳频繁'],['水质管理',0.40,'定期换水保持盐度溶氧稳定','海水虾需盐度10-30‰'],['起捕上市',1,'达到规格后起捕','选择清晨或傍晚']]],
    chinese_shrimp:[100,15,28,[['放苗',0,'选择健康虾苗，盐度适应放养','中国对虾北方品种'],['投喂',0.06,'投喂配合饲料，日投喂量3-5%','少量多餐'],['蜕壳期',0.20,'蜕壳期补充钙磷保持水质稳定','蜕壳频繁'],['水质管理',0.40,'定期换水保持盐度溶氧稳定','北方养殖注意水温'],['起捕上市',1,'达到规格后起捕','选择清晨或傍晚']]],
    crayfish:[90,15,28,[['放苗',0,'选择健康虾苗，消毒放养','小龙虾稻田或池塘养殖'],['投喂',0.07,'投喂配合饲料和农副产品','日投喂量3-5%'],['蜕壳期',0.20,'蜕壳期补充钙磷提供隐蔽场所','小龙虾需隐蔽物'],['稻田管理',0.40,'稻田养殖注意水位和水稻管理','水位10-30cm'],['起捕上市',1,'达到规格后起捕','地笼捕捞为主']]],
    scallop:[180,10,22,[['播苗',0,'选择健康苗种均匀播养','扇贝滤食性不需投喂'],['附着管理',0.10,'检查附着情况调整密度','扇贝附着生长'],['海上育肥',0.30,'移至海域育肥利用天然饵料','监测藻类浓度'],['日常检查',0.50,'检查生长和存活率清除敌害','定期清理附着物'],['采收上市',1,'达到规格采收','选择适宜潮汐时间']]],
    oyster:[240,8,28,[['播苗',0,'选择健康牡蛎苗均匀播养','牡蛎滤食性不需投喂'],['附着',0.05,'检查附着情况确保固着牢固','牡蛎固着生长'],['育肥',0.25,'移至肥沃海域促进快速生长','利用天然浮游饵料'],['日常管理',0.50,'检查生长和存活率清除敌害','定期清理壳表附着物'],['采收上市',1,'达到规格采收','选择适宜潮汐时间']]],
    clam:[150,10,25,[['播苗',0,'选择健康蛤蜊苗均匀播养','蛤蜊滤食性底播养殖'],['底质管理',0.15,'保持沙质底质松软透气','底播养殖关键'],['育肥',0.35,'利用天然饵料促进生长','监测藻类浓度'],['日常检查',0.60,'检查生长和存活率清除敌害','定期翻松底质'],['采收上市',1,'达到规格采收','选择适宜潮汐时间']]],
    mussel:[180,8,22,[['播苗',0,'选择健康贻贝苗均匀播养','贻贝滤食性筏式养殖'],['筏式附着',0.08,'检查附着绳情况调整密度','贻贝筏式附着生长'],['海上育肥',0.30,'利用天然浮游饵料促进生长','监测海况变化'],['日常管理',0.50,'检查生长和脱落情况','定期清理附着物'],['采收上市',1,'达到规格采收','选择适宜潮汐时间']]],
    blood_clam:[180,10,28,[['播苗',0,'选择健康泥蚶苗均匀播养','泥蚶滤食性底播养殖'],['底质管理',0.15,'保持泥质底质适宜','底播养殖关键'],['育肥',0.35,'利用天然饵料促进生长','监测藻类浓度'],['日常检查',0.60,'检查生长和存活率清除敌害','定期翻松底质'],['采收上市',1,'达到规格采收','选择适宜潮汐时间']]],
    ark_clam:[180,10,25,[['播苗',0,'选择健康毛蚶苗均匀播养','毛蚶滤食性底播养殖'],['底质管理',0.15,'保持沙泥底质适宜','底播养殖关键'],['育肥',0.35,'利用天然饵料促进生长','监测藻类浓度'],['日常检查',0.60,'检查生长和存活率清除敌害','定期翻松底质'],['采收上市',1,'达到规格采收','选择适宜潮汐时间']]],
    sea_cucumber:[270,10,18,[['放苗',0,'选择健康参苗投放附着基','海参适低温生长慢'],['投喂',0.06,'投喂配合饲料和海藻粉','日投喂量1-2%'],['夏眠管理',0.33,'水温升高进入夏眠停止投喂','保持环境安静阴暗'],['恢复摄食',0.56,'水温下降后恢复摄食','逐步增加投喂量'],['育肥',0.74,'加强营养促进增重','注意水质变化'],['采收上市',1,'达到规格采收','选择低温季节']]],
    soft_shell_turtle:[240,25,30,[['放苗',0,'选择健康稚鳖消毒后放养','中华鳖变温动物需恒温'],['投喂',0.03,'投喂高蛋白饲料日投喂量2-4%','定时定点定量'],['恒温管理',0.13,'保持水温28-30℃最佳生长温度','恒温是关键'],['越冬准备',0.38,'水温下降前加强育肥','越冬前停食2-3天'],['越冬管理',0.50,'保持水温稳定减少干扰','定期检查存活率'],['出池上市',1,'达到规格后出池','选择适宜天气']]],
    bullfrog:[120,20,28,[['放苗',0,'选择健康蝌蚪或幼蛙消毒放养','牛蛙变态期需高蛋白'],['投喂',0.04,'投喂高蛋白饲料日投喂量5-8%','变态前加大投喂'],['变态期管理',0.25,'蝌蚪变态为幼蛙注意营养充足','变态是关键期'],['日常管理',0.50,'定期换水保持水质清洁','注意红腿病防治'],['育肥',0.75,'强化投喂促进快速生长','蛋白含量35%以上'],['起捕上市',1,'达到规格后起捕','选择适宜天气']]],
    snail:[120,10,28,[['放苗',0,'选择健康田螺苗均匀放养','田螺杂食性易养殖'],['投喂菜叶',0.10,'投喂菜叶和配合饲料','少量投喂'],['日常管理',0.30,'定期换水保持水质清洁','田螺耐低氧'],['育肥',0.60,'加强营养促进生长','投喂量适当增加'],['采收上市',1,'达到规格采收','选择适宜时间']]],
    kelp:[150,5,15,[['夹苗',0,'选择健康种藻夹苗下海','海带靠光合作用生长'],['海上培育',0.20,'定期检查生长调整水层深度','注意海况变化'],['平养管理',0.40,'平养促进受光均匀生长','定期清洗藻体'],['成熟期',0.80,'藻体增厚宽度达标准备收割','监测水温变化'],['收割上市',1,'达到规格收割','选择适宜天气']]]
  };

  function findSpeciesInfo(id) {
    for (var i = 0; i < SPECIES_50.length; i++) {
      if (SPECIES_50[i][0] === id) return { id: SPECIES_50[i][0], name: SPECIES_50[i][1], category: SPECIES_50[i][2] };
    }
    return { id: id, name: id, category: '淡水鱼' };
  }

  function addDays(dateStr, days) {
    var d = new Date(dateStr + 'T00:00:00Z');
    d.setUTCDate(d.getUTCDate() + days);
    return d.toISOString().slice(0, 10);
  }

  function lastYearDate(dateStr) {
    var p = dateStr.split('-');
    return (parseInt(p[0], 10) - 1) + '-' + p[1] + '-' + p[2];
  }

  function fetchOsmWeather(baseUrl, lat, lon, dates) {
    if (!dates || dates.length === 0) return Promise.resolve({});
    var sorted = dates.slice().sort();
    var url = baseUrl + '?latitude=' + lat + '&longitude=' + lon
      + '&daily=temperature_2m_max,temperature_2m_min,precipitation_sum'
      + '&start_date=' + sorted[0] + '&end_date=' + sorted[sorted.length - 1]
      + '&timezone=Asia/Shanghai';
    return fetch(url).then(function (r) { return r.json(); }).then(function (d) {
      var map = {};
      if (d && d.daily && d.daily.time) {
        for (var i = 0; i < d.daily.time.length; i++) {
          map[d.daily.time[i]] = {
            max: d.daily.temperature_2m_max[i],
            min: d.daily.temperature_2m_min[i],
            rain: d.daily.precipitation_sum[i]
          };
        }
      }
      return map;
    }).catch(function () { return {}; });
  }

  function generateGenericTimeline(speciesId, stockDate, lat, lon) {
    var info = findSpeciesInfo(speciesId);
    var raw = SS[speciesId] || [150,15,30,[
      ['放苗',0,'选择健康苗种消毒放养','注意水温差'],
      ['投喂',0.1,'投喂配合饲料','根据水温调整'],
      ['水质管理',0.3,'保持水质良好','溶氧充足'],
      ['起捕上市',1,'达到规格起捕','选择适宜天气']
    ]];
    var cycle = raw[0], tMin = raw[1], tMax = raw[2], stages = raw[3];
    var nodes = stages.map(function (s, i) {
      var day = Math.round(cycle * s[1]);
      var isStock = i === 0;
      return {
        nodeSeq: i + 1,
        title: s[0],
        date: addDays(stockDate, day),
        dayOffset: day,
        opType: isStock ? 'stock' : 'care',
        detail: s[2],
        advice: s[3],
        status: 'unknown',
        tempC: null
      };
    });
    var baseData = {
      species: info.name,
      stockDate: stockDate,
      stockUnit: '苗种',
      waterTempMin: tMin,
      waterTempMax: tMax,
      tips: '本时间线按' + info.name + '品种特性生成，仅供参考。实际养殖请根据当地气候调整。',
      nodes: nodes,
      tempBasis: 'air',
      tempNote: '适宜水温' + tMin + '-' + tMax + '℃，养殖周期约' + cycle + '天。未接水温传感器时用气温近似判定。'
    };

    if (lat == null || lon == null) {
      nodes.forEach(function (n) { n.status = 'ok'; });
      return Promise.resolve(baseData);
    }

    var allDates = nodes.map(function (n) { return n.date; });
    return fetchOsmWeather('https://api.open-meteo.com/v1/forecast', lat, lon, allDates)
      .then(function (forecastMap) {
        var missing = allDates.filter(function (d) { return !forecastMap[d]; }).map(lastYearDate);
        if (missing.length === 0) return { forecast: forecastMap, archive: {} };
        return fetchOsmWeather('https://archive-api.open-meteo.com/v1/archive', lat, lon, missing)
          .then(function (archiveMap) { return { forecast: forecastMap, archive: archiveMap }; });
      })
      .then(function (maps) {
        nodes.forEach(function (n) {
          var w = maps.forecast[n.date];
          var fromNormal = false;
          if (!w) {
            var ly = lastYearDate(n.date);
            w = maps.archive[ly];
            fromNormal = w != null;
          }
          if (w) {
            var temp = Math.round((w.max + w.min) / 2 * 10) / 10;
            n.tempC = temp;
            if (temp < tMin) {
              n.status = 'caution';
              n.advice = '气温 ' + temp + '℃ 低于适宜区间（' + tMin + '~' + tMax + '℃），建议推迟或加深水位';
            } else if (temp > tMax) {
              n.status = 'caution';
              n.advice = '气温 ' + temp + '℃ 高于适宜区间（' + tMin + '~' + tMax + '℃），注意增氧与降温';
            } else {
              n.status = 'ok';
              n.advice = '气温 ' + temp + '℃ 在适宜区间（' + tMin + '~' + tMax + '℃）内';
            }
            if (w.rain >= 20) {
              n.advice += '；当日降雨约 ' + Math.round(w.rain) + 'mm，注意防溢流与水质突变';
            }
            if (fromNormal) {
              n.advice = '常年同期参考：' + n.advice + '（非预报，按去年同日天气估算）';
            }
          } else {
            n.status = 'unknown';
            n.advice = '无天气数据，按经验安排';
          }
        });
        baseData.weatherStatus = 'ok';
        return baseData;
      });
  }

  function render(data) {
    result.innerHTML = '';

    var head = document.createElement('div');
    head.className = 'industry-head';
    var title = document.createElement('div');
    title.className = 'industry-head-title';
    title.textContent = data.species + ' · 生产日历';
    var meta = document.createElement('div');
    meta.className = 'industry-head-meta';
    meta.textContent = '放养 ' + data.stockDate + ' · ' + (data.stockUnit || '')
      + ' · 适宜水温 ' + data.waterTempMin + '~' + data.waterTempMax + '℃';
    head.appendChild(title);
    head.appendChild(meta);
    result.appendChild(head);

    if (data.tips) {
      var tip = document.createElement('p');
      tip.className = 'industry-tip';
      tip.textContent = '养殖要点：' + data.tips;
      result.appendChild(tip);
    }

    var list = document.createElement('div');
    list.className = 'industry-nodes';
    (data.nodes || []).forEach(function (n) {
      var card = document.createElement('div');
      card.className = 'industry-node status-' + (n.status || 'unknown');

      var dot = document.createElement('span');
      dot.className = 'industry-dot';
      card.appendChild(dot);

      var body = document.createElement('div');
      body.className = 'industry-node-body';

      var top = document.createElement('div');
      top.className = 'industry-node-top';
      var name = document.createElement('span');
      name.className = 'name';
      name.textContent = n.title || '';
      var date = document.createElement('span');
      date.className = 'date';
      date.textContent = n.date + '（第 ' + n.dayOffset + ' 天）';
      top.appendChild(name);
      top.appendChild(date);
      body.appendChild(top);

      var detail = document.createElement('p');
      detail.className = 'industry-detail';
      detail.textContent = n.detail || '';
      body.appendChild(detail);

      var badge = document.createElement('span');
      badge.className = 'industry-badge';
      badge.textContent = statusLabel(n.status);
      var advice = document.createElement('p');
      advice.className = 'industry-advice';
      advice.textContent = n.advice || '';
      body.appendChild(badge);
      body.appendChild(advice);

      card.appendChild(body);
      list.appendChild(card);
    });
    result.appendChild(list);

    var note = document.createElement('p');
    note.className = 'industry-note';
    note.textContent = data.tempNote || '';
    result.appendChild(note);
  }

  function load() {
    var id = speciesSel.value;
    if (!id) return;
    var stock = dateInput.value || todayISO();
    var loc = locInput.value.trim();
    var c = coordsOf(loc);
    result.innerHTML = '<div class="industry-empty">正在生成生产日历…</div>';

    var xhr = new XMLHttpRequest();
    xhr.open('GET', API + '/timeline?species=' + encodeURIComponent(id)
      + '&stockDate=' + encodeURIComponent(stock)
      + '&lat=' + c[0] + '&lon=' + c[1], true);
    xhr.timeout = 15000;
    xhr.onload = function () {
      try {
        var body = JSON.parse(xhr.responseText);
        if (body.code === '0' && body.data) { render(body.data); return; }
        generateGenericTimeline(id, stock, c[0], c[1]).then(render);
      } catch (e) {
        generateGenericTimeline(id, stock, c[0], c[1]).then(render);
      }
    };
    xhr.onerror = xhr.ontimeout = function () {
      generateGenericTimeline(id, stock, c[0], c[1]).then(render);
    };
    xhr.send();
  }

  form.addEventListener('submit', function (e) {
    e.preventDefault();
    load();
  });

  // 换品种时把介绍区跟着换掉，否则用户看不到新选品种的适宜水温
  speciesSel.addEventListener('change', function () {
    var id = speciesSel.value;
    for (var i = 0; i < currentSpecies.length; i++) {
      if (currentSpecies[i].id === id) { showIntro(currentSpecies[i]); return; }
    }
  });

  dateInput.value = todayISO();
  loadSpecies();
})();
