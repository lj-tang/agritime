const api = require('../../utils/api');

// 把后端节点拍平成 WXML 能直接渲染的结构（WXML 里不做复杂表达式）
function toView(n, index) {
  const g = n.guidance || null;
  const lines = [];
  if (g && g.watering) {
    lines.push({ section: '浇水', text: (g.watering.method || '') + ' · ' + (g.watering.duration || '') + ' · ' + (g.watering.waterPerMu || '') });
    if (g.watering.basis) lines.push({ section: '', text: '依据：' + g.watering.basis });
  }
  if (g && g.fertilize && g.fertilize.type) {
    lines.push({ section: '施肥', text: g.fertilize.type + ' · N-P-K ' + (g.fertilize.npk || '') + ' · ' + (g.fertilize.amount || '') });
  }
  if (g && g.pest && g.pest.length) {
    g.pest.forEach(function (p) {
      lines.push({ section: '病虫害', text: p.name + '：' + (p.symptom || '') });
    });
  }
  if (g && g.tools && g.tools.length) {
    lines.push({ section: '农具', text: g.tools.join('、') });
  }

  return {
    index: index,
    name: n.name || n.operationType || '',
    date: n.date,
    copy: n.copy || '',
    status: n.status || 'green',
    forecastNote: !!n.forecastNote,
    lines: lines,
    open: false
  };
}

Page({
  data: {
    loading: true,
    error: '',
    cropName: '',
    propsText: '',
    nodes: []
  },

  onLoad: function (options) {
    const self = this;
    const crop = decodeURIComponent(options.crop || '');
    const sowDate = decodeURIComponent(options.sowDate || '');
    if (!crop || !sowDate) {
      this.setData({ loading: false, error: '缺少作物或播种日期' });
      return;
    }
    wx.setNavigationBarTitle({ title: crop + ' · 农事日历' });

    api.timeline(crop, sowDate, 35.07, 116.31).then(function (data) {
      const nodes = (data.nodes || []).map(toView);
      const p = data.cropProperties || null;
      const propsText = p
        ? [p.lifespan === 'perennial' ? '多年生' : '一年生',
           p.croppingSystem === 'single' ? '一年一熟' : (p.croppingSystem === 'double' ? '一年多熟' : '一年多茬'),
           '全生育期约 ' + p.totalDays + ' 天'].join(' · ')
        : '';
      self.setData({
        loading: false,
        cropName: data.cropName || crop,
        propsText: propsText,
        nodes: nodes
      });
    }).catch(function (err) {
      self.setData({ loading: false, error: err.message || '加载失败' });
    });
  },

  toggle: function (e) {
    const i = e.currentTarget.dataset.i;
    const key = 'nodes[' + i + '].open';
    const patch = {};
    patch[key] = !this.data.nodes[i].open;
    this.setData(patch);
  }
});
