/**
 * 左侧栏导航组件（V4 T01）
 * 6 个板块入口：种植推荐/我的地块/农事日历/病害检查/我的作物/客服
 * 点击切换对应 content pane，移动端抽屉式折叠。
 */
(function () {
  var MODULE_ENTRIES = [
    { id: 'recommend', label: '种植推荐', icon: '🌱', implemented: false },
    { id: 'calendar', label: '农事日历', icon: '📅', implemented: true },
    { id: 'disease', label: '病害检查', icon: '🔍', implemented: true },
    { id: 'my', label: '我的', icon: '👤', implemented: true, children: [
      { id: 'plots', label: '我的地块', icon: '📍', implemented: true },
      { id: 'my-crops', label: '我的产品', icon: '🌾', implemented: true }
    ]},
    { id: 'service', label: '客服', icon: '💬', implemented: false }
  ];

  var PLACEHOLDER_TEXT = '该板块正在开发中，敬请期待';

  var currentModule = 'calendar';

  function findEntry(id) {
    for (var i = 0; i < MODULE_ENTRIES.length; i++) {
      if (MODULE_ENTRIES[i].id === id) return MODULE_ENTRIES[i];
      if (MODULE_ENTRIES[i].children) {
        for (var j = 0; j < MODULE_ENTRIES[i].children.length; j++) {
          if (MODULE_ENTRIES[i].children[j].id === id) return MODULE_ENTRIES[i].children[j];
        }
      }
    }
    return null;
  }

  function renderSidebar() {
    var nav = document.getElementById('sidebar-nav');
    if (!nav) return;
    nav.innerHTML = '';
    MODULE_ENTRIES.forEach(function (entry) {
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'sidebar-item' + (entry.id === currentModule ? ' sidebar-item--active' : '');
      btn.setAttribute('data-module', entry.id);
      btn.setAttribute('aria-label', entry.label);
      btn.innerHTML = '<span class="sidebar-icon">' + entry.icon + '</span><span class="sidebar-label">' + entry.label + '</span>';
      if (entry.children) {
        btn.addEventListener('click', function () { toggleSubmenu(entry.id); });
        nav.appendChild(btn);
        var sub = document.createElement('div');
        sub.className = 'sidebar-submenu';
        sub.setAttribute('data-parent', entry.id);
        entry.children.forEach(function (child) {
          var childBtn = document.createElement('button');
          childBtn.type = 'button';
          childBtn.className = 'sidebar-item sidebar-item--sub' + (child.id === currentModule ? ' sidebar-item--active' : '');
          childBtn.setAttribute('data-module', child.id);
          childBtn.innerHTML = '<span class="sidebar-icon">' + child.icon + '</span><span class="sidebar-label">' + child.label + '</span>';
          childBtn.addEventListener('click', function () { switchModule(child.id); });
          sub.appendChild(childBtn);
        });
        nav.appendChild(sub);
      } else {
        btn.addEventListener('click', function () { switchModule(entry.id); });
        nav.appendChild(btn);
      }
    });
  }

  function toggleSubmenu(id) {
    var sub = document.querySelector('.sidebar-submenu[data-parent="' + id + '"]');
    if (sub) sub.classList.toggle('sidebar-submenu--open');
  }

  /** 切到子菜单里的板块时，把父级菜单自动展开，避免用户看不出自己在哪 */
  function openParentOf(id) {
    for (var i = 0; i < MODULE_ENTRIES.length; i++) {
      var e = MODULE_ENTRIES[i];
      if (!e.children) continue;
      for (var j = 0; j < e.children.length; j++) {
        if (e.children[j].id === id) {
          var sub = document.querySelector('.sidebar-submenu[data-parent="' + e.id + '"]');
          if (sub) sub.classList.add('sidebar-submenu--open');
          return;
        }
      }
    }
  }

  function switchModule(id) {
    currentModule = id;
    // 切到子功能板块时让日历主体整体让位，由板块占满内容区（见 common.css 的 .pane-mode）
    document.body.classList.toggle('pane-mode', id !== 'calendar');
    openParentOf(id);
    var tp = document.getElementById('timeline-pane');
    if (tp) tp.style.display = 'none';
    document.body.classList.remove('split-active');
    var bk = document.getElementById('home-back');
    if (bk) bk.style.display = 'none';
    var homeMain = document.querySelector('.page--home');
    if (homeMain) {
      homeMain.style.display = (id === 'calendar') ? '' : 'none';
    }
    var nav = document.getElementById('sidebar-nav');
    if (nav) {
      var items = nav.querySelectorAll('.sidebar-item');
      items.forEach(function (item) {
        item.classList.toggle('sidebar-item--active', item.getAttribute('data-module') === id);
      });
    }
    var panes = document.querySelectorAll('.content-pane');
    panes.forEach(function (pane) {
      pane.classList.toggle('content-pane--active', pane.getAttribute('data-module') === id);
    });
    var entry = findEntry(id);
    if (entry && !entry.implemented) {
      var ph = document.getElementById('module-placeholder');
      if (ph) {
        ph.style.display = 'block';
        ph.textContent = PLACEHOLDER_TEXT;
      }
    } else {
      var ph2 = document.getElementById('module-placeholder');
      if (ph2) ph2.style.display = 'none';
    }
    var drawer = document.getElementById('sidebar');
    if (drawer) drawer.classList.remove('sidebar--open');
  }

  function toggleDrawer() {
    var sidebar = document.getElementById('sidebar');
    if (sidebar) sidebar.classList.toggle('sidebar--open');
  }

  function init() {
    renderSidebar();
    var burger = document.getElementById('sidebar-burger');
    if (burger) burger.addEventListener('click', toggleDrawer);
    switchModule(currentModule);
  }

  if (typeof window !== 'undefined') {
    window.Sidebar = { init: init, switchModule: switchModule, renderSidebar: renderSidebar, MODULE_ENTRIES: MODULE_ENTRIES };
    if (document.readyState !== 'loading') { init(); }
    else { document.addEventListener('DOMContentLoaded', init); }
  }
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = { MODULE_ENTRIES: MODULE_ENTRIES, switchModule: switchModule };
  }
})();