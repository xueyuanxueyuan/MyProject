const businessTypes = {
    trade: { label: '单笔交易', tabId: 'businessTabTrade', panelId: 'businessPanelTrade',
        bodyId: 'businessTradeBody', pageInfoId: 'businessTradePageInfo',
        prevId: 'businessTradePrevBtn', nextId: 'businessTradeNextBtn', sizeId: 'businessTradeSize',
        emptyText: '暂无单笔交易记录。' },
    batch: { label: '批量交易', tabId: 'businessTabBatch', panelId: 'businessPanelBatch',
        bodyId: 'businessBatchBody', pageInfoId: 'businessBatchPageInfo',
        prevId: 'businessBatchPrevBtn', nextId: 'businessBatchNextBtn', sizeId: 'businessBatchSize',
        emptyText: '暂无批量交易记录。' },
    protocol: { label: '签约类交易', tabId: 'businessTabProtocol', panelId: 'businessPanelProtocol',
        bodyId: 'businessProtocolBody', pageInfoId: 'businessProtocolPageInfo',
        prevId: 'businessProtocolPrevBtn', nextId: 'businessProtocolNextBtn', sizeId: 'businessProtocolSize',
        emptyText: '暂无签约类交易记录。' }
};

const businessState = {
    type: 'trade',
    loading: 0,
    timer: null,
    pages: { trade: 1, batch: 1, protocol: 1 },
    sizes: { trade: 20, batch: 20, protocol: 20 },
    totals: { trade: null, batch: null, protocol: null },
    totalPages: { trade: 1, batch: 1, protocol: 1 },
    detailBatchNo: ''
};

const businessStatusLabels = {
    SUCC: '成功', PROC: '处理中', FAIL: '失败', PEND: '待处理', CANC: '已撤销', INIT: '已受理'
};

const flowStatusLabels = {
    MISSING: '未生成', PENDING: '待推送', SENDING: '发送中', SUCC: '已受理',
    FAIL: '失败', UNKNOWN: '结果不明', LEGACY: '历史保护'
};

function businessStatusText(value) {
    const key = (value || '').trim();
    if (!key) {
        return '未知';
    }
    return businessStatusLabels[key] ? key + '（' + businessStatusLabels[key] + '）' : key;
}

function businessCell(value) {
    return '<td>' + escapeHtml(value === undefined || value === null || value === '' ? '-' : String(value)) + '</td>';
}

function businessActionCell(buttons) {
    const cell = document.createElement('td');
    buttons.forEach(button => cell.appendChild(button));
    return cell;
}

function businessButton(label, handler, secondary = true) {
    const button = document.createElement('button');
    button.className = 'button ' + (secondary ? 'secondary' : 'primary');
    button.textContent = label;
    button.addEventListener('click', handler);
    return button;
}

function openTrace(keyword) {
    if (!keyword) {
        return;
    }
    setValue('traceKeyword', keyword);
    setValue('traceTypeFilter', '');
    setValue('traceStatusFilter', '');
    location.hash = '#trace';
    refreshLogs();
}

function businessSize(type) {
    const value = Number(valueOf(businessTypes[type].sizeId));
    return Number.isFinite(value) && value > 0 ? value : 20;
}

function buildBusinessRow(type, item) {
    const row = document.createElement('tr');
    if (type === 'trade') {
        row.innerHTML = businessCell(item.sysSeqNo || item.serialNum) + businessCell(item.tranCode)
            + businessCell(item.amount) + businessCell(businessStatusText(item.status))
            + businessCell(formatDateTime(item.updatedAt));
        row.appendChild(businessActionCell([
            businessButton('链路', () => openTrace(item.traceKeyword || item.reqId || item.key))
        ]));
    } else if (type === 'batch') {
        row.innerHTML = businessCell(item.batchNo) + businessCell(item.tranCode)
            + businessCell((item.totalCount || '0') + ' 笔 / ' + (item.totalAmount || '0.00'))
            + businessCell(businessStatusText(item.status)) + businessCell(formatDateTime(item.updatedAt));
        row.appendChild(businessActionCell([
            businessButton('链路', () => openTrace(item.traceKeyword || item.batchNo)),
            businessButton('明细', () => loadBatchDetails(item.batchNo))
        ]));
    } else {
        row.innerHTML = businessCell(item.protocolNo || item.signReqId) + businessCell(item.customerName)
            + businessCell(item.bankId) + businessCell(businessStatusText(item.status))
            + businessCell(formatDateTime(item.updatedAt));
        row.appendChild(businessActionCell([
            businessButton('链路', () => openTrace(item.traceKeyword || item.protocolNo))
        ]));
    }
    return row;
}

function renderBusinessPage(type, body) {
    const config = businessTypes[type];
    const items = body && Array.isArray(body.items) ? body.items : [];
    const total = Number(body && body.total ? body.total : items.length);
    const totalPages = Number(body && body.totalPages ? body.totalPages : 1);
    businessState.totals[type] = total;
    businessState.totalPages[type] = totalPages;
    const tbody = byId(config.bodyId);
    tbody.innerHTML = '';
    if (!items.length) {
        tbody.innerHTML = '<tr><td colspan="6">' + escapeHtml(config.emptyText) + '</td></tr>';
    } else {
        items.forEach(item => tbody.appendChild(buildBusinessRow(type, item)));
    }
    const page = businessState.pages[type];
    byId(config.pageInfoId).textContent = '第 ' + page + ' / ' + totalPages + ' 页，共 ' + total + ' 条';
    const prev = byId(config.prevId);
    const next = byId(config.nextId);
    if (prev) prev.disabled = page <= 1;
    if (next) next.disabled = page >= totalPages;
    renderBusinessSummary();
}

function renderBusinessSummary() {
    const describe = type => {
        const total = businessState.totals[type];
        const label = businessTypes[type].label;
        return label + ' ' + (total === null ? '—' : total + ' 条');
    };
    byId('businessSummary').textContent = [describe('trade'), describe('batch'), describe('protocol')].join('，')
        + '；当前标签：' + businessTypes[businessState.type].label
        + '，' + (isBusinessAutoRefresh() ? '实时刷新已开启（每 5 秒）。' : '实时刷新已关闭。');
}

async function refreshBusiness(type = businessState.type) {
    const config = businessTypes[type];
    const sequence = ++businessState.loading;
    const query = new URLSearchParams({ page: String(businessState.pages[type]), size: String(businessSize(type)) });
    const { ok, body } = await request('/yht-mock/api/business/' + type + '?' + query);
    if (sequence !== businessState.loading) {
        return;
    }
    if (!ok) {
        byId(config.bodyId).innerHTML = '<tr><td colspan="6">查询失败：'
            + escapeHtml(typeof body === 'string' ? body : pretty(body)) + '</td></tr>';
        return;
    }
    renderBusinessPage(type, body);
}

function setBusinessType(type) {
    if (!businessTypes[type]) {
        return;
    }
    businessState.type = type;
    Object.entries(businessTypes).forEach(([key, config]) => {
        const active = key === type;
        const panel = byId(config.panelId);
        if (panel) panel.hidden = !active;
        const tab = byId(config.tabId);
        if (tab) {
            tab.className = 'button ' + (active ? 'primary' : 'secondary');
            tab.setAttribute('aria-selected', String(active));
        }
    });
    renderBusinessSummary();
    refreshBusiness(type);
}

function isBusinessAutoRefresh() {
    const element = byId('businessAutoRefresh');
    return !element || element.value !== 'false';
}

function applyBusinessAutoRefresh() {
    if (businessState.timer) {
        clearInterval(businessState.timer);
        businessState.timer = null;
    }
    if (isBusinessAutoRefresh()) {
        businessState.timer = setInterval(() => {
            if (document.visibilityState !== 'visible' || location.hash === '#trace') {
                return;
            }
            refreshBusiness(businessState.type);
        }, 5000);
    }
}

function stepBusinessPage(type, delta) {
    const next = businessState.pages[type] + delta;
    if (next < 1 || next > businessState.totalPages[type]) {
        return;
    }
    businessState.pages[type] = next;
    refreshBusiness(type);
}

async function loadBatchDetails(batchNo) {
    businessState.detailBatchNo = batchNo;
    byId('batchDetailSummary').textContent = '正在加载批次 ' + batchNo + ' 的明细…';
    byId('batchDetailBody').innerHTML = '';
    openBatchDetailDialog();
    const { ok, body } = await request('/yht-mock/api/batches/' + encodeURIComponent(batchNo) + '/details');
    if (businessState.detailBatchNo !== batchNo) {
        return;
    }
    if (!ok) {
        byId('batchDetailSummary').textContent = '明细加载失败：' + pretty(body);
        return;
    }
    const details = Array.isArray(body.details) ? body.details : [];
    const packageFlow = body.packageFlow || null;
    const packageText = packageFlow
        ? (flowStatusLabels[packageFlow.status] || packageFlow.status)
            + (packageFlow.amount ? '，金额 ' + packageFlow.amount : '')
            + (packageFlow.lastError ? '，失败原因：' + packageFlow.lastError : '')
            + (packageFlow.attemptCount ? '，推送 ' + packageFlow.attemptCount + ' 次' : '')
        : '未生成（按包生成，需批次成功且有成功明细）';
    const tbody = byId('batchDetailBody');
    tbody.innerHTML = '';
    details.forEach(detail => {
        const row = document.createElement('tr');
        const included = detail.success
            ? (packageFlow ? '已纳入包流水（' + (flowStatusLabels[packageFlow.status] || packageFlow.status) + '）' : '待生成包流水')
            : '未纳入（明细失败）';
        row.innerHTML = businessCell(detail.seq) + businessCell(detail.bankId) + businessCell(detail.acctNo)
            + businessCell(detail.acctName) + businessCell(detail.amount)
            + businessCell('00' === detail.retCode ? '成功（00）' : (detail.retCode || '') + '（' + (detail.retMsg || '失败') + '）')
            + businessCell(detail.success ? '' : (detail.retMsg || detail.retCode || '未提供失败原因'))
            + businessCell(included);
        tbody.appendChild(row);
    });
    if (!details.length) {
        tbody.innerHTML = '<tr><td colspan="8">该批次没有可解析的明细（结果文件为空或无明细行）。</td></tr>';
    }
    byId('batchDetailSummary').textContent = '批次 ' + body.batchNo + '：当前状态 ' + businessStatusText(body.status)
        + '，明细 ' + body.detailCount + ' 笔（成功 ' + body.successCount + ' 笔，成功金额合计 '
        + (body.successAmount || '0.00') + '），中心银行 ' + (body.centerBankId || '未提供')
        + '；包级动账流水：' + packageText + '。';
}

function openBatchDetailDialog() {
    const dialog = byId('batchDetailDialog');
    if (!dialog) {
        return;
    }
    if (typeof dialog.showModal === 'function') {
        if (!dialog.open) {
            dialog.showModal();
        }
    } else {
        dialog.setAttribute('open', 'open');
    }
}

function closeBatchDetails() {
    const dialog = byId('batchDetailDialog');
    if (dialog && typeof dialog.close === 'function' && dialog.open) {
        dialog.close();
    } else if (dialog) {
        dialog.removeAttribute('open');
    }
    byId('batchDetailBody').innerHTML = '';
    businessState.detailBatchNo = '';
}

async function repairProcessingBatches() {
    const confirmed = confirm('确认按各批次自身结果文件，把仍停留在「处理中」的历史批次落为终态吗？'
        + '状态为 SUCC 的批次可在资金流水页执行「同步已有成功交易」补生成按包动账流水。');
    if (!confirmed) {
        return;
    }
    const button = byId('repairBatchBtn');
    button.disabled = true;
    byId('businessRepairResult').textContent = '正在修复…';
    try {
        const { ok, body } = await request('/yht-mock/api/batches/repair-processing', { method: 'POST' });
        if (!ok) {
            byId('businessRepairResult').textContent = '修复失败：' + pretty(body);
            return;
        }
        const skipped = (body.skipped || []).map(item => item.batchNo + '（' + item.reason + '）').join('；');
        byId('businessRepairResult').textContent = body.message + (skipped ? ' 跳过明细：' + skipped : '');
        await refreshBusiness('batch');
    } finally {
        button.disabled = false;
    }
}

function initBusinessSection() {
    addClick('refreshBusinessBtn', () => refreshBusiness(businessState.type));
    addClick('closeBatchDetailBtn', closeBatchDetails);
    addClick('repairBatchBtn', repairProcessingBatches);
    const dialog = byId('batchDetailDialog');
    if (dialog) {
        dialog.addEventListener('close', () => {
            byId('batchDetailBody').innerHTML = '';
            businessState.detailBatchNo = '';
        });
    }
    Object.entries(businessTypes).forEach(([type, config]) => {
        addClick(config.tabId, () => setBusinessType(type));
        addClick(config.prevId, () => stepBusinessPage(type, -1));
        addClick(config.nextId, () => stepBusinessPage(type, 1));
        const size = byId(config.sizeId);
        if (size) {
            size.addEventListener('change', () => {
                businessState.pages[type] = 1;
                if (type === businessState.type) {
                    refreshBusiness(type);
                }
            });
        }
    });
    const selector = byId('businessAutoRefresh');
    if (selector) {
        selector.addEventListener('change', applyBusinessAutoRefresh);
    }
    applyBusinessAutoRefresh();
    setBusinessType('trade');
}

document.addEventListener('DOMContentLoaded', initBusinessSection);
