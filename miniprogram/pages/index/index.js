const api = require('../../utils/api');

Page({
  data: {
    crop: '',
    sowDate: '',
    location: '湖北省荆州市',
    hint: '',
    busy: false,
    today: ''
  },

  onLoad: function () {
    const d = new Date();
    const iso = d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
    this.setData({ today: iso, sowDate: iso });
  },

  onCrop: function (e) { this.setData({ crop: e.detail.value }); },
  onDate: function (e) { this.setData({ sowDate: e.detail.value }); },
  onLocation: function (e) { this.setData({ location: e.detail.value }); },

  // T04 双模式：不填日期时按节气推荐起点
  suggestByTerm: function () {
    const self = this;
    if (!this.data.crop) {
      wx.showToast({ title: '先输入作物品种', icon: 'none' });
      return;
    }
    this.setData({ busy: true });
    api.sowWindow(this.data.crop).then(function (res) {
      const d = res && res.data;
      if (!d) throw new Error('推算失败');
      self.setData({
        sowDate: d.sowDate,
        hint: '按节气「' + d.solarTerm + '」推荐：' + d.sowDate + '（距今 ' + d.daysFromToday + ' 天）'
      });
    }).catch(function (err) {
      self.setData({ hint: err.message || '节气推荐不可用' });
    }).then(function () {
      self.setData({ busy: false });
    });
  },

  submit: function () {
    if (!this.data.crop) {
      wx.showToast({ title: '请输入作物品种', icon: 'none' });
      return;
    }
    wx.navigateTo({
      url: '/pages/timeline/timeline?crop=' + encodeURIComponent(this.data.crop)
        + '&sowDate=' + encodeURIComponent(this.data.sowDate)
        + '&location=' + encodeURIComponent(this.data.location)
    });
  }
});
