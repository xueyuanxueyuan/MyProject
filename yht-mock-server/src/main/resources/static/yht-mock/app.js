const state = {
    records: [],
    selectedRecordId: null,
    chainRecords: []
};

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

function escapeHtml(value) {
    return String(value == null ? '' : value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/\"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function statusTone(value) {
    const text = String(value == null ? '' : value).toUpperCase();
    if (['ACCEPTED', 'SUCCESS', 'SUCC', 'OK', '200', '\u5f00\u542f', 'TRUE'].some(key => text.includes(key))) {
        return 'is-success';
    }
    if (['FAIL', 'ERROR', 'TIMEOUT', 'UNSPRT', '\u5173\u95ed', 'FALSE'].some(key => text.includes(key))) {
        return 'is-danger';
    }
    if (['PENDING', 'WAIT', 'UNKNOWN'].some(key => text.includes(key))) {
        return 'is-waiting';
    }
    return 'is-neutral';
}

function statusPill(value, className = '') {
    const label = value == null || value === '' ? 'UNKNOWN' : value;
    return '<span class=\"status-pill ' + statusTone(label) + ' ' + className + '\">' + escapeHtml(label) + '</span>';
}

function formatDateTime(value) {
    if (!value) {
        return '';
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return escapeHtml(value);
    }
    return date.toLocaleString('zh-CN', {
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false
    });
}

function statVisual(label, value) {
    const unitMap = {
        '\u63a5\u53e3\u8bb0\u5f55': '\u6761',
        '\u534f\u8bae\u72b6\u6001': '\u4e2a',
        '\u4ea4\u6613\u72b6\u6001': '\u7b14',
        '\u6279\u6b21\u72b6\u6001': '\u6279',
        '\u573a\u666f\u89c4\u5219': '\u9879'
    };
    const hintMap = {
        '\u63a5\u53e3\u8bb0\u5f55': '\u6700\u8fd1\u63a5\u53e3\u8c03\u7528\u6c89\u6dc0',
        '\u534f\u8bae\u72b6\u6001': '\u534f\u8bae\u7b7e\u7ea6\u72b6\u6001\u7f13\u5b58',
        '\u4ea4\u6613\u72b6\u6001': '\u6263\u6b3e\u4e0e\u4ea4\u6613\u72b6\u6001',
        '\u6279\u6b21\u72b6\u6001': '\u6279\u91cf\u4e1a\u52a1\u72b6\u6001',
        '\u573a\u666f\u89c4\u5219': '\u547d\u4e2d\u89c4\u5219\u914d\u7f6e',
        '\u81ea\u52a8\u56de\u8c03': '\u56de\u8c03\u63a8\u9001\u5f00\u5173'
    };
    const isNumber = typeof value === 'number' || /^\\d+(\\.\\d+)?$/.test(String(value));
    return {
        unit: unitMap[label] || '',
        hint: hintMap[label] || '',
        valueClass: isNumber ? 'value number-value' : 'value text-value ' + statusTone(value)
    };
}
function byId(id) {
    return document.getElementById(id);
}

function valueOf(id) {
    const el = byId(id);
    return el ? el.value.trim() : '';
}

function setValue(id, value) {
    const el = byId(id);
    if (el) {
        el.value = value == null ? '' : value;
    }
}

function addClick(id, handler) {
    const el = byId(id);
    if (el) {
        el.addEventListener('click', handler);
    }
}

function nowStamp() {
    const date = new Date();
    const pad = value => String(value).padStart(2, '0');
    return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`;
}

function buildHeader(mesgType) {
    const stamp = nowStamp();
    return `{H:02CAPS          CAPS    33503C5801        CAPS904290099992      CAPS${stamp.slice(0, 8)}${stamp.slice(8)}XML${mesgType.padEnd(20, ' ')}MOCKMESG${stamp.slice(-12).padEnd(12, '0')}    3U         }`;
}

function xmlEscape(value) {
    return String(value == null ? '' : value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&apos;');
}

function normalizeAmount(value) {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) {
        return parsed.toFixed(2);
    }
    return '0.00';
}

function base64Utf8(value) {
    const text = String(value == null ? '' : value);
    if (typeof TextEncoder === 'function') {
        let binary = '';
        const bytes = new TextEncoder().encode(text);
        bytes.forEach(byte => {
            binary += String.fromCharCode(byte);
        });
        return base64Utf8(binary);
    }
    return base64Utf8(unescape(encodeURIComponent(text)));
}

function getTemplates() {
    const stamp = nowStamp();
    const batchFile = base64Utf8([
        `101|33503C5801|00600|1|100.00|0|0|0|BATCH-${stamp}|${stamp.slice(0, 8)}`,
        `1|105000||62220000000000009999|MOCK-PAYER|100.00|||SERIAL-${stamp}`
    ].join('\\n'));
    return {
        'caps.999.001.01': `${buildHeader('caps.999.001.01')}

<?xml version="1.0" encoding="UTF-8"?><Message xmlns="urn:caps:msg:caps.999.001.01"><Head><CorpNo>1111</CorpNo><CheckType>LINK</CheckType><SysChckNo>CHK${stamp}</SysChckNo></Head></Message>`,
        'caps.301.001.01': `${buildHeader('caps.301.001.01')}

<?xml version="1.0" encoding="UTF-8"?><Message xmlns="urn:caps:msg:caps.301.001.01"><Head><CorpNo>1111</CorpNo></Head><Body><ReqId>REQ-${stamp}</ReqId><DbtrProtocol>MOCK-PROT-${stamp}</DbtrProtocol><DbtrActId>62220000000000008888</DbtrActId><DbtrActName>MOCK-PAYER</DbtrActName><DbtrBankId>105000</DbtrBankId><CstmrId>CUST-${stamp}</CstmrId><CstmrNm>MOCK-PAYER</CstmrNm><FeeNoList>FEE001|FEE002</FeeNoList><Remark>protocol upload</Remark></Body></Message>`,
        'caps.303.001.01': `${buildHeader('caps.303.001.01')}

<?xml version="1.0" encoding="UTF-8"?><Message xmlns="urn:caps:msg:caps.303.001.01"><Head><CorpNo>1111</CorpNo></Head><Body><ReqId>REQ-${stamp}</ReqId><DbtrProtocol>MOCK-PROT-${stamp}</DbtrProtocol><DbtrActId>62220000000000008888</DbtrActId></Body></Message>`,
        'caps.305.sign': `${buildHeader('caps.305.001.01')}

<?xml version="1.0" encoding="UTF-8"?><Message xmlns="urn:caps:msg:caps.305.001.01"><Head><CorpNo>1111</CorpNo></Head><Body><ReqId>REQ-${stamp}</ReqId><ChngTp>ADDD</ChngTp><SndrFlg>CPSD</SndrFlg><SndTp>SD00</SndTp><DbtrProtocol>0</DbtrProtocol><DbtrActId>62220000000000008888</DbtrActId><DbtrActName>MOCK-PAYER</DbtrActName><DbtrBankId>105000</DbtrBankId><CstmrId>CUST-${stamp}</CstmrId><CstmrNm>MOCK-PAYER</CstmrNm><FeeNoList>FEE001|FEE002</FeeNoList><Remark>protocol sign</Remark></Body></Message>`,
        'caps.305.cancel': `${buildHeader('caps.305.001.01')}

<?xml version="1.0" encoding="UTF-8"?><Message xmlns="urn:caps:msg:caps.305.001.01"><Head><CorpNo>1111</CorpNo></Head><Body><ReqId>REQ-${stamp}</ReqId><ChngTp>DELE</ChngTp><SndrFlg>CPSD</SndrFlg><SndTp>SD00</SndTp><DbtrProtocol>MOCK-PROT-${stamp}</DbtrProtocol><DbtrActId>62220000000000008888</DbtrActId><DbtrActName>MOCK-PAYER</DbtrActName><DbtrBankId>105000</DbtrBankId><FeeNoList>FEE001|FEE002</FeeNoList><Remark>protocol cancel</Remark></Body></Message>`,
        'caps.305.sms': `${buildHeader('caps.305.001.01')}

<?xml version="1.0" encoding="UTF-8"?><Message xmlns="urn:caps:msg:caps.305.001.01"><Head><CorpNo>1111</CorpNo></Head><Body><ReqId>REQ-${stamp}</ReqId><ChngTp>ADDD</ChngTp><SndrFlg>CPSD</SndrFlg><SndTp>SD01</SndTp><AuthCd>123456</AuthCd><DbtrProtocol>MOCK-PROT-${stamp}</DbtrProtocol><DbtrActId>62220000000000008888</DbtrActId></Body></Message>`,
        'caps.305.query': `${buildHeader('caps.305.001.01')}

<?xml version="1.0" encoding="UTF-8"?><Message xmlns="urn:caps:msg:caps.305.001.01"><Head><CorpNo>1111</CorpNo></Head><Body><ReqId>REQ-${stamp}</ReqId><OrigMsgId>ORIG-${stamp}</OrigMsgId><QueryTime>${stamp}</QueryTime><PyerBgNum>PYER-${stamp}</PyerBgNum><FeeNoList>FEE001|FEE002</FeeNoList></Body></Message>`,
        'caps.305.001.01': `${buildHeader('caps.305.001.01')}

<?xml version="1.0" encoding="UTF-8"?><Message xmlns="urn:caps:msg:caps.305.001.01"><Head><CorpNo>1111</CorpNo></Head><Body><ReqId>REQ-${stamp}</ReqId><ChngTp>ADDD</ChngTp><SndrFlg>CPSD</SndrFlg><SndTp>SD00</SndTp><DbtrProtocol>0</DbtrProtocol><DbtrActId>62220000000000008888</DbtrActId><DbtrActName>MOCK-PAYER</DbtrActName><DbtrBankId>105000</DbtrBankId><CstmrId>CUST-${stamp}</CstmrId><CstmrNm>MOCK-PAYER</CstmrNm><FeeNoList>FEE001|FEE002</FeeNoList><Remark>protocol sign</Remark></Body></Message>`,
        'caps.101.001.01': `${buildHeader('caps.101.001.01')}

<?xml version="1.0" encoding="UTF-8"?><Message xmlns="urn:caps:msg:caps.101.001.01"><Head><CorpNo>1111</CorpNo></Head><Body><ReqId>REQ-${stamp}</ReqId><BatchNo>BATCH-${stamp}</BatchNo><TranCode>101</TranCode><FeeNo>00600</FeeNo><TotalCount>1</TotalCount><TotalAmt>100.00</TotalAmt><CheckDate>${stamp.slice(0, 8)}</CheckDate><FileData>${batchFile}</FileData></Body></Message>`,
        'caps.103.001.01': `${buildHeader('caps.103.001.01')}

<?xml version="1.0" encoding="UTF-8"?><Message xmlns="urn:caps:msg:caps.103.001.01"><Head><CorpNo>1111</CorpNo></Head><Body><ReqId>REQ-${stamp}</ReqId><BatchNo>BATCH-${stamp}</BatchNo></Body></Message>`,
        'caps.105.001.01': `${buildHeader('caps.105.001.01')}

<?xml version="1.0" encoding="UTF-8"?><Message xmlns="urn:caps:msg:caps.105.001.01"><Head><CorpNo>1111</CorpNo></Head><Body><ReqId>REQ-${stamp}</ReqId><BatchNo>BATCH-${stamp}</BatchNo></Body></Message>`,
        'caps.201.001.01': `${buildHeader('caps.201.001.01')}

<?xml version="1.0" encoding="UTF-8"?><Message xmlns="urn:caps:msg:caps.201.001.01"><Head><CorpNo>1111</CorpNo></Head><Body><ReqId>REQ-${stamp}</ReqId><TranCode>201</TranCode><SysSeqNo>MOCK-SEQ-${stamp}</SysSeqNo><SerialNum>SERIAL-${stamp}</SerialNum><DbtrProtocol>MOCK-PROT-${stamp}</DbtrProtocol><DbtrActName>MOCK-PAYER</DbtrActName><DbtrActId>62220000000000009999</DbtrActId><DbtrBankId>105000</DbtrBankId><CdtrActName>MOCK-PAYEE</CdtrActName><CdtrActId>3300000000000001</CdtrActId><CdtrBankId>105000</CdtrBankId><PayAmt>CNY100.00</PayAmt><BllNb>BILL-${stamp}</BllNb></Body></Message>`,
        'caps.203.001.01': `${buildHeader('caps.203.001.01')}

<?xml version="1.0" encoding="UTF-8"?><Message xmlns="urn:caps:msg:caps.203.001.01"><Head><CorpNo>1111</CorpNo><BizDate>${stamp.slice(0, 8)}</BizDate><TranCode>201</TranCode><FeeNo>00600</FeeNo><QueryType>1</QueryType><SerialNum>SERIAL-${stamp}</SerialNum><SysSeqNo>MOCK-SEQ-${stamp}</SysSeqNo></Head></Message>`,
        'caps.307.001.01': `${buildHeader('caps.307.001.01')}

<?xml version="1.0" encoding="UTF-8"?><Message xmlns="urn:caps:msg:caps.307.001.01"><Head><CorpNo>1111</CorpNo></Head><Body><ReqId>REQ-${stamp}</ReqId><OrgnlId>MOCK-PROT-${stamp}</OrgnlId><CancelId>CANCEL-${stamp}</CancelId></Body></Message>`,
        'caps.601.001.01': `${buildHeader('caps.601.001.01')}

<?xml version="1.0" encoding="UTF-8"?><Message xmlns="urn:caps:msg:caps.601.001.01"><Head><CorpNo>1111</CorpNo><CheckDate>${stamp.slice(0, 8)}</CheckDate><TranCode>201</TranCode></Head></Message>`
    };
}

function manualMesgType(value) {
    if (String(value || '').startsWith('caps.305.')) {
        return 'caps.305.001.01';
    }
    return value || 'caps.201.001.01';
}

function fillTemplate() {
    const templates = getTemplates();
    const type = valueOf('requestTemplateType');
    setValue('gatewayRequest', templates[type] || templates[manualMesgType(type)] || templates['caps.999.001.01']);
}

function buildManualMessage() {
    const stamp = nowStamp();
    const scenario = valueOf('manualBusinessType') || 'caps.201.001.01';
    const mesgType = manualMesgType(scenario);
    const reqId = valueOf('manualReqId') || `REQ-${stamp}`;
    const accountNo = valueOf('manualAccountNo') || '62220000000000009999';
    const bankId = valueOf('manualBankId') || '105000';
    const amount = normalizeAmount(valueOf('manualAmount'));
    const customerName = valueOf('manualCustomerName') || 'MOCK-PAYER';
    const customerId = valueOf('manualCustomerId') || `CUST-${stamp}`;
    const protocolNo = valueOf('manualProtocolNo') || `MOCK-PROT-${stamp}`;
    const batchNo = valueOf('manualBatchNo') || `BATCH-${stamp}`;
    const sysSeqNo = valueOf('manualSysSeqNo') || `MOCK-SEQ-${stamp}`;
    const serialNum = sysSeqNo.startsWith('SERIAL-') ? sysSeqNo : `SERIAL-${stamp}`;
    const remark = valueOf('manualRemark') || 'manual bank input';
    const checkDate = stamp.slice(0, 8);

    let xml;
    if (scenario === 'caps.999.001.01') {
        xml = `<?xml version="1.0" encoding="UTF-8"?><Message xmlns="urn:caps:msg:${mesgType}"><Head><CorpNo>1111</CorpNo><CheckType>LINK</CheckType><SysChckNo>${xmlEscape(reqId)}</SysChckNo></Head></Message>`;
    } else if (scenario === 'caps.305.cancel') {
        xml = `<?xml version="1.0" encoding="UTF-8"?><Message xmlns="urn:caps:msg:${mesgType}"><Head><CorpNo>1111</CorpNo></Head><Body><ReqId>${xmlEscape(reqId)}</ReqId><ChngTp>DELE</ChngTp><SndrFlg>CPSD</SndrFlg><SndTp>SD00</SndTp><DbtrProtocol>${xmlEscape(protocolNo)}</DbtrProtocol><DbtrActId>${xmlEscape(accountNo)}</DbtrActId><DbtrActName>${xmlEscape(customerName)}</DbtrActName><DbtrBankId>${xmlEscape(bankId)}</DbtrBankId><FeeNoList>FEE001|FEE002</FeeNoList><Remark>${xmlEscape(remark)}</Remark></Body></Message>`;
    } else if (scenario === 'caps.305.sms') {
        xml = `<?xml version="1.0" encoding="UTF-8"?><Message xmlns="urn:caps:msg:${mesgType}"><Head><CorpNo>1111</CorpNo></Head><Body><ReqId>${xmlEscape(reqId)}</ReqId><ChngTp>ADDD</ChngTp><SndrFlg>CPSD</SndrFlg><SndTp>SD01</SndTp><AuthCd>123456</AuthCd><DbtrProtocol>${xmlEscape(protocolNo)}</DbtrProtocol><DbtrActId>${xmlEscape(accountNo)}</DbtrActId><DbtrBankId>${xmlEscape(bankId)}</DbtrBankId><Remark>${xmlEscape(remark)}</Remark></Body></Message>`;
    } else if (scenario === 'caps.305.query') {
        xml = `<?xml version="1.0" encoding="UTF-8"?><Message xmlns="urn:caps:msg:${mesgType}"><Head><CorpNo>1111</CorpNo></Head><Body><ReqId>${xmlEscape(reqId)}</ReqId><OrigMsgId>${xmlEscape(sysSeqNo)}</OrigMsgId><QueryTime>${stamp}</QueryTime><PyerBgNum>${xmlEscape(protocolNo)}</PyerBgNum><FeeNoList>FEE001|FEE002</FeeNoList><Remark>${xmlEscape(remark)}</Remark></Body></Message>`;
    } else if (scenario === 'caps.305.sign' || scenario === 'caps.305.001.01') {
        xml = `<?xml version="1.0" encoding="UTF-8"?><Message xmlns="urn:caps:msg:${mesgType}"><Head><CorpNo>1111</CorpNo></Head><Body><ReqId>${xmlEscape(reqId)}</ReqId><ChngTp>ADDD</ChngTp><SndrFlg>CPSD</SndrFlg><SndTp>SD00</SndTp><DbtrProtocol>0</DbtrProtocol><DbtrActId>${xmlEscape(accountNo)}</DbtrActId><DbtrActName>${xmlEscape(customerName)}</DbtrActName><DbtrBankId>${xmlEscape(bankId)}</DbtrBankId><CstmrId>${xmlEscape(customerId)}</CstmrId><CstmrNm>${xmlEscape(customerName)}</CstmrNm><FeeNoList>FEE001|FEE002</FeeNoList><Remark>${xmlEscape(remark)}</Remark></Body></Message>`;
    } else if (scenario === 'caps.101.001.01') {
        const fileData = base64Utf8([`101|33503C5801|00600|1|${amount}|0|0|0|${batchNo}|${checkDate}`, `1|${bankId}||${accountNo}|${customerName}|${amount}|||${serialNum}`].join('\\n'));
        xml = `<?xml version="1.0" encoding="UTF-8"?><Message xmlns="urn:caps:msg:${mesgType}"><Head><CorpNo>1111</CorpNo></Head><Body><ReqId>${xmlEscape(reqId)}</ReqId><BatchNo>${xmlEscape(batchNo)}</BatchNo><TranCode>101</TranCode><FeeNo>00600</FeeNo><TotalCount>1</TotalCount><TotalAmt>${xmlEscape(amount)}</TotalAmt><CheckDate>${checkDate}</CheckDate><FileData>${xmlEscape(fileData)}</FileData><Remark>${xmlEscape(remark)}</Remark></Body></Message>`;
    } else if (scenario === 'caps.103.001.01' || scenario === 'caps.105.001.01') {
        xml = `<?xml version="1.0" encoding="UTF-8"?><Message xmlns="urn:caps:msg:${mesgType}"><Head><CorpNo>1111</CorpNo></Head><Body><ReqId>${xmlEscape(reqId)}</ReqId><BatchNo>${xmlEscape(batchNo)}</BatchNo><Remark>${xmlEscape(remark)}</Remark></Body></Message>`;
    } else if (scenario === 'caps.203.001.01') {
        xml = `<?xml version="1.0" encoding="UTF-8"?><Message xmlns="urn:caps:msg:${mesgType}"><Head><CorpNo>1111</CorpNo><BizDate>${checkDate}</BizDate><TranCode>201</TranCode><FeeNo>00600</FeeNo><QueryType>1</QueryType><SerialNum>${xmlEscape(serialNum)}</SerialNum><SysSeqNo>${xmlEscape(sysSeqNo)}</SysSeqNo></Head></Message>`;
    } else if (scenario === 'caps.307.001.01') {
        xml = `<?xml version="1.0" encoding="UTF-8"?><Message xmlns="urn:caps:msg:${mesgType}"><Head><CorpNo>1111</CorpNo></Head><Body><ReqId>${xmlEscape(reqId)}</ReqId><OrgnlId>${xmlEscape(protocolNo)}</OrgnlId><CancelId>CANCEL-${stamp}</CancelId><Remark>${xmlEscape(remark)}</Remark></Body></Message>`;
    } else if (scenario === 'caps.601.001.01') {
        xml = `<?xml version="1.0" encoding="UTF-8"?><Message xmlns="urn:caps:msg:${mesgType}"><Head><CorpNo>1111</CorpNo><CheckDate>${checkDate}</CheckDate><TranCode>201</TranCode></Head></Message>`;
    } else {
        xml = `<?xml version="1.0" encoding="UTF-8"?><Message xmlns="urn:caps:msg:${mesgType}"><Head><CorpNo>1111</CorpNo></Head><Body><ReqId>${xmlEscape(reqId)}</ReqId><TranCode>201</TranCode><SysSeqNo>${xmlEscape(sysSeqNo)}</SysSeqNo><SerialNum>${xmlEscape(serialNum)}</SerialNum><DbtrProtocol>${xmlEscape(protocolNo)}</DbtrProtocol><DbtrActName>${xmlEscape(customerName)}</DbtrActName><DbtrActId>${xmlEscape(accountNo)}</DbtrActId><DbtrBankId>${xmlEscape(bankId)}</DbtrBankId><CdtrActName>MOCK-PAYEE</CdtrActName><CdtrActId>3300000000000001</CdtrActId><CdtrBankId>105000</CdtrBankId><PayAmt>CNY${xmlEscape(amount)}</PayAmt><BllNb>BILL-${stamp}</BllNb><Remark>${xmlEscape(remark)}</Remark></Body></Message>`;
    }

    const traceId = reqId || protocolNo || batchNo || sysSeqNo;
    return {
        traceId,
        message: `${buildHeader(mesgType)}

${xml}`,
        summary: `Generated ${mesgType}; trace key: ${traceId}`
    };
}

function buildManualToEditor() {
    const result = buildManualMessage();
    setValue('gatewayRequest', result.message);
    byId('manualBuildResult').textContent = `${result.summary}\n\n报文已放入 CAPS 网关编辑器，可继续人工调整后发送。`;
    byId('traceKeyword').value = result.traceId;
    return result;
}

async function sendManualMessage() {
    const result = buildManualToEditor();
    await sendGateway(result.traceId);
}

function resetManualForm() {
    const stamp = nowStamp();
    setValue('manualBusinessType', 'caps.201.001.01');
    setValue('manualReqId', `REQ-${stamp}`);
    setValue('manualAccountNo', '62220000000000009999');
    setValue('manualBankId', '105000');
    setValue('manualAmount', '100.00');
    setValue('manualCustomerName', '测试用户');
    setValue('manualCustomerId', `CUST-${stamp}`);
    setValue('manualProtocolNo', '');
    setValue('manualBatchNo', '');
    setValue('manualSysSeqNo', `MOCK-SEQ-${stamp}`);
    setValue('manualRemark', '人工模拟银行发起');
    byId('manualBuildResult').textContent = '已重置人工录入字段。';
}

function extractFirst(raw, tag) {
    const match = String(raw || '').match(new RegExp(`<${tag}>([^<]+)</${tag}>`, 'i'));
    return match ? match[1] : '';
}

function extractTraceIdFromMessage(raw) {
    return extractFirst(raw, 'ReqId')
        || extractFirst(raw, 'DbtrProtocol')
        || extractFirst(raw, 'BatchNo')
        || extractFirst(raw, 'SysSeqNo')
        || extractFirst(raw, 'SerialNum')
        || extractFirst(raw, 'OrigMsgId')
        || extractFirst(raw, 'PyerBgNum')
        || extractFirst(raw, 'SysChckNo')
        || '';
}

function extractTagValues(raw, tags) {
    const text = String(raw || '');
    const values = [];
    tags.forEach(tag => {
        const regex = new RegExp(`<${tag}>([^<]+)</${tag}>`, 'ig');
        let match;
        while ((match = regex.exec(text)) !== null) {
            values.push({ label: tag, value: match[1] });
        }
    });
    return values;
}

function traceKeyEntries(item) {
    if (!item) {
        return [];
    }
    const entries = [
        { label: '记录ID', value: item.id },
        { label: 'MesgId', value: item.mesgId },
        { label: 'ReqId', value: item.reqId },
        { label: '协议号', value: item.protocolNo },
        { label: '批次号', value: item.batchNo },
        { label: '流水号', value: item.sysSeqNo }
    ];
    const tags = ['ReqId', 'OrgnlReqId', 'DbtrProtocol', 'OrgnlDbtrProtocol', 'OrgnlId', 'OrigMsgId', 'PyerBgNum', 'BatchNo', 'OrgnlBatchNo', 'BtchNb', 'SysSeqNo', 'OrgnlSysSeqNo', 'SerialNum', 'MesgId', 'SysChckNo', 'CancleId', 'CancelId'];
    extractTagValues(item.requestBody, tags).forEach(entry => entries.push(entry));
    extractTagValues(item.responseBody, tags).forEach(entry => entries.push(entry));
    const seen = new Set();
    return entries
        .filter(entry => entry.value != null && String(entry.value).trim() !== '')
        .map(entry => ({ label: entry.label, value: String(entry.value).trim() }))
        .filter(entry => {
            const key = `${entry.label}:${entry.value}`;
            if (seen.has(key)) {
                return false;
            }
            seen.add(key);
            return true;
        });
}

function traceTokens(item) {
    return [
        item.id,
        item.recordType,
        item.source,
        item.target,
        item.mesgType,
        item.mesgId,
        item.reqId,
        item.protocolNo,
        item.batchNo,
        item.sysSeqNo,
        item.status,
        item.remark,
        item.requestBody,
        item.responseBody,
        ...traceKeyEntries(item).map(entry => entry.value)
    ].filter(value => value != null && value !== '').map(String);
}

function primaryTrace(item) {
    return item.reqId || item.protocolNo || item.batchNo || item.sysSeqNo || item.mesgId || item.id || '';
}

function relationLabels(item, keyEntries) {
    const itemValues = new Set(traceKeyEntries(item).map(entry => entry.value.toLowerCase()));
    return keyEntries
        .filter(entry => itemValues.has(entry.value.toLowerCase()))
        .map(entry => `${entry.label}=${entry.value}`);
}

function matchesTraceFilters(item) {
    const keyword = valueOf('traceKeyword').toLowerCase();
    const type = valueOf('traceTypeFilter').toLowerCase();
    const status = valueOf('traceStatusFilter').toLowerCase();
    const tokens = traceTokens(item).map(value => value.toLowerCase());
    const keywordOk = !keyword || tokens.some(value => value.includes(keyword));
    const typeOk = !type || tokens.some(value => value.includes(type));
    const statusOk = !status || String(item.status || '').toLowerCase().includes(status);
    return keywordOk && typeOk && statusOk;
}

function buildTraceChain(filtered) {
    const selected = state.records.find(item => item.id === state.selectedRecordId);
    const seeds = selected ? [selected] : filtered;
    const keyMap = new Map();
    seeds.forEach(item => {
        traceKeyEntries(item).forEach(entry => {
            keyMap.set(entry.value.toLowerCase(), entry);
        });
    });
    const keyword = valueOf('traceKeyword');
    if (keyword) {
        keyMap.set(keyword.toLowerCase(), { label: '查询词', value: keyword });
    }
    const keyEntries = Array.from(keyMap.values());
    if (keyEntries.length === 0) {
        return { keyEntries, records: filtered };
    }
    const records = state.records
        .map(item => ({ item, relations: relationLabels(item, keyEntries) }))
        .filter(link => link.relations.length > 0)
        .sort((a, b) => (a.item.createdAt || 0) - (b.item.createdAt || 0) || (a.item.id || 0) - (b.item.id || 0));
    return { keyEntries, records };
}

function describeDirection(item) {
    const source = item.source || '调用方';
    const target = item.target || '被调用方';
    if (item.recordType === 'GATEWAY') {
        return `${source} → 挡板网关`;
    }
    if (item.recordType === 'CALLBACK') {
        return `挡板回调 → ${target}`;
    }
    if (item.recordType === 'HSM') {
        return `调用方 → 加密机模拟`;
    }
    return `${source} → ${target}`;
}

function renderTraceChain(filtered) {
    const timeline = byId('traceChainTimeline');
    const summary = byId('traceChainSummary');
    if (!timeline || !summary) {
        return;
    }
    const chain = buildTraceChain(filtered);
    state.chainRecords = chain.records.map(link => link.item);
    if (chain.records.length === 0) {
        timeline.className = 'trace-chain-timeline empty-chain';
        timeline.textContent = '暂无可串联的调用链，请输入 ReqId / 协议号 / 批次号 / 流水号，或点击左侧接口记录。';
        summary.textContent = '未找到共享业务标识的上下游调用。';
        return;
    }
    timeline.className = 'trace-chain-timeline';
    summary.innerHTML = `<span class="summary-lead">\u5df2\u4e32\u8054 <strong>${chain.records.length}</strong> \u4e2a\u8c03\u7528\u8282\u70b9</span><span class="summary-keys">${chain.keyEntries.slice(0, 4).map(entry => `${escapeHtml(entry.label)}=${escapeHtml(entry.value)}`).join('\uff0c')}${chain.keyEntries.length > 4 ? ' \u7b49' : ''}</span>`;
    timeline.innerHTML = chain.records.map((link, index) => {
        const item = link.item;
        const selected = item.id === state.selectedRecordId ? ' selected' : '';
        return `
            <div class="chain-node${selected}" data-record-id="${item.id}">
                <div class="chain-step">${index + 1}</div>
                <div class="chain-body">
                    <div class="chain-title">
                        <span class="chain-primary"><span class="record-type-chip">${escapeHtml(item.recordType || '\u63a5\u53e3\u8c03\u7528')}</span><span class="message-type">${escapeHtml(item.mesgType || '\u672a\u77e5\u62a5\u6587')}</span></span>
                        ${statusPill(item.status, 'chain-status')}
                    </div>
                    <div class="chain-meta"><span>${escapeHtml(describeDirection(item))}</span><span class="time-soft">${formatDateTime(item.createdAt)}</span></div>
                    <div class="chain-link"><span>\u5173\u8054</span><strong>${link.relations.slice(0, 3).map(escapeHtml).join('\uff0c')}</strong>${link.relations.length > 3 ? ' \u7b49' : ''}</div>
                </div>
            </div>
        `;
    }).join('');
    timeline.querySelectorAll('.chain-node').forEach(node => {
        node.addEventListener('click', () => {
            const id = Number(node.getAttribute('data-record-id'));
            const item = state.records.find(record => record.id === id);
            if (item) {
                state.selectedRecordId = item.id;
                byId('recordDetail').value = pretty(item);
                renderLogs();
            }
        });
    });
}

function renderLogs() {
    const tbody = byId('recordTableBody');
    if (!tbody) {
        return;
    }
    const filtered = state.records.filter(matchesTraceFilters);
    tbody.innerHTML = '';
    filtered.forEach(item => {
        const tr = document.createElement('tr');
        if (item.id === state.selectedRecordId) {
            tr.classList.add('selected');
        }
        tr.innerHTML = `
            <td><span class="mono-chip">#${escapeHtml(item.id)}</span></td>
            <td><span class="record-type-chip">${escapeHtml(item.recordType || '')}</span><br><small class="flow-text">${escapeHtml(item.source || '')}${item.target ? ' \u2192 ' + escapeHtml(item.target) : ''}</small></td>
            <td><span class="message-type">${escapeHtml(item.mesgType || '')}</span><br><small class="mono-muted">${escapeHtml(item.mesgId || '')}</small></td>
            <td>${statusPill(item.status)}</td>
            <td><span class="trace-token">${escapeHtml(primaryTrace(item))}</span></td>
            <td><span class="time-soft">${formatDateTime(item.createdAt)}</span></td>
        `;
        tr.addEventListener('click', () => selectRecord(item));
        tbody.appendChild(tr);
    });
    byId('traceSummary').textContent = `共 ${state.records.length} 条接口记录，当前显示 ${filtered.length} 条。右侧会按共享 ReqId / 协议号 / 批次号 / 流水号 / MesgId 串联前后调用关系。`;
    renderTraceChain(filtered);
}

function selectRecord(item) {
    state.selectedRecordId = item.id;
    byId('recordDetail').value = pretty(item);
    const trace = primaryTrace(item);
    if (trace) {
        byId('traceKeyword').value = trace;
    }
    renderLogs();
}

async function loadOverview() {
    const { body } = await request('/yht-mock/api/stats');
    const stats = [
        ['接口记录', body.recordCount ?? body.records ?? 0],
        ['协议状态', body.protocolCount ?? body.protocols ?? 0],
        ['交易状态', body.tradeCount ?? body.trades ?? 0],
        ['批次状态', body.batchCount ?? body.batches ?? 0],
        ['场景规则', body.scenarioCount ?? body.scenarios ?? 0],
        ['自动回调', body.callbackAutoPushEnabled === false ? '关闭' : '开启']
    ];
    byId('statsCards').innerHTML = stats.map(([label, value]) => {
        const visual = statVisual(label, value);
        return `
            <div class="stat-card">
                <div class="label">${escapeHtml(label)}</div>
                <div class="${visual.valueClass}"><span>${escapeHtml(value)}</span>${visual.unit ? `<em>${visual.unit}</em>` : ''}</div>
                <div class="stat-hint">${escapeHtml(visual.hint)}</div>
            </div>
        `;
    }).join('');
}

async function loadSettings() {
    const { body } = await request('/yht-mock/api/callback-config');
    setValue('autoPushEnabled', String(body.autoPushEnabled));
    setValue('delayMs', body.delayMs);
    setValue('defaultTargetUrl', body.defaultTargetUrl);
    setValue('hsmMockKey', body.hsmMockKey);
    setValue('svsMockKey', body.svsMockKey || 'YHT-MOCK-SVS');
    setValue('svsVerifyLenient', body.svsVerifyLenient === false ? 'false' : 'true');
    byId('pushCaps107').checked = body.pushCaps107;
    byId('pushCaps205').checked = body.pushCaps205;
    byId('pushCaps306').checked = body.pushCaps306;
    byId('pushCaps308').checked = body.pushCaps308;
}

async function saveSettings() {
    const payload = {
        autoPushEnabled: valueOf('autoPushEnabled') === 'true',
        delayMs: Number(valueOf('delayMs')),
        defaultTargetUrl: valueOf('defaultTargetUrl'),
        hsmMockKey: valueOf('hsmMockKey'),
        svsMockKey: valueOf('svsMockKey'),
        svsVerifyLenient: valueOf('svsVerifyLenient') !== 'false',
        pushCaps107: byId('pushCaps107').checked,
        pushCaps205: byId('pushCaps205').checked,
        pushCaps306: byId('pushCaps306').checked,
        pushCaps308: byId('pushCaps308').checked
    };
    const result = await request('/yht-mock/api/callback-config', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });
    byId('callbackResult').textContent = pretty(result.body);
    await loadOverview();
}

async function sendGateway(traceHint) {
    const raw = byId('gatewayRequest').value;
    const result = await request('/yht-mock/api/gateway', {
        method: 'POST',
        headers: { 'Content-Type': 'application/xml;charset=UTF-8' },
        body: raw
    });
    byId('gatewayResponse').value = typeof result.body === 'string' ? result.body : pretty(result.body);
    const trace = traceHint || extractTraceIdFromMessage(raw);
    if (trace) {
        byId('traceKeyword').value = trace;
    }
    await refreshAll();
initSectionNavigation();
    renderLogs();
}

async function runHsm() {
    const operation = valueOf('hsmOperation');
    const payload = {
        certId: valueOf('hsmCertId'),
        keyIndex: valueOf('hsmKeyIndex'),
        algorithm: valueOf('hsmAlgorithm'),
        data: valueOf('hsmInput'),
        signature: valueOf('hsmSignature'),
        cipher: valueOf('hsmSignature')
    };
    const result = await request(`/yht-mock/api/hsm/${operation}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });
    if (result.body && result.body.value) {
        setValue('hsmSignature', result.body.value);
    }
    byId('hsmResult').textContent = pretty(result.body);
    byId('traceTypeFilter').value = 'HSM';
    await refreshLogs();
    await loadOverview();
}

async function triggerCallback() {
    const payload = {
        callbackMesgType: valueOf('manualCallbackType'),
        targetUrl: valueOf('defaultTargetUrl'),
        reqId: valueOf('traceKeyword')
    };
    const result = await request('/yht-mock/api/trigger-callback', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });
    byId('callbackResult').textContent = pretty(result.body);
    byId('traceTypeFilter').value = '';
    await refreshLogs();
    await loadOverview();
}

function scenarioPayload() {
    return {
        id: Number(valueOf('scenarioId')) || 0,
        name: valueOf('scenarioName'),
        enabled: byId('scenarioEnabled').checked,
        requestMesgType: valueOf('scenarioMesgType'),
        matchAcctNo: valueOf('scenarioAcctNo'),
        matchAcctSuffix: valueOf('scenarioAcctSuffix'),
        matchProtocolNo: valueOf('scenarioProtocolNo'),
        matchReqId: valueOf('scenarioReqId'),
        matchBatchNo: valueOf('scenarioBatchNo'),
        matchSysSeqNo: valueOf('scenarioSysSeqNo'),
        forceResFlag: valueOf('scenarioResFlag'),
        forceStatus: valueOf('scenarioStatus'),
        forceRetCode: valueOf('scenarioRetCode'),
        forceRetMsg: valueOf('scenarioRetMsg'),
        disableAutoCallback: byId('scenarioDisableCallback').checked,
        callbackMesgType: valueOf('scenarioCallbackType'),
        remark: valueOf('scenarioRemark')
    };
}

async function saveScenario() {
    await request('/yht-mock/api/scenarios', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(scenarioPayload())
    });
    resetScenarioForm();
    await refreshScenarios();
    await loadOverview();
}

async function deleteScenario(id) {
    await request(`/yht-mock/api/scenarios/${id}`, { method: 'DELETE' });
    await refreshScenarios();
    await loadOverview();
}

function resetScenarioForm() {
    [
        'scenarioId', 'scenarioName', 'scenarioMesgType', 'scenarioAcctNo', 'scenarioAcctSuffix',
        'scenarioProtocolNo', 'scenarioReqId', 'scenarioBatchNo', 'scenarioSysSeqNo', 'scenarioResFlag',
        'scenarioStatus', 'scenarioRetCode', 'scenarioRetMsg', 'scenarioCallbackType', 'scenarioRemark'
    ].forEach(id => setValue(id, ''));
    byId('scenarioEnabled').checked = true;
    byId('scenarioDisableCallback').checked = false;
}

function loadScenarioToForm(item) {
    setValue('scenarioId', item.id);
    setValue('scenarioName', item.name);
    setValue('scenarioMesgType', item.requestMesgType);
    setValue('scenarioAcctNo', item.matchAcctNo);
    setValue('scenarioAcctSuffix', item.matchAcctSuffix);
    setValue('scenarioProtocolNo', item.matchProtocolNo);
    setValue('scenarioReqId', item.matchReqId);
    setValue('scenarioBatchNo', item.matchBatchNo);
    setValue('scenarioSysSeqNo', item.matchSysSeqNo);
    setValue('scenarioResFlag', item.forceResFlag);
    setValue('scenarioStatus', item.forceStatus);
    setValue('scenarioRetCode', item.forceRetCode);
    setValue('scenarioRetMsg', item.forceRetMsg);
    byId('scenarioEnabled').checked = item.enabled !== false;
    byId('scenarioDisableCallback').checked = item.disableAutoCallback === true;
    setValue('scenarioCallbackType', item.callbackMesgType);
    setValue('scenarioRemark', item.remark);
    byId('stateDetail').value = pretty(item);
    location.hash = '#scenario';
}

async function refreshScenarios() {
    const { body } = await request('/yht-mock/api/scenarios');
    const tbody = byId('scenarioTableBody');
    tbody.innerHTML = '';
    body.forEach(item => {
        const matches = [
            item.matchAcctNo ? `账号=${item.matchAcctNo}` : '',
            item.matchAcctSuffix ? `尾号=${item.matchAcctSuffix}` : '',
            item.matchProtocolNo ? `协议=${item.matchProtocolNo}` : '',
            item.matchReqId ? `ReqId=${item.matchReqId}` : '',
            item.matchBatchNo ? `批次=${item.matchBatchNo}` : '',
            item.matchSysSeqNo ? `流水=${item.matchSysSeqNo}` : ''
        ].filter(Boolean).join(' / ');
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>${item.id}</td>
            <td>${item.name || ''}<br><small>${item.enabled ? '启用' : '停用'}</small></td>
            <td>${item.requestMesgType || ''}</td>
            <td>${matches}</td>
            <td>${item.forceResFlag || ''} ${item.forceStatus || ''} ${item.forceRetCode || ''} ${item.forceRetMsg || ''}</td>
            <td>
                <div class="row-actions">
                    <button data-action="edit" class="button secondary">编辑</button>
                    <button data-action="delete" class="button danger">删除</button>
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
            const trace = item[keyField];
            if (trace) {
                byId('traceKeyword').value = trace;
                location.hash = '#trace';
                renderLogs();
            }
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
    const { body } = await request('/yht-mock/api/logs?limit=500');
    state.records = Array.isArray(body) ? body : [];
    renderLogs();
}

async function clearLogs() {
    await request('/yht-mock/api/logs', { method: 'DELETE' });
    state.records = [];
    state.selectedRecordId = null;
    setValue('recordDetail', '');
    renderLogs();
    await loadOverview();
}

function resetTraceFilters() {
    setValue('traceKeyword', '');
    setValue('traceTypeFilter', '');
    setValue('traceStatusFilter', '');
    state.selectedRecordId = null;
    renderLogs();
}

async function refreshAll() {
    await Promise.all([
        loadOverview(),
        refreshLogs(),
        refreshStates(),
        refreshScenarios()
    ]);
}

addClick('fillTemplateBtn', fillTemplate);
addClick('saveConfigBtn', saveSettings);
addClick('sendGatewayBtn', () => sendGateway());
addClick('runHsmBtn', runHsm);
addClick('refreshLogsBtn', refreshLogs);
addClick('clearLogsBtn', clearLogs);
addClick('triggerCallbackBtn', triggerCallback);
addClick('saveScenarioBtn', saveScenario);
addClick('newScenarioBtn', resetScenarioForm);
addClick('refreshScenarioBtn', refreshScenarios);
addClick('refreshOverviewBtn', loadOverview);
addClick('refreshStateBtn', refreshStates);
addClick('buildManualMessageBtn', buildManualToEditor);
addClick('sendManualMessageBtn', sendManualMessage);
addClick('resetManualFormBtn', resetManualForm);
addClick('applyTraceBtn', renderLogs);
addClick('resetTraceBtn', resetTraceFilters);

['traceKeyword', 'traceTypeFilter', 'traceStatusFilter'].forEach(id => {
    const el = byId(id);
    if (el) {
        el.addEventListener('input', renderLogs);
        el.addEventListener('change', renderLogs);
        el.addEventListener('keydown', event => {
            if (event.key === 'Enter') {
                renderLogs();
            }
        });
    }
});


function initSectionNavigation() {
    const links = Array.from(document.querySelectorAll('.side-nav a[href^="#"]'));
    const sections = links
        .map(link => document.querySelector(link.getAttribute('href')))
        .filter(Boolean);

    const activate = id => {
        links.forEach(link => {
            link.classList.toggle('active', link.getAttribute('href') === '#' + id);
        });
    };

    links.forEach(link => {
        link.addEventListener('click', () => {
            const id = link.getAttribute('href').slice(1);
            activate(id);
        });
    });

    if (location.hash) {
        activate(location.hash.slice(1));
    } else if (sections.length > 0) {
        activate(sections[0].id);
    }

    if (!('IntersectionObserver' in window)) {
        return;
    }

    const observer = new IntersectionObserver(entries => {
        const visible = entries
            .filter(entry => entry.isIntersecting)
            .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];
        if (visible && visible.target.id) {
            activate(visible.target.id);
        }
    }, {
        rootMargin: '-24% 0px -62% 0px',
        threshold: [0.12, 0.28, 0.5]
    });

    sections.forEach(section => observer.observe(section));
}

fillTemplate();
resetManualForm();
resetScenarioForm();
loadSettings();
refreshAll();
