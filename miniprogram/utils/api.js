/**
 * 小程序端请求封装（T11）。
 *
 * 与网页端共用同一套 /api/*，不另开后端分支；
 * 只做三件事：拼 baseUrl、统一错误文案、把 HTTP 非 2xx 变成可读错误。
 */
const app = getApp();

function base() {
  return (app && app.globalData && app.globalData.apiBase) || '';
}

function get(path, params) {
  const query = Object.keys(params || {})
    .filter(function (k) { return params[k] !== undefined && params[k] !== null && params[k] !== ''; })
    .map(function (k) { return k + '=' + encodeURIComponent(params[k]); })
    .join('&');
  const url = base() + path + (query ? '?' + query : '');

  return new Promise(function (resolve, reject) {
    wx.request({
      url: url,
      method: 'GET',
      timeout: 15000,
      success: function (res) {
        if (res.statusCode >= 200 && res.statusCode < 300 && res.data) {
          resolve(res.data);
          return;
        }
        const msg = (res.data && (res.data.message || res.data.error)) || ('HTTP ' + res.statusCode);
        reject(new Error(msg));
      },
      fail: function () {
        reject(new Error('网络异常，请检查域名白名单与服务器状态'));
      }
    });
  });
}

module.exports = {
  get: get,
  timeline: function (crop, sowDate, lat, lon) {
    return get('/api/timeline', { crop: crop, sowDate: sowDate, lat: lat, lon: lon });
  },
  crops: function (q) {
    return get('/api/crops', q ? { q: q } : {});
  },
  sowWindow: function (crop) {
    return get('/api/sow-window', { crop: crop });
  },
  disease: function (crop, q) {
    return get('/api/disease/detect', { crop: crop, q: q });
  }
};
