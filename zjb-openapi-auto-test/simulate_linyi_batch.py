#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""临沂环境 · 住建部批量交易大规模模拟驱动（批量收款 + 批量扣款）。

复用 zjb-openapi-auto-test 引擎（字段映射 / 城市补参 / 流水号唯一性 / 汇总），
本脚本只负责：① 生成合成(mock)对手账户；② 把 addSkywPlsk 明细模板扩展到 N 条；
③ 循环 B 批次分别构造「批量收款 / 批量扣款」；④ 可选真实调用临沂环境并出报告。

批量收款  addSkywPlsk  xtlx=GJGL / ywlx=11101 / jslx=wtsk
批量扣款  addSkywPlsk  xtlx=GDGL / ywlx=11305 / jslx=wksk  （复用收款接口）

用法：
  # 仅生成请求数据落盘（不调用，推荐先跑这个复核结构）
  python simulate_linyi_batch.py --bank-code 313000 --batches 50 --details 2000 --mode dry-run --out output/linyi_batch_sim

  # 真实调用临沂环境（首笔批量收款作为连通性探针；不可达则中止，可达则继续全量）
  python simulate_linyi_batch.py --bank-code 313000 --batches 50 --details 2000 --mode run --out output/linyi_batch_sim
"""
from __future__ import annotations

import argparse
import html
import json
import random
import sys
from datetime import datetime
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from zjb_openapi_runner import (  # noqa: E402
    DEFAULT_OPENAPI_URL,
    BankData,
    load_openapi,
    load_city_config,
    parse_json_example,
    build_case,
    call_http,
    iter_operations,
    today_yyyymmdd,
)

CITY = "linyi"
AMOUNT_PER_DETAIL = 2.0
SURNAMES = "王李张刘陈杨赵黄周吴徐孙马朱胡郭何高林罗郑梁谢宋唐许韩冯邓曹彭曾"
GIVEN = "伟芳娜秀英敏静丽强磊军洋勇艳杰娟涛明超霞平刚桂兰峰浩宇轩睿涵琪璇婷雪"


def rand_digits(n: int) -> str:
    return "".join(random.choice("0123456789") for _ in range(n))


def id18() -> str:
    # 18 位模拟身份证号（仅为仿真，非真实校验）
    return rand_digits(17) + random.choice("0123456789X")


def gen_center(bank_name: str) -> dict:
    return {
        "序号": "1",
        "账号*": "371300" + rand_digits(13),
        "户名*": "临沂市住房公积金管理中心",
        "开户银行": bank_name,
        "联行号": rand_digits(12),
    }


def gen_detail(i: int, account_type: str, bank_name: str) -> dict:
    """生成一条合成对手账户（写入 BankData 行，供引擎按字段名取值）。"""
    if account_type == "public":
        name = f"模拟对公企业{i:06d}号"
        return {
            "序号": str(i + 1),
            "账号*": "9113" + rand_digits(15),
            "户名*": name,
            "账户类型": "对公",
            "开户行": bank_name,
            "联行号": rand_digits(12),
            "证件类型": "统一社会信用代码",
            "统一社会信用代码": "91371300" + rand_digits(10),
        }
    name = random.choice(SURNAMES) + random.choice(GIVEN)
    return {
        "序号": str(i + 1),
        "账号*": "6217" + rand_digits(15),
        "姓名*": name,
        "账户类型": "对私",
        "开户行": bank_name,
        "联行号": rand_digits(12),
        "证件类型": "身份证",
        "证件号码*": id18(),
        "手机号": "1" + random.choice("356789") + rand_digits(9),
    }


def build_bank_data(details: int, account_type: str, bank_name: str, seed: int) -> BankData:
    """构造合成 BankData：中心专户 1 个 + details 个对手账户（按所选账户类型填充）。"""
    random.seed(seed)
    center = gen_center(bank_name)
    rows = [gen_detail(i, account_type, bank_name) for i in range(details)]
    if account_type == "public":
        return BankData(center_accounts=[center], private_accounts=[], public_accounts=rows, source="<synthetic-mock>")
    return BankData(center_accounts=[center], private_accounts=rows, public_accounts=[], source="<synthetic-mock>")


def expand_details(example: dict, n: int) -> dict:
    """深拷贝批量明细模板并扩展到 n 条。"""
    example = json.loads(json.dumps(example))
    for list_key in ("skywPlmxAddReqDTOList", "fkywPlmxAddReqDTOList"):
        if list_key in example and example[list_key]:
            template = json.loads(json.dumps(example[list_key][0]))
            example[list_key] = [json.loads(json.dumps(template)) for _ in range(n)]
    return example


def post_process(request: dict, account_type: str) -> dict:
    """按账户类型覆盖 jyzhlx（对公=2/对私=1），兼容批量(包裹+明细)结构。"""
    jyzhlx = "2" if account_type == "public" else "1"
    if "jyzhlx" in request:
        request["jyzhlx"] = jyzhlx
    header = request.get("zjjsSFkywxxAddReqDTO")
    if isinstance(header, dict):
        header["jyzhlx"] = jyzhlx
    for list_key in ("skywPlmxAddReqDTOList", "fkywPlmxAddReqDTOList"):
        for item in request.get(list_key, []):
            if isinstance(item, dict):
                item["jyzhlx"] = jyzhlx
    return request


def find_op(spec: dict, keyword: str) -> dict:
    for op in iter_operations(spec):
        if keyword in op["summary"]:
            return op
    raise SystemExit(f"未找到接口: {keyword}")


def write_report(output_dir: Path, summary: dict, mode: str) -> None:
    rows = []
    for r in summary["results"]:
        status_text = "通过" if r.get("passed") else ("失败" if r.get("mode") == "run" else "未发起")
        detail = r.get("error") or r.get("response_preview") or ""
        rows.append(
            "<tr>" + f"<td>{html.escape(str(r.get('batch')))}</td><td>{html.escape(str(r.get('business')))}</td>"
            f"<td>{html.escape(status_text)}</td><td>{html.escape(str(r.get('http_status')))}</td>"
            f"<td>{html.escape(str(r.get('code')))}</td><td>{html.escape(str(r.get('msg')))}</td>"
            f"<td>{html.escape(str(r.get('elapsed_ms')))}</td><td><pre>{html.escape(str(detail))}</pre></td>" + "</tr>"
        )
    html_text = f"""<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>临沂批量交易模拟报告</title>
<style>body{{font-family:Arial,'Microsoft YaHei',sans-serif;margin:24px}}table{{border-collapse:collapse;width:100%}}
td,th{{border:1px solid #ddd;padding:8px;vertical-align:top}}th{{background:#f5f5f5}}pre{{white-space:pre-wrap;max-height:220px;overflow:auto}}</style></head>
<body><h1>临沂环境 · 住建部批量交易模拟报告</h1>
<p>生成时间：{html.escape(summary['generated_at'])}；模式：{html.escape(mode)}；城市：{html.escape(summary['city'])}；
银行：{html.escape(summary['bankName'])}（{html.escape(summary['bankCode'])}）；批次：{summary['batches']}；每批明细：{summary['details']}；
账户类型：{html.escape(summary['accountType'])}；每笔金额：{summary['amountPerDetail']}</p>
<table><thead><tr><th>批次</th><th>业务</th><th>结果</th><th>HTTP</th><th>code</th><th>msg</th><th>耗时ms</th><th>错误/响应</th></tr></thead>
<tbody>{''.join(rows)}</tbody></table></body></html>"""
    (output_dir / "report.html").write_text(html_text, encoding="utf-8")


def main() -> int:
    ap = argparse.ArgumentParser(description="临沂环境 · 住建部批量交易大规模模拟（批量收款+批量扣款）")
    ap.add_argument("--openapi-url", default=DEFAULT_OPENAPI_URL)
    ap.add_argument("--openapi-file", default="", help="本地 OpenAPI/Swagger JSON，优先于 --openapi-url")
    ap.add_argument("--city", default=CITY, help="city-config.json 城市键，默认 linyi")
    ap.add_argument("--bank-code", required=True, help="银行代码 yhdm（6位），如 313000；临沂需用户确认实际值")
    ap.add_argument("--bank-name", default="", help="银行名称，缺省按 模拟-<bank-code> 生成")
    ap.add_argument("--batches", type=int, default=50, help="批量业务笔数（默认 50）")
    ap.add_argument("--details", type=int, default=2000, help="每笔批量业务的明细条数（默认 2000）")
    ap.add_argument("--account-type", choices=["private", "public"], default="private", help="明细账户类型，默认 对私")
    ap.add_argument("--amount", type=float, default=AMOUNT_PER_DETAIL, help="每笔明细金额，默认 2.0")
    ap.add_argument("--tx-type", choices=["sk", "kk", "all"], default="all", help="sk=仅批量收款 / kk=仅批量扣款 / all=两者")
    ap.add_argument("--mode", choices=["dry-run", "run"], default="dry-run", help="dry-run=仅生成 / run=真实调用")
    ap.add_argument("--out", default="output/linyi_batch_sim", help="输出目录")
    ap.add_argument("--timeout", type=int, default=120, help="单笔 HTTP 超时(秒)，默认 120")
    ap.add_argument("--expect-http", default="2xx")
    ap.add_argument("--save-requests", action="store_true", help="run 模式下也落盘每笔请求体（默认仅 dry-run 落盘）")
    args = ap.parse_args()

    bank_code = args.bank_code
    bank_name = args.bank_name or f"模拟银行({bank_code})"
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    req_dir = out / "requests"
    resp_dir = out / "responses"

    spec = load_openapi(args.openapi_url, args.openapi_file or None)
    city, fixed_params, base_url = load_city_config(HERE / "city-config.json", args.city)
    receipt_op = find_op(spec, "新增批量收款请求")  # addSkywPlsk

    businesses = []
    if args.tx_type in ("sk", "all"):
        businesses.append(("批量收款", None))
    if args.tx_type in ("kk", "all"):
        businesses.append(("批量扣款", {"xtlx": "GDGL", "ywlx": "11305", "jslx": "wksk"}))

    headers = {"Content-Type": "application/json"}
    results = []
    reached = None  # 首笔调用兼作连通性探针

    for b in range(args.batches):
        bank = build_bank_data(args.details, args.account_type, bank_name, seed=1000 + b)
        for business, overrides in businesses:
            op_copy = dict(receipt_op)
            op_copy["example"] = expand_details(parse_json_example(receipt_op["example"], receipt_op["summary"]), args.details)
            case = build_case(
                op_copy, bank, args.account_type, bank_code, bank_name,
                today_yyyymmdd(), args.amount, fixed_params, city, base_url, overrides,
            )
            case["request"] = post_process(case["request"], args.account_type)
            req_path = req_dir / f"batch{b+1:03d}_{business}_request.json"

            if args.mode == "dry-run" or args.save_requests:
                req_dir.mkdir(parents=True, exist_ok=True)
                req_path.write_text(json.dumps(case["request"], ensure_ascii=False, indent=2), encoding="utf-8")

            if args.mode == "run":
                r = call_http(base_url, case, headers, args.timeout, args.expect_http)
                if reached is None:
                    reached = r["status"] is not None
                    if not reached:
                        print(f"[探针失败] 首笔批量收款(batch1)无法到达临沂环境 {base_url}：{r['error']}；中止全量执行。")
                        results.append(_result_row(b + 1, business, "run", r, req_path))
                        summary = _summary(args, city, bank_name, bank_code, base_url, results)
                        (out / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
                        write_report(out, summary, args.mode)
                        return 1
                    print(f"[探针成功] 首笔批量收款(batch1)已到达临沂环境 http={r['status']}；继续全量 {args.batches}×{len(businesses)} 笔。")
                try:
                    body = json.loads(r["response_preview"]) if r["response_preview"] else None
                except Exception:
                    body = r["response_preview"]
                data = (body or {}).get("data") or {}
                code = (body or {}).get("code")
                msg = (body or {}).get("msg")
                print(f"[batch{b+1:03d}] {business}: http={r['status']} passed={r['passed']} code={code} msg={msg} jslsh={data.get('jslsh')} elapsed={r['elapsed_ms']}ms err={r['error']}")
                resp_dir.mkdir(parents=True, exist_ok=True)
                (resp_dir / f"batch{b+1:03d}_{business}_response.json").write_text(json.dumps(r, ensure_ascii=False, indent=2), encoding="utf-8")
                results.append(_result_row(b + 1, business, "run", r, req_path, code, msg, body))
            else:
                results.append({"batch": b + 1, "business": business, "mode": "dry-run",
                                "path": case["path"], "request_file": str(req_path),
                                "detail_count": len(case["request"].get("skywPlmxAddReqDTOList", []))})

    summary = _summary(args, city, bank_name, bank_code, base_url, results)
    (out / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    write_report(out, summary, args.mode)
    print(f"\n汇总：{out / 'summary.json'}")
    if args.mode == "run":
        failed = [r for r in results if not r.get("passed")]
        print(f"执行结果：通过 {len(results) - len(failed)}，失败 {len(failed)}")
        return 1 if failed else 0
    print(f"dry-run 生成 {len(results)} 个批量请求（{args.batches} 笔 × {len(businesses)} 业务，每笔 {args.details} 明细）。")
    return 0


def _result_row(batch, business, mode, r, req_path, code=None, msg=None, body=None):
    try:
        preview = (body or {}).get("msg") or (r.get("response_preview") or "")[:800]
    except Exception:
        preview = r.get("response_preview") or ""
    return {
        "batch": batch, "business": business, "mode": mode,
        "path": r.get("path"), "http_status": r.get("status"), "passed": r.get("passed"),
        "code": code, "msg": msg, "elapsed_ms": r.get("elapsed_ms"),
        "error": r.get("error"), "response_preview": preview,
        "request_file": str(req_path),
    }


def _summary(args, city, bank_name, bank_code, base_url, results):
    return {
        "generated_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "environment": "临沂", "city": city, "bankName": bank_name, "bankCode": bank_code,
        "baseUrl": base_url, "batches": args.batches, "details": args.details,
        "accountType": args.account_type, "amountPerDetail": args.amount,
        "txType": args.tx_type, "mode": args.mode,
        "results": results,
    }


if __name__ == "__main__":
    raise SystemExit(main())
