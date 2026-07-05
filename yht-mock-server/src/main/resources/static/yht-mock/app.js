async function request(url, options = {}) {
    const response = await fetch(url, options);
    const contentType = response.headers.get('content-type') || '';
    const body = contentType.includes('application/json')
        ? await response.json()
        : await response.text();
    return { ok: response.ok, status: response.status, body };
}

function pretty(value) {
    return typeof value === 'string' ? value : JSON.stringify(value, null, 2);
}

function byId(id) {
    return document.getElementById(id);
}

function nowStamp() {
    const date = new Date();
    const pad = value => String(value).padStart(2, '0');
    return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`;
}

function buildHeader(mesgType) {
    return `{H:02CAPS          CAPS    33503C5801        CAPS904290099992      CAPS${nowStamp().slice(0, 8)}${nowStamp().slice(8)}XML${mesgType.padEnd(20, ' ')}MOCKMESG${nowStamp().slice(-12).padEnd(12, '0')}    3U         }`;
}

function getTemplates() {
    const stamp = nowStamp();
    return {
        'caps.999.001.01': `${buildHeader('caps.999.001.01')}\r\n<?xml version="1.0" encoding="UTF-8"?><Message xmlns="urn:caps:msg:caps.999.001.01"><Head><CorpNo>1111</CorpNo><CheckType>LINK</CheckType><SysChckNo>CHK${stamp}</SysChckNo></Head></Message>`,
        'caps.201.001.01': `${buildHeader('caps.201.001.01')}\r\n<?xml version="1.0" encoding="UTF-8"?><Message xmlns="urn:caps:msg:caps.201.001.01"><Head><CorpNo>1111</CorpNo></Head><Body><ReqId>REQ-${stamp}</ReqId><TranCode>201</TranCode><DbtrActId>62220000000000009999</DbtrActId><DbtrBankId>105000</DbtrBankId><TxAmt>100.00</TxAmt></Body></Message>`,
        'caps.305.001.01': `${buildHeader('caps.305.001.01')}\r\n<?xml version="1.0" encoding="UTF-8"?><Message xmlns="urn:caps:msg:caps.305.001.01"><Head><CorpNo>1111</CorpNo></Head><Body><ReqId>REQ-${stamp}</ReqId><DbtrProtocol>0</DbtrProtocol><DbtrActId>62220000000000008888</DbtrActId><DbtrBankId>105000</DbtrBankId><CstmrId>CUST-${stamp}</CstmrId><CstmrNm>测试用户</CstmrNm><FeeNoList>FEE001,FEE002</FeeNoList><Remark>协议签约测试</Remark></Body></Message>`,
        'caps.101.001.01': `${buildHeader('caps.101.001.01')}\r\n<?xml version="1.0" encoding="UTF-8"?><Message xmlns="urn:caps:msg:caps.101.001.01"><Head><CorpNo>1111</CorpNo></Head><Body><ReqId>REQ-${stamp}</ReqId><BatchNo>BATCH-${stamp}</BatchNo><TranCode>101</TranCode><TotalCount>2</TotalCount><TotalAmt>300.00</TotalAmt></Body></Message>`
    };
}

function fillTemplate() {
    const templates = getTemplates();
    const type = byId('requestTemplateType').value;
    byId('gatewayRequest').value = templates[type] || templates['caps.999.001.01'];
}

async function loadOverview() {
    const { body } = await request('/yht-mock/api/stats');
    const container = byId('statsCards');
    const stats = [
        ['接口记录', body.recordCount],
        ['协议数', body.protocolCount],
        ['交易数', body.tradeCount],
        ['批次数', body.batchCount],
        ['场景规则', body.scenarioCount],
        ['回调开关', body.callbackAutoPushEnabled ? '开启' : '关闭']
    ];
    container.innerHTML = stats.map(([label, value]) => `
        <div class="stat-card">
            <div class="label">${label}</div>
            <div class="value">${value}</div>
        </div>
    `).join('');
}

async function loadSettings() {
    const { body } = await request('/yht-mock/api/callback-config');
    byId('autoPushEnabled').value = String(body.autoPushEnabled);
    byId('delayMs').value = body.delayMs;
    byId('defaultTargetUrl').value = body.defaultTargetUrl;
    byId('hsmMockKey').value = body.hsmMockKey;
    byId('pushCaps107').checked = body.pushCaps107;
    byId('pushCaps205').checked = body.pushCaps205;
    byId('pushCaps306').checked = body.pushCaps306;
    byId('pushCaps308').checked = body.pushCaps308;
}

async function saveSettings() {
    const payload = {
        autoPushEnabled: byId('autoPushEnabled').value === 'true',
        delayMs: Number(byId('delayMs').value || 800),
        defaultTargetUrl: byId('defaultTargetUrl').value,
        pushCaps107: byId('pushCaps107').checked,
        pushCaps205: byId('pushCaps205').checked,
        pushCaps306: byId('pushCaps306').checked,
        pushCaps308: byId('pushCaps308').checked,
        defaultProtocolResult: 'SUCC',
        defaultTradeResult: 'SUCC',
        defaultBatchResult: 'SUCC',
        protocolNotFoundCode: 'E0001',
        protocolNotFoundMsg: '未查到协议',
        hsmMockKey: byId('hsmMockKey').value
    };
    const result = await request('/yht-mock/api/callback-config', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });
    byId('callbackResult').textContent = pretty(result.body);
    await loadOverview();
}

async function sendGateway() {
    const raw = byId('gatewayRequest').value;
    const result = await request('/yht-mock/api/gateway', {
        method: 'POST',
        headers: { 'Content-Type': 'application/xml;charset=UTF-8' },
        body: raw
    });
    byId('gatewayResponse').value = typeof result.body === 'string' ? result.body : pretty(result.body);
    await refreshAll();
}

async function runHsm() {
    const operation = byId('hsmOperation').value;
    const payload = {
        certId: byId('hsmCertId').value,
        keyIndex: byId('hsmKeyIndex').value,
        algorithm: byId('hsmAlgorithm').value,
        data: byId('hsmInput').value,
        signature: byId('hsmSignature').value,
        cipher: byId('hsmSignature').value
    };
    const result = await request(`/yht-mock/api/hsm/${operation}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });
    if (result.body && result.body.value) {
        byId('hsmSignature').value = result.body.value;
    }
    byId('hsmResult').textContent = pretty(result.body);
    await refreshLogs();
    await loadOverview();
}

async function triggerCallback() {
    const payload = {
        callbackMesgType: byId('manualCallbackType').value,
        targetUrl: byId('defaultTargetUrl').value
    };
    const result = await request('/yht-mock/api/trigger-callback', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });
    byId('callbackResult').textContent = pretty(result.body);
    await refreshAll();
}

function scenarioPayload() {
    return {
        id: Number(byId('scenarioId').value || 0),
        name: byId('scenarioName').value,
        enabled: byId('scenarioEnabled').checked,
        requestMesgType: byId('scenarioMesgType').value,
        matchAcctNo: byId('scenarioAcctNo').value,
        matchAcctSuffix: byId('scenarioAcctSuffix').value,
        matchProtocolNo: byId('scenarioProtocolNo').value,
        matchReqId: byId('scenarioReqId').value,
        matchBatchNo: byId('scenarioBatchNo').value,
        matchSysSeqNo: byId('scenarioSysSeqNo').value,
        forceResFlag: byId('scenarioResFlag').value,
        forceStatus: byId('scenarioStatus').value,
        forceRetCode: byId('scenarioRetCode').value,
        forceRetMsg: byId('scenarioRetMsg').value,
        disableAutoCallback: byId('scenarioDisableCallback').checked,
        callbackMesgType: byId('scenarioCallbackType').value,
        remark: byId('scenarioRemark').value
    };
}

function resetScenarioForm() {
    byId('scenarioId').value = '';
    byId('scenarioName').value = '';
    byId('scenarioMesgType').value = '';
    byId('scenarioAcctNo').value = '';
    byId('scenarioAcctSuffix').value = '';
    byId('scenarioProtocolNo').value = '';
    byId('scenarioReqId').value = '';
    byId('scenarioBatchNo').value = '';
    byId('scenarioSysSeqNo').value = '';
    byId('scenarioResFlag').value = '';
    byId('scenarioStatus').value = '';
    byId('scenarioRetCode').value = '';
    byId('scenarioRetMsg').value = '';
    byId('scenarioCallbackType').value = '';
    byId('scenarioRemark').value = '';
    byId('scenarioEnabled').checked = true;
    byId('scenarioDisableCallback').checked = false;
}

function loadScenarioToForm(item) {
    byId('scenarioId').value = item.id || '';
    byId('scenarioName').value = item.name || '';
    byId('scenarioMesgType').value = item.requestMesgType || '';
    byId('scenarioAcctNo').value = item.matchAcctNo || '';
    byId('scenarioAcctSuffix').value = item.matchAcctSuffix || '';
    byId('scenarioProtocolNo').value = item.matchProtocolNo || '';
    byId('scenarioReqId').value = item.matchReqId || '';
    byId('scenarioBatchNo').value = item.matchBatchNo || '';
    byId('scenarioSysSeqNo').value = item.matchSysSeqNo || '';
    byId('scenarioResFlag').value = item.forceResFlag || '';
    byId('scenarioStatus').value = item.forceStatus || '';
    byId('scenarioRetCode').value = item.forceRetCode || '';
    byId('scenarioRetMsg').value = item.forceRetMsg || '';
    byId('scenarioCallbackType').value = item.callbackMesgType || '';
    byId('scenarioRemark').value = item.remark || '';
    byId('scenarioEnabled').checked = !!item.enabled;
    byId('scenarioDisableCallback').checked = !!item.disableAutoCallback;
}

async function saveScenario() {
    const result = await request('/yht-mock/api/scenarios', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(scenarioPayload())
    });
    byId('stateDetail').value = pretty(result.body);
    resetScenarioForm();
    await refreshScenarios();
    await loadOverview();
}

async function deleteScenario(id) {
    const result = await request(`/yht-mock/api/scenarios/${id}`, { method: 'DELETE' });
    byId('stateDetail').value = pretty(result.body);
    await refreshScenarios();
    await loadOverview();
}

async function refreshScenarios() {
    const { body } = await request('/yht-mock/api/scenarios');
    const tbody = byId('scenarioTableBody');
    tbody.innerHTML = '';
    body.forEach(item => {
        const tr = document.createElement('tr');
        const matches = [
            item.matchAcctNo ? `账号=${item.matchAcctNo}` : '',
            item.matchAcctSuffix ? `尾号=${item.matchAcctSuffix}` : '',
            item.matchProtocolNo ? `协议=${item.matchProtocolNo}` : '',
            item.matchReqId ? `ReqId=${item.matchReqId}` : '',
            item.matchBatchNo ? `批次=${item.matchBatchNo}` : '',
            item.matchSysSeqNo ? `流水=${item.matchSysSeqNo}` : ''
        ].filter(Boolean).join(' / ');
        tr.innerHTML = `
            <td>${item.id}</td>
            <td>${item.name || ''}<br><small>${item.enabled ? '启用' : '停用'}</small></td>
            <td>${item.requestMesgType || ''}</td>
            <td>${matches}</td>
            <td>${item.forceResFlag || ''} ${item.forceStatus || ''} ${item.forceRetCode || ''} ${item.forceRetMsg || ''}</td>
            <td>
                <div class="row-actions">
                    <button data-action="edit">编辑</button>
                    <button data-action="delete" class="danger">删除</button>
                </div>
            </td>
        `;
        tr.querySelector('[data-action="edit"]').addEventListener('click', event => {
            event.stopPropagation();
            loadScenarioToForm(item);
        });
        tr.querySelector('[data-action="delete"]').addEventListener('click', async event => {
            event.stopPropagation();
            await deleteScenario(item.id);
        });
        tr.addEventListener('click', () => {
            byId('stateDetail').value = pretty(item);
        });
        tbody.appendChild(tr);
    });
}

function fillStateTable(targetId, items, keyField) {
    const tbody = byId(targetId);
    tbody.innerHTML = '';
    items.forEach(item => {
        const tr = document.createElement('tr');
        tr.innerHTML = `<td>${item[keyField] || ''}</td><td>${item.status || ''}</td><td>${item.scenarioName || ''}</td>`;
        tr.addEventListener('click', () => {
            byId('stateDetail').value = pretty(item);
        });
        tbody.appendChild(tr);
    });
}

async function refreshStates() {
    const [protocols, trades, batches] = await Promise.all([
        request('/yht-mock/api/protocols'),
        request('/yht-mock/api/trades'),
        request('/yht-mock/api/batches')
    ]);
    fillStateTable('protocolTableBody', protocols.body, 'protocolNo');
    fillStateTable('tradeTableBody', trades.body, 'sysSeqNo');
    fillStateTable('batchTableBody', batches.body, 'batchNo');
}

async function refreshLogs() {
    const { body } = await request('/yht-mock/api/logs?limit=100');
    const tbody = byId('recordTableBody');
    tbody.innerHTML = '';
    body.forEach(item => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>${item.id}</td>
            <td>${item.recordType || ''}</td>
            <td>${item.mesgType || ''}</td>
            <td>${item.status || ''}</td>
            <td>${item.reqId || item.protocolNo || item.batchNo || item.sysSeqNo || ''}</td>
            <td>${new Date(item.createdAt).toLocaleString()}</td>
        `;
        tr.addEventListener('click', () => {
            byId('recordDetail').value = pretty(item);
        });
        tbody.appendChild(tr);
    });
}

async function clearLogs() {
    await request('/yht-mock/api/logs', { method: 'DELETE' });
    byId('recordDetail').value = '';
    await refreshLogs();
    await loadOverview();
}

async function refreshAll() {
    await Promise.all([
        loadOverview(),
        refreshLogs(),
        refreshStates(),
        refreshScenarios()
    ]);
}

byId('fillTemplateBtn').addEventListener('click', fillTemplate);
byId('saveConfigBtn').addEventListener('click', saveSettings);
byId('sendGatewayBtn').addEventListener('click', sendGateway);
byId('runHsmBtn').addEventListener('click', runHsm);
byId('refreshLogsBtn').addEventListener('click', refreshLogs);
byId('clearLogsBtn').addEventListener('click', clearLogs);
byId('triggerCallbackBtn').addEventListener('click', triggerCallback);
byId('saveScenarioBtn').addEventListener('click', saveScenario);
byId('newScenarioBtn').addEventListener('click', resetScenarioForm);
byId('refreshScenarioBtn').addEventListener('click', refreshScenarios);
byId('refreshOverviewBtn').addEventListener('click', loadOverview);
byId('refreshStateBtn').addEventListener('click', refreshStates);

fillTemplate();
resetScenarioForm();
loadSettings();
refreshAll();
