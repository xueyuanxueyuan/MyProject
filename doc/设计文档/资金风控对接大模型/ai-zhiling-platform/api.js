/* ============================================================================
 * api.js — 统一服务层（演示桩）
 * ----------------------------------------------------------------------------
 * 与真实后端对接时：仅替换下列函数的内部实现为 fetch 调用，
 * 函数签名与返回结构保持不变，视图层（app.js）无需任何改动。
 * 每个函数均以注释标注了目标 HTTP 方法、路径与请求/响应结构。
 * ========================================================================== */
(function () {
  var LS_KEY = 'aiqe.demo.usage.v1'; // 演示用：本地使用计数

  function delay(ms) { return new Promise(function (r) { setTimeout(r, ms || 300); }); }

  function loadExtra() {
    try { return JSON.parse(localStorage.getItem(LS_KEY)) || {}; }
    catch (e) { return {}; }
  }
  function saveExtra(map) { localStorage.setItem(LS_KEY, JSON.stringify(map)); }

  /* 获取全部应用（含实时使用统计）
   * TODO(后端对接): GET /api/apps
   *   响应: { code: 0, data: [{ id, name, category, icon, desc, tags[], usage, usageTotal, users, delta, heat, embedUrl }] }
   */
  async function fetchApps() {
    await delay(240);
    var extra = loadExtra();
    var data = DB.apps.map(function (a) {
      return Object.assign({}, a, { usageTotal: a.usage + (extra[a.id] || 0) });
    });
    return { code: 0, data: data };
  }

  /* 获取近 N 天使用趋势
   * TODO(后端对接): GET /api/stats/trend?days=14
   *   响应: { code: 0, data: { days: [{ date: 'MM/DD', count: Number }] } }
   */
  async function fetchTrend() {
    await delay(160);
    return { code: 0, data: { days: DB.trend } };
  }

  /* 记录一次应用使用（进入使用时触发）
   * TODO(后端对接): POST /api/usage/record   body: { appId }
   *   响应: { code: 0, data: { usageTotal: Number } }
   * 演示阶段：写入 localStorage 并返回最新累计值
   */
  async function recordUsage(appId) {
    await delay(120);
    var base = DB.apps.find(function (a) { return a.id === appId; });
    var extra = loadExtra();
    extra[appId] = (extra[appId] || 0) + 1;
    saveExtra(extra);
    return { code: 0, data: { usageTotal: base.usage + extra[appId] } };
  }

  /* 清空本地演示计数（真实环境由服务端统计，无需此函数） */
  function resetDemoUsage() { localStorage.removeItem(LS_KEY); }

  window.API = { fetchApps: fetchApps, fetchTrend: fetchTrend, recordUsage: recordUsage, resetDemoUsage: resetDemoUsage };
})();
