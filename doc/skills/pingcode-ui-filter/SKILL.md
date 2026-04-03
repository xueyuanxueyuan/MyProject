---
name: "pingcode-ui-filter"
description: "自动化PingCode任务列表筛选。当用户需要通过前端UI自动化（CDP）在PingCode页面精确筛选特定任务数据（如状态、负责人等）时调用此技能。"
---

# PingCode UI 自动化筛选技能

本技能用于通过 Chrome CDP (Chrome DevTools Protocol) 自动化操作 PingCode 的单页应用（SPA）前端 UI，实现任务列表的条件精确筛选。

## 适用场景
- 用户要求“打开我的pingcode”或在操作前需要唤起浏览器时。
- 用户要求“筛选PingCode数据”、“自动化过滤PingCode任务”、“按条件筛选列表”。
- 由于前端框架使用虚拟 DOM，直接修改 `innerHTML`/`value` 会报错（如 'slice'），需要绕过绑定限制，模拟真实用户点击来选择选项时。

## 前置条件：打开浏览器与特定 URL 规范
当用户要求打开 PingCode 时，必须遵守以下严格规范：
1. **浏览器偏好**：必须使用**本地 Chrome 浏览器**，且开启 CDP 调试端口（`--remote-debugging-port=9222`）并指定独立的数据目录（`--user-data-dir=/tmp/chrome-debug`），以防干扰用户日常使用。
2. **专属地址约束**：必须打开用户指定的专属项目 URL：`https://pingcode.capinfo.com.cn/pjm/projects/GJJ-TSHS/fwwFWqFj/WlytPXXa`。**严禁**使用 PingCode 官网或其他地址。

**启动命令示例（非阻塞后台运行）**：
```bash
google-chrome-stable --remote-debugging-port=9222 --user-data-dir=/tmp/chrome-debug https://pingcode.capinfo.com.cn/pjm/projects/GJJ-TSHS/fwwFWqFj/WlytPXXa
```

## 核心交互逻辑与经验法则
1. **建立 CDP 连接**：通过 `http://localhost:9222/json` 获取调试信息，筛选 url 包含 `pingcode` 的目标 tab。
2. **操作面板与分离渲染**：Angular Material 的下拉列表等组件 (`thy-select`, `.cdk-overlay-pane`) 通常渲染在 `body` 尾部的 `.cdk-overlay-container` 内，而不是在触发按钮的下方，必须去全局查找。
3. **强制环境清理（重置）**：
   - 打开筛选面板 `.styx-filter-panel` 后，**第一步必须点击“重置”按钮**，以清空可能积累的冗余项，使面板恢复到只有一行默认条件（如：“负责人 属于 当前用户”）的最干净状态。
4. **条件修改与赋值步骤**：
   - **新增行**：点击“添加筛选条件”新增第二行。
   - **修改属性**：点击新行默认出现的“标题”下拉框，在弹出的 `.cdk-overlay-pane` 中找到所需属性（如“状态”）并点击。
   - **选取值**：点击属性后方对应的输入框（如“选择状态”），在弹出的多选列表中，依次查找并点击目标状态值（如“新提交”、“需修复”）。
5. **生效执行**：点击“确定”按钮应用筛选条件。
6. **时序控制（Sleep）**：每执行一次 DOM 点击操作后，**必须**使用 `await sleep(1000)` 到 `1500` 等待异步渲染，否则会导致后续的 DOM 查找失败。

## JavaScript 注入模板示例

```javascript
(async () => {
    let result = [];
    let sleep = ms => new Promise(r => setTimeout(r, ms));
    
    // 1. 打开面板
    let getPanel = () => document.querySelector('styx-filter-panel, .filter-panel, [thy-filter-panel]');
    let filterPanel = getPanel();
    if (!filterPanel) {
        let btns = Array.from(document.querySelectorAll('button, .thy-action, [thy-action]'));
        let fbtn = btns.find(b => (b.innerText && b.innerText.includes('筛选') && !b.innerText.includes('添加筛选')) || b.querySelector('[thyiconname="filter"]'));
        if (fbtn) { fbtn.click(); await sleep(1500); }
    }
    
    // 2. 强制重置
    filterPanel = getPanel();
    if (!filterPanel) return "No panel";
    let resetBtn = Array.from(filterPanel.querySelectorAll('button, a, .thy-action')).find(b => b.innerText && b.innerText.trim() === '重置');
    if (resetBtn) { resetBtn.click(); await sleep(1500); }
    
    // 3. 添加条件行
    filterPanel = getPanel();
    let addCondEls = Array.from(filterPanel.querySelectorAll('*')).filter(el => el.innerText && el.innerText.trim() === '添加筛选条件');
    let addCondBtn = addCondEls.find(el => el.tagName === 'BUTTON' || el.tagName === 'A' || el.classList.contains('thy-action'));
    if (!addCondBtn && addCondEls.length > 0) addCondBtn = addCondEls[addCondEls.length - 1];
    if (addCondBtn) { addCondBtn.click(); await sleep(1500); }
    
    // 4. 定位新属性框并切换为"状态" (通常是第5个 thy-select 元素，即 props[4])
    filterPanel = getPanel();
    let props = Array.from(filterPanel.querySelectorAll('thy-select, .thy-select-custom'));
    if (props.length >= 5) {
        let targetProp = props[4];
        let clickTarget = targetProp.querySelector('.thy-select-custom-content, .form-control, .select-control, [class*="control"]') || targetProp;
        clickTarget.click();
        await sleep(1500);
        
        let overlays = Array.from(document.querySelectorAll('.cdk-overlay-pane'));
        let lastOverlay = overlays[overlays.length - 1];
        if (lastOverlay) {
            let items = Array.from(lastOverlay.querySelectorAll('thy-list-item, .thy-dropdown-menu-item, .thy-option-item, li, .thy-list-option'));
            let statusItem = items.find(el => el.innerText && el.innerText.trim() === '状态');
            if (statusItem) { statusItem.click(); await sleep(1500); }
        }
    }
    
    // 5. 点击"选择状态"输入框 (通常是 props[6])
    filterPanel = getPanel();
    props = Array.from(filterPanel.querySelectorAll('thy-select, .thy-select-custom'));
    if (props.length >= 7) {
        let statusValProp = props[6];
        let clickTarget = statusValProp.querySelector('.thy-select-custom-content, .form-control, .select-control, [class*="control"]') || statusValProp;
        clickTarget.click();
        await sleep(1500);
        
        let overlays = Array.from(document.querySelectorAll('.cdk-overlay-pane'));
        let lastOverlay = overlays[overlays.length - 1];
        if (lastOverlay) {
            let items = Array.from(lastOverlay.querySelectorAll('thy-list-item, .thy-dropdown-menu-item, .thy-option-item, li, .thy-list-option, .thy-tree-node'));
            // 多选点击
            let item1 = items.find(el => el.innerText && el.innerText.trim() === '新提交');
            if (item1) { item1.click(); await sleep(500); }
            let item2 = items.find(el => el.innerText && el.innerText.trim() === '需修复');
            if (item2) { item2.click(); await sleep(500); }
            
            // 点击表头收起下拉
            let header = filterPanel.querySelector('.dialog-header, h3');
            if (header) header.click();
            await sleep(1000);
        }
    }
    
    // 6. 点击确定应用
    filterPanel = getPanel();
    let confirmBtns = Array.from(filterPanel.querySelectorAll('button, a, .thy-action'));
    let confirmBtn = confirmBtns.find(b => b.innerText && b.innerText.trim() === '确定');
    if (confirmBtn) {
        confirmBtn.click();
        result.push("Filter Applied!");
    }
    
    return result.join('\\n');
})();
```
