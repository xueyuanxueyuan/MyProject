#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
交易类接口自动化测试脚本（仅覆盖交易类，不含签约类）

覆盖范围（交易类 / 住建部）：
  单笔收款  POST /api/v1/ywgl/addSkywDbsk
  批量收款  POST /api/v1/ywgl/addSkywPlsk
  单笔付款  POST /api/v1/ywgl/addFkywDbfk
  批量付款  POST /api/v1/ywgl/addFkywPlfk
  批量扣款  POST /api/v1/ywgl/addSkywPlsk  （复用批量收款接口，xtlx=GDGL / ywlx=11305 / jslx=wksk）

设计原则：
  - 签约类接口（plQyJysq / getDbqyMessage 等）统一由签约自动化测试脚本负责，本脚本不测。
  - 通过路径白名单 TRADE_INIT_PATHS 硬性排除签约类接口，确保本脚本只跑交易类。
  - 环境配置统一引用同目录的 city-config.json，与签约自动化测试脚本共用同一份配置，不产生多份独立配置。
  - 复用 zjb_openapi_runner 的引擎（OpenAPI 解析、Excel 读取、请求构造、调用、报告），不重复实现。

用法：
  # 仅生成用例（不调用）
  python jiaoyi_runner.py --city jiaxing --excel "<银行xlsx>" --output-dir tmp/jiaoyi
  # 真实调用（需测试环境与数据可用）
  python jiaoyi_runner.py --mode run --city jiaxing --excel "<银行xlsx>" --bank-code 313331 --bank-name 杭州银行
  # 只测批量扣款（复用批量收款接口，自动切换 xtlx/ywlx/jslx）
  python jiaoyi_runner.py --mode run --city jiaxing --excel "<银行xlsx>" --tx-type kk --bank-code 313331
  # 只测收款 / 付款
  python jiaoyi_runner.py --mode run --city jiaxing --excel "<银行xlsx>" --tx-type sk --bank-code 313331
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

# 确保无论以何种 cwd 运行，都能导入同目录引擎
_HERE = Path(__file__).resolve().parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))

from zjb_openapi_runner import (  # noqa: E402
    DEFAULT_OPENAPI_URL,
    DEFAULT_EXCEL,
    DEFAULT_CITY_CONFIG,
    TRADE_INIT_PATHS,
    load_openapi,
    load_bank_data,
    load_city_config,
    build_case,
    call_http,
    write_case_files,
    write_report,
    parse_field_overrides,
    parse_extra_headers,
    iter_operations,
    today_yyyymmdd,
)

# 交易类型 -> (路径白名单, 该类型自带的请求字段默认值)
# 批量扣款复用批量收款接口 addSkywPlsk，仅切换 xtlx/ywlx/jslx
TX_TYPE_MAP = {
    "all": (set(TRADE_INIT_PATHS), {}),
    "sk": ({p for p in TRADE_INIT_PATHS if p.endswith("addSkywDbsk") or p.endswith("addSkywPlsk")}, {}),
    "fk": ({p for p in TRADE_INIT_PATHS if "Fk" in p}, {}),
    "kk": (
        {p for p in TRADE_INIT_PATHS if p.endswith("addSkywPlsk")},
        {"xtlx": "GDGL", "ywlx": "11305", "jslx": "wksk"},
    ),
}


def select_trade_operations(spec: dict, tx_type: str, tag_filter: str = "", summary_filter: str = "") -> list:
    """只返回交易类接口（按 path 是否在白名单内过滤），硬性排除签约类。"""
    allow, _ = TX_TYPE_MAP[tx_type]
    full = list(iter_operations(spec, tag_filter, summary_filter))
    return [op for op in full if op["path"] in allow]


def parse_args(argv: list[str]) -> argparse.Namespace:
    p = argparse.ArgumentParser(description="交易类接口自动化测试（仅交易类，不含签约类）")
    p.add_argument("--openapi-url", default=DEFAULT_OPENAPI_URL)
    p.add_argument("--openapi-file", default="", help="本地 OpenAPI/Swagger JSON 文件，优先于 --openapi-url")
    p.add_argument("--excel", default=DEFAULT_EXCEL, help="银行测试数据 Excel")
    p.add_argument("--output-dir", default=".codex-run/jiaoyi-test")
    p.add_argument("--mode", choices=["dry-run", "run"], default="dry-run", help="dry-run=只生成用例 / run=真实调用")
    p.add_argument("--tx-type", choices=["all", "sk", "fk", "kk"], default="all",
                   help="交易类型：all=全部交易类 / sk=收款 / fk=付款 / kk=批量扣款(复用收款接口)")
    p.add_argument("--base-url", default="", help="临时覆盖服务地址（优先于配置文件）")
    p.add_argument("--tag-filter", default="")
    p.add_argument("--summary-filter", default="")
    p.add_argument("--city-config", default=str(DEFAULT_CITY_CONFIG),
                   help="环境配置文件（与签约自动化测试脚本共用，默认同目录 city-config.json）")
    p.add_argument("--city", default="", help="city-config.json 中的城市键；不传用 defaultCity")
    p.add_argument("--account-type", choices=["private", "public", "both"], default="private")
    p.add_argument("--bank-code", default="", help="银行代码（如无法从 Excel 取得则必传）")
    p.add_argument("--bank-name", default="")
    p.add_argument("--trade-date", default=today_yyyymmdd())
    p.add_argument("--amount", type=float, default=None)
    p.add_argument("--timeout", type=int, default=30)
    p.add_argument("--expect-http", default="2xx")
    p.add_argument("--header", action="append", default=[], help="额外请求头，格式 key:value")
    p.add_argument("--set-field", action="append", default=[], help="递归覆盖请求字段，格式 name=value")
    p.add_argument("--xtlx", default="", help="快捷覆盖 xtlx（等价 --set-field xtlx=）")
    p.add_argument("--ywlx", default="", help="快捷覆盖 ywlx（等价 --set-field ywlx=）")
    return p.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    output_dir = Path(args.output_dir)

    spec = load_openapi(args.openapi_url, args.openapi_file or None)
    bank_data = load_bank_data(args.excel)
    city, fixed_params, config_base_url = load_city_config(args.city_config, args.city)
    base_url = (args.base_url or "").strip() or config_base_url
    if args.mode == "run" and not base_url:
        raise SystemExit("run 模式需要 city-config.json 中的 serviceBaseUrl 或 --base-url")

    allow, tx_defaults = TX_TYPE_MAP[args.tx_type]
    # 交易类型自带字段默认值（如批量扣款）与命令行 --set-field/--xtlx/--ywlx 合并，命令行优先
    overrides_from_cli = parse_field_overrides(args.set_field, args.xtlx, args.ywlx)
    request_overrides = {**tx_defaults, **overrides_from_cli}

    operations = select_trade_operations(spec, args.tx_type, args.tag_filter, args.summary_filter)
    if not operations:
        raise SystemExit("未匹配到任何交易类接口；检查 --tx-type / --tag-filter / --summary-filter 与 OpenAPI 内容")

    # 二次保险：确认没有签约类接口混入（任何不在 TRADE_INIT_PATHS 的路径都不允许出现）
    signing_leak = [op["path"] for op in operations if op["path"] not in TRADE_INIT_PATHS]
    if signing_leak:
        raise SystemExit(f"内部约束违反：交易类脚本出现非交易类接口 {signing_leak}")

    cases = [
        build_case(
            op, bank_data, args.account_type, args.bank_code, args.bank_name,
            args.trade_date, args.amount, fixed_params, city, base_url, request_overrides,
        )
        for op in operations
    ]
    cases_dir, results = write_case_files(cases, output_dir), []
    if args.mode == "run":
        headers = parse_extra_headers(args.header)
        for case in cases:
            results.append(call_http(base_url, case, headers, args.timeout, args.expect_http))
    write_report(output_dir, cases, results, args.mode)
    print(f"[{args.tx_type}] 已生成 {len(cases)} 个交易类用例（仅交易类，不含签约类）：{cases_dir}")
    print(f"报告：{output_dir / 'report.html'}")
    if results:
        failed = [r for r in results if not r.get("passed")]
        print(f"执行结果：通过 {len(results) - len(failed)}，失败 {len(failed)}")
        return 1 if failed else 0
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
