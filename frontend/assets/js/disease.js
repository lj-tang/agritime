/**
 * 病害检查（V4 T13）
 * 文字描述 → /api/disease/detect → Top3 判断 + 应对策略。
 * 图片目前只留档不参与识别，界面上明确写出来，不假装能看图。
 */
(function () {
  var API = '/api/disease/detect';
  var TYPE_LABEL = { disease: '病害', pest: '虫害' };

  var form, cropSel, textInput, photoInput, photoBtn, photoName, resultBox;

  function init() {
    form = document.getElementById('disease-form');
    cropSel = document.getElementById('disease-crop');
    textInput = document.getElementById('disease-text');
    photoInput = document.getElementById('disease-photo');
    photoBtn = document.getElementById('disease-photo-btn');
    photoName = document.getElementById('disease-photo-name');
    resultBox = document.getElementById('disease-result');
    if (!form || !resultBox) return;

    form.addEventListener('submit', onSubmit);
    photoBtn.addEventListener('click', function () { photoInput.click(); });
    photoInput.addEventListener('change', function () {
      var f = photoInput.files && photoInput.files[0];
      photoName.textContent = f ? ('已选择：' + f.name) : '';
    });
    document.getElementById('disease-reset').addEventListener('click', reset);
  }

  function reset() {
    form.reset();
    photoName.textContent = '';
    resultBox.innerHTML = '';
  }

  function onSubmit(e) {
    e.preventDefault();
    var q = (textInput.value || '').trim();
    var hasImage = !!(photoInput.files && photoInput.files.length);
    if (!q && !hasImage) {
      showMessage('请先描述症状，或者拍照上传');
      return;
    }
    var url = API + '?crop=' + encodeURIComponent(cropSel.value || '') +
      '&q=' + encodeURIComponent(q) +
      '&hasImage=' + (hasImage ? '1' : '0');

    showLoading();
    var controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
    var timer = setTimeout(function () { if (controller) controller.abort(); }, 20000);

    fetch(url, { method: 'GET', signal: controller ? controller.signal : undefined })
      .then(function (res) {
        clearTimeout(timer);
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function (data) { render(data); })
      .catch(function (err) {
        clearTimeout(timer);
        var msg = err && err.name === 'AbortError' ? '请求超时，请稍后重试' : '查询失败：' + (err.message || '网络异常');
        showMessage(msg);
      });
  }

  function showLoading() {
    resultBox.innerHTML = '<div class="disease-empty">正在比对病害库…</div>';
  }

  function showMessage(text) {
    resultBox.innerHTML = '';
    var d = document.createElement('div');
    d.className = 'disease-empty';
    d.textContent = text;
    resultBox.appendChild(d);
  }

  function render(data) {
    resultBox.innerHTML = '';
    if (data.note) {
      var note = document.createElement('div');
      note.className = 'disease-note';
      note.textContent = data.note;
      resultBox.appendChild(note);
    }
    var matches = data.matches || [];
    if (!matches.length) {
      showMessage('没有匹配到已知病害，建议补充症状细节（部位、颜色、有没有霉层或虫体）');
      return;
    }
    matches.forEach(function (m, i) { resultBox.appendChild(renderCard(m, i)); });
  }

  function renderCard(m, index) {
    var card = document.createElement('div');
    card.className = 'disease-card';

    var head = document.createElement('div');
    head.className = 'disease-card-head';
    head.innerHTML =
      '<span class="disease-rank">' + (index + 1) + '</span>' +
      '<span class="disease-name"></span>' +
      '<span class="disease-type disease-type--' + (m.type === 'pest' ? 'pest' : 'disease') + '">' +
      (TYPE_LABEL[m.type] || '病害') + '</span>' +
      '<span class="disease-conf">' + Math.round((m.confidence || 0) * 100) + '%</span>';
    head.querySelector('.disease-name').textContent = m.name;
    card.appendChild(head);

    var bar = document.createElement('div');
    bar.className = 'disease-bar';
    var fill = document.createElement('div');
    fill.className = 'disease-bar-fill';
    fill.style.width = Math.round((m.confidence || 0) * 100) + '%';
    bar.appendChild(fill);
    card.appendChild(bar);

    if (m.conditions) {
      var cond = document.createElement('p');
      cond.className = 'disease-cond';
      cond.textContent = '发生条件：' + m.conditions;
      card.appendChild(cond);
    }

    card.appendChild(section('典型症状', m.symptoms));
    card.appendChild(section('怎么处理', m.treatment));
    card.appendChild(section('可用药剂', m.medicine, true));
    card.appendChild(section('下次预防', m.prevention));

    return card;
  }

  function section(title, items, pill) {
    var box = document.createElement('div');
    box.className = 'disease-section';
    var h = document.createElement('div');
    h.className = 'disease-section-title';
    h.textContent = title;
    box.appendChild(h);

    var list = document.createElement('div');
    list.className = pill ? 'disease-pills' : 'disease-list';
    (items || []).forEach(function (t) {
      var el = document.createElement(pill ? 'span' : 'div');
      if (pill) el.className = 'disease-pill';
      el.textContent = t;
      list.appendChild(el);
    });
    box.appendChild(list);
    return box;
  }

  if (typeof window !== 'undefined') {
    window.Disease = { init: init };
    if (document.readyState !== 'loading') { init(); }
    else { document.addEventListener('DOMContentLoaded', init); }
  }
})();
