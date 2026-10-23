/* ============================================================================
 * app-view.js — 统一应用界面容器页（新页签打开）
 * URL 形如 app-view.html?app=<appId>
 * embedUrl 有值 → iframe 整页加载实际系统；留空 → 「应用界面」占位容器
 * 使用计数由门户页点击进入时记录（api.js recordUsage），本页延迟刷新一次展示
 * ========================================================================== */
(function () {
  var $ = function (s) { return document.querySelector(s); };
  var icon = function (n, s, w) { return window.icon(n, s == null ? 20 : s, w == null ? 2 : w); };
  var fmt = function (n) { return n.toLocaleString('zh-CN'); };
  var esc = function (s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/"/g, '&quot;'); };

  var LS_KEY = 'aiqe.demo.usage.v1';

  function catOf(app) {
    return DB.categories.find(function (c) { return c.id === app.category; });
  }
  function boardOf(cat) {
    return DB.boards.find(function (b) { return b.id === cat.board; });
  }
  function extraUsage(appId) {
    try { var m = JSON.parse(localStorage.getItem(LS_KEY)) || {}; return m[appId] || 0; }
    catch (e) { return 0; }
  }

  document.addEventListener('DOMContentLoaded', init);

  function init() {
    /* 填充静态图标位 */
    document.querySelectorAll('[data-icon]').forEach(function (el) {
      el.innerHTML = icon(el.dataset.icon, +(el.dataset.size || 16));
    });
    $('#btnExternal').innerHTML = icon('external-link', 16);

    var id = new URLSearchParams(location.search).get('app');
    var app = DB.apps.find(function (a) { return a.id === id; });

    if (!app || app.soon) { renderMissing(); return; }

    var cat = catOf(app);
    var board = boardOf(cat);
    document.title = app.name + ' · 应用界面 · AI智擎提效平台';

    $('#viewTitle').innerHTML =
      '<span class="view-ic" style="--accent:' + cat.accent + '">' + icon(app.icon, 20) + '</span>' +
      '<span class="view-name"><b>' + esc(app.name) + '</b><i>' + board.name + ' · ' + cat.name + '</i></span>' +
      '<span class="view-chip">' + icon('monitor', 13, 2) + '应用界面</span>';

    $('#btnExternal').disabled = !app.embedUrl && !app.previewImage;
    $('#btnExternal').addEventListener('click', function () {
      var url = app.embedUrl || app.previewImage;
      if (url) window.open(url, '_blank', 'noopener');
    });

    $('#viewStage').innerHTML = app.embedUrl
      ? '<iframe class="app-frame" src="' + esc(app.embedUrl) + '" title="' + esc(app.name) +
        ' · 应用界面" sandbox="allow-forms allow-scripts allow-same-origin allow-popups"></iframe>'
      : app.previewImage
        ? previewHTML(app)
        : placeholderHTML(app);

    renderUsage(app);
    /* 门户页点击进入时写入的演示计数有约 120ms 延迟，稍后刷新一次展示 */
    setTimeout(function () { renderUsage(app); }, 800);
  }

  function renderUsage(app) {
    $('#viewUsage').innerHTML =
      icon('activity', 13, 2) + '<b>' + fmt(app.usage + extraUsage(app.id)) + '</b> 次使用';
  }

  /* 应用界面截图展示（未配置 embedUrl 时以截图模拟系统界面） */
  function previewHTML(app) {
    return (
      '<figure class="app-preview">' +
        '<div class="app-preview-stage">' +
          '<img src="' + esc(app.previewImage) + '" alt="' + esc(app.name) + ' · 应用界面" loading="eager">' +
        '</div>' +
        '<figcaption class="app-preview-note">' + icon('monitor', 13, 2) +
          '应用界面预览 · 截图示意，实际以系统在线版本为准' +
        '</figcaption>' +
      '</figure>'
    );
  }

  function placeholderHTML(app) {
    return (
      '<div class="frame-placeholder">' +
        '<span class="fp-icon">' + icon('monitor', 40) + '</span>' +
        '<h4>应 用 界 面</h4>' +
        '<p class="fp-name">' + esc(app.name) + '</p>' +
        '<p class="fp-desc">' + esc(app.desc) + '</p>' +
        '<p class="fp-hint">' + icon('info', 14, 2) +
          '统一应用容器：在 <code>mock.js</code> 中为该应用配置 <code>embedUrl</code> 后，此页签将自动加载实际系统页面' +
        '</p>' +
      '</div>'
    );
  }

  function renderMissing() {
    $('#viewTitle').innerHTML =
      '<span class="view-chip">' + icon('info', 13, 2) + '未找到应用</span>';
    $('#viewStage').innerHTML =
      '<div class="frame-placeholder">' +
        '<span class="fp-icon">' + icon('search', 40) + '</span>' +
        '<h4>应 用 界 面</h4>' +
        '<p class="fp-name">应用不存在或未开放</p>' +
        '<p class="fp-desc">请从 AI智擎提效平台 门户进入应用，或检查链接中的应用参数是否正确。</p>' +
        '<a class="btn-use" href="index.html#apps" style="margin-top:16px">返回应用中心 ' + icon('arrow-left', 14, 2) + '</a>' +
      '</div>';
  }
})();
