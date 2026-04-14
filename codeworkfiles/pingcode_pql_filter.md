# PingCode 任务列表 PQL 筛选实现

## 任务目标
用户希望在 PingCode 中过滤出负责人为“当前用户”（自己），且状态为“新提交”或“需修复”的任务列表。此前由于 PingCode DOM 结构复杂（如嵌套下拉弹窗、无明确 ID 标识、事件拦截等），普通的界面点击模拟极易点错或失败。因此用户建议直接使用 PingCode 的 PQL (PingCode Query Language) 进行查询。

## 解决思路与步骤

### 1. 定位 PQL 按钮
通过 CDP (Chrome DevTools Protocol) 向已打开的 PingCode 页面注入执行 JavaScript，查找整个 DOM 树中具有 `PQL` 文本且无子节点的元素（即按钮文本），成功在筛选栏右上角找到 PQL 切换按钮并执行点击操作，从而打开 PQL 输入框。

```javascript
let all = Array.from(document.querySelectorAll('*'));
let pql = all.find(el => el.innerText && el.innerText.toUpperCase() === 'PQL' && el.children.length === 0);
if (pql) {
    pql.click();
}
```

### 2. 注入 PQL 语句
PingCode 的 PQL 输入框实际上是一个封装过的 `CodeMirror` 或 `contenteditable` 的 DOM 节点。直接修改 `innerHTML` 或 `textContent` 可能会破坏内部 React/Angular 绑定的模型，导致触发 `input`/`change` 事件时抛出 `Cannot read properties of undefined (reading 'slice')` 错误。

为了安全注入：
1. 设定查询语句为 `assignee = currentUser() AND state IN ('新提交', '需修复')`
2. 如果是 `contenteditable` 的普通输入框，先调用 `focus()`，接着通过原生的 `document.execCommand('selectAll')` 和 `document.execCommand('insertText', false, query)` 来模拟真实用户的键盘输入。
3. 如果为 `CodeMirror` 实例，则安全地通过其暴露的 API `input.CodeMirror.setValue(query)` 进行设置。

```javascript
let query = "assignee = currentUser() AND state IN ('新提交', '需修复')";
if (input.isContentEditable) {
    input.focus();
    document.execCommand('selectAll', false, null);
    document.execCommand('insertText', false, query);
} else if (input.classList.contains('CodeMirror')) {
    input.CodeMirror.setValue(query);
} else {
    input.value = query;
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
}
```

### 3. 触发查询
设定 PQL 查询语句后，稍微延迟（如 `setTimeout` 100ms）等待底层框架模型同步完毕，然后再点击搜索图标（放大镜/查询按钮）以应用过滤。

```javascript
setTimeout(() => {
    let searchIcon = document.querySelector('thy-icon[thyIconName="search"], .icon-search');
    if (searchIcon) {
        searchIcon.click();
        let p = searchIcon.parentElement;
        if (p) p.click();
    } else {
        let searchBtn = Array.from(document.querySelectorAll('button, a, span.icon, thy-action')).find(b => b.innerText && b.innerText.includes('查询') && b.offsetParent !== null);
        if (searchBtn) searchBtn.click();
    }
}, 100);
```

## 结果
成功使用 PQL 语句跳过了复杂的 UI 表单组件嵌套，实现了精准的任务过滤，确保列表只显示当前用户需要处理（新提交/需修复）的任务，避免了由于 DOM 层级导致点击穿透或点到数据行里的错误。
