# PingCode 自动化筛选解决方案 (UI 选择器方案)

## 需求背景
使用前端自动化脚本（通过 Chrome CDP）在 PingCode 系统中，通过**界面选择器**（UI Dropdowns）自动化实现列表数据的过滤，筛选条件为：
- **负责人**：当前用户
- **状态**：新提交、需修复

## 解决思路与难点分析

在单页应用（SPA，如 Angular/React）中，由于前端框架使用虚拟 DOM 和内部状态管理，直接修改元素的 `value` 或 `innerHTML` 往往无法触发框架的数据绑定更新。为了确保操作有效，需要模拟真实用户的点击行为（Click Events）来打开下拉菜单并选择选项。

主要难点包括：
1. **弹出层分离 (Overlay Containers)**：
   Angular Material 等组件库生成的弹窗、下拉菜单通常不挂载在触发元素所在的 DOM 节点下，而是附加在 `<body>` 尾部的 `.cdk-overlay-container` 等专门的层级中。因此，查询选项元素时需要在全局或专门的容器中查找。
2. **事件绑定目标**：
   在一些复杂的选择器组件（如 `thy-select`）中，点击最外层容器并不会触发下拉菜单的打开，事件往往绑定在其内部的 `.thy-select-custom-content` 或 `.form-control` 元素上。
3. **条件动态渲染**：
   点击“添加筛选条件”后，系统默认增加的通常是“标题包含”的空行。需要先定位到新增加行的“属性名”选择器，将其从“标题”改为“状态”，然后再点击“属性值”选择器，在展开的选项中勾选所需状态。
4. **异步等待**：
   每一步点击操作都会触发 DOM 的重绘或网络请求，因此必须在每次点击后加入适当的延时（Sleep），以等待动画或渲染完成。

## 自动化执行步骤

1. **建立 CDP 连接**：
   通过 `http://localhost:9222/json` 找到目标 PingCode 页面的 WebSocket 调试地址，建立连接。

2. **确保筛选面板打开与环境清理**：
   - 查找页面上带有 `thyiconname="filter"` 或包含“筛选”文本的按钮并点击，展开 `.styx-filter-panel` 筛选面板。
   - **关键重置**：点击面板中的 **“重置”** 按钮。这一步非常关键，它能清空之前可能残留的冗余筛选条件，使面板恢复到只有一行默认条件（即 **“负责人 属于 当前用户”**）的干净状态，防止过滤项过多。

3. **添加并配置状态筛选条件**：
   - 查找并点击筛选面板中的 **“添加筛选条件”** 按钮（这会增加第二行过滤项）。
   - 找到新增加一行的默认 **“标题”** 下拉选择器并点击。
   - 在弹出的 `.cdk-overlay-pane` 中找到 **“状态”** 选项并点击。
   - 此时属性选择器已变更为状态，接着点击后方的 **“选择状态”** 下拉框。
   - 在展开的多选列表中，依次查找并点击 **“新提交”** 和 **“需修复”** 选项。

4. **应用筛选**：
   - 点击面板空白处或表头以收起下拉菜单。
   - 查找面板底部的 **“确定”** 按钮并点击，触发 PingCode 的数据查询更新。此时页面将只有明确的两行过滤项。

## 执行脚本片段

核心交互逻辑由以下 JavaScript 注入完成：

```javascript
// 先进行重置，保证只有一行默认的负责人条件
let resetBtn = document.querySelector('.styx-filter-panel').querySelectorAll('button')[...]; // 查找重置按钮
resetBtn.click();
await sleep(1500);

// 点击添加筛选条件，增加第二行
let addCondBtn = document.querySelector('.styx-filter-panel').querySelectorAll('button, a')[...];
addCondBtn.click();
await sleep(1500);


// 点击新行属性并切换为“状态”
let targetProp = document.querySelectorAll('thy-select')[...]; // 定位到'标题'
targetProp.querySelector('.form-control').click();
await sleep(1000);
let statusItem = document.querySelector('.cdk-overlay-container').querySelectorAll('thy-list-item')[...]; // 定位到'状态'
statusItem.click();

// 点击值选择器并选中具体状态
let statusValProp = document.querySelectorAll('thy-select')[...]; // 定位到'选择状态'
statusValProp.querySelector('.form-control').click();
await sleep(1000);
let item1 = document.querySelector('.cdk-overlay-container').querySelectorAll('thy-list-item')[...]; // 定位到'新提交'
item1.click();
// 同理点击'需修复'

// 确认筛选
let confirmBtn = document.querySelector('.styx-filter-panel').querySelectorAll('button')[...]; // 定位到'确定'
confirmBtn.click();
```

## 总结
通过逐层剖析 Angular 组件树结构并模拟真实的 UI 点击流，成功规避了直接修改 DOM 导致的状态丢失问题，也避开了 PQL 的复杂查询语法问题。操作更加贴近真实用户的使用路径，提高了自动化脚本的稳定性和兼容性。
