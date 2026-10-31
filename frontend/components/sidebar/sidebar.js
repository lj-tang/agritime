/**
 * 左侧栏导航组件（V4 T01）
 * 6 个板块入口：种植推荐/我的地块/农事日历/病害检查/我的作物/客服
 * 点击切换对应 content pane，移动端抽屉式折叠。
 */
(function () {
  var MODULE_ENTRIES = [
    // 农事日历是主功能，放最上面；其余按「先看天气再选种」的使用顺序排
    { id: 'calendar', label: '农事日历', icon: '📅', implemented: true },
    { id: 'recommend', label: '种植推荐', icon: '🌱', implemented: true },
    { id: 'disease', label: '病害检查', icon: '🔍', implemented: true },
    { id: 'my', label: '我的', icon: '👤', implemented: true, children: [
      { id: 'plots', label: '我的地块', icon: '📍', implemented: true },
      { id: 'my-crops', label: '我的作物', icon: '🌾', implemented: true }
    ]},
    { id: 'service', label: '客服', icon: '💬', implemented: true },
    // 管理后台只给超管看：非管理员连入口都不出现，权限判断仍在服务端
    { id: 'admin', label: '管理后台', icon: '⚙️', implemented: true, adminOnly: true }
  ];

  var PLACEHOLDER_TEXT = '该板块正在开发中，敬请期待';

  var currentModule = 'calendar';
  var isAdmin = false;
  var adminChecked = false;

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
      if (entry.adminOnly && !isAdmin) return;
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
        btn.addEventListener('click', function () {
          // 「管理后台」是独立控制台（账号/文件/开关/日志），不是页内面板
          if (entry.id === 'admin') {
            location.href = './admin.html';
            return;
          }
          switchModule(entry.id);
        });
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
    var guard = findEntry(id);
    if (guard && guard.adminOnly && !isAdmin) return;
    currentModule = id;
    // 广播切换事件：各模块可据此刷新自己的数据（如推荐页的地块下拉），
    // 否则进页面时初始化一次，之后新建的地块在别的模块里看不到
    try {
      window.dispatchEvent(new CustomEvent('agritime:module-changed', { detail: { module: id } }));
    } catch (e) {
      if (document.createEvent) {
        var ev = document.createEvent('CustomEvent');
        ev.initCustomEvent('agritime:module-changed', true, true, { module: id });
        window.dispatchEvent(ev);
      }
    }
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
    closeDrawer();
  }

  /**
   * 抽屉的开关状态要同时反映到 body 上：
   * CSS 里有 `body.sidebar-open .sidebar-burger { display: none }`，
   * 只 toggle .sidebar--open 的话汉堡按钮不会让位，会压住品牌名。
   */
  function closeDrawer() {
    var sidebar = document.getElementById('sidebar');
    if (!sidebar) return;
    sidebar.classList.remove('sidebar--open');
    document.body.classList.remove('sidebar-open');
  }

  function toggleDrawer() {
    var sidebar = document.getElementById('sidebar');
    if (!sidebar) return;
    var open = sidebar.classList.toggle('sidebar--open');
    document.body.classList.toggle('sidebar-open', open);
  }

  function init() {
    renderSidebar();
    var burger = document.getElementById('sidebar-burger');
    if (burger) burger.addEventListener('click', function (e) {
      e.stopPropagation();
      toggleDrawer();
    });

    // 点抽屉外部收起：移动端没有遮罩层，用户习惯点空白处关闭
    document.addEventListener('click', function (e) {
      var sidebar = document.getElementById('sidebar');
      if (!sidebar || !sidebar.classList.contains('sidebar--open')) return;
      if (sidebar.contains(e.target) || (burger && burger.contains(e.target))) return;
      closeDrawer();
    });
    var pane = new URLSearchParams(location.search).get('pane');
    if (pane && findEntry(pane)) currentModule = pane;
    switchModule(currentModule);
    checkAdmin();
  }

  /**
   * 只有超管才挂「管理后台」入口。
   * 先渲染再查身份，避免等接口时侧栏是空的；查到是超管就重渲染一次。
   */
  function checkAdmin() {
    if (typeof fetch !== 'function') return;
    fetch('/api/auth/me')
      .then(function (res) { return res.ok ? res.json() : null; })
      .then(function (d) {
        var user = d && d.data && d.data.user;
        isAdmin = !!(user && user.role === 'admin');
        if (!adminChecked && isAdmin) {
          adminChecked = true;
          renderSidebar();
          switchModule(currentModule);
        }
        adminChecked = true;
      })
      .catch(function () { adminChecked = true; });
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