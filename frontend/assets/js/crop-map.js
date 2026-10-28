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

  var CROP_LIST_ALL = [
    {cropId:'C001',name:'水稻'},{cropId:'C002',name:'小麦'},{cropId:'C003',name:'玉米'},{cropId:'C004',name:'大豆'},
    {cropId:'C005',name:'棉花'},{cropId:'C006',name:'油菜'},{cropId:'C007',name:'花生'},{cropId:'C008',name:'芝麻'},
    {cropId:'C009',name:'向日葵'},{cropId:'C010',name:'高粱'},{cropId:'C011',name:'谷子'},{cropId:'C012',name:'红薯'},
    {cropId:'C013',name:'马铃薯'},{cropId:'C014',name:'甘蔗'},{cropId:'C015',name:'甜菜'},{cropId:'C016',name:'烟草'},
    {cropId:'C017',name:'茶'},{cropId:'C018',name:'桑'},{cropId:'C019',name:'苎麻'},{cropId:'C020',name:'亚麻'},
    {cropId:'C021',name:'大白菜'},{cropId:'C022',name:'小白菜'},{cropId:'C023',name:'菠菜'},{cropId:'C024',name:'芹菜'},
    {cropId:'C025',name:'生菜'},{cropId:'C026',name:'空心菜'},{cropId:'C027',name:'韭菜'},{cropId:'C028',name:'蒜苗'},
    {cropId:'C029',name:'花椰菜'},{cropId:'C030',name:'西兰花'},{cropId:'C031',name:'番茄'},{cropId:'C032',name:'茄子'},
    {cropId:'C033',name:'辣椒'},{cropId:'C034',name:'黄瓜'},{cropId:'C035',name:'冬瓜'},{cropId:'C036',name:'南瓜'},
    {cropId:'C037',name:'丝瓜'},{cropId:'C038',name:'苦瓜'},{cropId:'C039',name:'西葫芦'},{cropId:'C040',name:'西瓜'},
    {cropId:'C041',name:'豆角'},{cropId:'C042',name:'豇豆'},{cropId:'C043',name:'豌豆'},{cropId:'C044',name:'蚕豆'},
    {cropId:'C045',name:'葱'},{cropId:'C046',name:'洋葱'},{cropId:'C047',name:'大蒜'},{cropId:'C048',name:'甘蓝'},
    {cropId:'C049',name:'苋菜'},{cropId:'C050',name:'茼蒿'},{cropId:'C051',name:'萝卜'},{cropId:'C052',name:'胡萝卜'},
    {cropId:'C053',name:'莲藕'},{cropId:'C054',name:'茭白'},{cropId:'C055',name:'芋头'},{cropId:'C056',name:'山药'},
    {cropId:'C057',name:'生姜'},{cropId:'C058',name:'莴笋'},{cropId:'C059',name:'苹果'},{cropId:'C060',name:'梨'},
    {cropId:'C061',name:'桃'},{cropId:'C062',name:'李子'},{cropId:'C063',name:'杏'},{cropId:'C064',name:'樱桃'},
    {cropId:'C065',name:'草莓'},{cropId:'C066',name:'葡萄'},{cropId:'C067',name:'柑橘'},{cropId:'C068',name:'橙子'},
    {cropId:'C069',name:'柚子'},{cropId:'C070',name:'柠檬'},{cropId:'C071',name:'甜瓜'},{cropId:'C072',name:'哈密瓜'},
    {cropId:'C073',name:'猕猴桃'},{cropId:'C074',name:'石榴'},{cropId:'C075',name:'柿子'},{cropId:'C076',name:'枣'},
    {cropId:'C077',name:'芒果'},{cropId:'C078',name:'百香果'},{cropId:'C079',name:'橡胶'},{cropId:'C080',name:'咖啡'},
    {cropId:'C081',name:'可可'},{cropId:'C082',name:'枸杞'},{cropId:'C083',name:'金银花'},{cropId:'C084',name:'黄芪'},
    {cropId:'C085',name:'人参'},{cropId:'C086',name:'当归'},{cropId:'C087',name:'三七'},{cropId:'C088',name:'板蓝根'},
    {cropId:'C089',name:'薄荷'},{cropId:'C090',name:'薰衣草'},{cropId:'C091',name:'荞麦'},{cropId:'C092',name:'燕麦'},
    {cropId:'C093',name:'大麦'},{cropId:'C094',name:'黑豆'},{cropId:'C095',name:'绿豆'},{cropId:'C096',name:'红小豆'},
    {cropId:'C097',name:'薏米'},{cropId:'C098',name:'黍子'},{cropId:'C099',name:'糜子'},{cropId:'C100',name:'青稞'},
    {cropId:'C101',name:'大葱'},{cropId:'C102',name:'分葱'},{cropId:'C103',name:'香葱'},{cropId:'C104',name:'韭黄'},
    {cropId:'C105',name:'蒜薹'},{cropId:'C106',name:'芥菜'},{cropId:'C107',name:'雪里蕻'},{cropId:'C108',name:'榨菜'},
    {cropId:'C109',name:'大头菜'},{cropId:'C110',name:'儿菜'},{cropId:'C111',name:'抱子甘蓝'},{cropId:'C112',name:'羽衣甘蓝'},
    {cropId:'C113',name:'紫甘蓝'},{cropId:'C114',name:'芥蓝'},{cropId:'C115',name:'菜心'},{cropId:'C116',name:'奶白菜'},
    {cropId:'C117',name:'乌塌菜'},{cropId:'C118',name:'木耳菜'},{cropId:'C119',name:'秋葵'},{cropId:'C120',name:'黄秋葵'},
    {cropId:'C121',name:'红秋葵'},{cropId:'C122',name:'娃娃菜'},{cropId:'C123',name:'快菜'},{cropId:'C124',name:'鸡毛菜'},
    {cropId:'C125',name:'油麦菜'},{cropId:'C126',name:'苦菊'},{cropId:'C127',name:'芝麻菜'},{cropId:'C128',name:'穿心莲'},
    {cropId:'C129',name:'紫苏'},{cropId:'C130',name:'罗勒'},{cropId:'C131',name:'香椿'},{cropId:'C132',name:'蕨菜'},
    {cropId:'C133',name:'马齿苋'},{cropId:'C134',name:'蒲公英'},{cropId:'C135',name:'鱼腥草'},{cropId:'C136',name:'益母草'},
    {cropId:'C137',name:'车前草'},{cropId:'C138',name:'紫背天葵'},{cropId:'C139',name:'雪莲果'},{cropId:'C140',name:'佛手瓜'},
    {cropId:'C141',name:'红富士'},{cropId:'C142',name:'嘎啦果'},{cropId:'C143',name:'国光苹果'},{cropId:'C144',name:'鸭梨'},
    {cropId:'C145',name:'雪花梨'},{cropId:'C146',name:'砀山梨'},{cropId:'C147',name:'库尔勒梨'},{cropId:'C148',name:'水蜜桃'},
    {cropId:'C149',name:'油桃'},{cropId:'C150',name:'蟠桃'},{cropId:'C151',name:'黄桃'},{cropId:'C152',name:'冬枣'},
    {cropId:'C153',name:'灰枣'},{cropId:'C154',name:'骏枣'},{cropId:'C155',name:'板栗'},{cropId:'C156',name:'核桃'},
    {cropId:'C157',name:'巴旦木'},{cropId:'C158',name:'开心果'},{cropId:'C159',name:'碧根果'},{cropId:'C160',name:'夏威夷果'},
    {cropId:'C161',name:'腰果'},{cropId:'C162',name:'榛子'},{cropId:'C163',name:'松子'},{cropId:'C164',name:'莲子'},
    {cropId:'C165',name:'桂圆'},{cropId:'C166',name:'荔枝'},{cropId:'C167',name:'龙眼'},{cropId:'C168',name:'山竹'},
    {cropId:'C169',name:'榴莲'},{cropId:'C170',name:'菠萝'},{cropId:'C171',name:'凤梨'},{cropId:'C172',name:'椰子'},
    {cropId:'C173',name:'芭乐'},{cropId:'C174',name:'番石榴'},{cropId:'C175',name:'莲雾'},{cropId:'C176',name:'杨桃'},
    {cropId:'C177',name:'释迦果'},{cropId:'C178',name:'无花果'},{cropId:'C179',name:'桑葚'},{cropId:'C180',name:'蓝莓'},
    {cropId:'C181',name:'香菇'},{cropId:'C182',name:'平菇'},{cropId:'C183',name:'金针菇'},{cropId:'C184',name:'杏鲍菇'},
    {cropId:'C185',name:'白灵菇'},{cropId:'C186',name:'茶树菇'},{cropId:'C187',name:'猴头菇'},{cropId:'C188',name:'竹荪'},
    {cropId:'C189',name:'木耳菌'},{cropId:'C190',name:'银耳'},{cropId:'C191',name:'松茸'},{cropId:'C192',name:'羊肚菌'},
    {cropId:'C193',name:'鸡枞菌'},{cropId:'C194',name:'灰树花'},{cropId:'C195',name:'双孢菇'},{cropId:'C196',name:'水芹'},
    {cropId:'C197',name:'莼菜'},{cropId:'C198',name:'蒲菜'},{cropId:'C199',name:'菱角'},{cropId:'C200',name:'芡实'},
    {cropId:'C201',name:'桔梗'},{cropId:'C202',name:'麦冬'},{cropId:'C203',name:'柴胡'},{cropId:'C204',name:'白术'},
    {cropId:'C205',name:'白芍'},{cropId:'C206',name:'甘草'},{cropId:'C207',name:'苦参'},{cropId:'C208',name:'黄连'},
    {cropId:'C209',name:'黄柏'},{cropId:'C210',name:'杜仲'},{cropId:'C211',name:'厚朴'},{cropId:'C212',name:'肉桂'},
    {cropId:'C213',name:'五味子'},{cropId:'C214',name:'决明子'},{cropId:'C215',name:'薏苡仁'},{cropId:'C216',name:'车前子'},
    {cropId:'C217',name:'菟丝子'},{cropId:'C218',name:'蛇床子'},{cropId:'C219',name:'山茱萸'},{cropId:'C220',name:'连翘'},
    {cropId:'C221',name:'丹参'},{cropId:'C222',name:'玄参'},{cropId:'C223',name:'生地'},{cropId:'C224',name:'白芷'},
    {cropId:'C225',name:'藿香'},{cropId:'C226',name:'荆芥'},{cropId:'C227',name:'防风'},{cropId:'C228',name:'羌活'},
    {cropId:'C229',name:'独活'},{cropId:'C230',name:'前胡'},{cropId:'C231',name:'玫瑰'},{cropId:'C232',name:'月季'},
    {cropId:'C233',name:'牡丹'},{cropId:'C234',name:'芍药花'},{cropId:'C235',name:'菊花'},{cropId:'C236',name:'康乃馨'},
    {cropId:'C237',name:'百合花'},{cropId:'C238',name:'郁金香'},{cropId:'C239',name:'茉莉花'},{cropId:'C240',name:'桂花'},
    {cropId:'C241',name:'栀子花'},{cropId:'C242',name:'杜鹃花'},{cropId:'C243',name:'茶花'},{cropId:'C244',name:'梅花'},
    {cropId:'C245',name:'兰花'},{cropId:'C246',name:'水仙花'},{cropId:'C247',name:'荷花'},{cropId:'C248',name:'睡莲'},
    {cropId:'C249',name:'鸢尾'},{cropId:'C250',name:'大丽花'},{cropId:'C251',name:'一串红'},{cropId:'C252',name:'万寿菊'},
    {cropId:'C253',name:'孔雀草'},{cropId:'C254',name:'三色堇'},{cropId:'C255',name:'矮牵牛'},{cropId:'C256',name:'金鱼草'},
    {cropId:'C257',name:'石竹'},{cropId:'C258',name:'满天星'},{cropId:'C259',name:'葵花'},{cropId:'C260',name:'油菜花'},
    {cropId:'C261',name:'紫菜苔'},{cropId:'C262',name:'红菜苔'},{cropId:'C263',name:'洪山菜苔'},{cropId:'C264',name:'有机花菜'},
    {cropId:'C265',name:'松花菜'},{cropId:'C266',name:'白菜花'},{cropId:'C267',name:'黄花菜'},{cropId:'C268',name:'百合球'},
    {cropId:'C269',name:'芦笋'},{cropId:'C270',name:'竹笋'},{cropId:'C271',name:'荠菜'},{cropId:'C272',name:'马兰头'},
    {cropId:'C273',name:'艾草'},{cropId:'C274',name:'灰灰菜'},{cropId:'C275',name:'苜蓿'},{cropId:'C276',name:'豌豆尖'},
    {cropId:'C277',name:'红薯叶'},{cropId:'C278',name:'南瓜藤'},{cropId:'C279',name:'丝瓜尖'},{cropId:'C280',name:'辣椒叶'},
    {cropId:'C281',name:'芝麻叶'},{cropId:'C282',name:'花椒芽'},{cropId:'C283',name:'香椿苗'},{cropId:'C284',name:'萝卜苗'},
    {cropId:'C285',name:'黄豆芽'},{cropId:'C286',name:'绿豆芽'},{cropId:'C287',name:'黑豆芽'},{cropId:'C288',name:'萝卜芽'},
    {cropId:'C289',name:'荞麦芽'},{cropId:'C290',name:'苜蓿芽'},{cropId:'C291',name:'豌豆苗'},{cropId:'C292',name:'香椿芽'},
    {cropId:'C293',name:'蒜黄'},{cropId:'C294',name:'韭黄苗'},{cropId:'C295',name:'姜芽'},{cropId:'C296',name:'葱芽'},
    {cropId:'C297',name:'彩椒'},{cropId:'C298',name:'尖椒'},{cropId:'C299',name:'朝天椒'},{cropId:'C300',name:'小米椒'},
    {cropId:'C301',name:'藜麦'},{cropId:'C302',name:'籽粒苋'},{cropId:'C303',name:'莜麦'},{cropId:'C304',name:'黑麦'},
    {cropId:'C305',name:'红米'},{cropId:'C306',name:'黑米'},{cropId:'C307',name:'紫米'},{cropId:'C308',name:'糯米稻'},
    {cropId:'C309',name:'粳稻'},{cropId:'C310',name:'籼稻'},{cropId:'C311',name:'旱稻'},{cropId:'C312',name:'陆稻'},
    {cropId:'C313',name:'鹰嘴豆'},{cropId:'C314',name:'小扁豆'},{cropId:'C315',name:'木豆'},{cropId:'C316',name:'饭豆'},
    {cropId:'C317',name:'眉豆'},{cropId:'C318',name:'龙爪豆'},{cropId:'C319',name:'草豌豆'},{cropId:'C320',name:'刀豆'},
    {cropId:'C321',name:'扁豆'},{cropId:'C322',name:'菜豆'},{cropId:'C323',name:'芸豆'},{cropId:'C324',name:'红芸豆'},
    {cropId:'C325',name:'白芸豆'},{cropId:'C326',name:'黑芸豆'},{cropId:'C327',name:'花芸豆'},{cropId:'C328',name:'绿豆种'},
    {cropId:'C329',name:'赤小豆'},{cropId:'C330',name:'赤豆'},{cropId:'C331',name:'四季豆'},{cropId:'C332',name:'蔓生豆'},
    {cropId:'C333',name:'矮生豆'},{cropId:'C334',name:'红花菜豆'},{cropId:'C335',name:'龙芽豆'},{cropId:'C336',name:'甜豌豆'},
    {cropId:'C337',name:'食荚豌豆'},{cropId:'C338',name:'皱粒豌豆'},{cropId:'C339',name:'甜玉米'},{cropId:'C340',name:'糯玉米'},
    {cropId:'C341',name:'黑莓'},{cropId:'C342',name:'树莓'},{cropId:'C343',name:'蔓越莓'},{cropId:'C344',name:'鹅莓'},
    {cropId:'C345',name:'金桔'},{cropId:'C346',name:'砂糖橘'},{cropId:'C347',name:'蜜橘'},{cropId:'C348',name:'血橙'},
    {cropId:'C349',name:'脐橙'},{cropId:'C350',name:'冰糖橙'},{cropId:'C351',name:'丑橘'},{cropId:'C352',name:'沃柑'},
    {cropId:'C353',name:'皇帝柑'},{cropId:'C354',name:'红心柚'},{cropId:'C355',name:'葡萄柚'},{cropId:'C356',name:'青柠'},
    {cropId:'C357',name:'丑柠檬'},{cropId:'C358',name:'佛手柑'},{cropId:'C359',name:'金枣'},{cropId:'C360',name:'酸枣'},
    {cropId:'C361',name:'蜜枣'},{cropId:'C362',name:'毛樱桃'},{cropId:'C363',name:'酸樱桃'},{cropId:'C364',name:'甜樱桃种'},
    {cropId:'C365',name:'大樱桃'},{cropId:'C366',name:'杨梅'},{cropId:'C367',name:'青梅'},{cropId:'C368',name:'乌梅'},
    {cropId:'C369',name:'黄梅'},{cropId:'C370',name:'黑李'},{cropId:'C371',name:'红李'},{cropId:'C372',name:'黄李'},
    {cropId:'C373',name:'西梅'},{cropId:'C374',name:'布朗'},{cropId:'C375',name:'油奈'},{cropId:'C376',name:'寿桃'},
    {cropId:'C377',name:'蟠桃王'},{cropId:'C378',name:'黄蟠桃'},{cropId:'C379',name:'油蟠桃'},{cropId:'C380',name:'蟠桃种'},
    {cropId:'C381',name:'花椒'},{cropId:'C382',name:'八角'},{cropId:'C383',name:'桂皮'},{cropId:'C384',name:'丁香'},
    {cropId:'C385',name:'胡椒'},{cropId:'C386',name:'白胡椒'},{cropId:'C387',name:'肉豆蔻'},{cropId:'C388',name:'草果'},
    {cropId:'C389',name:'砂仁'},{cropId:'C390',name:'白蔻'},{cropId:'C391',name:'孜然'},{cropId:'C392',name:'小茴香'},
    {cropId:'C393',name:'大茴香'},{cropId:'C394',name:'香叶'},{cropId:'C395',name:'咖喱叶'},{cropId:'C396',name:'迷迭香'},
    {cropId:'C397',name:'百里香'},{cropId:'C398',name:'牛至'},{cropId:'C399',name:'鼠尾草'},{cropId:'C400',name:'番红花'}
  ];

  var ALIAS_MAP = {
    '番茄':['西红柿','tomato'],'西红柿':['番茄'],'马铃薯':['土豆','洋芋'],'土豆':['马铃薯','洋芋'],
    '红薯':['地瓜','番薯','甘薯'],'地瓜':['红薯'],'番薯':['红薯','地瓜'],
    '辣椒':['青椒','海椒'],'青椒':['辣椒'],'大白菜':['白菜'],'白菜':['大白菜'],
    '萝卜':['莱菔'],'胡萝卜':['红萝卜'],'红萝卜':['胡萝卜'],'洋葱':['圆葱'],
    '大蒜':['蒜','蒜头'],'韭菜':['韭'],'芹菜':['旱芹'],'菠菜':['波斯菜'],
    '黄瓜':['青瓜'],'南瓜':['倭瓜'],'冬瓜':['白瓜'],'丝瓜':['水瓜'],
    '茄子':['矮瓜'],'豆角':['豇豆'],'豌豆':['荷兰豆'],'花生':['落花生'],
    '玉米':['苞谷','玉蜀黍'],'苞谷':['玉米'],'水稻':['稻','稻谷'],'小麦':['麦'],
    '大豆':['黄豆','毛豆'],'黄豆':['大豆','毛豆'],'油菜':['油菜籽'],
    '芝麻':['胡麻'],'高粱':['蜀黍'],'谷子':['小米'],'小米':['谷子'],
    '棉花':['棉'],'茶':['茶叶'],'茶叶':['茶'],'甘蔗':['蔗'],
    '苹果':['苹'],'梨':['梨子'],'桃':['桃子'],'葡萄':['提子'],
    '柑橘':['橘子','橙子'],'橘子':['柑橘'],'橙子':['橙','柑橘'],
    '香蕉':['芭蕉'],'草莓':['士多啤梨'],'西瓜':['水瓜'],'甜瓜':['哈密瓜'],
    '哈密瓜':['甜瓜'],'猕猴桃':['奇异果'],'奇异果':['猕猴桃'],
    '樱桃':['车厘子'],'车厘子':['樱桃'],'蓝莓':['蓝梅'],'核桃':['胡桃'],
    '板栗':['栗子'],'栗子':['板栗'],'柿子':['柿'],'枣':['红枣'],'红枣':['枣'],
    '芒果':['杧果'],'荔枝':['荔'],'龙眼':['桂圆'],'桂圆':['龙眼'],
    '柚子':['柚'],'杨梅':['梅'],'李子':['李'],'杏':['杏子'],
    '石榴':['安石榴'],'芋头':['芋'],'山药':['薯蓣'],'生姜':['姜'],
    '莴笋':['莴苣'],'空心菜':['蕹菜'],'蕹菜':['空心菜'],'芫荽':['香菜'],
    '香菜':['芫荽'],'花椰菜':['菜花'],'菜花':['花椰菜'],'西蓝花':['青花菜'],
    '青花菜':['西蓝花'],'苦瓜':['凉瓜'],'芦笋':['竹笋'],'薏米':['薏苡仁'],
    '荞麦':['荞'],'燕麦':['雀麦'],'向日葵':['葵花'],'葵花':['向日葵'],
    '桑':['桑树'],'松':['松树'],'杨':['杨树'],'柳':['柳树'],
    '豇豆':['豆角'],'毛豆':['大豆','黄豆'],'油菜籽':['油菜'],
    '蒜':['大蒜','蒜头'],'蒜头':['大蒜'],'麦':['小麦'],
    '稻':['水稻','稻谷'],'稻谷':['水稻'],'棉':['棉花'],
    '橙':['橙子','柑橘'],'梨子':['梨'],'桃子':['桃'],
    '杏子':['杏'],'柚':['柚子'],'柿':['柿子'],'荔':['荔枝'],
    '槟榔':['槟'],'芋':['芋头'],'姜':['生姜'],
    '苋菜':['苋'],'茼蒿':['蒿'],'茴香':['小茴香'],
    '芥蓝':['甘蓝'],'甘蓝':['芥蓝'],'秋葵':['黄秋葵'],
    '佛手瓜':['隼人瓜'],'莙荙菜':['牛皮菜']
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

  function editDistance(a, b) {
    if (a === b) return 0;
    var la = a.length, lb = b.length;
    if (la === 0) return lb;
    if (lb === 0) return la;
    var prev = [], curr = [];
    for (var j = 0; j <= lb; j++) prev[j] = j;
    for (var i = 1; i <= la; i++) {
      curr[0] = i;
      for (j = 1; j <= lb; j++) {
        var cost = a.charAt(i - 1) === b.charAt(j - 1) ? 0 : 1;
        curr[j] = Math.min(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost);
      }
      prev = curr.slice();
    }
    return prev[lb];
  }

  function matchCrop(keyword) {
    var kw = normalize(keyword);
    if (!kw) return [];
    var lower = kw.toLowerCase();
    var scored = [];
    var seen = {};
    function push(name, score) {
      if (seen[name]) return;
      seen[name] = 1;
      scored.push({ name: name, score: score });
    }
    for (var i = 0; i < CROP_LIST_ALL.length; i++) {
      var c = CROP_LIST_ALL[i];
      var n = c.name;
      if (n === kw) { push(n, 0); continue; }
      if (ALIAS_MAP[n]) {
        for (var k = 0; k < ALIAS_MAP[n].length; k++) {
          if (ALIAS_MAP[n][k] === kw || ALIAS_MAP[n][k] === lower) { push(n, 1); break; }
        }
      }
      if (n.indexOf(kw) !== -1) { push(n, 2); continue; }
      if (ALIAS_MAP[n]) {
        for (k = 0; k < ALIAS_MAP[n].length; k++) {
          if (ALIAS_MAP[n][k].indexOf(kw) !== -1) { push(n, 3); break; }
        }
      }
      var ed = editDistance(kw, n);
      if (ed <= 2) { push(n, 10 + ed); continue; }
      if (ALIAS_MAP[n]) {
        for (k = 0; k < ALIAS_MAP[n].length; k++) {
          var ed2 = editDistance(kw, ALIAS_MAP[n][k]);
          if (ed2 <= 2) { push(n, 10 + ed2); break; }
        }
      }
    }
    scored.sort(function (a, b) { return a.score - b.score; });
    return scored.slice(0, 8).map(function (s) { return s.name; });
  }

  window.CropMap = {
    CROP_IDS: CROP_IDS,
    CROPS: CROPS,
    CROP_KEYWORDS: CROP_KEYWORDS,
    CROP_LIST_ALL: CROP_LIST_ALL,
    ALIAS_MAP: ALIAS_MAP,
    getCropByName: getCropByName,
    resolveCropId: resolveCropId,
    buildCropParam: buildCropParam,
    matchCrop: matchCrop,
    editDistance: editDistance
  };
})();