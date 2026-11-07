/**
 * 用户本地偏好（头像 / 定位权限等），走 localStorage，跨会话保留。
 *
 * 与 theme.js 分工：主题单独成模块（要在首帧前应用），其余偏好都放这里。
 * 统一前缀 agritime.prefs.v1，一个 JSON 对象，避免散落一堆 key。
 */
(function () {
  var KEY = 'agritime.prefs.v1';
  var DEFAULTS = {
    avatar: '',        // 头像 emoji，空串表示用昵称首字母
    location: true     // 是否允许使用定位（自动定位 / 当前位置）
  };

  function read() {
    var out = {};
    try {
      var raw = localStorage.getItem(KEY);
      var d = raw ? JSON.parse(raw) : {};
      Object.keys(DEFAULTS).forEach(function (k) {
        out[k] = (k in d) ? d[k] : DEFAULTS[k];
      });
    } catch (e) {
      out = Object.assign({}, DEFAULTS);
    }
    return out;
  }

  function write(obj) {
    try { localStorage.setItem(KEY, JSON.stringify(obj)); } catch (e) { /* 忽略 */ }
  }

  function get(key) {
    return read()[key];
  }

  function set(key, value) {
    var obj = read();
    obj[key] = value;
    write(obj);
  }

  window.Prefs = { get: get, set: set, read: read };
  if (typeof module !== 'undefined' && module.exports) module.exports = window.Prefs;
})();
