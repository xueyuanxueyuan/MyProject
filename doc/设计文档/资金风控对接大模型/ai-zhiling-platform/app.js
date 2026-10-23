/* ============================================================================
 * app.js — 视图与交互（hash 路由：#apps 应用中心 / #stats 统计洞察）
 * 数据来源：mock.js（经 api.js 服务层）；图标：icons.js
 * ========================================================================== */
(function () {
  var $ = function (s, el) { return (el || document).querySelector(s); };
  var $$ = function (s, el) { return Array.prototype.slice.call((el || document).querySelectorAll(s)); };
  var icon = function (n, s, w) { return window.icon(n, s == null ? 20 : s, w == null ? 2 : w); };
  var fmt = function (n) { return n.toLocaleString('zh-CN'); };
  var esc = function (s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/"/g, '&quot;'); };

  var state = {
    view: 'apps',
    portal: 'tech', /* 门户端：business 业务前端 / tech 技术中台（默认）/ admin 管理后台 */
    board: 'efficiency',
    query: '',
    apps: [],
    sortKey: 'usageTotal',
    sortDir: -1,
    /* ----- 能力管理状态 ----- */
    ability: {
      module: 'meta', /* meta | kpis | groups | apps | versions | logs */
      draft: 'v1.1.0-draft', /* 待发布草稿版本号 */
      published: 'v1.0.0',
      dirty: true,
      /* 查询条件 */
      q: {
        apps: { name: '', category: '', status: '' },
        logs: { type: '', user: '', from: '', to: '' }
      }
    }
  };

  /* ---------- 派生数据 ---------- */
  var CAT_NONE = { id: null, name: '', board: null, accent: '#8492A8', desc: '' };
  function catOf(app) {
    return DB.categories.find(function (c) { return c.id === app.category; }) || CAT_NONE;
  }
  function boardOf(cat) {
    return DB.boards.find(function (b) { return b.id === cat.board; });
  }
  function portalOf(app) {
    return app.portal || 'tech';
  }
  function totalUsage() {
    return state.apps.reduce(function (s, a) { return s + a.usageTotal; }, 0);
  }
  function last7() {
    return DB.trend.slice(-7).reduce(function (s, d) { return s + d.count; }, 0);
  }
  function hexA(hex, a) {
    var n = parseInt(hex.slice(1), 16);
    return 'rgba(' + ((n >> 16) & 255) + ',' + ((n >> 8) & 255) + ',' + (n & 255) + ',' + a + ')';
  }

  /* ---------- 通用 ---------- */
  function countUp(el, target, dur) {
    if (!el) return;
    dur = dur || 950;
    var start = null;
    function tick(t) {
      if (!start) start = t;
      var p = Math.min(1, (t - start) / dur);
      var eased = 1 - Math.pow(1 - p, 3);
      el.textContent = fmt(Math.round(target * eased));
      if (p < 1) requestAnimationFrame(tick);
    }
    requestAnimationFrame(tick);
  }

  var toastTimer = null;
  function toast(msg) {
    var el = $('#toast');
    el.innerHTML = msg;
    el.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { el.classList.remove('show'); }, 2400);
  }

  /* ---------- 初始化 ---------- */
  document.addEventListener('DOMContentLoaded', init);

  async function init() {
    /* 填充静态图标位 */
    $$('[data-icon]').forEach(function (el) {
      el.innerHTML = icon(el.dataset.icon, +(el.dataset.size || 16));
    });

    bindEvents();

    /* 首屏加载应用数据 */
    var res = await API.fetchApps();
    state.apps = res.data;

    renderCombinedApps(true);
    renderHeroKpis();
    handleHash();
    window.addEventListener('hashchange', handleHash);
  }

  /* ---------- 事件绑定 ---------- */
  function bindEvents() {
    /* 搜索（防抖，作用于组合视图内全部应用） */
    var searchTimer = null;
    $('#searchInput').addEventListener('input', function (e) {
      clearTimeout(searchTimer);
      var v = e.target.value;
      searchTimer = setTimeout(function () {
        state.query = v;
        renderCombinedApps(false);
      }, 140);
    });

    /* 悬浮分类导航：点击定位、滚动高亮 */
    var cn = document.getElementById('catNavPanel');
    if (cn) cn.addEventListener('click', function (e) {
      var item = e.target.closest('[data-nav-target]');
      if (!item) return;
      var sec = document.getElementById(item.dataset.navTarget);
      if (sec) sec.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
    window.addEventListener('scroll', function () {
      if (!$('#catNav').hidden) requestAnimationFrame(updateCatNavActive);
    }, { passive: true });

    /* 应用卡片：点击 / 回车 在新页签打开统一应用界面 */
    $('#view-apps').addEventListener('click', function (e) {
      var openBtn = e.target.closest('[data-open]');
      var card = e.target.closest('.app-card');
      var id = (openBtn && openBtn.dataset.open) || (card && card.dataset.app);
      if (id) openApp(id);
    });
    $('#view-apps').addEventListener('keydown', function (e) {
      if ((e.key === 'Enter' || e.key === ' ') && e.target.classList && e.target.classList.contains('app-card')) {
        e.preventDefault();
        openApp(e.target.dataset.app);
      }
    });

    /* 统计洞察：表头排序 / 重置演示数据 */
    $('#view-stats').addEventListener('click', function (e) {
      var thEl = e.target.closest('th[data-sort]');
      if (thEl) {
        var key = thEl.dataset.sort;
        if (state.sortKey === key) state.sortDir *= -1;
        else { state.sortKey = key; state.sortDir = -1; }
        renderTablePanel();
        return;
      }
      if (e.target.closest('#btnReset')) resetDemoData();
    });
  }

  /* ---------- hash 路由 ---------- */
  function handleHash() {
    var h = (location.hash || '#apps').replace('#', '');
    var ALL_VIEWS = ['apps','stats','ability','users'];
    var view = ALL_VIEWS.indexOf(h) >= 0 ? h : 'apps';
    /* 权限校验：能力管理 / 用户管理 需要已登录且具有相应角色；否则跳转应用中心并 toast */
    var needPerm = null;
    if (view === 'ability') needPerm = 'config';
    else if (view === 'users') needPerm = 'super';
    if (needPerm && (!window.Auth || !window.Auth.hasPerm || !window.Auth.hasPerm(needPerm))) {
      toast(view === 'users' ? '仅超级管理员可进入用户管理后台' : '请以管理员身份登录后再使用能力配置');
      view = 'apps';
      location.hash = '#apps';
    }
    state.view = view;
    document.body.dataset.view = view;
    $('#view-apps').hidden = view !== 'apps';
    $('#view-stats').hidden = view !== 'stats';
    var va = $('#view-ability'); if (va) va.hidden = view !== 'ability';
    var vu = $('#view-users');   if (vu) vu.hidden = view !== 'users';
    $$('.main-nav a').forEach(function (a) {
      a.classList.toggle('active', a.dataset.nav === view);
    });
    if (view === 'stats') renderStats();
    if (view === 'ability') renderAbility();
    if (view === 'users' && window.Auth && typeof window.Auth.renderPage === 'function') window.Auth.renderPage();
    window.scrollTo({ top: 0 });
  }

  /* ============================================================
   * 应用中心
   * ============================================================ */
  function renderHeroKpis(animate) {
    var map = {
      kpiApps: state.apps.filter(function (a) { return !a.soon; }).length,
      kpiUsage: totalUsage(),
      kpiUsers: DB.overview.monthlyUsers,
      kpiWeek: last7()
    };
    Object.keys(map).forEach(function (id) {
      var el = $('#' + id);
      if (animate !== false) countUp(el, map[id]);
      else el.textContent = fmt(map[id]);
    });
  }

  /* ============================================================
   * 组合视图（取消一级 Tab / 二级 Tab，一次性渲染所有分组）
   * ============================================================ */
  function matchApp(a, q) {
    if (!q) return true;
    var cat = catOf(a);
    var portal = DB.portals.find(function (p) { return p.id === (a.portal || 'tech'); }) || {};
    var hay = [a.name, a.desc, cat.name, portal.name, (a.tags || []).join(' ')].join(' ').toLowerCase();
    return hay.indexOf(q) > -1;
  }

  /* 扁平化视图：只保留"应用分组（categories）"一个层级，去掉一级/二级 Tab */
  function renderCombinedApps(animated) {
    var box = $('#appsCombined');
    var q = state.query.trim().toLowerCase();
    var globalSeq = 0;
    var html = '';
    var totalMatched = 0;

    /* 构建分组列表：categories + 未分组兜底 */
    var groups = (DB.categories || []).slice();
    var ungrouped = state.apps.filter(function (a) {
      return !a.category && (a.visible !== false) && matchApp(a, q);
    });
    if (ungrouped.length > 0) {
      groups.push({ id: 'cat-ungrouped', name: '其他类', desc: '尚未分配分组的应用卡片集合', accent: '#7F8FA6' });
    }

    groups.forEach(function (cat) {
      var apps;
      if (cat.id === 'cat-ungrouped') {
        apps = ungrouped;
      } else {
        apps = state.apps.filter(function (a) {
          return a.category === cat.id && (a.visible !== false) && matchApp(a, q);
        });
      }
      if (!apps.length) return;
      totalMatched += apps.length;
      html += groupHTML(cat, apps, animated, globalSeq);
      globalSeq += apps.length;
    });

    if (totalMatched === 0 && q) {
      html = emptyHTML();
    } else if (totalMatched === 0) {
      html = '<div class="empty-state">' + icon('info', 34) + '<h3>应用筹备中</h3><p>全平台应用正在建设中，敬请期待</p></div>';
    }

    box.innerHTML = html;
    updateCatNav();
  }

  /* ---------- 悬浮分类导航（右侧快速定位，鼠标滑过展开） ---------- */
  function updateCatNav() {
    var aside = $('#catNav');
    var panel = $('#catNavPanel');
    var wrap = $('#appsCombined');
    var groups = wrap ? $$('.cat-group', wrap) : [];
    if (groups.length < 2) {
      aside.hidden = true;
      panel.innerHTML = '';
      return;
    }
    panel.innerHTML = groups.map(function (g) {
      var nameEl = g.querySelector('.ph');
      var countEl = g.querySelector('.pcount');
      var accent = (g.style.getPropertyValue('--accent') || '').trim() || '#2A6BF0';
      return '<a class="cn-item" data-nav-target="' + g.id + '" style="--accent:' + accent + '"' +
        ' title="' + esc(nameEl ? nameEl.textContent : '') + '">' +
        '<span class="cn-label">' + esc(nameEl ? nameEl.textContent : '') +
          (countEl ? '<i class="cn-count">' + esc(countEl.textContent) + '</i>' : '') + '</span>' +
        '<span class="cn-dot"></span>' +
      '</a>';
    }).join('');
    aside.hidden = false;
    updateCatNavActive();
  }

  /* scrollspy：按当前滚动位置高亮所在分类 */
  function updateCatNavActive() {
    var items = $$('#catNavPanel .cn-item');
    if (!items.length) return;
    var activeId = items[0].dataset.navTarget;
    items.forEach(function (it) {
      var sec = document.getElementById(it.dataset.navTarget);
      if (sec && sec.getBoundingClientRect().top <= 120) activeId = it.dataset.navTarget;
    });
    items.forEach(function (it) {
      it.classList.toggle('active', it.dataset.navTarget === activeId);
    });
  }

  function groupHTML(cat, apps, animated, seqOffset) {
    var base = typeof seqOffset === 'number' ? seqOffset : 0;
    var ico = cat.icon || 'layout-grid';
    var accent = cat.accent || '#5B8AF5';
    return (
      '<section class="cat-group" id="cat-' + cat.id + '" style="--accent:' + accent + '">' +
        '<header class="portal-head">' +
          '<div class="portal-title">' +
            '<span class="portal-ic" style="background:linear-gradient(135deg,' + accent + ', #2F7BFF)">' +
              icon(ico, 18) +
            '</span>' +
            '<div>' +
              '<h2 class="ph">' + cat.name + '</h2>' +
              '<p class="pd">' + (cat.desc || '') + '</p>' +
            '</div>' +
            '<span class="pcount">' + apps.length + ' 个应用</span>' +
          '</div>' +
        '</header>' +
        '<div class="portal-body">' +
          '<div class="card-grid' + (animated ? '' : ' no-anim') + '">' +
            apps.map(function (a, i) { return cardHTML(a, base + i, animated); }).join('') +
          '</div>' +
        '</div>' +
      '</section>'
    );
  }

  function cardHTML(app, i, animated) {
    var cat = catOf(app);
    var d = animated ? i * 55 : 0;
    var ver = app.version || 'v1.0.0';
    var verChip = '<span class="app-ver mono" title="版本号 ' + esc(ver) + '">' + esc(ver) + '</span>';

    /* 规划中应用：敬请期待占位卡片（不可进入、不计统计） */
    if (app.soon) {
      return (
        '<article class="app-card soon" style="--d:' + d + 'ms">' +
          '<div class="card-top">' +
            '<span class="app-icon">' + icon(app.icon, 22) + '</span>' +
            '<span class="soon-chip">' + icon('clock', 12, 2) + '敬请期待</span>' +
          '</div>' +
          '<h3 class="app-name">' + esc(app.name) + verChip + '</h3>' +
          '<p class="app-desc">' + esc(app.desc) + '</p>' +
          '<div class="app-tags">' +
            app.tags.slice(0, 3).map(function (t) { return '<span>' + t + '</span>'; }).join('') +
          '</div>' +
          '<div class="card-foot">' +
            '<div class="usage-info"><span class="soon-hint">规划建设中 · 即将上线</span></div>' +
            '<button class="btn-use" disabled tabindex="-1">敬请期待</button>' +
          '</div>' +
        '</article>'
      );
    }

    var up = app.delta >= 0;
    return (
      '<article class="app-card" data-app="' + app.id + '" tabindex="0" role="button"' +
        ' aria-label="打开应用 ' + esc(app.name) + '" style="--d:' + d + 'ms">' +
        '<div class="card-top">' +
          '<span class="app-icon">' + icon(app.icon, 22) + '</span>' +
          '<span class="heat-chip">' + icon('flame', 12, 2) + app.heat + '</span>' +
        '</div>' +
        '<h3 class="app-name">' + esc(app.name) + verChip + '</h3>' +
        '<p class="app-desc">' + esc(app.desc) + '</p>' +
        '<div class="app-tags">' +
          app.tags.slice(0, 3).map(function (t) { return '<span>' + t + '</span>'; }).join('') +
        '</div>' +
        '<div class="card-foot">' +
          '<div class="usage-info">' +
            '<span class="usage-num" data-usage="' + app.id + '">' + fmt(app.usageTotal) + '</span>' +
            '<span class="usage-label">次使用</span>' +
            '<span class="delta ' + (up ? 'up' : 'down') + '">' +
              icon(up ? 'trending-up' : 'trending-down', 12, 2) + (up ? '+' : '') + app.delta + '%' +
            '</span>' +
          '</div>' +
          '<button class="btn-use" data-open="' + app.id + '">进入使用 ' + icon('arrow-up-right', 14, 2) + '</button>' +
        '</div>' +
      '</article>'
    );
  }

  function emptyHTML() {
    return (
      '<div class="empty-state">' +
        icon('search', 34) +
        '<h3>未找到匹配的应用</h3>' +
        '<p>没有与「' + esc(state.query.trim()) + '」相关的应用，换个关键词试试</p>' +
      '</div>'
    );
  }

  /* ============================================================
   * 进入使用：在新页签打开统一应用界面容器页（app-view.html）
   * ============================================================ */
  function openApp(appId) {
    var app = state.apps.find(function (a) { return a.id === appId; });
    if (!app || app.soon) return;
    window.open('app-view.html?app=' + encodeURIComponent(appId), '_blank', 'noopener');
    toast('已在新页签打开 · ' + esc(app.name));

    /* 记录一次使用（真实环境为后端埋点，见 api.js recordUsage） */
    API.recordUsage(appId).then(function (res) {
      app.usageTotal = res.data.usageTotal;
      updateUsageUI(app);
    });
  }

  function updateUsageUI(app) {
    var cardNum = $('[data-usage="' + app.id + '"]');
    if (cardNum) {
      cardNum.textContent = fmt(app.usageTotal);
      cardNum.classList.remove('bump');
      void cardNum.offsetWidth;
      cardNum.classList.add('bump');
    }
    if (!$('#view-apps').hidden) renderHeroKpis(false);
  }

  /* ============================================================
   * 统计洞察
   * ============================================================ */
  function skeletonHTML() {
    var kpi = '<div class="kpi-card skel-card">' +
      '<span class="skel skel-block-sq"></span><span class="skel-wrap"><span class="skel skel-line w60"></span><span class="skel skel-line w40"></span></span></div>';
    return (
      '<div class="stats-kpis">' + kpi + kpi + kpi + kpi + '</div>' +
      '<div class="stats-grid">' +
        '<div class="panel panel-rank"><span class="skel skel-line w40"></span><span class="skel skel-block"></span></div>' +
        '<div class="panel panel-donut"><span class="skel skel-line w40"></span><span class="skel skel-block"></span></div>' +
      '</div>'
    );
  }

  async function renderStats() {
    var wrap = $('#statsBody');
    wrap.innerHTML = skeletonHTML();
    var trendRes = await API.fetchTrend();
    var days = trendRes.data.days;
    var apps = state.apps.filter(function (a) { return !a.soon; }); /* 已上线应用（排行 / 分布 / 总量口径） */
    var techN = DB.apps.filter(function (a) { return portalOf(a) === 'tech'; }).length;
    var bizN = DB.apps.filter(function (a) { return portalOf(a) === 'business'; }).length;
    var admN = DB.apps.filter(function (a) { return portalOf(a) === 'admin'; }).length;
    var total = totalUsage();
    var trendSum = days.reduce(function (s, d) { return s + d.count; }, 0);

    var kpis = [
      { label: '累计使用次数', value: total, delta: DB.overview.totalDelta, ic: 'activity' },
      { label: '近 14 天使用', value: trendSum, delta: DB.overview.trendDelta, ic: 'zap' },
      { label: '汇聚应用', value: state.apps.length, sub: '技术中台 ' + techN + ' · 业务前端 ' + bizN + ' · 管理后台 ' + admN, ic: 'layout-grid' },
      { label: '月活跃用户', value: DB.overview.monthlyUsers, delta: DB.overview.usersDelta, ic: 'users' }
    ];

    wrap.innerHTML =
      '<div class="stats-kpis">' + kpis.map(kpiHTML).join('') + '</div>' +
      '<div class="stats-grid">' +
        '<div class="panel panel-rank">' + rankPanelHTML(apps) + '</div>' +
        '<div class="panel panel-donut">' + donutPanelHTML(apps) + '</div>' +
        '<div class="panel panel-trend">' + trendPanelHTML(days) + '</div>' +
        '<div class="panel" id="tablePanel">' + tablePanelInner(apps) + '</div>' +
      '</div>' +
      '<div class="stats-foot">' +
        '<span class="stats-note">' + icon('info', 14, 2) +
          '热度指数由使用次数、活跃用户与增长趋势综合计算（演示口径）；在应用中心点击「进入使用」，统计将实时变化</span>' +
        '<button class="btn-ghost" id="btnReset">' + icon('refresh', 14, 2) + '重置演示数据</button>' +
      '</div>';

    /* 动画：KPI 数字 / 排行条 / 环图 */
    $$('[data-countup]', wrap).forEach(function (el) { countUp(el, +el.dataset.countup); });
    setTimeout(function () {
      $$('.rank-fill', wrap).forEach(function (f) { f.style.width = f.dataset.w; });
    }, 60);
    requestAnimationFrame(function () {
      requestAnimationFrame(function () {
        $$('circle[data-len]', wrap).forEach(function (c) {
          c.setAttribute('stroke-dasharray', c.dataset.len + ' ' + c.dataset.c);
        });
      });
    });
  }

  function kpiHTML(k) {
    var foot = k.sub != null
      ? '<span class="kpi-sub">' + k.sub + '</span>'
      : '<span class="kpi-sub"><span class="delta-pill up">+' + k.delta + '%</span>较上期</span>';
    return (
      '<div class="kpi-card">' +
        '<span class="kpi-ic">' + icon(k.ic, 18) + '</span>' +
        '<span class="kpi-body">' +
          '<span class="kpi-label">' + k.label + '</span>' +
          '<span class="kpi-num" data-countup="' + k.value + '">0</span>' +
          foot +
        '</span>' +
      '</div>'
    );
  }

  /* --- 热门应用排行 TOP10 --- */
  function rankPanelHTML(apps) {
    var top = apps.slice().sort(function (a, b) { return b.usageTotal - a.usageTotal; }).slice(0, 10);
    var max = top[0] ? top[0].usageTotal : 1;
    var rows = top.map(function (a, i) {
      var cat = catOf(a);
      var r = i + 1;
      var pct = Math.max(4, Math.round(a.usageTotal / max * 100));
      return (
        '<div class="rank-row">' +
          '<span class="rank-no top' + r + '">' + r + '</span>' +
          '<span class="rank-ic" style="color:' + cat.accent + ';background:' + hexA(cat.accent, 0.12) + ';border-color:' + hexA(cat.accent, 0.3) + '">' + icon(a.icon, 16) + '</span>' +
          '<span class="rank-main">' +
            '<span class="rank-name">' + esc(a.name) + '<i class="rank-cat">' + cat.name + '</i></span>' +
            '<span class="rank-track"><span class="rank-fill" style="--accent:' + cat.accent + '" data-w="' + pct + '%"></span></span>' +
          '</span>' +
          '<span class="rank-val">' + fmt(a.usageTotal) + '</span>' +
        '</div>'
      );
    }).join('');
    return (
      '<div class="panel-head"><h3 class="panel-title">' + icon('flame', 16, 2) + '热门应用排行 TOP 10</h3>' +
      '<span class="panel-sub">按累计使用次数</span></div><div class="rank-list">' + rows + '</div>'
    );
  }

  /* --- 分类使用分布（环形图） --- */
  function donutPanelHTML(apps) {
    var items = DB.categories.map(function (c) {
      var v = apps.filter(function (a) { return a.category === c.id; })
        .reduce(function (s, a) { return s + a.usageTotal; }, 0);
      return { label: c.name, value: v, color: c.accent };
    }).filter(function (d) { return d.value > 0; });
    var total = items.reduce(function (s, d) { return s + d.value; }, 0) || 1;

    var R = 78, C = 2 * Math.PI * R, acc = 0;
    var circles = items.map(function (d, i) {
      var len = (d.value / total) * C;
      var off = -(acc * C);
      acc += d.value / total;
      return '<circle r="' + R + '" cx="110" cy="110" fill="none" stroke="' + d.color +
        '" stroke-width="24" stroke-dasharray="0 ' + C.toFixed(2) + '" data-len="' + len.toFixed(2) +
        '" data-c="' + C.toFixed(2) + '" stroke-dashoffset="' + off.toFixed(2) +
        '" transform="rotate(-90 110 110)" style="transition: stroke-dasharray .9s cubic-bezier(.22,.8,.28,1) ' + (i * 60) + 'ms"/>';
    }).join('');

    var legend = items.map(function (d) {
      var pct = (d.value / total * 100).toFixed(1);
      return (
        '<div class="lg-item">' +
          '<span class="lg-dot" style="background:' + d.color + '"></span>' +
          '<span class="lg-name">' + d.label + '</span>' +
          '<span class="lg-val">' + fmt(d.value) + '<span class="lg-pct">' + pct + '%</span></span>' +
        '</div>'
      );
    }).join('');

    return (
      '<div class="panel-head"><h3 class="panel-title">' + icon('layers', 16, 2) + '分类使用分布</h3>' +
      '<span class="panel-sub">按分类累计使用</span></div>' +
      '<div class="donut-wrap">' +
        '<svg class="donut" viewBox="0 0 220 220" role="img" aria-label="分类使用分布">' + circles +
          '<text x="110" y="104" text-anchor="middle" font-size="24" font-weight="700" fill="#16233B">' + fmt(total) + '</text>' +
          '<text x="110" y="126" text-anchor="middle" font-size="11" fill="#5A6B85">累计使用次数</text>' +
        '</svg>' +
        '<div class="donut-legend">' + legend + '</div>' +
      '</div>'
    );
  }

  /* --- 近 14 天使用趋势 --- */
  function trendPanelHTML(days) {
    var W = 780, H = 300, P = { l: 52, r: 18, t: 24, b: 36 };
    var max = Math.max.apply(null, days.map(function (d) { return d.count; })) * 1.18;
    function x(i) { return P.l + i * (W - P.l - P.r) / (days.length - 1); }
    function y(v) { return H - P.b - v / max * (H - P.t - P.b); }

    var d = 'M ' + x(0).toFixed(1) + ' ' + y(days[0].count).toFixed(1);
    for (var i = 1; i < days.length; i++) {
      var x0 = x(i - 1), y0 = y(days[i - 1].count), x1 = x(i), y1 = y(days[i].count);
      var mx = (x0 + x1) / 2;
      d += ' C ' + mx.toFixed(1) + ' ' + y0.toFixed(1) + ', ' + mx.toFixed(1) + ' ' + y1.toFixed(1) + ', ' + x1.toFixed(1) + ' ' + y1.toFixed(1);
    }
    var area = d + ' L ' + x(days.length - 1).toFixed(1) + ' ' + (H - P.b) + ' L ' + x(0).toFixed(1) + ' ' + (H - P.b) + ' Z';

    var grid = '', yLabels = '';
    for (var g = 0; g <= 4; g++) {
      var val = Math.round(max / 4 * g);
      var gy = y(val);
      grid += '<line x1="' + P.l + '" x2="' + (W - P.r) + '" y1="' + gy.toFixed(1) + '" y2="' + gy.toFixed(1) + '" stroke="rgba(18,47,92,.1)" stroke-dasharray="3 5"/>';
      yLabels += '<text x="' + (P.l - 10) + '" y="' + (gy + 4).toFixed(1) + '" text-anchor="end">' + val + '</text>';
    }
    var xLabels = days.map(function (dd, i2) {
      return i2 % 2 === 0
        ? '<text x="' + x(i2).toFixed(1) + '" y="' + (H - 12) + '" text-anchor="middle">' + dd.date + '</text>'
        : '';
    }).join('');
    var dots = days.map(function (dd, i2) {
      return '<circle cx="' + x(i2).toFixed(1) + '" cy="' + y(dd.count).toFixed(1) + '" r="3.5" fill="#FFFFFF" stroke="#0AA2C0" stroke-width="2"><title>' + dd.date + ' · ' + fmt(dd.count) + ' 次使用</title></circle>';
    }).join('');
    var lp = days[days.length - 1];

    return (
      '<div class="panel-head"><h3 class="panel-title">' + icon('trending-up', 16, 2) + '近 14 天使用趋势</h3>' +
      '<span class="panel-sub">全平台日均 ' + fmt(Math.round(days.reduce(function (s, dd) { return s + dd.count; }, 0) / days.length)) + ' 次</span></div>' +
      '<svg class="trend-chart" viewBox="0 0 ' + W + ' ' + H + '" role="img" aria-label="近14天使用趋势">' +
        '<defs>' +
          '<linearGradient id="tgArea" x1="0" y1="0" x2="0" y2="1">' +
            '<stop offset="0%" stop-color="rgba(42,107,240,.16)"/><stop offset="100%" stop-color="rgba(42,107,240,0)"/>' +
          '</linearGradient>' +
          '<linearGradient id="tgLine" x1="0" y1="0" x2="1" y2="0">' +
            '<stop offset="0%" stop-color="#2A6BF0"/><stop offset="100%" stop-color="#0AA2C0"/>' +
          '</linearGradient>' +
        '</defs>' +
        grid + yLabels + xLabels +
        '<path class="trend-area" d="' + area + '" fill="url(#tgArea)"/>' +
        '<path class="trend-line" d="' + d + '" fill="none" stroke="url(#tgLine)" stroke-width="2.5" stroke-linecap="round" pathLength="1"/>' +
        dots +
        '<circle cx="' + x(days.length - 1).toFixed(1) + '" cy="' + y(lp.count).toFixed(1) + '" r="5" fill="#0AA2C0"/>' +
        '<text x="' + (x(days.length - 1) - 10).toFixed(1) + '" y="' + (y(lp.count) - 12).toFixed(1) + '" text-anchor="end" class="trend-last">' + fmt(lp.count) + '</text>' +
      '</svg>'
    );
  }

  /* --- 应用使用明细表（可排序） --- */
  function sortedApps() {
    /* 明细表含筹备中的应用（数量为 0，排序时自然靠后） */
    return state.apps.slice().sort(function (a, b) {
      return (a[state.sortKey] - b[state.sortKey]) * state.sortDir;
    });
  }

  function tablePanelInner() {
    var th = function (label, key) {
      if (!key) return '<th>' + label + '</th>';
      var cls = 'sortable';
      if (state.sortKey === key) cls += ' sorted ' + (state.sortDir === -1 ? 'desc' : 'asc');
      return '<th class="' + cls + '" data-sort="' + key + '">' + label +
        '<span class="sort-ind">' + icon('chevron-down', 12, 2.5) + '</span></th>';
    };
    var soonN = state.apps.filter(function (a) { return a.soon; }).length;
    var rows = sortedApps().map(function (a, i) {
      var cat = catOf(a);
      var b = cat.id ? boardOf(cat) : null;
      var portal = DB.portals.find(function (p) { return p.id === portalOf(a); }) || {};
      var up = a.delta >= 0;
      var soon = !!a.soon;
      return (
        '<tr' + (soon ? ' class="soon-row"' : '') + '>' +
          '<td class="rank-td">' + (i + 1) + '</td>' +
          '<td><span class="cell-app">' +
            '<span class="mini-ic" style="color:' + cat.accent + ';background:' + hexA(cat.accent, 0.12) + ';border-color:' + hexA(cat.accent, 0.3) + '">' + icon(a.icon, 15) + '</span>' +
            esc(a.name) +
            (soon ? '<span class="soon-badge">' + icon('clock', 11, 2) + '筹备中</span>' : '') +
          '</span></td>' +
          '<td>' + (portal.name || '') + '</td>' +
          '<td>' + (cat.id ? b.short : '—') + '</td>' +
          '<td>' + (cat.id ? cat.name : '—') + '</td>' +
          '<td class="num">' + (soon ? '—' : fmt(a.usageTotal)) + '</td>' +
          '<td class="num">' + (soon ? '—' : fmt(a.users)) + '</td>' +
          '<td>' + (soon ? '—' : '<span class="delta-pill ' + (up ? 'up' : 'down') + '">' + (up ? '+' : '') + a.delta + '%</span>') + '</td>' +
          '<td>' + (soon ? '—' : '<span class="heat-cell"><span class="heat-bar"><i style="width:' + a.heat + '%"></i></span><b>' + a.heat + '</b></span>') + '</td>' +
        '</tr>'
      );
    }).join('');
    return (
      '<div class="panel-head"><h3 class="panel-title">' + icon('bar-chart', 16, 2) + '应用使用明细</h3>' +
      '<span class="panel-sub">点击表头排序 · 共 ' + state.apps.length + ' 个应用' +
        (soonN ? '（含 ' + soonN + ' 个筹备中）' : '') + '</span></div>' +
      '<div class="table-scroll"><table class="stats-table">' +
        '<thead><tr>' + th('排名') + th('应用') + th('所属门户') + th('所属板块') + th('分类') +
        th('累计使用', 'usageTotal') + th('月活用户', 'users') + th('周环比', 'delta') + th('热度指数', 'heat') + '</tr></thead>' +
        '<tbody>' + rows + '</tbody>' +
      '</table></div>'
    );
  }

  function renderTablePanel() {
    var panel = $('#tablePanel');
    if (panel) panel.innerHTML = tablePanelInner();
  }

  /* --- 重置演示数据 --- */
  function resetDemoData() {
    API.resetDemoUsage();
    API.fetchApps().then(function (res) {
      state.apps = res.data;
      toast('已重置演示使用计数');
      if (state.view === 'stats') renderStats();
      else { renderCombinedApps(false); renderHeroKpis(false); }
    });
  }

  /* ============================================================
   * 能力管理
   * ============================================================ */
  var DRAFT = {
    meta: JSON.parse(JSON.stringify(DB.meta)),
    heroKpis: [
      { id: 'k1', name: '在线应用', source: 'auto', value: 0, visible: true, order: 1, ic: 'layout-grid' },
      { id: 'k2', name: '累计使用次数', source: 'auto', value: 0, visible: true, order: 2, ic: 'activity' },
      { id: 'k3', name: '月活跃用户', source: 'manual', value: 2347, visible: true, order: 3, ic: 'users' },
      { id: 'k4', name: '近 7 天使用', source: 'auto', value: 0, visible: true, order: 4, ic: 'zap' }
    ],
    portals: JSON.parse(JSON.stringify(DB.portals)).map(function (p, i) { p.order = i + 1; p.visible = true; p.hasBoard = p.id === 'tech'; return p; }),
    boards: JSON.parse(JSON.stringify(DB.boards)).map(function (b, i) { b.order = i + 1; b.visible = true; b.portal = 'tech'; return b; }),
    categories: JSON.parse(JSON.stringify(DB.categories)).map(function (c, i) { c.order = i + 1; c.visible = true; return c; }),
    apps: JSON.parse(JSON.stringify(DB.apps)).map(function (a, i) {
      a.order = i + 1; a.visible = true;
      if (a.version == null) { a.version = 'v' + (1 + (i % 3)) + '.' + (i % 4) + '.' + (i % 2); }
      if (!a.updateTime) a.updateTime = '2026-09-' + ((10 + (i % 18)) < 10 ? '0' : '') + (10 + (i % 18)) + ' ' + (9 + (i % 10)) + ':' + ((i * 7) % 60 < 10 ? '0' : '') + ((i * 7) % 60);
      if (!a.releaseOwner) a.releaseOwner = (i % 3 === 0) ? '产品经理·小王' : ((i % 3 === 1) ? '研发主管·老李' : '运营·小周');
      if (!a.releaseNote && (i % 2 === 0)) a.releaseNote = '基础功能稳定版；修复若干小缺陷与体验优化。';
      return a;
    }),
    versions: [
      { no: 'v1.1.0-draft', time: '2026-10-09 10:48:06', user: '系统管理员·admin', desc: '草稿：新增「招投标助手」应用卡片、调整一级分类排序与指标卡显隐', draft: true },
      { no: 'v1.0.0', time: '2026-09-28 14:22:15', user: '系统管理员·admin', desc: '平台首次上线，初始配置发布（超级管理员发布）', current: true }
    ],
    logs: [
      { time: '2026-10-09 10:48:06', user: '系统管理员·admin', ip: '10.12.3.45', type: '保存', obj: '草稿版本：v1.1.0-draft', diff: '<span class="new">+ 草稿快照已保存：新增应用、调整分类排序与指标卡显隐</span>' },
      { time: '2026-09-28 14:22:15', user: '系统管理员·admin', ip: '10.12.3.45', type: '发布', obj: '全局配置', diff: '首次发布，初始化全量配置，版本号 v1.0.0（超级管理员发布）' },
      { time: '2026-09-28 11:06:32', user: '产品经理·小王', ip: '10.12.5.128', type: '编辑', obj: '应用卡片：智能需求助手', diff: '<span class="old">- 热度指数 92</span>\n<span class="new">+ 热度指数 95</span>' },
      { time: '2026-09-28 10:14:09', user: '研发主管·老李', ip: '10.12.5.62', type: '新增', obj: '应用卡片：AICoding知识共享平台', diff: '<span class="new">+ 新增应用，分类：知识与数据赋能类</span>' },
      { time: '2026-09-27 17:48:21', user: '系统管理员·admin', ip: '10.12.3.45', type: '排序', obj: '一级分类Tab', diff: '<span class="old">- [业务前端, 管理后台, 技术中台]</span>\n<span class="new">+ [业务前端, 技术中台, 管理后台]</span>' },
      { time: '2026-09-27 15:33:02', user: '运营·小周', ip: '10.12.4.77', type: '显隐', obj: '一级分类Tab：管理后台', diff: '<span class="old">- 隐藏</span>\n<span class="new">+ 显示</span>' },
      { time: '2026-09-27 14:02:18', user: '系统管理员·admin', ip: '10.12.3.45', type: '预览', obj: '草稿配置', diff: '预览沙箱地址已打开' },
      { time: '2026-09-26 16:55:47', user: '产品经理·小王', ip: '10.12.5.128', type: '删除', obj: '应用分组：旧版工具合集', diff: '<span class="old">- 分组"旧版工具合集"已删除，下属 2 张卡片归入其他类</span>' },
      { time: '2026-09-26 10:29:11', user: '系统管理员·admin', ip: '10.12.3.45', type: '编辑', obj: '首页顶部指标卡片：月活跃用户', diff: '<span class="old">- 数据来源：自动同步</span>\n<span class="new">+ 数据来源：手动录入，数值 2347</span>' }
    ]
  };

  function currentAdmin() {
    if (window.Auth && typeof window.Auth.currentUser === 'function') {
      var u = window.Auth.currentUser();
      if (u) return { name: u.name || u.username, ip: '10.12.3.45' };
    }
    return { name: '系统管理员·admin', ip: '10.12.3.45' };
  }
  function addLog(type, obj, diff) {
    var who = currentAdmin();
    DRAFT.logs.unshift({
      time: new Date().toISOString().slice(0, 19).replace('T', ' '),
      user: who.name,
      ip: who.ip,
      type: type,
      obj: obj,
      diff: diff || ''
    });
    state.ability.dirty = true;
  }

  /* ---------- 弹窗工具 ---------- */
  var dialogEl = null;
  function closeDialog() {
    if (dialogEl) { dialogEl.remove(); dialogEl = null; }
  }
  function openDialog(html, size) {
    closeDialog();
    dialogEl = document.createElement('div');
    dialogEl.className = 'dialog-mask';
    dialogEl.innerHTML = '<div class="dialog' + (size ? ' ' + size : '') + '">' + html + '</div>';
    dialogEl.addEventListener('click', function (e) { if (e.target === dialogEl) closeDialog(); });
    document.body.appendChild(dialogEl);
    $$('[data-icon]', dialogEl).forEach(function (el) { el.innerHTML = icon(el.dataset.icon, +(el.dataset.size || 16)); });
    var closeBtn = dialogEl.querySelector('.dialog-close');
    if (closeBtn) closeBtn.addEventListener('click', closeDialog);
    return dialogEl;
  }

  /* ---------- 通用：确认弹窗 ---------- */
  function confirmDialog(title, msg, onOk) {
    var d = openDialog(
      '<div class="dialog-head">' +
        '<h3><span data-icon="alert-circle" data-size="16"></span>' + esc(title) + '</h3>' +
        '<button class="dialog-close" aria-label="关闭">&times;</button>' +
      '</div>' +
      '<div class="dialog-body" style="padding:22px 24px;font-size:13.5px;color:var(--text-sub);">' + msg + '</div>' +
      '<div class="dialog-foot">' +
        '<button class="btn-secondary" data-act="cancel">取消</button>' +
        '<button class="btn-primary" data-act="ok">' + icon('check', 14) + '确定</button>' +
      '</div>', 'small'
    );
    d.querySelector('[data-act="cancel"]').onclick = closeDialog;
    d.querySelector('[data-act="ok"]').onclick = function () { closeDialog(); onOk && onOk(); };
  }

  /* ---------- 主渲染入口 ---------- */
  function renderAbility() {
    var wrap = $('#abilityBody');
    /* 顶栏操作区：发布/预览/版本管理 */
    var pb =
      '<div class="publish-bar">' +
        '<div class="pb-left">' +
          '<span class="pb-status"><span class="dot"></span>当前线上版本 <b>' + esc(state.ability.published) + '</b></span>' +
          '<span class="pb-info">' + (state.ability.dirty
              ? '<b style="color:var(--gold)">存在未发布修改</b> · 已编辑 ' + DRAFT.logs.filter(function(l){ return l.time.startsWith('2026'); }).length + ' 项配置'
              : '草稿与线上版本一致，无待发布内容') +
          '</span>' +
        '</div>' +
        '<div class="pb-right">' +
          '<button class="btn-secondary" data-pb="preview"><span data-icon="eye" data-size="14"></span>预览</button>' +
          '<button class="btn-secondary" data-pb="versions"><span data-icon="clock" data-size="14"></span>版本管理</button>' +
          '<button class="btn-primary" data-pb="publish"><span data-icon="upload-cloud" data-size="14"></span>审核发布</button>' +
        '</div>' +
      '</div>';

    var modules = [
      { id: 'meta',       name: '基础文案配置',     ic: 'type' },
      { id: 'kpis',       name: '指标卡片配置',     ic: 'bar-chart-2' },
      { id: 'groups',     name: '应用分组配置',     ic: 'folder-tree' },
      { id: 'apps',       name: '应用卡片管理',     ic: 'layout-grid' },
      { id: 'versions',   name: '版本快照管理',     ic: 'git-branch' },
      { id: 'logs',       name: '全量操作日志',     ic: 'clipboard-list' }
    ];

    var html =
      pb +
      '<div class="ability-layout">' +
        '<aside class="ability-sidemenu">' +
          '<h4>配置导航</h4>' +
          '<nav class="sm-list">' +
            modules.map(function (m) {
              return '<div class="sm-item' + (state.ability.module === m.id ? ' active' : '') + '" data-mod="' + m.id + '">' +
                icon(m.ic, 16) + esc(m.name) + '</div>';
            }).join('') +
          '</nav>' +
        '</aside>' +
        '<div class="ability-content" id="abilityContent"></div>' +
      '</div>';
    wrap.innerHTML = html;
    $$('[data-icon]', wrap).forEach(function (el) { el.innerHTML = icon(el.dataset.icon, +(el.dataset.size || 16)); });

    /* 绑定左侧菜单 */
    $$('.sm-item', wrap).forEach(function (it) {
      it.onclick = function () { state.ability.module = it.dataset.mod; renderAbility(); };
    });
    /* 绑定发布条按钮 */
    wrap.querySelector('[data-pb="preview"]').onclick = function () {
      addLog('预览', '草稿配置', '预览沙箱地址已打开');
      window.open('#apps', '_blank', 'noopener');
      toast('预览沙箱已在新页签打开');
      renderAbilityContent();
    };
    wrap.querySelector('[data-pb="versions"]').onclick = function () {
      state.ability.module = 'versions'; renderAbility();
    };
    wrap.querySelector('[data-pb="publish"]').onclick = onPublish;

    renderAbilityContent();
  }

  function renderAbilityContent() {
    var box = $('#abilityContent');
    if (!box) return;
    var mod = state.ability.module;
    if (mod === 'meta') renderMetaConfig(box);
    else if (mod === 'kpis') renderKpiConfig(box);
    else if (mod === 'groups') renderGroupsConfig(box);
    else if (mod === 'apps') renderAppsConfig(box);
    else if (mod === 'versions') renderVersions(box);
    else if (mod === 'logs') renderLogs(box);
    else { state.ability.module = 'meta'; return renderAbilityContent(); }
    $$('[data-icon]', box).forEach(function (el) { el.innerHTML = icon(el.dataset.icon, +(el.dataset.size || 16)); });
  }

  /* ======== 3.1 基础文案配置 ======== */
  function renderMetaConfig(box) {
    var m = DRAFT.meta;
    box.innerHTML =
      '<div class="ability-card">' +
        '<div class="ability-card-head">' +
          '<div><h3><span data-icon="type" data-size="16"></span>首页基础文案配置</h3>' +
          '<p>配置首页顶部展示的平台基础文案，修改后请「保存」并发布生效</p></div>' +
          '<div class="ability-ops"><button class="btn-primary" data-save="meta"><span data-icon="save" data-size="14"></span>保存配置</button></div>' +
        '</div>' +
        '<div class="form-grid">' +
          '<div class="fg-item full">' +
            '<label>平台主标题 <span class="req">*</span></label>' +
            '<input type="text" id="f_meta_platform" maxlength="30" value="' + esc(m.platform) + '">' +
            '<span class="fg-hint">必填，最大 30 字，前台顶部品牌名称展示</span>' +
          '</div>' +
          '<div class="fg-item full">' +
            '<label>平台Slogan副标题</label>' +
            '<input type="text" id="f_meta_slogan" maxlength="50" value="' + esc(m.slogan) + '">' +
            '<span class="fg-hint">非必填，最大 50 字，示例：让每一个岗位，一键唤起 AI 生产力</span>' +
          '</div>' +
          '<div class="fg-item full">' +
            '<label>平台简介文案</label>' +
            '<textarea id="f_meta_org" maxlength="200">' + esc(m.org + '打造 · 全司 AI 应用统一门户') + '</textarea>' +
            '<span class="fg-hint">非必填，最大 200 字，展示于标题下方，支持换行排版</span>' +
          '</div>' +
          '<div class="fg-item">' +
            '<label>页面底部版权文案</label>' +
            '<input type="text" id="f_meta_copy" value="© 2026 首信技术中台 · AI智擎提效平台">' +
          '</div>' +
          '<div class="fg-item">' +
            '<label>备注提示文案开关</label>' +
            '<label class="switch" style="margin-top:2px"><input type="checkbox" id="f_meta_noteOn" checked><span class="slider"></span></label>' +
            '<span class="fg-hint">关闭后前台页脚不再展示环境备注</span>' +
          '</div>' +
          '<div class="fg-item full">' +
            '<label>页面备注提示文案</label>' +
            '<input type="text" id="f_meta_note" value="演示原型 · 应用清单与统计数据为模拟内容，点击「进入使用」会累计演示计数">' +
          '</div>' +
        '</div>' +
      '</div>';

    box.querySelector('[data-save="meta"]').onclick = function () {
      var v1 = $('#f_meta_platform').value.trim();
      if (!v1) { toast('平台标题不能为空'); return; }
      var oldName = m.platform;
      var changed = [];
      if (oldName !== v1) changed.push('主标题: "' + esc(oldName) + '" → "' + esc(v1) + '"');
      m.platform = v1;
      var v2 = $('#f_meta_slogan').value.trim();
      if (m.slogan !== v2) changed.push('Slogan 已更新');
      m.slogan = v2;
      m.copyright = $('#f_meta_copy').value.trim();
      m.noteOn = $('#f_meta_noteOn').checked;
      m.note = $('#f_meta_note').value.trim();
      addLog('编辑', '首页基础文案', changed.length ? changed.join('\n') : '文案配置已保存');
      toast('已保存文案配置草稿，发布后生效');
      renderAbility();
    };
  }

  /* ======== 3.2 指标卡片配置 ======== */
  function renderKpiConfig(box) {
    var sorted = DRAFT.heroKpis.slice().sort(function (a, b) { return a.order - b.order; });
    var cards = sorted.map(function (k, idx) {
      return '<div class="kpi-config-card" data-kid="' + k.id + '">' +
        '<div class="kcc-head">' +
          '<b><span class="drag-handle">' + icon('grip-vertical', 16) + '</span>指标卡片 ' + (idx + 1) + ' · ' + esc(k.name) + '</b>' +
          '<label class="switch" title="显隐"><input type="checkbox" data-kvis ' + (k.visible ? 'checked' : '') + '><span class="slider"></span></label>' +
        '</div>' +
        '<div class="kcc-body">' +
          '<div class="inline-field full"><label>指标名称</label><input data-k="name" value="' + esc(k.name) + '"></div>' +
          '<div class="inline-field"><label>数据来源</label>' +
            '<select data-k="source"><option value="auto"' + (k.source === 'auto' ? ' selected' : '') + '>自动同步（统计洞察）</option>' +
            '<option value="manual"' + (k.source === 'manual' ? ' selected' : '') + '>手动录入</option></select>' +
          '</div>' +
          '<div class="inline-field"><label>手动数值（正整数）</label>' +
            '<input data-k="value" type="number" min="0" value="' + k.value + '"' + (k.source === 'auto' ? ' disabled' : '') + '>' +
          '</div>' +
        '</div>' +
      '</div>';
    }).join('');

    box.innerHTML =
      '<div class="ability-card">' +
        '<div class="ability-card-head">' +
          '<div><h3><span data-icon="bar-chart-2" data-size="16"></span>首页顶部指标卡片配置</h3>' +
          '<p>支持自定义 4 个核心数据指标，可选择自动同步或手动录入，卡片顺序支持拖拽调整</p></div>' +
          '<div class="ability-ops"><button class="btn-primary" data-save="kpis"><span data-icon="save" data-size="14"></span>保存配置</button></div>' +
        '</div>' +
        '<div class="kpi-config-list" id="kpiList">' + cards + '</div>' +
      '</div>';

    /* 切换来源联动：自动同步时手动数值置灰 */
    $$('[data-k="source"]', box).forEach(function (sel) {
      sel.onchange = function () {
        var card = sel.closest('.kpi-config-card');
        var vInput = card.querySelector('[data-k="value"]');
        vInput.disabled = sel.value === 'auto';
      };
    });

    /* 显隐校验：至少保留 1 张显示 */
    $$('[data-kvis]', box).forEach(function (sw) {
      sw.onchange = function () {
        var visibleCount = $$('[data-kvis]', box).filter(function (s) { return s.checked; }).length;
        if (visibleCount < 1) {
          sw.checked = true;
          toast('至少保留 1 个指标卡片为显示状态');
        }
      };
    });

    box.querySelector('[data-save="kpis"]').onclick = function () {
      $$('.kpi-config-card', box).forEach(function (c) {
        var id = c.dataset.kid;
        var k = DRAFT.heroKpis.find(function (x) { return x.id === id; });
        if (!k) return;
        k.name = c.querySelector('[data-k="name"]').value.trim() || k.name;
        k.source = c.querySelector('[data-k="source"]').value;
        k.value = k.source === 'manual' ? Math.max(0, parseInt(c.querySelector('[data-k="value"]').value) || 0) : 0;
        k.visible = c.querySelector('[data-kvis]').checked;
      });
      addLog('编辑', '首页顶部指标卡片配置', '4 张指标卡片配置已更新');
      toast('已保存指标卡片配置，发布后生效');
      renderAbility();
    };
  }

  /* ======== 3.3 一级分类Tab ======== */
  function renderPortalTabsConfig(box) {
    var sorted = DRAFT.portals.slice().sort(function (a, b) { return a.order - b.order; });
    var rows = sorted.map(function (p, i) {
      return '<tr>' +
        '<td style="width:50px"><span class="drag-handle">' + icon('grip-vertical', 16) + '</span></td>' +
        '<td style="width:60px">' + (i + 1) + '</td>' +
        '<td><span class="cell-name"><span class="mini-ic" style="color:' + p.accent + ';background:' + hexA(p.accent, 0.12) + ';border-color:' + hexA(p.accent, 0.3) + '">' + icon(p.icon, 15) + '</span>' + esc(p.name) + '</span></td>' +
        '<td>' + esc(p.desc || '—') + '</td>' +
        '<td>' + (p.hasBoard ? '<span class="cell-badge primary">已开启</span>' : '<span class="cell-badge dim">未开启</span>') + '</td>' +
        '<td style="width:80px"><label class="switch"><input type="checkbox" data-vis ' + (p.visible ? 'checked' : '') + '><span class="slider"></span></label></td>' +
        '<td style="width:170px">' +
          '<div class="row-ops">' +
            '<button class="row-op" data-edit="' + p.id + '">' + icon('edit-3', 12) + '编辑</button>' +
            '<button class="row-op danger" data-del="' + p.id + '">' + icon('trash-2', 12) + '删除</button>' +
          '</div>' +
        '</td>' +
      '</tr>';
    }).join('');

    box.innerHTML =
      '<div class="ability-card">' +
        '<div class="ability-card-head">' +
          '<div><h3><span data-icon="layout" data-size="16"></span>一级分类Tab配置</h3>' +
          '<p>管理首页顶级分类：业务前端、技术中台、管理后台，拖拽排序，显隐开关控制前台</p></div>' +
          '<div class="ability-ops"><button class="btn-primary" data-new="portal"><span data-icon="plus" data-size="14"></span>新增一级分类</button></div>' +
        '</div>' +
        '<table class="mgmt-table"><thead><tr>' +
          '<th style="width:50px"></th><th style="width:60px">序号</th><th>分类名称</th><th>分类简介</th><th>二级子标签</th><th style="width:80px">前台显示</th><th style="width:170px">操作</th>' +
        '</tr></thead><tbody>' + (rows || emptyTr(7)) + '</tbody></table>' +
      '</div>';

    /* 显隐校验 */
    $$('[data-vis]', box).forEach(function (sw) {
      sw.onchange = function () {
        var vis = $$('[data-vis]', box).filter(function (s) { return s.checked; }).length;
        if (vis < 1) { sw.checked = true; toast('至少保留 1 个一级分类为显示状态'); }
      };
    });
    box.querySelector('[data-new="portal"]').onclick = function () { editPortalDialog(null); };
    $$('[data-edit]', box).forEach(function (b) { b.onclick = function () { editPortalDialog(b.dataset.edit); }; });
    $$('[data-del]', box).forEach(function (b) { b.onclick = function () {
      var id = b.dataset.del;
      if (DRAFT.portals.length <= 1) { toast('至少保留 1 个一级分类'); return; }
      confirmDialog('删除一级分类', '确认删除此分类？删除后相关应用归属需重新分配。', function () {
        var name = DRAFT.portals.find(function (x) { return x.id === id; }).name;
        DRAFT.portals = DRAFT.portals.filter(function (x) { return x.id !== id; });
        /* 关联应用自动归入"未分类门户"兜底（此处默认 tech） */
        DRAFT.apps.forEach(function (a) { if (a.portal === id) a.portal = 'tech'; });
        addLog('删除', '一级分类Tab：' + name, '<span class="old">- 分类 "' + esc(name) + '" 已删除</span>');
        toast('已删除分类：' + name);
        renderAbilityContent();
      });
    }; });
  }

  function editPortalDialog(id) {
    var isNew = !id;
    var p = isNew ? { id: 'p' + Date.now(), name: '', desc: '', hasBoard: false, visible: true, order: DRAFT.portals.length + 1, icon: 'briefcase', accent: '#5B8AF5' }
                   : DRAFT.portals.find(function (x) { return x.id === id; });
    var d = openDialog(
      '<div class="dialog-head">' +
        '<h3><span data-icon="' + (isNew ? 'plus' : 'edit-3') + '" data-size="16"></span>' + (isNew ? '新增一级分类' : '编辑一级分类') + '</h3>' +
        '<button class="dialog-close">&times;</button>' +
      '</div>' +
      '<div class="dialog-body">' +
        '<div class="form-grid">' +
          '<div class="fg-item"><label>分类名称 <span class="req">*</span></label><input id="f_p_name" maxlength="10" value="' + esc(p.name) + '"><span class="fg-hint">必填，最大 10 字</span></div>' +
          '<div class="fg-item"><label>分类图标</label><div class="icon-picker" id="iconPicker">' + iconPickerHTML(p.icon) + '</div></div>' +
          '<div class="fg-item full"><label>分类简介</label><textarea id="f_p_desc" maxlength="100">' + esc(p.desc) + '</textarea></div>' +
          '<div class="fg-item"><label>功能色</label><div class="color-row" id="colorRow">' + colorRowHTML(p.accent) + '</div></div>' +
          '<div class="fg-item"><label>开启二级子标签</label><label class="switch" style="margin-top:2px"><input type="checkbox" id="f_p_board" ' + (p.hasBoard ? 'checked' : '') + '><span class="slider"></span></label><span class="fg-hint">开启后前台该分类下展示二级Tab栏</span></div>' +
        '</div>' +
      '</div>' +
      '<div class="dialog-foot"><button class="btn-secondary" data-act="cancel">取消</button><button class="btn-primary" data-act="ok">' + icon('check', 14) + '保存</button></div>'
    );
    var chosenIcon = p.icon, chosenAccent = p.accent;
    $$('.ip-item', d).forEach(function (it) { it.onclick = function () { chosenIcon = it.dataset.ic; $$('.ip-item', d).forEach(function (x) { x.classList.toggle('active', x === it); }); }; });
    $$('.color-dot', d).forEach(function (dt) { dt.onclick = function () { chosenAccent = dt.dataset.color; $$('.color-dot', d).forEach(function (x) { x.classList.toggle('active', x === dt); }); }; });
    d.querySelector('[data-act="cancel"]').onclick = closeDialog;
    d.querySelector('[data-act="ok"]').onclick = function () {
      var name = $('#f_p_name').value.trim();
      if (!name) { toast('分类名称不能为空'); return; }
      var dup = DRAFT.portals.find(function (x) { return x.name === name && x.id !== id; });
      if (dup) { toast('分类名称已存在'); return; }
      p.name = name; p.desc = $('#f_p_desc').value.trim(); p.icon = chosenIcon; p.accent = chosenAccent;
      p.hasBoard = $('#f_p_board').checked;
      if (isNew) { DRAFT.portals.push(p); addLog('新增', '一级分类Tab：' + name, '<span class="new">+ 新增分类 "' + esc(name) + '"</span>'); }
      else addLog('编辑', '一级分类Tab：' + name, '分类配置已更新');
      closeDialog(); toast('已保存');
      renderAbilityContent();
    };
  }

  /* ======== 3.4 二级子标签 ======== */
  function renderBoardTabsConfig(box) {
    var sorted = DRAFT.boards.slice().sort(function (a, b) { return a.order - b.order; });
    var rows = sorted.map(function (b, i) {
      var portal = DRAFT.portals.find(function (p) { return p.id === (b.portal || 'tech'); }) || {};
      return '<tr>' +
        '<td style="width:50px"><span class="drag-handle">' + icon('grip-vertical', 16) + '</span></td>' +
        '<td style="width:60px">' + (i + 1) + '</td>' +
        '<td><span class="cell-name">' + esc(b.name) + '</span><span style="font-size:11px;color:var(--text-dim);margin-left:8px">简称：' + esc(b.short) + '</span></td>' +
        '<td><span class="cell-badge primary">' + esc(portal.name || '—') + '</span></td>' +
        '<td>' + esc(b.desc || '—') + '</td>' +
        '<td>' + DRAFT.apps.filter(function (a) { var c = DRAFT.categories.find(function (x) { return x.id === a.category; }); return c && c.board === b.id; }).length + ' 张卡片</td>' +
        '<td style="width:80px"><label class="switch"><input type="checkbox" data-vis ' + (b.visible ? 'checked' : '') + '><span class="slider"></span></label></td>' +
        '<td style="width:170px">' +
          '<div class="row-ops">' +
            '<button class="row-op" data-edit="' + b.id + '">' + icon('edit-3', 12) + '编辑</button>' +
            '<button class="row-op danger" data-del="' + b.id + '">' + icon('trash-2', 12) + '删除</button>' +
          '</div>' +
        '</td>' +
      '</tr>';
    }).join('');

    box.innerHTML =
      '<div class="ability-card">' +
        '<div class="ability-card-head">' +
          '<div><h3><span data-icon="layers" data-size="16"></span>二级子标签配置</h3>' +
          '<p>针对一级分类下开启二级标签场景（如技术中台），管理二级导航标签</p></div>' +
          '<div class="ability-ops"><button class="btn-primary" data-new="board"><span data-icon="plus" data-size="14"></span>新增二级标签</button></div>' +
        '</div>' +
        '<table class="mgmt-table"><thead><tr>' +
          '<th style="width:50px"></th><th style="width:60px">序号</th><th>子标签名称</th><th style="width:140px">所属一级标签</th><th>子标签描述</th><th>关联卡片数</th><th style="width:80px">前台显示</th><th style="width:170px">操作</th>' +
        '</tr></thead><tbody>' + (rows || emptyTr(8)) + '</tbody></table>' +
      '</div>';

    box.querySelector('[data-new="board"]').onclick = function () { editBoardDialog(null); };
    $$('[data-edit]', box).forEach(function (btn) { btn.onclick = function () { editBoardDialog(btn.dataset.edit); }; });
    $$('[data-del]', box).forEach(function (btn) { btn.onclick = function () {
      var id = btn.dataset.del;
      if (DRAFT.boards.length <= 1) { toast('至少保留 1 个二级标签'); return; }
      confirmDialog('删除二级标签', '确认删除此二级标签？关联分类下应用将重新分配。', function () {
        var name = DRAFT.boards.find(function (x) { return x.id === id; }).name;
        DRAFT.boards = DRAFT.boards.filter(function (x) { return x.id !== id; });
        /* 对应分类归到第一个 board */
        var def = DRAFT.boards[0] && DRAFT.boards[0].id;
        DRAFT.categories.forEach(function (c) { if (c.board === id) c.board = def; });
        addLog('删除', '二级子标签：' + name, '<span class="old">- 二级标签 "' + esc(name) + '" 已删除</span>');
        toast('已删除二级标签：' + name);
        renderAbilityContent();
      });
    }; });
  }

  function editBoardDialog(id) {
    var isNew = !id;
    var b = isNew ? { id: 'b' + Date.now(), name: '', short: '', desc: '', visible: true, order: DRAFT.boards.length + 1, portal: 'tech' }
                   : DRAFT.boards.find(function (x) { return x.id === id; });
    var portalOpts = DRAFT.portals.map(function (p) {
      return '<option value="' + p.id + '"' + ((b.portal || 'tech') === p.id ? ' selected' : '') + '>' + esc(p.name) + '</option>';
    }).join('');
    var d = openDialog(
      '<div class="dialog-head"><h3><span data-icon="' + (isNew ? 'plus' : 'edit-3') + '" data-size="16"></span>' + (isNew ? '新增二级标签' : '编辑二级标签') + '</h3><button class="dialog-close">&times;</button></div>' +
      '<div class="dialog-body"><div class="form-grid">' +
        '<div class="fg-item"><label>子标签名称 <span class="req">*</span></label><input id="f_b_name" maxlength="12" value="' + esc(b.name) + '"><span class="fg-hint">必填，最大 12 字</span></div>' +
        '<div class="fg-item"><label>所属一级标签</label><select id="f_b_portal">' + portalOpts + '</select></div>' +
        '<div class="fg-item"><label>子标签简称</label><input id="f_b_short" maxlength="8" value="' + esc(b.short) + '"></div>' +
        '<div class="fg-item full"><label>子标签描述</label><textarea id="f_b_desc" maxlength="120">' + esc(b.desc) + '</textarea></div>' +
      '</div></div>' +
      '<div class="dialog-foot"><button class="btn-secondary" data-act="cancel">取消</button><button class="btn-primary" data-act="ok">' + icon('check', 14) + '保存</button></div>'
    );
    d.querySelector('[data-act="cancel"]').onclick = closeDialog;
    d.querySelector('[data-act="ok"]').onclick = function () {
      var n = $('#f_b_name').value.trim();
      if (!n) { toast('子标签名称不能为空'); return; }
      b.name = n;
      b.portal = $('#f_b_portal').value;
      b.short = $('#f_b_short').value.trim();
      b.desc = $('#f_b_desc').value.trim();
      var portalName = (DRAFT.portals.find(function (p) { return p.id === b.portal; }) || {}).name || '';
      if (isNew) { DRAFT.boards.push(b); addLog('新增', '二级子标签：' + n, '<span class="new">+ 新增二级标签 "' + esc(n) + '"，所属一级标签：' + esc(portalName) + '</span>'); }
      else addLog('编辑', '二级子标签：' + n, '标签配置已更新，所属一级标签：' + esc(portalName));
      closeDialog(); toast('已保存'); renderAbilityContent();
    };
  }

  /* ======== 3.5 应用分组配置（即分类 categories） ======== */
  function renderGroupsConfig(box) {
    var sorted = DRAFT.categories.slice().sort(function (a, b) { return a.order - b.order; });
    var rows = sorted.map(function (g, i) {
      var cnt = DRAFT.apps.filter(function (a) { return a.category === g.id; }).length;
      return '<tr>' +
        '<td style="width:50px"><span class="drag-handle">' + icon('grip-vertical', 16) + '</span></td>' +
        '<td style="width:60px">' + (i + 1) + '</td>' +
        '<td><span class="cell-name"><span class="mini-ic" style="color:' + g.accent + ';background:' + hexA(g.accent, 0.12) + ';border-color:' + hexA(g.accent, 0.3) + '">' + icon(g.icon || 'folder', 15) + '</span>' + esc(g.name) + '</span></td>' +
        '<td>' + esc(g.desc || '—') + '</td>' +
        '<td style="width:100px">' + cnt + ' 张</td>' +
        '<td style="width:80px"><label class="switch"><input type="checkbox" data-vis ' + (g.visible ? 'checked' : '') + '><span class="slider"></span></label></td>' +
        '<td style="width:170px">' +
          '<div class="row-ops">' +
            '<button class="row-op" data-edit="' + g.id + '">' + icon('edit-3', 12) + '编辑</button>' +
            '<button class="row-op danger" data-del="' + g.id + '">' + icon('trash-2', 12) + '删除</button>' +
          '</div>' +
        '</td>' +
      '</tr>';
    }).join('');

    box.innerHTML =
      '<div class="ability-card">' +
        '<div class="ability-card-head">' +
          '<div><h3><span data-icon="folder-tree" data-size="16"></span>应用分组配置</h3>' +
          '<p>对应用卡片进行分组管理：新增 / 编辑 / 删除分组；删除分组后下属卡片归入「其他类」</p></div>' +
          '<div class="ability-ops"><button class="btn-primary" data-new="group"><span data-icon="plus" data-size="14"></span>新增分组</button></div>' +
        '</div>' +
        '<table class="mgmt-table"><thead><tr>' +
          '<th style="width:50px"></th><th style="width:60px">序号</th><th>分组标题</th><th>分组描述</th><th style="width:100px">卡片数</th><th style="width:80px">前台显示</th><th style="width:170px">操作</th>' +
        '</tr></thead><tbody>' + (rows || emptyTr(7)) + '</tbody></table>' +
      '</div>';

    box.querySelector('[data-new="group"]').onclick = function () { editGroupDialog(null); };
    $$('[data-edit]', box).forEach(function (btn) { btn.onclick = function () { editGroupDialog(btn.dataset.edit); }; });
    $$('[data-del]', box).forEach(function (btn) { btn.onclick = function () {
      var id = btn.dataset.del;
      confirmDialog('删除应用分组', '删除分组后，下属所有应用卡片自动归为其他类状态，可重新分配。', function () {
        var name = DRAFT.categories.find(function (x) { return x.id === id; }).name;
        DRAFT.categories = DRAFT.categories.filter(function (x) { return x.id !== id; });
        DRAFT.apps.forEach(function (a) { if (a.category === id) a.category = null; });
        addLog('删除', '应用分组：' + name, '<span class="old">- 分组"' + esc(name) + '"已删除，下属卡片归入其他类</span>');
        toast('已删除分组：' + name); renderAbilityContent();
      });
    }; });
  }

  function editGroupDialog(id) {
    var isNew = !id;
    var g = isNew ? { id: 'g' + Date.now(), name: '', desc: '', accent: '#2CA55F', icon: 'folder', visible: true, order: DRAFT.categories.length + 1 }
                   : DRAFT.categories.find(function (x) { return x.id === id; });
    var d = openDialog(
      '<div class="dialog-head"><h3><span data-icon="' + (isNew ? 'plus' : 'edit-3') + '" data-size="16"></span>' + (isNew ? '新增应用分组' : '编辑应用分组') + '</h3><button class="dialog-close">&times;</button></div>' +
      '<div class="dialog-body"><div class="form-grid">' +
        '<div class="fg-item full"><label>分组标题 <span class="req">*</span></label><input id="f_g_name" maxlength="15" value="' + esc(g.name || '') + '"><span class="fg-hint">必填，最大 15 字</span></div>' +
        '<div class="fg-item full"><label>分组图标</label><div class="icon-picker" id="ip_group">' +
          ['folder','folder-tree','briefcase','book-open','library','users','pie-chart','calculator','activity','settings','flask','code-2'].map(function(ic){ return '<div class="ip-item'+((g.icon===ic)?' active':'')+'" data-ic="'+ic+'">'+icon(ic,18)+'</div>';}).join('') +
        '</div></div>' +
        '<div class="fg-item full"><label>分组描述</label><textarea id="f_g_desc" maxlength="100">' + esc(g.desc || '') + '</textarea></div>' +
        '<div class="fg-item full"><label>分组功能色</label><div class="color-row" id="colorRowG">' + colorRowHTML(g.accent || '#2CA55F') + '</div></div>' +
      '</div></div>' +
      '<div class="dialog-foot"><button class="btn-secondary" data-act="cancel">取消</button><button class="btn-primary" data-act="ok">' + icon('check', 14) + '保存</button></div>'
    );
    var chosenColor = g.accent || '#2CA55F';
    var chosenIcon = g.icon || 'folder';
    $$('.ip-item', d).forEach(function (it) { it.onclick = function () { chosenIcon = it.dataset.ic; $$('.ip-item', d).forEach(function (x) { x.classList.toggle('active', x === it); }); }; });
    $$('.color-dot', d).forEach(function (dt) { dt.onclick = function () { chosenColor = dt.dataset.color; $$('.color-dot', d).forEach(function (x) { x.classList.toggle('active', x === dt); }); }; });
    d.querySelector('[data-act="cancel"]').onclick = closeDialog;
    d.querySelector('[data-act="ok"]').onclick = function () {
      var n = $('#f_g_name').value.trim();
      if (!n) { toast('分组标题不能为空'); return; }
      g.name = n; g.desc = $('#f_g_desc').value.trim(); g.accent = chosenColor; g.icon = chosenIcon;
      if (isNew) { DRAFT.categories.push(g); addLog('新增', '应用分组：' + n, '<span class="new">+ 新增分组 "' + esc(n) + '"，功能色 ' + chosenColor + '</span>'); }
      else addLog('编辑', '应用分组：' + n, '分组配置已更新（图标/功能色/描述已同步）');
      closeDialog(); toast('已保存'); renderAbilityContent();
    };
  }

  /* ======== 3.6 应用卡片管理（核心） ======== */
  function renderAppsConfig(box) {
    var q = state.ability.q.apps;
    /* 查询表单（去掉所属门户，只保留分类/状态） */
    var catOpts = '<option value="">全部分类</option>' + DRAFT.categories.map(function (c) { return '<option value="' + c.id + '"' + (q.category === c.id ? ' selected' : '') + '>' + esc(c.name) + '</option>'; }).join('');
    var statusOpts = '<option value="">全部状态</option><option value="online"' + (q.status === 'online' ? ' selected' : '') + '>已上线</option><option value="soon"' + (q.status === 'soon' ? ' selected' : '') + '>待上线</option>';

    var search =
      '<div class="query-form">' +
        '<div class="qf-item"><label>应用名称</label><input id="q_app_name" placeholder="按名称关键词" value="' + esc(q.name) + '"></div>' +
        '<div class="qf-item"><label>所属分类</label><select id="q_app_cat">' + catOpts + '</select></div>' +
        '<div class="qf-item"><label>应用状态</label><select id="q_app_status">' + statusOpts + '</select></div>' +
        '<div class="qf-ops">' +
          '<button class="btn-secondary" id="q_app_reset"><span data-icon="rotate-ccw" data-size="13"></span>重置</button>' +
          '<button class="btn-primary" id="q_app_go"><span data-icon="search" data-size="13"></span>查询</button>' +
        '</div>' +
      '</div>';

    var list = DRAFT.apps.filter(function (a) {
      if (q.name && a.name.indexOf(q.name) < 0) return false;
      if (q.category && a.category !== q.category) return false;
      if (q.status === 'online' && a.soon) return false;
      if (q.status === 'soon' && !a.soon) return false;
      return true;
    }).sort(function (a, b) { return a.order - b.order; });

    var rows = list.map(function (a, i) {
      var cat = DRAFT.categories.find(function (c) { return c.id === a.category; });
      var catAccent = cat ? cat.accent : '#8492A8';
      return '<tr data-aid="' + a.id + '">' +
        '<td style="width:44px"><span class="drag-handle">' + icon('grip-vertical', 16) + '</span></td>' +
        '<td style="width:54px">' + (i + 1) + '</td>' +
        '<td><span class="cell-name"><span class="mini-ic" style="color:' + catAccent + ';background:' + hexA(catAccent, 0.12) + ';border-color:' + hexA(catAccent, 0.3) + '">' + icon(a.icon, 15) + '</span>' + esc(a.name) +
          (a.soon ? '<span class="cell-badge warning" style="margin-left:8px">待上线</span>' : '<span class="cell-badge success" style="margin-left:8px">已上线</span>') +
        '</span></td>' +
        '<td>' + (a.version ? '<span class="mono cell-badge dim">' + esc(a.version) + '</span>' : '<span style="color:var(--text-dim)">—</span>') + '</td>' +
        '<td>' + esc(cat ? cat.name : '其他类') + '</td>' +
        '<td class="num mono">' + fmt(a.usageTotal || 0) + '</td>' +
        '<td>' + (a.visible ? '<span class="cell-badge success">显示</span>' : '<span class="cell-badge dim">隐藏</span>') + '</td>' +
        '<td style="width:230px">' +
          '<div class="row-ops">' +
            '<button class="row-op" data-view="' + a.id + '">' + icon('eye', 12) + '详情</button>' +
            '<button class="row-op" data-edit="' + a.id + '">' + icon('edit-3', 12) + '编辑</button>' +
            '<button class="row-op danger" data-del="' + a.id + '">' + icon('trash-2', 12) + '删除</button>' +
          '</div>' +
        '</td>' +
      '</tr>';
    }).join('');

    box.innerHTML =
      '<div class="ability-card">' +
        '<div class="ability-card-head">' +
          '<div><h3><span data-icon="layout-grid" data-size="16"></span>应用卡片精细化配置（核心模块）</h3>' +
          '<p>支持对每一张应用卡片进行独立配置：基础信息、数据展示、上线状态、排序与显隐，数据口径与统计洞察自动同步</p></div>' +
          '<div class="ability-ops"><button class="btn-primary" data-new="app"><span data-icon="plus" data-size="14"></span>新增应用卡片</button></div>' +
        '</div>' +
        search +
        '<table class="mgmt-table"><thead><tr>' +
          '<th style="width:44px"></th><th style="width:54px">序号</th><th>应用名称</th><th style="width:140px">版本号</th><th style="width:120px">所属分类</th><th style="width:110px">累计使用</th><th style="width:90px">显隐</th><th style="width:230px">操作</th>' +
        '</tr></thead><tbody>' + (rows || emptyTr(8)) + '</tbody></table>' +
      '</div>';

    /* 查询 */
    $('#q_app_go', box).onclick = function () {
      state.ability.q.apps = {
        name: $('#q_app_name', box).value.trim(),
        category: $('#q_app_cat', box).value,
        status: $('#q_app_status', box).value
      };
      renderAbilityContent();
    };
    $('#q_app_reset', box).onclick = function () {
      state.ability.q.apps = { name: '', category: '', status: '' };
      renderAbilityContent();
    };

    box.querySelector('[data-new="app"]').onclick = function () { editAppDialog(null); };
    $$('[data-view]', box).forEach(function (btn) { btn.onclick = function () { viewAppDetail(btn.dataset.view); }; });
    $$('[data-edit]', box).forEach(function (btn) { btn.onclick = function () { editAppDialog(btn.dataset.edit); }; });
    $$('[data-del]', box).forEach(function (btn) { btn.onclick = function () {
      var id = btn.dataset.del;
      confirmDialog('删除应用卡片', '确认删除此应用卡片？该操作不可恢复，请谨慎操作。', function () {
        var name = DRAFT.apps.find(function (x) { return x.id === id; }).name;
        DRAFT.apps = DRAFT.apps.filter(function (x) { return x.id !== id; });
        addLog('删除', '应用卡片：' + name, '<span class="old">- 应用卡片 "' + esc(name) + '" 已删除</span>');
        toast('已删除卡片：' + name); renderAbilityContent();
      });
    }; });
  }

  function viewAppDetail(id) {
    var a = DRAFT.apps.find(function (x) { return x.id === id; });
    if (!a) return;
    var portal = DRAFT.portals.find(function (p) { return p.id === (a.portal || 'tech'); }) || {};
    var cat = DRAFT.categories.find(function (c) { return c.id === a.category; });
    var tagsHTML = '<div class="tags-line">' + (a.tags || []).map(function (t) { return '<span class="cell-badge dim">' + esc(t) + '</span>'; }).join('') + '</div>';
    openDialog(
      '<div class="dialog-head">' +
        '<h3><span data-icon="file-text" data-size="16"></span>应用详情 · ' + esc(a.name) + '</h3>' +
        '<button class="dialog-close">&times;</button>' +
      '</div>' +
      '<div class="dialog-body">' +
        '<div class="desc-list">' +
          dlRow('应用名称', esc(a.name)) +
          dlRow('应用简介', esc(a.desc || '—')) +
          dlRow('应用标签', a.tags && a.tags.length ? tagsHTML : '—') +
          dlRow('版本号', a.version ? '<span class="mono cell-badge dim">' + esc(a.version) + '</span>' : '<span style="color:var(--text-dim)">—</span>') +
          dlRow('发布说明', a.releaseNote ? esc(a.releaseNote) : '<span style="color:var(--text-dim)">—</span>') +
          dlRow('最近更新时间', a.updateTime ? '<span class="mono">' + esc(a.updateTime) + '</span>' : '<span style="color:var(--text-dim)">—</span>') +
          dlRow('版本负责人', a.releaseOwner ? esc(a.releaseOwner) : '<span style="color:var(--text-dim)">—</span>') +
          dlRow('所属分类', esc(cat ? cat.name : '其他类')) +
          dlRow('前台显示', a.visible ? '<span class="cell-badge success">显示</span>' : '<span class="cell-badge dim">隐藏</span>') +
          dlRow('上线状态', a.soon ? '<span class="cell-badge warning">待上线</span>' : '<span class="cell-badge success">已上线</span>') +
          dlRow('累计使用次数', a.soon ? '<span class="mono">—</span>' : '<span class="mono">' + fmt(a.usageTotal || 0) + '</span>') +
          dlRow('月活跃用户', a.soon ? '<span class="mono">—</span>' : '<span class="mono">' + fmt(a.users || 0) + '</span>') +
          dlRow('周环比增长率', a.soon ? '—' : (a.delta >= 0 ? '<span class="delta-pill up">+' + a.delta + '%</span>' : '<span class="delta-pill down">' + a.delta + '%</span>')) +
          dlRow('热度指数', a.soon ? '—' : '<span class="heat-cell"><span class="heat-bar"><i style="width:' + (a.heat || 0) + '%"></i></span><b class="mono">' + (a.heat || 0) + '</b></span>') +
          dlRow('跳转链接', a.embedUrl ? '<span class="mono">' + esc(a.embedUrl) + '</span>' : '<span style="color:var(--text-dim)">未配置（将展示预览占位）</span>') +
          dlRow('待上线说明', a.soon ? (a.soonHint || '规划建设中，即将上线') : '<span class="mono">—</span>') +
        '</div>' +
      '</div>' +
      '<div class="dialog-foot"><button class="btn-primary" data-close>' + icon('x', 14) + '关闭</button></div>',
      'large'
    ).querySelector('[data-close]').onclick = closeDialog;
  }

  function dlRow(k, v) {
    return '<div class="dl-row"><div class="dl-label">' + k + '</div><div class="dl-value">' + v + '</div></div>';
  }

  function editAppDialog(id) {
    var isNew = !id;
    var nowStr = (function(){ var d=new Date();function p(n){return n<10?'0'+n:n;} return d.getFullYear()+'-'+p(d.getMonth()+1)+'-'+p(d.getDate())+' '+p(d.getHours())+':'+p(d.getMinutes()); })();
    var a = isNew ? {
      id: 'a' + Date.now(), name: '', desc: '', icon: 'app-window', tags: [],
      category: DRAFT.categories[0] && DRAFT.categories[0].id,
      usageTotal: 0, users: 0, delta: 0, heat: 0,
      usageSrc: 'manual', usersSrc: 'manual', heatSrc: 'manual',
      soon: false, soonHint: '规划建设中，即将上线',
      embedUrl: '', visible: true, order: DRAFT.apps.length + 1,
      version: 'v1.0.0', releaseNote: '', updateTime: nowStr,
      releaseOwner: (currentAdmin()||{}).name || ''
    } : JSON.parse(JSON.stringify(DRAFT.apps.find(function (x) { return x.id === id; })));

    var catOpts = '<option value="">其他类</option>' + DRAFT.categories.map(function (c) { return '<option value="' + c.id + '"' + (a.category === c.id ? ' selected' : '') + '>' + esc(c.name) + '</option>'; }).join('');
    var icons = ['app-window','file-text','cpu','bot','flask','library','book-open','tags','share-2','briefcase','pie-chart','calendar','calculator','gauge','factory','activity','clipboard-list'];

    openDialog(
      '<div class="dialog-head"><h3><span data-icon="' + (isNew ? 'plus' : 'edit-3') + '" data-size="16"></span>' + (isNew ? '新增应用卡片' : '编辑应用卡片') + '</h3><button class="dialog-close">&times;</button></div>' +
      '<div class="dialog-body"><div class="form-grid">' +
        /* 基础信息 */
        '<div class="fg-item full"><label>应用名称 <span class="req">*</span></label><input id="f_app_name" maxlength="20" value="' + esc(a.name) + '"><span class="fg-hint">必填，最大 20 字</span></div>' +
        '<div class="fg-item full"><label>应用简介</label><textarea id="f_app_desc" maxlength="80">' + esc(a.desc || '') + '</textarea><span class="fg-hint">非必填，最大 80 字</span></div>' +
        '<div class="fg-item"><label>应用图标</label><div class="icon-picker" id="ip_app">' + icons.map(function (ic) { return '<div class="ip-item' + (a.icon === ic ? ' active' : '') + '" data-ic="' + ic + '">' + icon(ic, 18) + '</div>'; }).join('') + '</div></div>' +
        '<div class="fg-item"><label>应用标签</label><div class="tag-input-wrap" id="tagWrap">' +
          (a.tags || []).map(function (t, i) { return '<span class="tag-chip">' + esc(t) + '<button type="button" data-rm="' + i + '">&times;</button></span>'; }).join('') +
          '<input id="f_app_tagInput" placeholder="回车或空格新增标签"></div></div>' +
        /* 归属 */
        '<div class="fg-item"><label>所属分类</label><select id="f_app_cat">' + catOpts + '</select></div>' +
        /* 版本信息录入（核心新增） */
        '<div class="fg-item"><label>版本号</label><input id="f_app_version" maxlength="20" value="' + esc(a.version || '') + '" placeholder="如：v1.0.0"><span class="fg-hint">支持语义化版本，如 v1.2.3</span></div>' +
        '<div class="fg-item"><label>最近更新时间</label><input id="f_app_updateTime" value="' + esc(a.updateTime || nowStr) + '" placeholder="YYYY-MM-DD HH:mm"></div>' +
        '<div class="fg-item"><label>版本负责人</label><input id="f_app_owner" maxlength="30" value="' + esc(a.releaseOwner || '') + '" placeholder="例：产品经理·小王"></div>' +
        '<div class="fg-item full"><label>发布说明</label><textarea id="f_app_releaseNote" maxlength="200" placeholder="本次版本的主要特性、变更点、修复内容...">' + esc(a.releaseNote || '') + '</textarea><span class="fg-hint">可选，最大 200 字</span></div>' +
        /* 数据展示配置（每个字段双模式） */
        '<div class="fg-item"><label>累计使用数据来源</label><select id="f_app_useSrc"><option value="auto"' + (a.usageSrc === 'auto' ? ' selected' : '') + '>自动同步</option><option value="manual"' + (a.usageSrc === 'manual' ? ' selected' : '') + '>手动录入</option></select></div>' +
        '<div class="fg-item fg-num"><label>累计使用次数</label><input id="f_app_useVal" type="number" min="0" value="' + (a.usageTotal || 0) + '"' + (a.usageSrc === 'auto' ? ' disabled' : '') + '></div>' +
        '<div class="fg-item"><label>月活用户来源</label><select id="f_app_uSrc"><option value="auto">自动同步</option><option value="manual" selected>手动录入</option></select></div>' +
        '<div class="fg-item fg-num"><label>月活跃用户</label><input id="f_app_uVal" type="number" min="0" value="' + (a.users || 0) + '"></div>' +
        '<div class="fg-item"><label>热度指数来源</label><select id="f_app_hSrc"><option value="auto">自动计算</option><option value="manual" selected>手动录入</option></select></div>' +
        '<div class="fg-item fg-num"><label>热度指数（0-100）</label><input id="f_app_hVal" type="number" min="0" max="100" value="' + (a.heat || 0) + '"></div>' +
        /* 状态与交互 */
        '<div class="fg-item"><label>应用状态</label><select id="f_app_soon"><option value="online"' + (!a.soon ? ' selected' : '') + '>已上线</option><option value="soon"' + (a.soon ? ' selected' : '') + '>待上线</option></select></div>' +
        '<div class="fg-item"><label>前台显隐</label><label class="switch" style="margin-top:2px"><input type="checkbox" id="f_app_vis" ' + (a.visible ? 'checked' : '') + '><span class="slider"></span></label></div>' +
        '<div class="fg-item full" id="wrap_online"><label>跳转链接（内部/外链）</label><input id="f_app_url" value="' + esc(a.embedUrl || '') + '"></div>' +
        '<div class="fg-item full" id="wrap_soon" style="display:' + (a.soon ? 'block' : 'none') + '"><label>待上线状态说明</label><input id="f_app_soonHint" value="' + esc(a.soonHint || '规划建设中，即将上线') + '"><span class="fg-hint">前台按钮展示"敬请期待"时的辅助说明</span></div>' +
      '</div></div>' +
      '<div class="dialog-foot"><button class="btn-secondary" data-act="cancel">取消</button><button class="btn-primary" data-act="ok">' + icon('check', 14) + '保存</button></div>',
      'large'
    );
    var chosenIcon = a.icon, tags = (a.tags || []).slice();

    var d = document.querySelector('.dialog-mask:last-child');
    $$('.ip-item', d).forEach(function (it) { it.onclick = function () { chosenIcon = it.dataset.ic; $$('.ip-item', d).forEach(function (x) { x.classList.toggle('active', x === it); }); }; });
    /* 标签输入 */
    var tw = d.querySelector('#tagWrap');
    var tagInput = d.querySelector('#f_app_tagInput');
    function renderTags() {
      $$('.tag-chip', tw).forEach(function (x) { x.remove(); });
      tags.forEach(function (t, i) {
        var sp = document.createElement('span'); sp.className = 'tag-chip';
        sp.innerHTML = esc(t) + '<button type="button" data-rm="' + i + '">&times;</button>';
        tw.insertBefore(sp, tagInput);
      });
      $$('.tag-chip button', tw).forEach(function (b) { b.onclick = function () { tags.splice(+b.dataset.rm, 1); renderTags(); }; });
    }
    tagInput.onkeydown = function (e) {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        var v = tagInput.value.trim();
        if (v) { tags.push(v); tagInput.value = ''; renderTags(); }
      }
    };
    /* 状态切换 */
    var selSoon = d.querySelector('#f_app_soon');
    selSoon.onchange = function () {
      var isSoon = selSoon.value === 'soon';
      d.querySelector('#wrap_online').style.display = isSoon ? 'none' : 'block';
      d.querySelector('#wrap_soon').style.display = isSoon ? 'block' : 'none';
    };
    /* 数据来源联动 */
    d.querySelector('#f_app_useSrc').onchange = function () { d.querySelector('#f_app_useVal').disabled = this.value === 'auto'; };

    d.querySelector('[data-act="cancel"]').onclick = closeDialog;
    d.querySelector('[data-act="ok"]').onclick = function () {
      var name = d.querySelector('#f_app_name').value.trim();
      if (!name) { toast('应用名称不能为空'); return; }
      a.name = name;
      a.desc = d.querySelector('#f_app_desc').value.trim();
      a.icon = chosenIcon; a.tags = tags;
      a.category = d.querySelector('#f_app_cat').value || null;
      /* 版本信息 */
      a.version = d.querySelector('#f_app_version').value.trim();
      a.updateTime = d.querySelector('#f_app_updateTime').value.trim();
      a.releaseOwner = d.querySelector('#f_app_owner').value.trim();
      a.releaseNote = d.querySelector('#f_app_releaseNote').value.trim();
      a.usageSrc = d.querySelector('#f_app_useSrc').value;
      a.usageTotal = a.usageSrc === 'manual' ? (+d.querySelector('#f_app_useVal').value || 0) : (a.usageTotal || 0);
      a.users = +d.querySelector('#f_app_uVal').value || 0;
      a.heat = Math.min(100, Math.max(0, +d.querySelector('#f_app_hVal').value || 0));
      a.soon = d.querySelector('#f_app_soon').value === 'soon';
      a.visible = d.querySelector('#f_app_vis').checked;
      a.embedUrl = d.querySelector('#f_app_url').value.trim();
      a.soonHint = d.querySelector('#f_app_soonHint').value.trim();

      var diffBits = [];
      if (a.version) diffBits.push('<span class="new">+ 版本：' + esc(a.version) + '</span>');
      if (a.releaseNote) diffBits.push('<span class="new">+ 发布说明已录入</span>');
      if (a.updateTime) diffBits.push('更新时间 ' + esc(a.updateTime));
      if (isNew) { DRAFT.apps.push(a); addLog('新增', '应用卡片：' + name, '<span class="new">+ 新增应用卡片 "' + esc(name) + '"，分类：' + (DRAFT.categories.find(function(c){return c.id===a.category;})||{}).name + '</span>' + (diffBits.length ? '<br>' + diffBits.join('、') : '')); }
      else {
        var old = DRAFT.apps.find(function (x) { return x.id === id; });
        Object.keys(a).forEach(function (k) { old[k] = a[k]; });
        addLog('编辑', '应用卡片：' + name, '应用配置已更新' + (diffBits.length ? '：' + diffBits.join('、') : ''));
      }
      /* 双模块口径同步：隐藏应用从统计洞察明细中自动隐藏 */
      syncAbilityToStats();
      closeDialog(); toast('已保存应用卡片'); renderAbilityContent();
    };
  }

  /* 5.1 与统计洞察模块联动 —— 同步口径 */
  function syncAbilityToStats() {
    /* 将草稿中的可见应用、名称、分类同步回 DB，使应用中心/统计洞察实时预览一致 */
    DRAFT.apps.forEach(function (da) {
      var orig = DB.apps.find(function (x) { return x.id === da.id; });
      if (orig) {
        orig.name = da.name; orig.desc = da.desc; orig.icon = da.icon; orig.tags = da.tags;
        orig.portal = da.portal; orig.category = da.category; orig.visible = da.visible;
        orig.usageTotal = da.usageTotal; orig.users = da.users; orig.delta = da.delta; orig.heat = da.heat;
        orig.soon = da.soon; orig.embedUrl = da.embedUrl; orig.soonHint = da.soonHint;
        orig.version = da.version; orig.releaseNote = da.releaseNote; orig.updateTime = da.updateTime; orig.releaseOwner = da.releaseOwner;
      }
    });
    /* 分类名称同步 */
    DRAFT.categories.forEach(function (dc) {
      var orig = DB.categories.find(function (x) { return x.id === dc.id; });
      if (orig) { orig.name = dc.name; orig.board = dc.board; orig.accent = dc.accent; orig.desc = dc.desc; orig.visible = dc.visible; }
    });
    /* 一级/二级配置同步 */
    DRAFT.portals.forEach(function (dp) {
      var orig = DB.portals.find(function (x) { return x.id === dp.id; });
      if (orig) { orig.name = dp.name; orig.icon = dp.icon; orig.accent = dp.accent; orig.desc = dp.desc; orig.visible = dp.visible; }
    });
    DRAFT.boards.forEach(function (db) {
      var orig = DB.boards.find(function (x) { return x.id === db.id; });
      if (orig) { orig.name = db.name; orig.short = db.short; orig.desc = db.desc; orig.portal = db.portal || orig.portal || 'tech'; orig.visible = db.visible; }
    });
    /* 如果当前处于应用中心视图，立即重绘组合视图，保证配置生效无刷新 */
    if (state.view === 'apps') {
      var q = state.query;
      state.query = q;
      renderCombinedApps(false);
      renderHeroKpis(false);
    }
  }

  /* ======== 4.3 版本快照管理 ======== */
  function renderVersions(box) {
    var rows = DRAFT.versions.map(function (v) {
      var badgeHtml = '';
      if (v.draft) badgeHtml = '<span class="cell-badge" style="margin-left:8px;background:linear-gradient(90deg,#ffb547,#f59e0b);color:#fff;border-color:#f59e0b">草稿</span>';
      else if (v.current) badgeHtml = '<span class="cell-badge success" style="margin-left:8px">当前线上</span>';
      var opHtml;
      if (v.current) {
        opHtml = '<button class="btn-secondary" disabled><span data-icon="check-circle" data-size="13"></span>已发布</button>';
      } else if (v.draft) {
        opHtml = '<div class="row-ops">' +
                   '<button class="row-op" data-view-ver="' + v.no + '">' + icon('eye', 12) + '查看详情</button>' +
                   '<button class="row-op" data-continue="' + v.no + '">' + icon('edit-3', 12) + '继续编辑</button>' +
                 '</div>';
      } else {
        opHtml = '<div class="row-ops">' +
                   '<button class="row-op" data-view-ver="' + v.no + '">' + icon('eye', 12) + '查看详情</button>' +
                   '<button class="row-op" data-rollback="' + v.no + '">' + icon('undo', 12) + '回滚至此</button>' +
                 '</div>';
      }
      return '<div class="version-item' + (v.current ? ' current' : '') + (v.draft ? ' draft' : '') + '">' +
        '<div class="vi-no">' + esc(v.no) + badgeHtml + '</div>' +
        '<div class="vi-info">' +
          '<div class="vi-title">' + esc(v.desc) + '</div>' +
          '<div class="vi-meta">' + icon('user', 12) + esc(v.user) + ' · ' + icon('calendar', 12) + esc(v.time) + '</div>' +
        '</div>' +
        '<div>' + opHtml + '</div>' +
      '</div>';
    }).join('');

    box.innerHTML =
      '<div class="ability-card">' +
        '<div class="ability-card-head">' +
          '<div><h3><span data-icon="git-branch" data-size="16"></span>版本快照管理</h3>' +
          '<p>每一次审核发布自动生成版本快照，永久保留，支持查看详情与一键回滚</p></div>' +
        '</div>' +
        '<div class="version-list">' + rows + '</div>' +
      '</div>';

    $$('[data-view-ver]', box).forEach(function (btn) {
      btn.onclick = function () {
        var v = DRAFT.versions.find(function (x) { return x.no === btn.dataset.viewVer; });
        if (!v) return;
        var statusHtml;
        if (v.draft) statusHtml = '<span class="cell-badge" style="background:linear-gradient(90deg,#ffb547,#f59e0b);color:#fff;border-color:#f59e0b">草稿版本（未发布）</span>';
        else if (v.current) statusHtml = '<span class="cell-badge success">当前线上版本</span>';
        else statusHtml = '<span class="cell-badge dim">历史版本</span>';
        openDialog(
          '<div class="dialog-head"><h3><span data-icon="file-search" data-size="16"></span>版本详情 · ' + esc(v.no) + '</h3><button class="dialog-close">&times;</button></div>' +
          '<div class="dialog-body">' +
            '<div class="desc-list">' +
              dlRow('版本号', '<span class="mono">' + esc(v.no) + '</span>') +
              dlRow((v.draft ? '保存时间' : '发布时间'), esc(v.time)) +
              dlRow((v.draft ? '保存人' : '发布人'), esc(v.user)) +
              dlRow('变更概要', esc(v.desc)) +
              dlRow('线上状态', statusHtml) +
            '</div>' +
          '</div>' +
          '<div class="dialog-foot"><button class="btn-primary" data-close>关闭</button></div>'
        ).querySelector('[data-close]').onclick = closeDialog;
      };
    });
    $$('[data-rollback]', box).forEach(function (btn) {
      btn.onclick = function () {
        confirmDialog('版本回滚确认', '即将回滚至版本 <b style="color:var(--primary)">' + esc(btn.dataset.rollback) + '</b>，回滚后线上首页立即恢复至该版本状态。', function () {
          var v = DRAFT.versions.find(function (x) { return x.no === btn.dataset.rollback; });
          if (!v) return;
          DRAFT.versions.forEach(function (x) { x.current = false; });
          v.current = true; state.ability.published = v.no;
          addLog('回滚', '版本快照：' + v.no, '<span class="new">+ 线上版本已回滚至 "' + v.no + '"</span>');
          toast('已成功回滚至 ' + v.no); renderAbility();
        });
      };
    });
    $$('[data-continue]', box).forEach(function (btn) {
      btn.onclick = function () {
        toast('继续编辑草稿：' + btn.dataset.continue);
        state.ability.module = 'meta';
        renderAbility();
      };
    });
  }

  /* ======== 4.4 全量操作日志 ======== */
  function renderLogs(box) {
    var q = state.ability.q.logs;
    var typeOpts = '<option value="">全部类型</option>';
    ['新增','编辑','删除','排序','显隐','预览','保存','发布','回滚'].forEach(function (t) { typeOpts += '<option value="' + t + '"' + (q.type === t ? ' selected' : '') + '>' + t + '</option>'; });

    var search =
      '<div class="query-form">' +
        '<div class="qf-item"><label>操作类型</label><select id="q_log_type" style="width:160px">' + typeOpts + '</select></div>' +
        '<div class="qf-item"><label>操作人</label><input id="q_log_user" placeholder="按操作人关键词" value="' + esc(q.user) + '" style="width:190px"></div>' +
        '<div class="qf-ops">' +
          '<button class="btn-secondary" id="q_log_reset"><span data-icon="rotate-ccw" data-size="13"></span>重置</button>' +
          '<button class="btn-primary" id="q_log_go"><span data-icon="search" data-size="13"></span>查询</button>' +
          '<button class="btn-secondary" id="q_log_export" style="margin-left:8px"><span data-icon="download" data-size="13"></span>导出日志</button>' +
        '</div>' +
      '</div>';

    var list = DRAFT.logs.filter(function (l) {
      if (q.type && l.type !== q.type) return false;
      if (q.user && l.user.indexOf(q.user) < 0) return false;
      return true;
    });

    var typeBadge = function (t) {
      var map = { '新增':'success','编辑':'primary','删除':'dim','排序':'primary','显隐':'warning','预览':'primary','保存':'','发布':'success','回滚':'warning' };
      var cls = map[t] || 'dim';
      var style = t === '保存' ? 'style="background:linear-gradient(90deg,#ffb547,#f59e0b);color:#fff;border-color:#f59e0b"' : '';
      return '<span class="cell-badge ' + cls + '" ' + style + '>' + esc(t) + '</span>';
    };

    var rows = list.map(function (l) {
      return '<tr>' +
        '<td class="num" style="width:160px">' + esc(l.time) + '</td>' +
        '<td>' + esc(l.user) + '</td>' +
        '<td class="num" style="width:120px">' + esc(l.ip) + '</td>' +
        '<td style="width:84px">' + typeBadge(l.type) + '</td>' +
        '<td>' + esc(l.obj) + '</td>' +
        '<td><div class="diff-block">' + l.diff + '</div></td>' +
      '</tr>';
    }).join('');

    box.innerHTML =
      '<div class="ability-card">' +
        '<div class="ability-card-head">' +
          '<div><h3><span data-icon="clipboard-list" data-size="16"></span>全量操作日志</h3>' +
          '<p>所有配置操作全量留痕（保留 90 天以上），支持查询与 CSV 导出</p></div>' +
        '</div>' +
        search +
        '<div class="table-scroll"><table class="log-table"><colgroup>' +
          '<col style="width:160px"><col style="width:140px"><col style="width:120px"><col style="width:90px"><col style="width:200px"><col>' +
        '</colgroup><thead><tr>' +
          '<th>操作时间</th><th>操作人</th><th>操作IP</th><th>操作类型</th><th>操作对象</th><th>变更内容</th>' +
        '</tr></thead><tbody>' + (rows || emptyTr(6, true)) + '</tbody></table></div>' +
      '</div>';

    box.querySelector('#q_log_go').onclick = function () {
      state.ability.q.logs.type = box.querySelector('#q_log_type').value;
      state.ability.q.logs.user = box.querySelector('#q_log_user').value.trim();
      renderAbilityContent();
    };
    box.querySelector('#q_log_reset').onclick = function () {
      state.ability.q.logs = { type: '', user: '', from: '', to: '' };
      renderAbilityContent();
    };
    box.querySelector('#q_log_export').onclick = function () {
      addLog('导出', '全量操作日志', '已导出 ' + list.length + ' 条日志记录');
      toast('日志 CSV 已生成（演示）');
      renderAbilityContent();
    };
  }

  /* ======== 4.2 审核发布 ======== */
  function onPublish() {
    if (!state.ability.dirty && DRAFT.versions[0] && DRAFT.versions[0].current) {
      toast('当前配置与线上版本一致，无需发布');
      return;
    }
    confirmDialog('审核发布确认', '即将将当前草稿配置更新至线上首页。<br>发布成功后，线上首页实时刷新生效，无需前端发版。<br><br>确定继续吗？', function () {
      /* 生成新版本号 */
      var lastVer = DRAFT.versions[0] ? DRAFT.versions[0].no : 'v1.0.0';
      var parts = lastVer.match(/^v(\d+)\.(\d+)\.(\d+)$/);
      var next;
      if (parts) { next = 'v' + parts[1] + '.' + (+parts[2] + 1) + '.0'; } else { next = 'v1.1.0'; }
      DRAFT.versions.forEach(function (x) { x.current = false; });
      var who = currentAdmin();
      DRAFT.versions.unshift({
        no: next,
        time: new Date().toISOString().slice(0, 19).replace('T', ' '),
        user: who.name,
        desc: '能力管理配置发布，更新首页分类与卡片配置（管理员发布）',
        current: true
      });
      state.ability.published = next;
      state.ability.dirty = false;
      syncAbilityToStats();
      addLog('发布', '全局配置', '<span class="new">+ 审核发布版本 ' + next + '，线上首页已刷新生效</span>');
      toast('发布成功 · 线上版本已更新为 ' + next);
      renderAbility();
    });
  }

  /* ---------- 辅助：图标选择器 / 颜色选择 / 空行 ---------- */
  function iconPickerHTML(active) {
    var icons = ['sparkles','layout-grid','cpu','briefcase','settings','bar-chart','activity','zap','users','layers','share-2','flame','trending-up','clipboard-list','calculator','gauge','factory','bot','flask','library','book-open','tags','file-text','pie-chart','calendar','app-window','eye','edit-3','trash-2','plus','save','folder','folder-tree'];
    return icons.map(function (ic) { return '<div class="ip-item' + (ic === active ? ' active' : '') + '" data-ic="' + ic + '">' + icon(ic, 18) + '</div>'; }).join('');
  }
  function colorRowHTML(active) {
    var colors = ['#2A6BF0','#0AA2C0','#0FA36B','#2CA55F','#17AD9B','#5B8AF5','#DB6DA6','#E8590C','#F0703C','#C7860A','#8492A8','#6B7A93'];
    return colors.map(function (c) { return '<span class="color-dot' + (c === active ? ' active' : '') + '" style="background:' + c + '" data-color="' + c + '" title="' + c + '"></span>'; }).join('');
  }
  function emptyTr(n, isLog) {
    return '<tr><td colspan="' + (isLog ? 6 : n) + '" style="padding:40px;text-align:center;color:var(--text-dim)">' + icon('inbox', 28) + '<div style="margin-top:10px;font-size:13px">暂无数据</div></td></tr>';
  }
})();
