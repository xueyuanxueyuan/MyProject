const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const pagePath = path.resolve(__dirname, '../../main/resources/static/yht-mock/index.html');
const bytes = fs.readFileSync(pagePath);
const html = new TextDecoder('utf-8', { fatal: true }).decode(bytes);

test('homepage uses UTF-8 without BOM and has an intact Chinese title', () => {
    assert.notEqual(bytes.subarray(0, 3).toString('hex'), 'efbbbf');
    assert.match(html, /<meta charset="UTF-8">/);
    assert.match(html, /<title>一户通独立挡板<\/title>/);
});

test('homepage contains no known encoding damage or broken closing tags', () => {
    assert.doesNotMatch(html, /[\uE000-\uF8FF\uFFFD]|涓€|鍗忚|鍥炶|鎸℃|\?\/[a-z]/);
    for (const line of html.split(/\r?\n/)) {
        assert.equal((line.match(/"/g) || []).length % 2, 0, 'Unclosed attribute: ' + line);
    }
});

test('homepage closes its HTML elements in order', () => {
    const stack = [];
    const voidElements = new Set(['meta', 'link', 'input', 'br', 'hr', 'img']);
    for (const match of html.matchAll(/<(\/?)([a-z][a-z0-9-]*)\b[^>]*>/gi)) {
        const [, closing, name] = match;
        if (closing) {
            assert.equal(stack.pop(), name, 'Unexpected closing tag: ' + match[0]);
        } else if (!voidElements.has(name)) {
            stack.push(name);
        }
    }
    assert.deepEqual(stack, []);
});

test('homepage preserves bank signing and movement settings', () => {
    for (const id of ['manualFeeNoList', 'movementEnabled', 'movementTargetUrl',
        'movementReceiveCode', 'movementPayCode', 'saveMovementConfigBtn',
        'counterpartyBankId', 'counterpartyBankName', 'counterpartyAccountNo',
        'counterpartyAccountName', 'counterpartyAccountBankId', 'counterpartyEnabled',
        'saveBankCounterpartyBtn', 'bankCounterpartyTableBody']) {
        assert.ok(html.includes('id="' + id + '"'), 'Missing control: ' + id);
    }
    assert.match(html, /value="caps\.305\.bank-sign"/);
    assert.match(html, /value="caps\.305\.bank-cancel"/);
    assert.match(html, /value="MOVEMENT"/);
    assert.match(html, /<script src="\/yht-mock\/app\.js"><\/script>/);
});

const vm = require('node:vm');
const script = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/yht-mock/app.js'), 'utf8');
const base64Function = script.slice(script.indexOf('function base64Utf8('), script.indexOf('\nfunction getTemplates('));

for (const useTextEncoder of [true, false]) {
    test('UTF-8 Base64 encoding terminates and round-trips with TextEncoder=' + useTextEncoder, () => {
        for (const input of ['', 'CAPS|100.00', '一户通测试用户', '测试😀']) {
            const actual = vm.runInNewContext(base64Function + '\nbase64Utf8(input)', {
                input,
                TextEncoder: useTextEncoder ? TextEncoder : undefined,
                btoa
            }, { timeout: 1000 });
            assert.equal(actual, Buffer.from(input, 'utf8').toString('base64'));
        }
    });
}
