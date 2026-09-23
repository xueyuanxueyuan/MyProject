---
name: "mock-ui-interaction-test"
description: "挡板（yht-mock-server）静态前端交互的脚本级验证方法：用 node:test + vm.runInNewContext + 手写假 DOM 真实加载前端脚本并驱动它，验证控件可用性、勾选/全选、请求体与事件接线是否真的通，而不是只断言 HTML 里 id 存在。适用于改动 src/main/resources/static/yht-mock/ 下 movements.js / business.js / app.js / index.html 后，需要证明「按钮点得动、交互真的生效」的场景。"
---

# 挡板前端交互的脚本级验证

挡板前端的既有测试（`src/test/js/*.test.cjs`）以**静态断言**为主：读 `index.html` 断言控件 id 存在、读 `*.js` 正则断言某个函数名或接口路径存在。
静态断言证明不了「按钮点不动」「勾选没反应」这类缺陷——**id 存在不等于交互可用**。

本技能给出低成本替代：不装 jsdom、不开浏览器，用 `vm` 在一个 60 行的假 DOM 里**真实加载前端脚本并驱动它**。

## 何时使用

- 修了「按钮无法使用 / 勾不了 / 点了没反应」这类交互缺陷，需要能复现与回归的证据。
- 新增/改动前端控件（勾选框、批量工具条、弹窗、分页），需要证明接线（DOMContentLoaded 里的 addEventListener）真的挂上了。
- 改动会发请求的前端逻辑，需要断言**请求体**（勾选了哪些 id、带了什么标记位）。
- 无浏览器会话时（本机无 Edge 无头可用/不便启动），仍需交互级证据。

不适用：要验证真实渲染、CSS 布局、`<dialog>` 背衬与动画、真实网络。这些必须上浏览器，本技能只能作前置快速回归。

## 标准流程

### 第 1 步：语法与静态层（必跑，最快）

```powershell
$node='C:\Users\Sour\.workbuddy\binaries\node\versions\22.22.2-3\node.exe'
Set-Location D:\Probject\Gjj\yht-mock-server
& $node --check src/main/resources/static/yht-mock/movements.js
& $node --test src/test/js/static-page.test.cjs src/test/js/movement-page.test.cjs `
    src/test/js/movement-controls.test.cjs src/test/js/business-page.test.cjs
```

### 第 2 步：写交互测试（`src/test/js/<页面>-controls.test.cjs`）

骨架：手写 `element()` 造节点 → 造 `context`（假 `document` + app.js 提供的全局工具函数 + 打桩的 `request`）→ `vm.runInNewContext(脚本源码 + 导出表达式, context)` 拿到内部函数。

```js
function element(id = '') {
    const node = {
        id, value: '', textContent: '', innerHTML: '', hidden: false, disabled: false,
        checked: false, indeterminate: false, open: false, children: [], handlers: {},
        dataset: {}, attributes: {}, classList: { toggle() {}, add() {}, remove() {} },
        setAttribute(k, v) { node.attributes[k] = v; },
        removeAttribute(k) { delete node.attributes[k]; },
        addEventListener(type, handler) { (node.handlers[type] = node.handlers[type] || []).push(handler); },
        appendChild(child) { node.children.push(child); return child; },
        insertBefore(child) { node.children.unshift(child); return child; },
        get firstChild() { return node.children[0] || null; },
        querySelectorAll() { return []; },
        focus() {},
        showModal() { node.open = true; },
        close() { node.open = false; },
        fire(type, event) { (node.handlers[type] || []).forEach(h => h(event || {})); }
    };
    return node;
}
```

`context` 里必须提供脚本依赖的全部全局：`byId / valueOf / setValue / addClick / escapeHtml / pretty / request / confirm`，以及 `document.getElementById / createElement / addEventListener`。
**`document.addEventListener` 要把回调记下来**（`context.domReady = handler`），这样测试可以手动触发 DOMContentLoaded 来验证事件接线。

用导出表达式拿内部函数（脚本顶层 `function`/`const` 在同一次求值内可见）：

```js
const api = vm.runInNewContext(source + `
    ;({ flowState, renderFlowButtons, flowPushRule, toggleSelectAll, batchPushFlows, loadFlows })`,
    context, { filename: 'movements.js' });
```

### 第 3 步：断言三元组，而不是单个字段

对每个状态断言 **`状态 → 可用的动作 + 不可用时的文案 + 请求体`**，例如：

```js
app.flowState.selected = flow({ status: 'FAIL' });
app.renderFlowButtons();
assert.equal(app.elements.flowConfirmed.disabled, false, '核对勾选框不应因状态被硬禁用');
assert.equal(app.elements.pushFlowBtn.disabled, false, '推送失败的流水必须能补推');
assert.equal(app.elements.pushFlowBtn.textContent, '补推到结算');
```

状态集合类规则要直接断言集合本身，而不是数关键词个数：

```js
const single = statuses.filter(s => app.flowPushRule(s).single);
assert.deepEqual(single, ['MISSING', 'PENDING', 'FAIL', 'UNKNOWN']);
```

### 第 4 步：异步驱动要 flush 多轮

前端逻辑是 `await request(...) → await loadFlows()` 的链式异步，**一次 `setImmediate` 不够**：

```js
const tick = () => new Promise(resolve => setImmediate(resolve));
const flush = async (rounds = 6) => { for (let i = 0; i < rounds; i++) await tick(); };
app.elements.batchPushFlowsBtn.fire('click');
await flush();
assert.ok(app.calls.some(call => call.url.includes('batch-push')));
```

`request` 打桩要记录 `calls`，并按 URL 分支返回固定体；否则未处理的 rejection 会污染测试。

## 踩坑记录（都已踩过）

1. **`innerHTML` 在假 DOM 里只是字符串**：`row.innerHTML = '<td>...'` 不产生子节点，只有 `appendChild` / `insertBefore` 产生的是真实节点。断言列数时要写成 `innerHTML 里 <td 的个数 + children.length`，否则必然误判（我第一版就断言失败在 2 !== 8）。
2. **别把断言写成对实现细节的复述**：第一版我按「含『禁止/需』字样的文案数量」断言，实际 5 我写 4 → 失败。改为直接断言可推送状态集合，才是需求口径。
3. **`indeterminate` 半选**：只更新选择集合、不同步表头勾选框状态，看代码看不出来——本技能第一次跑就抓出「勾选单行表头不变半选」的真缺陷。**全选与单行勾选后都要调同一个 `syncSelectAllState()`**。
4. **控件 id 拼错/漏加不会报错**：`byId()` 查不到返回 null，`addEventListener` 会抛异常并中断后续接线（后面的控件全部静默失效）。新增控件时优先复用 app.js 里已有的 **null-safe `addClick`**，并补静态断言（id 必须在 HTML 里存在）。
5. **重复 id**：结构性改 HTML 后必查重复 id 与标签闭合：

```powershell
$html=[IO.File]::ReadAllText('...\index.html')
([regex]::Matches($html,'id="([^"]+)"') | ForEach-Object { $_.Groups[1].Value }) |
  Group-Object | Where-Object { $_.Count -gt 1 }
"DIALOG $(([regex]::Matches($html,'<dialog')).Count)/$(([regex]::Matches($html,'</dialog>')).Count)"
```

6. **`title` 当唯一说明不可靠**：控件不可用时应同时给可见文案（弹窗内状态说明行 / 列表行内小字），只写 `title` 用户看不到。

## 输出规范

- 测试文件放 `src/test/js/`，命名 `<页面>-controls.test.cjs`，与既有静态测试并列。
- 交付时必须报告：`node --check` 结果、`node --test` 的 tests/pass/fail 计数、以及**本技能是否抓到新缺陷**（抓到就要在同一轮修掉并说明）。
- 诚实边界：明确写出「未经浏览器实机点击验证」，把渲染层结论标为未验证。
