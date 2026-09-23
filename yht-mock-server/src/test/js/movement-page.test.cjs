const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../../main/resources/static/yht-mock');
const html = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
const app = fs.readFileSync(path.join(root, 'app.js'), 'utf8');

test('ledger has filters, separate generation and push actions, and no inline event handlers', () => {
    for (const id of ['movements', 'flowFrom', 'flowTo', 'flowBank', 'flowAccount', 'flowKeyword',
        'flowDirection', 'flowStatus', 'flowSize', 'flowTableBody', 'flowPreviousBtn', 'flowNextBtn',
        'flowSelectAll', 'flowSelectionCount', 'batchPushReason', 'batchPushConfirmed',
        'batchPushTargetPreview', 'batchPushFlowsBtn', 'clearFlowSelectionBtn', 'batchPushResult',
        'generateFlowBtn', 'pushFlowBtn', 'flowConfirmed', 'flowReason', 'flowAttempts',
        'flowDetailDialog', 'closeFlowDetailBtn', 'flowSelection', 'flowActionHint', 'flowPayload',
        'flowOperationResult', 'flowRecoveryPanel', 'flowSenderStopped', 'recoverFlowBtn',
        'syncFlowsBtn', 'queryFlowsBtn', 'resetFlowsBtn', 'flowResult', 'refreshFlowDetailBtn']) {
        assert.ok(html.includes('id="' + id + '"'), id);
    }
    assert.match(html, /role="tablist"/);
    // 一次筛出可批量补推的流水，避免逐条翻页勾选。
    assert.match(html, /<option value="MISSING,PENDING,FAIL">可推送（未推送 \/ 失败）<\/option>/);
    assert.ok(html.includes('src="/yht-mock/movements.js"'));
    assert.doesNotMatch(html, /onclick=/);
    // 流水详情与补操作必须是页面级模态弹窗，不再是页面底部的内联面板（列表很长时要滚到底才能操作）。
    assert.equal((html.match(/id="flowDetailDialog"/g) || []).length, 1);
    assert.match(html, /<dialog id="flowDetailDialog" class="mock-dialog">/);
    assert.doesNotMatch(html, /id="flowDetailPanel"/);
    new vm.Script(fs.readFileSync(path.join(root, 'movements.js'), 'utf8'));
});

test('tabs show only the requested panel and support history navigation without recreating forms', () => {
    const panels = ['overview', 'movements', 'callback'].map(id => ({id, hidden: false, setAttribute() {}}));
    const links = panels.map(panel => ({hash: '#' + panel.id, attributes: {}, handlers: {},
        classList: {toggle() {}}, setAttribute(key, value) {this.attributes[key] = value;},
        addEventListener(key, handler) {this.handlers[key] = handler;}, focus() {},
        click() {this.handlers.click({preventDefault() {}});}}));
    const listeners = {};
    const location = {hash: '#movements'};
    const context = {document: {querySelectorAll: () => links, getElementById: id => panels.find(panel => panel.id === id)},
        location, history: {pushState: (state, title, hash) => {location.hash = hash;}},
        window: {addEventListener: (event, callback) => {listeners[event] = callback;}}, loadFlows() {}};
    const source = app.slice(app.indexOf('function initSectionNavigation()'), app.indexOf('\ninitSectionNavigation();'));
    vm.runInNewContext(source + '\ninitSectionNavigation();', context);
    assert.deepEqual(panels.filter(panel => !panel.hidden).map(panel => panel.id), ['movements']);
    links[2].click();
    assert.deepEqual(panels.filter(panel => !panel.hidden).map(panel => panel.id), ['callback']);
    assert.equal(links[2].attributes['aria-selected'], 'true');
    location.hash = '#overview'; listeners.popstate();
    assert.deepEqual(panels.filter(panel => !panel.hidden).map(panel => panel.id), ['overview']);
    links[0].handlers.keydown({key: 'ArrowRight', preventDefault() {}});
    assert.equal(location.hash, '#movements');
});
