/**
 * 我的作物页面交互逻辑（T12）
 * 添加/编辑/删除作物记录，拍照分析自动补全，日历联动自动新增。
 */
(function () {
  var GERMINATION_OPTIONS = ['未发芽', '已发芽', '幼苗期', '生长期', '开花期', '结果期', '成熟期'];
  var VIGOR_OPTIONS = ['弱', '一般', '良好', '旺盛'];

  function init() {
    var pane = document.querySelector('[data-module="my-crops"]');
    if (!pane) return;

    renderList();
    refreshPlotOptions();

    var addBtn = document.getElementById('my-crops-add-btn');
    if (addBtn) addBtn.addEventListener('click', function () { showForm(null); });

    var form = document.getElementById('my-crops-form');
    if (form) form.addEventListener('submit', handleSubmit);

    var cancelBtn = document.getElementById('my-crops-cancel');
    if (cancelBtn) cancelBtn.addEventListener('click', hideForm);

    window.addEventListener('agritime:data-synced', renderList);
    window.addEventListener('agritime:sowing-confirmed', function (e) {
      var d = e.detail || {};
      if (!d.cropName) return;
      if (window.MyCropsStore) {
        window.MyCropsStore.add({
          cropName: d.cropName,
          cropId: d.cropId || null,
          sownDate: d.sownDate || null,
          plotId: d.plotId || null,
          germinationStatus: '未发芽'
        });
        renderList();
      }
    });
  }

  function refreshPlotOptions() {
    var plotSel = document.querySelector('#my-crops-form select[name="mc-plot-id"]');
    if (!plotSel || !window.MyPlotsStore) return;
    var plots = window.MyPlotsStore.list();
    plotSel.innerHTML = '<option value="">无关联地块</option>';
    plots.forEach(function (p) {
      var opt = document.createElement('option');
      opt.value = p.plotId;
      opt.textContent = p.name || '未命名地块';
      plotSel.appendChild(opt);
    });
  }

  function renderList() {
    var container = document.getElementById('my-crops-list');
    if (!container || !window.MyCropsStore) return;
    var items = window.MyCropsStore.list();
    container.innerHTML = '';
    if (!items.length) {
      container.innerHTML =
        '<div class="my-crops-empty">' +
          '<div>还没有作物记录</div>' +
          '<div style="margin-top:8px;font-size:13px;">点击「+ 添加作物」开始记录，或在农事日历确认播种后自动生成</div>' +
        '</div>';
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

  function getPlotName(plotId) {
    if (!plotId || !window.MyPlotsStore) return '';
    var p = window.MyPlotsStore.getById(plotId);
    return p ? (p.name || '未命名') : '';
  }

  function renderCard(r) {
    var card = document.createElement('div');
    card.className = 'my-crops-card';
    var name = document.createElement('div');
    name.className = 'my-crops-card-name';
    name.textContent = r.cropName || '未命名';
    card.appendChild(name);

    var fields = document.createElement('div');
    fields.className = 'my-crops-card-fields';
    var lines = [];
    if (r.sownDate) lines.push('📅 种下：' + r.sownDate);
    if (r.germinationStatus) lines.push('🌱 发芽：' + r.germinationStatus);
    if (r.height != null) lines.push('📏 高度：' + r.height + 'cm');
    if (r.growthVigor) lines.push('📊 长势：' + r.growthVigor);
    var plotName = getPlotName(r.plotId);
    if (plotName) lines.push('📍 地块：' + plotName);
    if (r.remark) lines.push('📝 ' + r.remark);
    fields.innerHTML = lines.map(function (l) { return '<div>' + l + '</div>'; }).join('');
    card.appendChild(fields);

    if (r.photoHistory && r.photoHistory.length) {
      var photoDiv = document.createElement('div');
      photoDiv.className = 'my-crops-card-photos';
      photoDiv.textContent = '📷 拍照记录 ' + r.photoHistory.length + ' 次';
      if (r.photoHistory[r.photoHistory.length - 1].cropName) {
        photoDiv.textContent += '（最近识别：' + r.photoHistory[r.photoHistory.length - 1].cropName + '）';
      }
      card.appendChild(photoDiv);
    }

    var actions = document.createElement('div');
    actions.className = 'my-crops-card-actions';

    var editBtn = document.createElement('button');
    editBtn.type = 'button'; editBtn.className = 'btn btn-ghost'; editBtn.textContent = '编辑';
    editBtn.addEventListener('click', function () { showForm(r); });
    actions.appendChild(editBtn);

    var photoBtn = document.createElement('button');
    photoBtn.type = 'button'; photoBtn.className = 'btn btn-ghost'; photoBtn.textContent = '📷 拍照';
    photoBtn.addEventListener('click', function () { handlePhoto(r); });
    actions.appendChild(photoBtn);

    var delBtn = document.createElement('button');
    delBtn.type = 'button'; delBtn.className = 'btn btn-ghost'; delBtn.textContent = '删除';
    delBtn.addEventListener('click', function () {
      if (confirm('确认删除「' + (r.cropName || '未命名') + '」这条记录？')) {
        window.MyCropsStore.remove(r.recordId);
        renderList();
      }
    });
    actions.appendChild(delBtn);

    card.appendChild(actions);
    return card;
  }

  function showForm(record) {
    var form = document.getElementById('my-crops-form');
    if (!form) return;
    refreshPlotOptions();
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
    form.scrollIntoView({ behavior: 'smooth' });
  }

  function hideForm() {
    var form = document.getElementById('my-crops-form');
    if (form) { form.style.display = 'none'; form.reset(); }
  }

  function handleSubmit(e) {
    e.preventDefault();
    var form = e.target;
    var f = form.elements;
    var cropName = f['mc-crop-name'].value.trim();
    if (!cropName) { alert('请输入作物名称'); return; }
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
    if (id) { window.MyCropsStore.update(id, data); } else { window.MyCropsStore.add(data); }
    hideForm();
    renderList();
  }

  function handlePhoto(record) {
    if (!window.CropPhoto || !window.CropPhoto.analyze) {
      alert('拍照组件未加载');
      return;
    }
    window.CropPhoto.analyze(function (result) {
      if (result.error) { alert(result.error); return; }
      if (!result.cropName) { alert('未识别到作物'); return; }

      var patch = {};
      var photoEntry = {
        date: new Date().toISOString().substring(0, 10),
        cropName: result.cropName,
        confirmed: result.confirmed || false
      };

      var existing = window.MyCropsStore.getById(record.recordId);
      var photoHistory = (existing && existing.photoHistory) ? existing.photoHistory.slice() : [];
      photoHistory.push(photoEntry);
      patch.photoHistory = photoHistory;

      if (result.cropName && result.cropName !== record.cropName) {
        if (confirm('拍照识别为「' + result.cropName + '」，是否更新作物名称？')) {
          patch.cropName = result.cropName;
        }
      }

      if (result.germinationStatus) patch.germinationStatus = result.germinationStatus;
      if (result.height != null) patch.height = result.height;
      if (result.growthVigor) patch.growthVigor = result.growthVigor;

      window.MyCropsStore.update(record.recordId, patch);
      renderList();
    }, record.cropName);
  }

  if (typeof window !== 'undefined') {
    window.MyCrops = { init: init, renderList: renderList, GERMINATION_OPTIONS: GERMINATION_OPTIONS, VIGOR_OPTIONS: VIGOR_OPTIONS };
    if (document.readyState !== 'loading') { init(); }
    else { document.addEventListener('DOMContentLoaded', init); }
  }
})();