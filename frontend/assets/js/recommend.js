/**
 * 种植推荐（T15）
 * 输入时间+地点，综合节气/气候/市场推荐作物，可一键进入农事日历。
 */
(function () {
  var API = '/api/recommend';
  var formEl, resultEl;

  function init() {
    formEl = document.getElementById('recommend-form');
    resultEl = document.getElementById('recommend-result');
    if (!formEl || !resultEl) return;

    var monthSel = document.getElementById('recommend-month');
    if (monthSel && !monthSel.dataset.filled) {
      var now = new Date();
      for (var m = 1; m <= 12; m++) {
        var opt = document.createElement('option');
        opt.value = m;
        opt.textContent = m + '月';
        if (m === now.getMonth() + 1) opt.selected = true;
        monthSel.appendChild(opt);
      }
      monthSel.dataset.filled = '1';
    }

    var locBtn = document.getElementById('recommend-loc-btn');
    if (locBtn) locBtn.addEventListener('click', detectLocation);

    formEl.addEventListener('submit', onSubmit);
  }

  function detectLocation() {
    var btn = document.getElementById('recommend-loc-btn');
    var statusEl = document.getElementById('recommend-loc-status');
    if (!navigator.geolocation) { if (statusEl) statusEl.textContent = '浏览器不支持定位'; return; }
    if (statusEl) statusEl.textContent = '定位中…';
    if (btn) btn.disabled = true;
    navigator.geolocation.getCurrentPosition(function (pos) {
      var lat = pos.coords.latitude;
      var lon = pos.coords.longitude;
      var latInput = document.getElementById('recommend-lat');
      var lonInput = document.getElementById('recommend-lon');
      if (latInput) latInput.value = lat.toFixed(2);
      if (lonInput) lonInput.value = lon.toFixed(2);
      if (statusEl) statusEl.textContent = '已定位：' + lat.toFixed(1) + '°, ' + lon.toFixed(1) + '°';
      if (btn) btn.disabled = false;
    }, function () {
      if (statusEl) statusEl.textContent = '定位失败，可手动填写或使用默认';
      if (btn) btn.disabled = false;
    }, { timeout: 8000 });
  }

  function onSubmit(e) {
    e.preventDefault();
    var month = document.getElementById('recommend-month').value;
    var lat = (document.getElementById('recommend-lat').value || '35.07').trim();
    var lon = (document.getElementById('recommend-lon').value || '116.31').trim();
    var location = (document.getElementById('recommend-location').value || '').trim();

    var btn = formEl.querySelector('button[type="submit"]');
    btn.disabled = true; btn.textContent = '推荐中…';

    var url = API + '?month=' + month + '&lat=' + lat + '&lon=' + lon;
    if (location) url += '&location=' + encodeURIComponent(location);

    fetch(url).then(function (r) { return r.json(); }).then(function (data) {
      btn.disabled = false; btn.textContent = '获取推荐';
      renderResults(data);
    }).catch(function () {
      btn.disabled = false; btn.textContent = '获取推荐';
      resultEl.innerHTML = '<div class="recommend-empty">推荐服务暂不可用，请稍后重试</div>';
    });
  }

  function renderResults(data) {
    var recs = data.recommendations || [];
    if (!recs.length) {
      resultEl.innerHTML = '<div class="recommend-empty">当前时段暂无强推荐作物，试试其他月份</div>';
      return;
    }

    var header =
      '<div class="recommend-info">' +
        '<span>当前节气：<strong>' + (data.term || '') + '</strong></span>' +
        '<span>共推荐 ' + recs.length + ' 种作物</span>' +
      '</div>';

    var html = header;
    recs.forEach(function (r, i) {
      var scorePct = Math.round(r.score * 100);
      var scoreClass = scorePct >= 75 ? 'recommend-score--high' : scorePct >= 50 ? 'recommend-score--mid' : 'recommend-score--low';
      var demandLabel = { high: '热度高', medium: '热度中', low: '热度低' }[r.demand] || r.demand;
      var demandClass = 'recommend-demand--' + (r.demand || 'low');

      html +=
        '<div class="recommend-card">' +
          '<div class="recommend-card-head">' +
            '<span class="recommend-rank">' + (i + 1) + '</span>' +
            '<span class="recommend-crop-name"></span>' +
            '<span class="recommend-category">' + (r.category || '') + '</span>' +
            '<span class="recommend-demand ' + demandClass + '">' + demandLabel + '</span>' +
            '<span class="recommend-score ' + scoreClass + '">' + scorePct + '分</span>' +
          '</div>' +
          '<div class="recommend-bar">' +
            '<div class="recommend-bar-fill" style="width:' + scorePct + '%"></div>' +
          '</div>' +
          '<div class="recommend-reasons"></div>' +
          '<div class="recommend-card-foot">' +
            '<span class="recommend-growth">生长周期约 ' + (r.growthDays || '?') + ' 天</span>' +
            '<span class="recommend-sow">建议播种：' + (r.sowDate || '') + '</span>' +
            '<button type="button" class="btn btn-primary recommend-go-btn" data-crop="" data-sow="">进入农事日历 →</button>' +
          '</div>' +
        '</div>';
    });

    resultEl.innerHTML = html;

    recs.forEach(function (r, i) {
      var card = resultEl.querySelectorAll('.recommend-card')[i];
      card.querySelector('.recommend-crop-name').textContent = r.name;

      var reasonsEl = card.querySelector('.recommend-reasons');
      (r.reasons || []).forEach(function (reason) {
        var div = document.createElement('div');
        div.className = 'recommend-reason';
        div.textContent = reason;
        reasonsEl.appendChild(div);
      });

      var goBtn = card.querySelector('.recommend-go-btn');
      goBtn.setAttribute('data-crop', r.name);
      goBtn.setAttribute('data-sow', r.sowDate || '');
      goBtn.addEventListener('click', function () { gotoCalendar(r.name, r.sowDate || ''); });
    });
  }

  function gotoCalendar(cropName, sowDate) {
    var cropParam = cropName;
    if (window.CropMap && window.CropMap.buildCropParam) {
      cropParam = window.CropMap.buildCropParam(cropName);
    }
    var params = new URLSearchParams();
    params.set('crop', cropParam);
    params.set('sowDate', sowDate);
    params.set('location', '');

    var hit = null;
    if (window.CropMap && window.CropMap.findCrop) {
      hit = window.CropMap.findCrop(cropName);
    }
    if (hit) {
      params.set('lat', String(hit.lat));
      params.set('lon', String(hit.lng));
    } else {
      params.set('lat', '35.07');
      params.set('lon', '116.31');
    }

    try {
      var pane = document.getElementById('timeline-pane');
      var iframe = document.getElementById('timeline-iframe');
      var backBtn = document.getElementById('home-back');
      if (pane && iframe) {
        iframe.src = '/timeline.html?' + params.toString();
        document.body.classList.add('split-active');
        pane.style.display = 'block';
        if (backBtn) backBtn.style.display = 'inline-block';
        return;
      }
    } catch (err) {}
    window.location.href = '/timeline.html?' + params.toString();
  }

  if (typeof window !== 'undefined') {
    window.Recommend = { init: init };
    if (document.readyState !== 'loading') { init(); }
    else { document.addEventListener('DOMContentLoaded', init); }
  }
})();