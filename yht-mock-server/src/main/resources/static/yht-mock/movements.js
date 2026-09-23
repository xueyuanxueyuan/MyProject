const flowState = { page: 1, total: 0, selected: null, loading: 0, busy: false, detailRequest: 0,
    pageBoxes: [], selection: new Set(), pushing: false };
const flowLabels = { MISSING: '未生成', PENDING: '待推送', SENDING: '发送中', SUCC: '已受理', FAIL: '失败', UNKNOWN: '结果不明', LEGACY: '历史保护' };

// 推送口径的唯一判定来源（与后端 MovementNotificationService.batchPush 一致）：
// 未推送（未生成 / 待推送）与推送失败才可以推送；已受理、发送中、历史保护禁止重复推送；
// 结果不明可能已经推送，批量推送一律拒绝，只允许在详情弹窗里核对结算后单笔补推。
const flowPushRules = {
    MISSING: { single: true, batch: true, label: '未推送：需先补生成通知' },
    PENDING: { single: true, batch: true, label: '未推送：通知已生成，可推送' },
    FAIL: { single: true, batch: true, label: '推送失败：可补推' },
    UNKNOWN: { single: true, batch: false, label: '结果不明：需核对后单笔补推' },
    SENDING: { single: false, batch: false, label: '推送中：禁止重复推送' },
    SUCC: { single: false, batch: false, label: '已推送受理：禁止重复推送' },
    LEGACY: { single: false, batch: false, label: '历史保护：缺少通知快照，禁止推送' }
};

function flowPushRule(status) {
    return flowPushRules[status] || { single: false, batch: false, label: '未知状态：禁止推送' };
}

function flowPushLabel(status) {
    return flowPushRule(status).label;
}

function flowCanSinglePush(flow) {
    return !!flow && flowPushRule(flow.status).single && !!flow.payload;
}

async function movementRequest(url, options) {
    const result = await request(url, options);
    if (!result.ok) throw new Error(result.body?.message || pretty(result.body) || '请求失败，请稍后重试');
    return result.body;
}

async function loadFlows() {
    const sequence = ++flowState.loading;
    const filters = { from: 'flowFrom', to: 'flowTo', bank: 'flowBank', account: 'flowAccount',
        keyword: 'flowKeyword', direction: 'flowDirection', status: 'flowStatus', size: 'flowSize' };
    const query = new URLSearchParams({ page: String(flowState.page) });
    Object.entries(filters).forEach(([key, id]) => query.set(key, valueOf(id)));
    byId('flowResult').textContent = '正在查询…';
    try {
        const data = await movementRequest('/yht-mock/api/movements?' + query);
        if (sequence !== flowState.loading) return;
        flowState.total = data.total;
        flowState.pageBoxes = [];
        const tbody = byId('flowTableBody');
        tbody.innerHTML = '';
        data.items.forEach(flow => {
            const row = document.createElement('tr');
            const cell = value => '<td>' + escapeHtml(value) + '</td>';
            row.innerHTML = cell(flow.businessDate) + '<td>' + escapeHtml(flow.centerBankId || '缺少中心银行')
                + '<small>' + escapeHtml(flow.acctNo) + '</small></td><td>' + escapeHtml(flow.sysSeqNo)
                + '<small>' + escapeHtml(flow.batchNo || flow.reqId) + '</small></td>'
                + cell(flow.direction === 'IN' ? '收款' : '付款') + cell(flow.amount)
                + '<td>' + escapeHtml(flowLabels[flow.status] || flow.status)
                + '<small class="push-state">' + escapeHtml(flowPushLabel(flow.status)) + '</small></td>'
                + cell(flow.attemptCount);
            const check = document.createElement('td');
            check.className = 'check-col';
            const box = document.createElement('input');
            box.type = 'checkbox';
            box.checked = flowState.selection.has(flow.id);
            box.setAttribute('aria-label', '选择流水 ' + flow.sysSeqNo);
            box.addEventListener('change', () => {
                if (box.checked) flowState.selection.add(flow.id); else flowState.selection.delete(flow.id);
                syncSelectAllState();
                renderSelectionCount();
            });
            check.appendChild(box);
            row.insertBefore(check, row.firstChild);
            const actions = document.createElement('td');
            const button = document.createElement('button');
            button.className = 'button secondary';
            button.textContent = '详情 / 补操作';
            button.addEventListener('click', () => selectFlow(flow.id));
            actions.appendChild(button);
            row.appendChild(actions);
            tbody.appendChild(row);
            flowState.pageBoxes.push({ id: flow.id, box: box });
        });
        if (!data.items.length) tbody.innerHTML = '<tr><td colspan="9">没有符合条件的流水。可调整条件或同步已有成功交易。</td></tr>';
        const pages = Math.max(1, Math.ceil(data.total / data.size));
        byId('flowPageInfo').textContent = '第 ' + data.page + ' / ' + pages + ' 页，共 ' + data.total + ' 条';
        byId('flowPreviousBtn').disabled = data.page <= 1;
        byId('flowNextBtn').disabled = data.page >= pages;
        syncSelectAllState();
        renderSelectionCount();
        byId('flowResult').textContent = '查询完成，共 ' + data.total + ' 条模拟资金流水。';
    } catch (error) {
        if (sequence === flowState.loading) byId('flowResult').textContent = error.message;
    }
}

function renderSelectionCount() {
    const boxes = flowState.pageBoxes;
    const onPage = boxes.filter(item => flowState.selection.has(item.id)).length;
    byId('flowSelectionCount').textContent = flowState.selection.size
        ? '已勾选 ' + flowState.selection.size + ' 条流水（本页 ' + onPage + ' 条），可批量补生成并推送。'
        : '未勾选流水。可勾选「未生成 / 待推送 / 失败」的流水后批量补生成并推送。';
}

function syncSelectAllState() {
    const boxes = flowState.pageBoxes;
    const all = byId('flowSelectAll');
    const chosen = boxes.filter(item => flowState.selection.has(item.id)).length;
    all.checked = boxes.length > 0 && chosen === boxes.length;
    all.indeterminate = chosen > 0 && chosen < boxes.length;
}

function refreshPageCheckboxes() {
    flowState.pageBoxes.forEach(item => { item.box.checked = flowState.selection.has(item.id); });
    syncSelectAllState();
}

function toggleSelectAll() {
    const all = byId('flowSelectAll').checked;
    flowState.pageBoxes.forEach(item => {
        item.box.checked = all;
        if (all) flowState.selection.add(item.id); else flowState.selection.delete(item.id);
    });
    syncSelectAllState();
    renderSelectionCount();
}

function clearFlowSelection() {
    flowState.selection.clear();
    refreshPageCheckboxes();
    renderSelectionCount();
    byId('flowResult').textContent = '已清除勾选。';
}

function renderBatchPushSummary(result) {
    const lines = ['勾选 ' + result.requested + ' 条：补生成 ' + result.generatedCount
        + ' 条，推送 ' + result.pushedCount + ' 条，拒绝 ' + result.rejectedCount + ' 条。'];
    const pushed = Array.isArray(result.pushed) ? result.pushed : [];
    if (pushed.length) {
        lines.push('已推送：' + pushed.map(item => (item.sysSeqNo || item.id) + '（'
            + (flowLabels[item.status] || item.status) + '）').join('，'));
    }
    const rejected = Array.isArray(result.rejected) ? result.rejected : [];
    if (rejected.length) {
        lines.push('被拒绝：');
        rejected.forEach(item => lines.push('· ' + (item.sysSeqNo || item.id) + '（'
            + (flowLabels[item.status] || item.status) + '）：' + (item.reason || '未提供原因')));
    }
    return lines.join('\n');
}

async function batchPushFlows() {
    if (flowState.pushing) return;
    const ids = Array.from(flowState.selection);
    const output = byId('batchPushResult');
    if (!ids.length) {
        byId('flowResult').textContent = '请先勾选要推送的流水。';
        return;
    }
    const reason = valueOf('batchPushReason');
    if (!reason) {
        byId('flowResult').textContent = '请填写批量推送原因（必填）。';
        byId('batchPushReason').focus();
        return;
    }
    const confirmed = byId('batchPushConfirmed').checked;
    if (!confirm('确认对勾选的 ' + ids.length + ' 条流水执行「补生成并推送」？\n'
            + '未推送的会先补生成通知再推送；已受理、发送中、历史保护、结果不明的流水会被拒绝，不会重复动账。')) return;
    flowState.pushing = true;
    byId('batchPushFlowsBtn').disabled = true;
    output.hidden = false;
    output.textContent = '正在批量处理 ' + ids.length + ' 条流水，请勿重复操作…';
    try {
        const result = await movementRequest('/yht-mock/api/movements/batch-push', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ ids, confirmedNotReceived: confirmed, reason })
        });
        output.textContent = renderBatchPushSummary(result);
        byId('flowResult').textContent = result.message;
        flowState.selection.clear();
        await loadFlows();
        await loadCallbackTarget();
    } catch (error) {
        output.textContent = error.message;
    } finally {
        flowState.pushing = false;
        byId('batchPushFlowsBtn').disabled = false;
        renderSelectionCount();
    }
}

function renderFlowButtons() {
    const flow = flowState.selected;
    const pushable = flowCanSinglePush(flow);
    const rule = flowPushRule(flow && flow.status);
    byId('generateFlowBtn').disabled = flowState.busy || !flow || flow.status !== 'MISSING';
    byId('pushFlowBtn').disabled = flowState.busy || !pushable;
    byId('pushFlowBtn').textContent = flow && ['FAIL', 'UNKNOWN'].includes(flow.status) ? '补推到结算' : '推送到结算';
    byId('pushFlowBtn').title = pushable ? '' : rule.label;
    // 核对勾选框始终可勾选：是否必勾由推送动作在提交时校验，避免控件看起来是坏的。
    byId('flowConfirmed').disabled = flowState.busy || !flow;
    byId('flowRecoveryPanel').hidden = !flow || flow.status !== 'SENDING';
    byId('recoverFlowBtn').disabled = flowState.busy || !flow || flow.status !== 'SENDING';
}

function flowActionHint(flow) {
    if (!flow) return '请选择一条流水。';
    switch (flow.status) {
        case 'MISSING':
            return '当前状态：未生成（未推送）。请先点「补动账通知（仅生成）」生成通知快照，生成后按钮变为「推送到结算」；批量推送也会自动先补生成。';
        case 'PENDING':
            return '当前状态：待推送（未推送）。通知已生成、尚未发送，核对报文后可直接「推送到结算」，首次推送无需勾选核对确认。';
        case 'FAIL':
            return '当前状态：推送失败，可以补推。请先核对结算端确实未收到，再勾选下方核对确认，然后「补推到结算」。';
        case 'UNKNOWN':
            return '当前状态：结果不明（可能已推送）。批量推送已拒绝该状态，请先核对结算接收结果，确认未收到后勾选核对确认并在此单笔补推。';
        case 'SENDING':
            return '当前状态：发送中，禁止重复发送；确认原发送节点已停止并重启、且已核对结算结果后，可恢复为「结果不明」。';
        case 'SUCC':
            return '当前状态：已受理（已推送），禁止重复推送（重复推送可能重复动账）。如需补记，请到结算侧核对后处理。';
        case 'LEGACY':
            return '当前状态：历史保护，缺少通知快照，禁止重推；请核对原接口记录。';
        default:
            return '当前状态：' + (flowLabels[flow.status] || flow.status) + '（' + flowPushLabel(flow.status) + '）。';
    }
}

function openFlowDetailDialog() {
    const dialog = byId('flowDetailDialog');
    if (!dialog) return;
    if (typeof dialog.showModal === 'function') {
        if (!dialog.open) dialog.showModal();
    } else {
        dialog.setAttribute('open', 'open');
    }
}

function closeFlowDetail() {
    const dialog = byId('flowDetailDialog');
    if (!dialog) return;
    if (typeof dialog.close === 'function' && dialog.open) {
        dialog.close();
    } else {
        dialog.removeAttribute('open');
    }
}

async function loadCallbackTarget() {
    try {
        const settings = await movementRequest('/yht-mock/api/callback-config');
        setValue('batchPushTargetPreview', settings.movement?.targetUrl);
        return settings;
    } catch (error) {
        setValue('batchPushTargetPreview', '取回调配置失败：' + error.message);
        return null;
    }
}

async function selectFlow(id, preserveMessage = false) {
    if (flowState.busy) return;
    const sequence = ++flowState.detailRequest;
    flowState.selected = null;
    renderFlowButtons();
    try {
        const [flow, attempts, settings] = await Promise.all([
            movementRequest('/yht-mock/api/movements/' + encodeURIComponent(id)),
            movementRequest('/yht-mock/api/movements/' + encodeURIComponent(id) + '/attempts'),
            movementRequest('/yht-mock/api/callback-config')
        ]);
        if (sequence !== flowState.detailRequest) return;
        flowState.selected = flow;
        openFlowDetailDialog();
        byId('flowSelection').textContent = '银行流水：' + flow.sysSeqNo + ' ｜ 状态：' + (flowLabels[flow.status] || flow.status)
            + '（' + flowPushLabel(flow.status) + '）' + (flow.lastError ? ' ｜ ' + flow.lastError : '');
        byId('flowActionHint').textContent = flowActionHint(flow);
        setValue('flowTargetPreview', settings.movement?.targetUrl);
        byId('flowPayload').textContent = flow.payload || '尚未生成通知；补生成后可预览，再手动推送。';
        byId('flowAttempts').textContent = attempts.length ? pretty(attempts) : '暂无生成或推送记录。';
        byId('flowConfirmed').checked = false;
        byId('flowSenderStopped').checked = false;
        if (!preserveMessage) {
            setValue('flowReason', '');
            byId('flowOperationResult').textContent = '';
        }
        renderFlowButtons();
    } catch (error) {
        if (sequence === flowState.detailRequest) byId('flowResult').textContent = error.message;
    }
}

async function operateFlow(action) {
    const flow = flowState.selected;
    if (!flow || flowState.busy) return;
    const output = byId('flowOperationResult');
    const reason = valueOf('flowReason');
    if (!reason) { output.textContent = '请填写操作原因。'; byId('flowReason').focus(); return; }
    const confirmed = byId('flowConfirmed').checked;
    if (action === 'recover' && !byId('flowSenderStopped').checked) {
        output.textContent = '必须先停止原发送节点并重启，核对结算接收结果后确认恢复。'; return;
    }
    if (action === 'push') {
        if (['FAIL', 'UNKNOWN'].includes(flow.status) && !confirmed) {
            output.textContent = '补推前请核对结算端未收到，并勾选确认。'; return;
        }
        if (!confirm('确认向结算服务推送这笔通知？银行流水：' + flow.sysSeqNo
                + '；金额：' + flow.amount + '。结算已收到时不得重复推送。')) return;
    }
    flowState.busy = true;
    renderFlowButtons();
    output.textContent = action === 'push' ? '正在推送，请勿重复操作…'
        : action === 'recover' ? '正在恢复中断记录，不会发送通知…' : '正在补生成通知…';
    try {
        const result = await movementRequest('/yht-mock/api/movements/' + encodeURIComponent(flow.id) + '/' + action, {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ version: flow.version, reason, confirmedNotReceived: confirmed, confirmedSenderStopped: byId('flowSenderStopped').checked })
        });
        output.textContent = action === 'recover' ? '已恢复为结果不明，没有发送通知。确认结算未收到后才可另行补推。'
            : action === 'generate' ? '通知已生成，尚未发送。请预览后推送。'
            : '本次推送结果：' + (flowLabels[result.status] || result.status) + '。已受理不代表后续记账完成。';
    } catch (error) {
        output.textContent = error.message + '；请刷新详情核对状态，勿直接重复推送。';
    } finally {
        flowState.busy = false;
        await selectFlow(flow.id, true);
        await loadFlows();
        renderFlowButtons();
    }
}

document.addEventListener('DOMContentLoaded', () => {
    addClick('queryFlowsBtn', () => { flowState.page = 1; loadFlows(); });
    addClick('resetFlowsBtn', () => {
        ['flowFrom', 'flowTo', 'flowBank', 'flowAccount', 'flowKeyword', 'flowDirection', 'flowStatus'].forEach(id => setValue(id, ''));
        flowState.page = 1; loadFlows();
    });
    addClick('flowPreviousBtn', () => { if (flowState.page > 1) { flowState.page--; loadFlows(); } });
    addClick('flowNextBtn', () => { if (flowState.page * Number(valueOf('flowSize')) < flowState.total) { flowState.page++; loadFlows(); } });
    addClick('generateFlowBtn', () => operateFlow('generate'));
    addClick('pushFlowBtn', () => operateFlow('push'));
    addClick('recoverFlowBtn', () => operateFlow('recover'));
    addClick('refreshFlowDetailBtn', () => { if (flowState.selected) selectFlow(flowState.selected.id); });
    addClick('closeFlowDetailBtn', closeFlowDetail);
    addClick('batchPushFlowsBtn', batchPushFlows);
    addClick('clearFlowSelectionBtn', clearFlowSelection);
    addClick('flowSelectAll', toggleSelectAll);
    loadCallbackTarget();
    addClick('syncFlowsBtn', async () => {
        const button = byId('syncFlowsBtn');
        button.disabled = true;
        try {
            const result = await movementRequest('/yht-mock/api/movements/sync-history', { method: 'POST' });
            flowState.page = 1;
            await loadFlows();
            byId('flowResult').textContent = result.message + '；检查 ' + result.inspected + ' 笔/批，跳过 ' + result.skipped + ' 笔/批。';
        } catch (error) {
            byId('flowResult').textContent = error.message;
        } finally { button.disabled = false; }
    });
    addClick('normalizeSerialsBtn', async () => {
        const button = byId('normalizeSerialsBtn');
        if (!confirm('确认把所有超过 32 位的银行流水号统一收敛到 32 位以内？\n'
                + '超长流水号按「保留前缀 + 确定性哈希」改写，同一原值结果一致；已生成通知报文中的银行流水号会同步改写。')) return;
        button.disabled = true;
        try {
            const result = await movementRequest('/yht-mock/api/movements/normalize-serials', { method: 'POST' });
            flowState.page = 1;
            await loadFlows();
            const fixed = Array.isArray(result.fixed) ? result.fixed : [];
            const lines = [result.message];
            fixed.slice(0, 20).forEach(item => lines.push('· ' + item.before + ' → ' + item.after));
            if (fixed.length > 20) lines.push('… 其余 ' + (fixed.length - 20) + ' 条见后端日志');
            byId('flowResult').textContent = lines.join('\n');
        } catch (error) {
            byId('flowResult').textContent = error.message;
        } finally { button.disabled = false; }
    });
});
