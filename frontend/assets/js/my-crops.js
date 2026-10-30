/**
 * 我的作物页面交互逻辑（V4 T12）
 */
(function () {
  var GERMINATION_OPTIONS = ['未发芽', '已发芽', '幼苗期', '生长期', '开花期', '结果期', '成熟期'];
  var VIGOR_OPTIONS = ['弱', '一般', '良好', '旺盛'];

  function init() {
    var pane = document.querySelector('[data-module="my-crops"]');
    if (!pane) return;
    renderList();
    var addBtn = document.getElementById('my-crops-add-btn');
    if (addBtn) addBtn.addEventListener('click', function () { showForm(null); });
    window.addEventListener('agritime:data-synced', renderList);
    window.addEventListener('agritime:sowing-confirmed', function (e) {
      var d = e.detail || {};
      if (!d.cropName) return;
      if (!window.MyCropsStore) return;
      var sownDate = d.sownDate || null;
      // 同作物同播期视为同一条记录：用户重复点「确认已播种」不应堆出多条。
      var dup = window.MyCropsStore.list().filter(function (r) {
        return r.cropName === d.cropName && (r.sownDate || null) === sownDate;
      })[0];
      if (dup) {
        window.MyCropsStore.update(dup.recordId, {
          location: d.location || dup.location || null,
          plotId: d.plotId || dup.plotId || null
        });
      } else {
        window.MyCropsStore.add({
          cropName: d.cropName,
          cropId: d.cropId || null,
          sownDate: sownDate,
          location: d.location || null,
          plotId: d.plotId || null,
          source: 'calendar'
        });
      }
      renderList();
    });
  }

  function renderList() {
    var container = document.getElementById('my-crops-list');
    if (!container || !window.MyCropsStore) return;
    var items = window.MyCropsStore.list();
    container.innerHTML = '';
    if (!items.length) {
      container.innerHTML = '<div class="my-crops-empty">还没有作物记录，点击「添加」开始记录</div>';
      return;
    }
    var groups = {};
    items.forEach(function (r) {
      var key = r.cropName || '未命名';
      if (!groups[key]) groups[key] = [];
      groups[key].push(r);
    });
    Object.keys(groups).forEach(function (key) {
      var group = document.createElement('div');
      group.className = 'my-crops-group';
      var title = document.createElement('div');
      title.className = 'my-crops-group-title';
      title.textContent = key + '（' + groups[key].length + '）';
      group.appendChild(title);
      var cards = document.createElement('div');
      cards.className = 'my-crops-cards';
      groups[key].forEach(function (r) { cards.appendChild(renderCard(r)); });
      group.appendChild(cards);
      container.appendChild(group);
    });
  }

  function renderCard(r) {
    var card = document.createElement('div');
    card.className = 'my-crops-card';
    var name = document.createElement('div');
    name.className = 'my-crops-card-name';
    name.textContent = r.cropName || '未命名';
    if (r.source === 'calendar') {
      var badge = document.createElement('span');
      badge.className = 'my-crops-source';
      badge.textContent = '来自日历';
      name.appendChild(badge);
    }
    card.appendChild(name);
    var fields = document.createElement('div');
    fields.className = 'my-crops-card-fields';
    var lines = [];
    if (r.sownDate) lines.push('种下时间：' + r.sownDate);
    if (r.germinationStatus) lines.push('发芽状况：' + r.germinationStatus);
    if (r.height != null) lines.push('高度：' + r.height + 'cm');
    if (r.growthVigor) lines.push('长势：' + r.growthVigor);
    if (r.plotId) {
      var plot = window.MyPlotsStore && window.MyPlotsStore.getById ? window.MyPlotsStore.getById(r.plotId) : null;
      lines.push('地块：' + (plot && plot.name ? plot.name : '已关联（地块已删除）'));
    }
    if (r.remark) lines.push('备注：' + r.remark);
    fields.textContent = lines.join(' | ');
    card.appendChild(fields);
    var actions = document.createElement('div');
    actions.className = 'my-crops-card-actions';
    var editBtn = document.createElement('button');
    editBtn.type = 'button';
    editBtn.className = 'btn btn-ghost';
    editBtn.textContent = '编辑';
    editBtn.addEventListener('click', function () { showForm(r); });
    actions.appendChild(editBtn);
    var delBtn = document.createElement('button');
    delBtn.type = 'button';
    delBtn.className = 'btn btn-ghost';
    delBtn.textContent = '删除';
    delBtn.addEventListener('click', function () {
      if (confirm('确认删除这条记录？')) {
        window.MyCropsStore.remove(r.recordId);
        renderList();
      }
    });
    actions.appendChild(delBtn);
    var photoBtn = document.createElement('button');
    photoBtn.type = 'button';
    photoBtn.className = 'btn btn-ghost';
    photoBtn.textContent = '拍照';
    photoBtn.addEventListener('click', function () { handlePhoto(r); });
    actions.appendChild(photoBtn);
    card.appendChild(actions);
    return card;
  }

  function showForm(record) {
    var form = document.getElementById('my-crops-form');
    if (!form) return;
    form.style.display = 'block';
    var f = form.elements;
    f['mc-record-id'].value = record ? record.recordId : '';
    f['mc-crop-name'].value = record ? (record.cropName || '') : '';
    f['mc-sown-date'].value = record ? (record.sownDate || '') : '';
    f['mc-germination'].value = record ? (record.germinationStatus || '') : '';
    f['mc-height'].value = record ? (record.height != null ? record.height : '') : '';
    f['mc-vigor'].value = record ? (record.growthVigor || '') : '';
    f['mc-plot-id'].value = record ? (record.plotId || '') : '';
    f['mc-remark'].value = record ? (record.remark || '') : '';
  }

  function handleSubmit(e) {
    e.preventDefault();
    var form = e.target;
    var f = form.elements;
    var cropName = f['mc-crop-name'].value.trim();
    if (!cropName) return;
    var data = {
      cropName: cropName,
      sownDate: f['mc-sown-date'].value || null,
      germinationStatus: f['mc-germination'].value || null,
      height: f['mc-height'].value ? Number(f['mc-height'].value) : null,
      growthVigor: f['mc-vigor'].value || null,
      plotId: f['mc-plot-id'].value.trim() || null,
      remark: f['mc-remark'].value.trim() || null
    };
    var id = f['mc-record-id'].value;
    if (id) {
      window.MyCropsStore.update(id, data);
    } else {
      window.MyCropsStore.add(data);
    }
    form.style.display = 'none';
    renderList();
  }

  function handlePhoto(record) {
    if (!window.CropPhoto) return;
    window.CropPhoto.analyze(function (result) {
      if (result.error) { alert(result.error); return; }
      if (!result.cropName || !record) return;
      // 识别结果要留痕：只写 name 会让 photoHistory 永远是空数组，回看不了长势。
      var history = (record.photoHistory || []).slice();
      history.push({
        at: new Date().toISOString(),
        crop: result.cropName,
        confidence: result.confidence == null ? null : result.confidence
      });
      var patch = { photoHistory: history, source: record.source || 'photo' };
      // 已有名称不覆盖：拍一次照不该把用户手填的品种改掉
      if (!record.cropName) patch.cropName = result.cropName;
      window.MyCropsStore.update(record.recordId, patch);
      renderList();
      var msg = '已识别为「' + result.cropName + '」，记录已更新';
      if (history.length >= 2) msg += '；已累计 ' + history.length + ' 次拍照，可回看长势变化';
      alert(msg);
    });
  }

  if (typeof window !== 'undefined') {
    window.MyCrops = { init: init, renderList: renderList, GERMINATION_OPTIONS: GERMINATION_OPTIONS, VIGOR_OPTIONS: VIGOR_OPTIONS };
    if (document.readyState !== 'loading') { init(); }
    else { document.addEventListener('DOMContentLoaded', init); }
  }
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = { GERMINATION_OPTIONS: GERMINATION_OPTIONS, VIGOR_OPTIONS: VIGOR_OPTIONS };
  }
})();