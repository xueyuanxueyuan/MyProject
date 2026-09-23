const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../../main/resources/static/yht-mock');
const html = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
const source = fs.readFileSync(path.join(root, 'business.js'), 'utf8');

test('business query page exposes tabbed ledgers, pagination and batch detail panel', () => {
    for (const id of ['business', 'businessAutoRefresh', 'refreshBusinessBtn', 'businessSummary',
        'businessTabTrade', 'businessTabBatch', 'businessTabProtocol',
        'businessPanelTrade', 'businessPanelBatch', 'businessPanelProtocol',
        'businessTradeBody', 'businessBatchBody', 'businessProtocolBody',
        'businessTradePageInfo', 'businessTradePrevBtn', 'businessTradeNextBtn', 'businessTradeSize',
        'businessBatchPageInfo', 'businessBatchPrevBtn', 'businessBatchNextBtn', 'businessBatchSize',
        'businessProtocolPageInfo', 'businessProtocolPrevBtn', 'businessProtocolNextBtn', 'businessProtocolSize',
        'repairBatchBtn', 'businessRepairResult',
        'batchDetailDialog', 'batchDetailSummary', 'batchDetailBody', 'closeBatchDetailBtn']) {
        assert.ok(html.includes('id="' + id + '"'), 'Missing control: ' + id);
    }
    assert.ok(html.includes('src="/yht-mock/business.js"'));
    assert.doesNotMatch(html, /onclick=/);
    // 明细必须是弹窗（dialog）而非页面底部内联卡片，且对话框位于区块之外，避免祖先 hidden 影响展示。
    assert.equal((html.match(/id="batchDetailDialog"/g) || []).length, 1);
    assert.match(html, /<dialog id="batchDetailDialog" class="mock-dialog">/);
    assert.doesNotMatch(html, /id="batchDetailCard"/);
});

test('business script can repair legacy processing batches through the management API', () => {
    assert.match(source, /async function repairProcessingBatches\(/);
    assert.match(source, /'\/yht-mock\/api\/batches\/repair-processing'/);
    assert.match(source, /repairBatchBtn/);
    assert.match(source, /confirm\(/);
});

test('batch detail dialog uses modal APIs and package-level flow wording', () => {
    assert.match(source, /dialog\.showModal\(\)/);
    assert.match(source, /dialog\.close\(\)/);
    assert.match(source, /body\.packageFlow/);
    assert.match(source, /body\.successAmount/);
    assert.doesNotMatch(source, /detail\.flowStatus/);
});

test('business script paginates on the server and switches tabs without reloading the page', () => {
    new vm.Script(source);
    assert.match(source, /async function refreshBusiness\(/);
    assert.match(source, /function setBusinessType\(/);
    assert.match(source, /function stepBusinessPage\(/);
    assert.match(source, /URLSearchParams\(\{ page: String\(businessState\.pages\[type\]\), size: String\(businessSize\(type\)\) \}\)/);
    assert.match(source, /'\/yht-mock\/api\/business\/' \+ type \+ '\?' \+ query/);
    assert.match(source, /async function loadBatchDetails\(/);
    assert.match(source, /setInterval\(/);
    assert.match(source, /document\.addEventListener\('DOMContentLoaded', initBusinessSection\)/);
});

test('trace panel separates raw and decrypted request/response messages', () => {
    for (const id of ['recordRequestRaw', 'recordRequestPlain', 'recordResponseRaw', 'recordResponsePlain']) {
        assert.ok(html.includes('id="' + id + '"'), 'Missing control: ' + id);
    }
    const app = fs.readFileSync(path.join(root, 'app.js'), 'utf8');
    assert.ok(app.includes('item.decryptedRequestBody'), 'app.js must surface decrypted request body');
    assert.ok(app.includes('item.decryptedResponseBody'), 'app.js must surface decrypted response body');
});

test('business script parses and wires realtime refresh plus batch drill-down', () => {
    new vm.Script(source);
    assert.match(source, /function applyBusinessAutoRefresh\(/);
    assert.match(source, /function renderBusinessPage\(/);
    assert.match(source, /function renderBusinessSummary\(/);
});

test('business status labels map known codes to Chinese and keep unknown codes visible', () => {
    const functions = source.slice(source.indexOf('function businessStatusText('),
        source.indexOf('function businessCell('));
    const labels = source.slice(source.indexOf('const businessStatusLabels = {'),
        source.indexOf('};', source.indexOf('const businessStatusLabels = {')) + 2);
    const run = value => vm.runInNewContext(labels + '\n' + functions + '\nbusinessStatusText(value)',
        { value }, { timeout: 1000 });
    assert.equal(run('SUCC'), 'SUCC（成功）');
    assert.equal(run('PROC'), 'PROC（处理中）');
    assert.equal(run('XYZ'), 'XYZ');
    assert.equal(run(''), '未知');
});
