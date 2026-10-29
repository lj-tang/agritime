/**
 * 站点开关的前端落地（T10）
 *
 * 超管在后台关掉「作物搜索」后，这里负责把搜索入口禁用掉，
 * 而不是等用户输入完再报错。开关本身由服务端裁决，前端只是配合。
 */
(function () {
  function apply(cfg) {
    window.AgriSiteConfig = cfg;

    if (cfg.searchEnabled === false) {
      var input = document.getElementById('crop');
      if (input) {
        input.disabled = true;
        input.placeholder = '搜索已被管理员关闭，请从推荐列表中选择作物';
        input.setAttribute('aria-disabled', 'true');
      }
      var box = document.getElementById('crop-suggestions');
      if (box) {
        box.innerHTML = '';
        box.classList.remove('open');
      }
      var field = document.getElementById('field-crop');
      if (field && !field.querySelector('.field-locked')) {
        var tip = document.createElement('p');
        tip.className = 'field-locked';
        tip.textContent = '作物搜索已被管理员关闭';
        field.appendChild(tip);
      }
    }

    if (cfg.llmEnabled === false) {
      // 大模型由服务端拦截，前端不提示，避免干扰正常使用
    }
  }

  function boot() {
    if (typeof fetch !== 'function') return;
    fetch('/api/site-config')
      .then(function (res) { return res.ok ? res.json() : null; })
      .then(function (d) { if (d && d.data) apply(d.data); })
      .catch(function () { /* 拿不到就按默认可用处理 */ });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
