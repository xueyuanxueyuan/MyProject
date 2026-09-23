#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""建行(105000)对私账户有效性校验批量工具。

读取 建行测试数据预埋收集表-建设银行.xlsx 的"对私账户信息"sheet，
对每一户构造 khzhyxxCx(客户账户有效查询) 请求并逐笔发起，
解析响应中 data.cxjg / msg 判定账户有效性，输出 HTML + JSON 报告。

请求头部参数沿用用户在 apipost 上测 khzhyxxCx 所用的嘉兴环境配置：
  zxbh=330400000000000, qdlx=01, qdbm=gt, jbjgbh=0101, jbjgmc=市本级,
  xtlx=GJGL, yhdm=105000
每户户级字段：zhlx=1(对私), zh, hm, zjlx(身份证→01), zjh, jyzhlx=1
"""
from __future__ import annotations

import json
import time
import urllib.request
import urllib.error
from datetime import datetime
from pathlib import Path

import openpyxl

# ---------- 配置 ----------
XLSX = Path(r"D:\文档\工作文档\嘉兴住建部测试数据\建行测试数据预埋收集表-建设银行.xlsx")
SHEET_NAME = "对私账户信息"
BASE_URL = "http://k8s.gjj.jxgjj:31129"
BANK_NAME = "建设银行"
BANK_CODE = "105000"
OUT_DIR = Path(r"D:\Probject\Gjj\.codex-run\ccb-105000-private-account-validity")
TIMEOUT = 30

# 用户在 apipost 上验证过的固定头部
HEADER_PARAMS = {
    "zxbh": "330400000000000",
    "qdlx": "01",
    "qdbm": "gt",
    "jbjgbh": "0101",
    "jbjgmc": "市本级",
    "xtlx": "GJGL",
    "yhdm": BANK_CODE,
}
# 候选路径（先 ywgl 中信先例跑通；设计文档另记 cxtj）
CANDIDATE_PATHS = ["/api/v1/ywgl/khzhyxxCx", "/api/v1/cxtj/khzhyxxCx"]

ZJLX_MAP = {"身份证": "01", "统一社会信用代码": "18", "组织机构代码": "10"}


def read_private_accounts() -> list[dict]:
    wb = openpyxl.load_workbook(XLSX, data_only=True, read_only=True)
    ws = wb[SHEET_NAME]
    rows = [list(r) for r in ws.iter_rows(values_only=True)]
    header = [("" if c is None else str(c).strip()) for c in rows[0]]
    print("表头:", header)
    # 定位列索引
    def col(name_part):
        for i, h in enumerate(header):
            if name_part in h:
                return i
        raise RuntimeError(f"未找到包含 '{name_part}' 的列")

    i_seq = col("序号")
    i_zh = col("账号")
    i_zjlx = col("证件类型")
    i_zjh = col("证件号码")
    i_hm = col("姓名")
    i_zhlx = col("账户类型")

    accounts = []
    for r in rows[1:]:
        if not r or all(c is None or str(c).strip() == "" for c in r):
            continue
        zh = "" if r[i_zh] is None else str(r[i_zh]).strip()
        if not zh:
            continue  # 空账号跳过
        hm = "" if r[i_hm] is None else str(r[i_hm]).strip()
        zjlx_raw = "" if r[i_zjlx] is None else str(r[i_zjlx]).strip()
        zjh = "" if r[i_zjh] is None else str(r[i_zjh]).strip()
        zjlx = ZJLX_MAP.get(zjlx_raw, zjlx_raw)
        accounts.append({
            "seq": "" if r[i_seq] is None else str(r[i_seq]).strip(),
            "zh": zh,
            "hm": hm,
            "zjlx": zjlx,
            "zjh": zjh,
            "zhlx_raw": "" if r[i_zhlx] is None else str(r[i_zhlx]).strip(),
        })
    return accounts


def build_body(acc: dict) -> dict:
    body = dict(HEADER_PARAMS)
    body.update({
        "zhlx": "1",               # 对私
        "zh": acc["zh"],
        "hm": acc["hm"],
        "zjlx": acc["zjlx"] or "01",
        "zjh": acc["zjh"],
        "jyzhlx": "1",
    })
    return body


def post_json(path: str, body: dict) -> tuple[int, str, dict | None]:
    url = BASE_URL + path
    data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Content-Type", "application/json; charset=utf-8")
    req.add_header("Accept", "application/json")
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
            raw = resp.read().decode("utf-8", "replace")
            status = resp.status
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        status = e.code
    except Exception as e:  # noqa: BLE001
        return -1, str(e), None
    elapsed = (time.perf_counter() - t0) * 1000.0
    try:
        parsed = json.loads(raw)
    except Exception:  # noqa: BLE001
        parsed = None
    return status, raw, parsed


def parse_validation(parsed: dict | None) -> dict:
    """从响应解析账户有效性结论。"""
    if parsed is None:
        return {"code": None, "msg": "", "isSuccess": None, "data": None,
                "validationSuccess": False, "validationResultCode": None,
                "accountStatus": None, "accountLevel": None}
    data = parsed.get("data") or {}
    cxjg = data.get("cxjg")
    msg = parsed.get("msg") or data.get("msg") or ""
    # 接口层成功：code=0 / isSuccess=true
    code = parsed.get("code")
    is_success = parsed.get("isSuccess")
    # 账户有效性：cxjg=0 或 消息含"校验成功"
    valid = (str(cxjg) == "0") or ("校验成功" in str(msg))
    return {
        "code": code,
        "msg": str(msg),
        "isSuccess": is_success,
        "data": data,
        "validationSuccess": valid,
        "validationResultCode": cxjg,
        "accountStatus": data.get("zhzt"),
        "accountLevel": data.get("zhjb"),
    }


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    accounts = read_private_accounts()
    print(f"读取到 {len(accounts)} 个对私账户：")
    for a in accounts:
        print(f"  {a['seq']:>2}  {a['zh']}  {a['hm']}  zjlx={a['zjlx']}  zjh={a['zjh']}")

    # 探路：用首户同时测候选路径，选返回真实业务响应的
    first = accounts[0]
    first_body = build_body(first)
    print(f"\n=== 路径探测（首户 {first['zh']} {first['hm']}）===")
    chosen_path = None
    probe = {}
    for p in CANDIDATE_PATHS:
        st, raw, parsed = post_json(p, first_body)
        v = parse_validation(parsed)
        probe[p] = {"httpStatus": st, "hasCxjg": v["validationResultCode"] is not None,
                    "msg": v["msg"][:60]}
        print(f"  {p} -> HTTP {st}, cxjg={v['validationResultCode']}, msg={v['msg'][:60]}")
        if st == 200 and v["validationResultCode"] is not None and chosen_path is None:
            chosen_path = p
    if chosen_path is None:
        chosen_path = CANDIDATE_PATHS[0]
        print(f"  警告：候选路径均未返回业务响应，回退默认 {chosen_path}")
    print(f"选定路径：{chosen_path}\n")

    # 全量校验
    results = []
    for idx, acc in enumerate(accounts, 1):
        body = build_body(acc)
        st, raw, parsed = post_json(chosen_path, body)
        v = parse_validation(parsed)
        rec = {
            "seq": idx,
            "accountNo": acc["zh"],
            "accountName": acc["hm"],
            "idType": acc["zjlx"],
            "idNo": acc["zjh"],
            "path": chosen_path,
            "httpStatus": st,
            "httpPassed": st == 200,
            "code": v["code"],
            "msg": v["msg"],
            "isSuccess": v["isSuccess"],
            "data": v["data"],
            "validationSuccess": v["validationSuccess"],
            "validationResultCode": v["validationResultCode"],
            "accountStatus": v["accountStatus"],
            "accountLevel": v["accountLevel"],
            "rawResponse": raw,
            "requestBody": body,
        }
        results.append(rec)
        tag = "有效" if v["validationSuccess"] else "未通过"
        print(f"  [{idx}/{len(accounts)}] {acc['zh']} {acc['hm']}: HTTP {st} | {tag} | cxjg={v['validationResultCode']} | {v['msg'][:50]}")

    success = sum(1 for r in results if r["validationSuccess"])
    failed = len(results) - success
    http_ok = sum(1 for r in results if r["httpPassed"])
    summary = {
        "generatedAt": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "city": "jiaxing",
        "baseUrl": BASE_URL,
        "bankName": BANK_NAME,
        "bankCode": BANK_CODE,
        "excel": str(XLSX),
        "interfaceName": "客户账户有效查询",
        "path": chosen_path,
        "pathProbe": probe,
        "accountType": "对私",
        "accountCount": len(results),
        "httpPassed": http_ok,
        "httpFailed": len(results) - http_ok,
        "validationSuccess": success,
        "validationFailed": failed,
        "results": results,
    }
    json_path = OUT_DIR / "account-validity-summary.json"
    json_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")

    # HTML 报告
    html = build_html(summary)
    html_path = OUT_DIR / "account-validity-report.html"
    html_path.write_text(html, encoding="utf-8")
    print(f"\n报告已生成：\n  JSON: {json_path}\n  HTML: {html_path}")
    print(f"汇总：HTTP通过 {http_ok}/{len(results)}，账户有效 {success}，未通过 {failed}")


def build_html(s: dict) -> str:
    rows = []
    for r in s["results"]:
        tag = "有效" if r["validationSuccess"] else "未通过"
        color = "#167c3b" if r["validationSuccess"] else "#c0392b"
        rows.append(
            f"<tr><td>{r['seq']}</td><td>{r['accountNo']}</td><td>{r['accountName']}</td>"
            f"<td>{r['idType']}</td><td>{r['idNo']}</td><td>{r['httpStatus']}</td>"
            f"<td style='color:{color};font-weight:600'>{tag}</td>"
            f"<td>{r['validationResultCode']}</td><td>{r['accountStatus']}</td>"
            f"<td>{r['accountLevel']}</td><td>{r['msg']}</td></tr>"
        )
    probe_html = "<br>".join(f"{k}: HTTP {v['httpStatus']}, cxjg={v['hasCxjg']}, {v['msg']}" for k, v in s.get("pathProbe", {}).items())
    return f"""<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8">
<title>建行对私账户有效性校验报告</title>
<style>
 body{{font-family:-apple-system,'Microsoft YaHei',sans-serif;margin:24px;color:#222}}
 h1{{font-size:22px}} h2{{font-size:16px;margin-top:24px}}
 table{{border-collapse:collapse;width:100%;font-size:13px;margin-top:8px}}
 th,td{{border:1px solid #ddd;padding:6px 8px;text-align:left}}
 th{{background:#f5f5f5}}
 .meta{{color:#555;font-size:13px;line-height:1.7}}
 .ok{{color:#167c3b;font-weight:600}} .bad{{color:#c0392b;font-weight:600}}
 .probe{{background:#fafafa;border:1px solid #eee;padding:8px;font-size:12px;color:#555}}
</style></head><body>
<h1>建设银行（{s['bankCode']}）对私账户有效性校验报告</h1>
<div class="meta">
 生成时间：{s['generatedAt']}　|　城市：{s['city']}　|　环境：{s['baseUrl']}<br>
 接口：{s['interfaceName']}　|　路径：<b>{s['path']}</b>　|　账户类型：{s['accountType']}<br>
 数据源：{s['excel']}<br>
 汇总：HTTP 通过 <b class="ok">{s['httpPassed']}/{s['accountCount']}</b>，
 账户有效 <b class="ok">{s['validationSuccess']}</b>，
 未通过 <b class="bad">{s['validationFailed']}</b>
</div>
<h2>路径探测（首户）</h2>
<div class="probe">{probe_html}</div>
<h2>逐户结果</h2>
<table>
<tr><th>序号</th><th>账号</th><th>户名</th><th>证件类型</th><th>证件号</th><th>HTTP</th>
<th>校验结论</th><th>cxjg</th><th>账户状态</th><th>账户级别</th><th>返回消息</th></tr>
{''.join(rows)}
</table>
<h2>完整请求/响应明细</h2>
{"".join(
    f"<h3>[{r['seq']}] {r['accountName']} ({r['accountNo']})</h3>"
    f"<div class='meta'>校验结论：<b style='color:{'#167c3b' if r['validationSuccess'] else '#c0392b'}'>"
    f"{'有效' if r['validationSuccess'] else '未通过'}</b>　code={r['code']}　isSuccess={r['isSuccess']}　"
    f"cxjg={r['validationResultCode']}</div>"
    f"<p><b>请求：</b></p><pre>{json.dumps(r['requestBody'], ensure_ascii=False, indent=2)}</pre>"
    f"<p><b>响应：</b></p><pre>{r['rawResponse']}</pre>"
    for r in s['results'])}
</body></html>"""


if __name__ == "__main__":
    main()
