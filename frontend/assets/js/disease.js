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

    // 进来先给一个引导态，别让右侧结果区空着
    showMessage('在左侧写下症状，点「开始判断」，这里会给出最可能的三条判断和应对策略');
  }

  function reset() {
    form.reset();
    photoName.textContent = '';
    showMessage('在左侧写下症状，点「开始判断」，这里会给出最可能的三条判断和应对策略');
  }

  /**
   * 从图片里提取颜色特征（T13）。
   *
   * 说明：这里做的是像素颜色统计，不是病害识别模型——
   * 缩到 64×64 后统计褐斑/黄化/白粉的占比，作为"看图"的辅助信号交给后端排序。
   * 之所以不用大模型，是因为当前环境的 openPangu 是纯文本模型，不收图像输入。
   */
  function extractColorFeatures(file, cb) {
    var url = URL.createObjectURL(file);
    var img = new Image();
    img.onload = function () {
      try {
        var size = 64;
        var canvas = document.createElement('canvas');
        canvas.width = size;
        canvas.height = size;
        var ctx = canvas.getContext('2d');
        ctx.drawImage(img, 0, 0, size, size);
        var data = ctx.getImageData(0, 0, size, size).data;
        var brown = 0, yellow = 0, white = 0, green = 0, total = 0;
        for (var i = 0; i < data.length; i += 4) {
          var r = data[i], g = data[i + 1], b = data[i + 2], a = data[i + 3];
          if (a < 32) continue;
          total++;
          var max = Math.max(r, g, b), min = Math.min(r, g, b);
          var sat = max === 0 ? 0 : (max - min) / max;
          if (r > 195 && g > 195 && b > 185 && sat < 0.12) white++;
          else if (r > 110 && r < 205 && g > 80 && g < 165 && b < 100 && sat > 0.22) brown++;
          else if (r > 150 && g > 140 && b < 115 && sat > 0.22) yellow++;
          else if (g > 80 && g >= r * 1.03 && g >= b * 1.03) green++;
        }
        if (!total) { cb(null); return; }
        cb({
          brown: Math.round(brown / total * 1000) / 1000,
          yellow: Math.round(yellow / total * 1000) / 1000,
          white: Math.round(white / total * 1000) / 1000,
          green: Math.round(green / total * 1000) / 1000
        });
      } catch (e) {
        cb(null);
      } finally {
        URL.revokeObjectURL(url);
      }
    };
    img.onerror = function () { URL.revokeObjectURL(url); cb(null); };
    img.src = url;
  }

  function onSubmit(e) {
    e.preventDefault();
    var q = (textInput.value || '').trim();
    var hasImage = !!(photoInput.files && photoInput.files.length);
    if (!q && !hasImage) {
      showMessage('请先描述症状，或者拍照上传');
      return;
    }
    var file = hasImage ? (photoInput.files && photoInput.files[0]) : null;

    // 有图就先提颜色特征，再带着特征去查；提不出来就只按文字查
    if (file) {
      photoName.textContent = '正在分析图片颜色…';
      extractColorFeatures(file, function (f) { run(f); });
    } else {
      run(null);
    }
    return;

    function run(f) {
    var url = API + '?crop=' + encodeURIComponent(cropSel.value || '') +
      '&q=' + encodeURIComponent(q) +
      '&hasImage=' + (hasImage ? '1' : '0');
    if (f) {
      url += '&brown=' + f.brown + '&yellow=' + f.yellow + '&white=' + f.white;
      photoName.textContent = '已选图片：' + file.name + '（褐斑 ' + Math.round(f.brown * 100)
        + '% · 黄化 ' + Math.round(f.yellow * 100) + '% · 白粉 ' + Math.round(f.white * 100) + '%）';
    }

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
