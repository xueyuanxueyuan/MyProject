const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../../main/resources/static/yht-mock');
const source = fs.readFileSync(path.join(root, 'movements.js'), 'utf8');

/**
 * 用假 DOM 真实加载 movements.js 并驱动它：静态断言只能证明控件 id 存在，
 * 证明不了「按钮点不动」这类交互缺陷，因此这里把脚本跑起来看控件状态与请求体。
 */
function element(id = '') {
    const node = {
        id, value: '', textContent: '', innerHTML: '', hidden: false, disabled: false,
        checked: false, indeterminate: false, open: false, children: [], handlers: {},
        dataset: {}, attributes: {}, classList: { toggle() {}, add() {}, remove() {} },
        setAttribute(key, value) { node.attributes[key] = value; },
        removeAttribute(key) { delete node.attributes[key]; },
        addEventListener(type, handler) { (node.handlers[type] = node.handlers[type] || []).push(handler); },
        appendChild(child) { node.children.push(child); return child; },
        insertBefore(child) { node.children.unshift(child); return child; },
        get firstChild() { return node.children[0] || null; },
        querySelectorAll() { return []; },
        focus() {},
        showModal() { node.open = true; },
        close() { node.open = false; },
        fire(type, event) { (node.handlers[type] || []).forEach(handler => handler(event || {})); }
    };
    return node;
}

function flow(overrides = {}) {
    return Object.assign({ id: 'f1', sysSeqNo: 'HOST-1', businessDate: '20260921', centerBankId: 'CENTER',
        acctNo: 'ACCT', status: 'PENDING', direction: 'IN', amount: '10.00', attemptCount: 0,
        payload: '{"yhzhhm":"ACCT"}', batchNo: '', reqId: 'REQ', lastError: '', version: 3 }, overrides);
}

function harness(options = {}) {
    const ids = ['flowResult', 'flowTableBody', 'flowPageInfo', 'flowPreviousBtn', 'flowNextBtn',
        'flowSelectAll', 'flowSelectionCount', 'batchPushReason', 'batchPushConfirmed',
        'batchPushTargetPreview', 'batchPushFlowsBtn', 'clearFlowSelectionBtn', 'batchPushResult',
        'generateFlowBtn', 'pushFlowBtn', 'flowConfirmed', 'flowRecoveryPanel', 'recoverFlowBtn',
        'flowDetailDialog', 'flowSelection', 'flowActionHint', 'flowTargetPreview', 'flowPayload',
        'flowAttempts', 'flowOperationResult', 'flowReason', 'flowSenderStopped',
        'queryFlowsBtn', 'resetFlowsBtn', 'syncFlowsBtn', 'refreshFlowDetailBtn', 'closeFlowDetailBtn',
        'flowFrom', 'flowTo', 'flowBank', 'flowAccount', 'flowKeyword', 'flowDirection', 'flowStatus', 'flowSize'];
    const elements = Object.fromEntries(ids.map(id => [id, element(id)]));
    elements.flowSize.value = '20';
    const calls = [];
    const rows = options.rows || [flow()];
    const context = {
        console,
        URLSearchParams,
        document: {
            getElementById: id => elements[id] || null,
            createElement: tag => element('<' + tag + '>'),
            addEventListener: (type, handler) => { context.domReady = handler; }
        },
        confirm: () => options.confirm !== false,
        byId: id => elements[id] || null,
        valueOf: id => (elements[id] ? String(elements[id].value).trim() : ''),
        setValue: (id, value) => { if (elements[id]) elements[id].value = value == null ? '' : value; },
        addClick: (id, handler) => { if (elements[id]) elements[id].addEventListener('click', handler); },
        escapeHtml: value => String(value == null ? '' : value),
        pretty: value => JSON.stringify(value, null, 2),
        request: async (url, requestOptions) => {
            calls.push({ url, options: requestOptions });
            if (url.startsWith('/yht-mock/api/movements?')) {
                return { ok: true, body: { items: rows, total: rows.length, page: 1, size: 20 } };
            }
            if (url.includes('/attempts')) return { ok: true, body: [] };
            if (url.includes('/callback-config')) {
                return { ok: true, body: { movement: { targetUrl: 'http://settle/saveZhbdzt' } } };
            }
            if (url.includes('/batch-push')) {
                return { ok: true, body: options.batchPushResult || { requested: 2, generatedCount: 1, pushedCount: 1,
                    rejectedCount: 1, generated: [], pushed: [{ id: 'f2', sysSeqNo: 'HOST-2', status: 'SUCC', amount: '20.00' }],
                    rejected: [{ id: 'f3', sysSeqNo: 'HOST-3', status: 'SUCC', amount: '', reason: '已受理的流水不允许重复推送' }],
                    message: '勾选 2 条：补生成 1 条，推送 1 条，拒绝 1 条。' } };
            }
            const id = url.split('/').pop();
            return { ok: true, body: rows.find(item => item.id === id) || flow() };
        }
    };
    context.window = { addEventListener() {} };
    const exported = vm.runInNewContext(source + `
        ;({ flowState, renderFlowButtons, flowCanSinglePush, flowPushLabel, flowPushRule, toggleSelectAll,
            renderSelectionCount, clearFlowSelection, batchPushFlows, loadFlows, selectFlow, operateFlow })`,
    context, { filename: 'movements.js' });
    return Object.assign(exported, { elements, calls, context });
}

const tick = () => new Promise(resolve => setImmediate(resolve));
const flush = async (rounds = 6) => { for (let index = 0; index < rounds; index++) await tick(); };

test('流水列表渲染勾选列、可推送提示，并保持分页与全选状态', async () => {
    const app = harness({ rows: [
        flow({ id: 'a', sysSeqNo: 'HOST-A', status: 'PENDING' }),
        flow({ id: 'b', sysSeqNo: 'HOST-B', status: 'SUCC', payload: '{"x":1}' })
    ] });
    await app.loadFlows();
    const rows = app.elements.flowTableBody.children;
    assert.equal(rows.length, 2);
    // 假 DOM 里 innerHTML 是字符串，只有勾选列与操作列是两个真实子节点；合计应为 9 列（与表头一致）。
    assert.equal(rows[0].children.length, 2);
    assert.equal((rows[0].innerHTML.match(/<td/g) || []).length + rows[0].children.length, 9);
    assert.equal(rows[0].children[0].className, 'check-col');
    assert.equal(rows[0].children[0].children[0].type, 'checkbox');
    assert.match(rows[1].innerHTML, /禁止重复推送/, '已受理的行要显式写出不可推送原因');
    assert.match(app.elements.flowPageInfo.textContent, /第 1 \/ 1 页，共 2 条/);
    assert.equal(app.elements.flowSelectAll.checked, false);

    app.elements.flowSelectAll.checked = true;
    app.toggleSelectAll();
    assert.match(app.elements.flowSelectionCount.textContent, /已勾选 2 条流水（本页 2 条）/);
    assert.equal(app.elements.flowSelectAll.indeterminate, false);

    app.elements.flowTableBody.children[0].children[0].children[0].checked = false;
    app.elements.flowTableBody.children[0].children[0].children[0].fire('change');
    assert.match(app.elements.flowSelectionCount.textContent, /已勾选 1 条流水（本页 1 条）/);
    assert.equal(app.elements.flowSelectAll.indeterminate, true, '部分勾选要显示半选');

    app.clearFlowSelection();
    assert.equal(app.elements.flowSelectionCount.textContent, '未勾选流水。可勾选「未生成 / 待推送 / 失败」的流水后批量补生成并推送。');
});

test('核对勾选框与推送按钮按状态可用，并解释不可用原因', () => {
    const app = harness();
    // 回归：之前核对勾选框只在失败 / 结果不明时可勾，用户看到的是「勾不了」且没有任何说明。
    app.flowState.selected = flow({ status: 'FAIL' });
    app.renderFlowButtons();
    assert.equal(app.elements.flowConfirmed.disabled, false, '核对勾选框不应因状态被硬禁用');
    assert.equal(app.elements.pushFlowBtn.disabled, false, '推送失败的流水必须能补推');
    assert.equal(app.elements.pushFlowBtn.textContent, '补推到结算');
    assert.equal(app.elements.pushFlowBtn.title, '');

    app.flowState.selected = flow({ status: 'PENDING' });
    app.renderFlowButtons();
    assert.equal(app.elements.flowConfirmed.disabled, false);
    assert.equal(app.elements.pushFlowBtn.disabled, false, '待推送（未推送）必须能推送');
    assert.equal(app.elements.pushFlowBtn.textContent, '推送到结算');

    app.flowState.selected = flow({ status: 'MISSING', payload: null });
    app.renderFlowButtons();
    assert.equal(app.elements.pushFlowBtn.disabled, true);
    assert.equal(app.elements.generateFlowBtn.disabled, false, '未生成要先补生成');
    assert.match(app.elements.pushFlowBtn.title, /需先补生成通知/);
    assert.equal(app.flowCanSinglePush(app.flowState.selected), false);

    app.flowState.selected = flow({ status: 'SUCC' });
    app.renderFlowButtons();
    assert.equal(app.elements.pushFlowBtn.disabled, true, '已受理禁止重复推送');
    assert.match(app.elements.pushFlowBtn.title, /禁止重复推送/);

    app.flowState.selected = flow({ status: 'SENDING' });
    app.renderFlowButtons();
    assert.equal(app.elements.pushFlowBtn.disabled, true);
    assert.equal(app.elements.flowRecoveryPanel.hidden, false, '发送中要露出恢复入口');

    app.flowState.selected = flow({ status: 'LEGACY', payload: null });
    app.renderFlowButtons();
    assert.match(app.elements.pushFlowBtn.title, /缺少通知快照/);

    const statuses = ['MISSING', 'PENDING', 'FAIL', 'UNKNOWN', 'SENDING', 'SUCC', 'LEGACY'];
    const single = statuses.filter(status => app.flowPushRule(status).single);
    const batch = statuses.filter(status => app.flowPushRule(status).batch);
    assert.deepEqual(single, ['MISSING', 'PENDING', 'FAIL', 'UNKNOWN'], '单笔可推送：未推送与推送失败，结果不明需核对后补推');
    assert.deepEqual(batch, ['MISSING', 'PENDING', 'FAIL'], '批量可推送只有未推送与推送失败');
    assert.ok(statuses.every(status => app.flowPushLabel(status).length > 0), '每个状态都要有中文推送说明');
});

test('批量推送提交勾选 id、原因与核对标记，并回显逐条拒绝原因', async () => {
    const app = harness({ rows: [
        flow({ id: 'a', status: 'MISSING', payload: null }),
        flow({ id: 'b', status: 'FAIL' }),
        flow({ id: 'c', status: 'SUCC' })
    ] });
    await app.loadFlows();
    app.elements.flowSelectAll.checked = true;
    app.toggleSelectAll();

    app.elements.batchPushReason.value = '';
    await app.batchPushFlows();
    assert.equal(app.calls.some(call => call.url.includes('batch-push')), false, '缺少原因时不得发请求');
    assert.match(app.elements.flowResult.textContent, /请填写批量推送原因/);

    app.elements.batchPushReason.value = '已核对结算未收到，批量补推';
    app.elements.batchPushConfirmed.checked = true;
    await app.batchPushFlows();
    const call = app.calls.find(item => item.url.includes('batch-push'));
    assert.ok(call, '应调用批量推送接口');
    assert.equal(call.url, '/yht-mock/api/movements/batch-push');
    assert.equal(call.options.method, 'POST');
    assert.deepEqual(JSON.parse(call.options.body), { ids: ['a', 'b', 'c'], confirmedNotReceived: true,
        reason: '已核对结算未收到，批量补推' });
    assert.match(app.elements.batchPushResult.textContent, /补生成 1 条，推送 1 条，拒绝 1 条/);
    assert.match(app.elements.batchPushResult.textContent, /HOST-3（已受理）：已受理的流水不允许重复推送/);
    assert.equal(app.flowState.selection.size, 0, '批量处理后清空勾选');
});

test('批量推送原因缺失时不静默失败，且全选不会误伤未勾选', async () => {
    const app = harness({ rows: [flow({ id: 'a', status: 'SUCC' })] });
    await app.loadFlows();
    app.context.domReady();
    await tick();
    assert.equal(app.elements.batchPushTargetPreview.value, 'http://settle/saveZhbdzt',
        '页面加载应回显本次推送地址');

    app.elements.flowSelectAll.checked = false;
    app.toggleSelectAll();
    assert.equal(app.flowState.selection.size, 0);
    app.elements.batchPushReason.value = '原因';
    await app.batchPushFlows();
    assert.equal(app.calls.some(call => call.url.includes('batch-push')), false, '未勾选时不得发请求');
    assert.match(app.elements.flowResult.textContent, /请先勾选要推送的流水/);
});

test('筛选「可推送」后全选即可批量补推，筛选值按多状态传给服务端', async () => {
    const app = harness({ rows: [flow({ id: 'a', status: 'FAIL' })] });
    app.elements.flowStatus.value = 'MISSING,PENDING,FAIL';
    await app.loadFlows();
    const query = app.calls[0].url;
    assert.match(query, /status=MISSING%2CPENDING%2CFAIL|status=MISSING,PENDING,FAIL/);
    app.elements.flowSelectAll.checked = true;
    app.toggleSelectAll();
    assert.equal(app.flowState.selection.size, 1);
});

test('页面事件接线：批量推送、清除选择、关闭弹窗都挂在真实控件上', async () => {
    const app = harness({ rows: [flow({ id: 'a', status: 'FAIL' })] });
    app.context.domReady();
    await tick();
    await app.loadFlows();

    app.elements.flowSelectAll.checked = true;
    app.elements.flowSelectAll.fire('click');
    assert.equal(app.flowState.selection.size, 1, '全选应挂 change / click 事件');

    app.elements.clearFlowSelectionBtn.fire('click');
    assert.equal(app.flowState.selection.size, 0);
    assert.match(app.elements.flowResult.textContent, /已清除勾选/);

    app.elements.batchPushReason.value = '原因';
    app.elements.batchPushConfirmed.checked = true;
    app.elements.flowSelectAll.checked = true;
    app.elements.flowSelectAll.fire('click');
    app.elements.batchPushFlowsBtn.fire('click');
    await flush();
    assert.ok(app.calls.some(call => call.url.includes('batch-push')), '点批量按钮要真的发起推送');

    app.elements.flowDetailDialog.open = true;
    app.elements.closeFlowDetailBtn.fire('click');
    assert.equal(app.elements.flowDetailDialog.open, false, '关闭按钮必须能关掉弹窗');
});
