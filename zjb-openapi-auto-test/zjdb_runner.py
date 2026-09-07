#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
资金调拨测试脚本（复用单笔付款接口 /api/v1/ywgl/addFkywDbfk）

业务说明：
  - 资金调拨没有独立 OpenAPI 路径，复用「单笔付款 addFkywDbfk」。
  - 通过 xtlx=ZJJS、ywlx=zjdb 标识资金调拨业务。
  - 请求体中 yhzhhm 为转出方中心专户账号，skzh 为转入方中心专户账号。
  - 转出/转入银行均从中心专户数据（见 CENTER_ACCOUNTS）按银行简称或银行代码匹配。

用法：
  # 仅生成用例（dry-run）
  python zjdb_runner.py --from-bank 工行 --to-bank 建行 --amount 100

  # 真实调用（需 base-url）
  python zjdb_runner.py --mode run --city jiaxing --from-bank 工行 --to-bank 建行 --amount 100

  # 按银行代码匹配
  python zjdb_runner.py --from-bank 102000 --to-bank 105000 --amount 100

  # 覆盖户名或联行号
  python zjdb_runner.py --from-bank 工行 --to-bank 建行 --from-account-name "嘉兴市住房公积金管理中心" --to-lhh 105100000017
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

_HERE = Path(__file__).resolve().parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))

from zjb_openapi_runner import (  # noqa: E402
    DEFAULT_OPENAPI_URL,
    DEFAULT_CITY_CONFIG,
    TRADE_INIT_PATHS,
    load_openapi,
    load_city_config,
    apply_fixed_params,
    inject_city_trade_fields,
    write_case_files,
    write_report,
    call_http,
    parse_extra_headers,
    now_text,
    today_yyyymmdd,
    digits,
    parse_field_overrides,
    parse_override_value,
)

TARGET_PATH = "/api/v1/ywgl/addFkywDbfkZdzh"

# 从用户截图整理的中心专户数据。
# 列含义（按业务识别）：银行简称、银行代码(yhdm)、清算渠道、中心专户账号(yhzhhm)、中心专户户名(yhzhmc)、账户类型
# 注：截图中部分户名显示乱码，已用可识别值或合理默认值填充，运行时可通过 --from-account-name / --to-account-name 覆盖。
CENTER_ACCOUNTS: dict[str, dict[str, str]] = {
    "建行": {
        "code": "105000",
        "name": "中国建设银行",
        "account": "33001638048050004672",
        "account_name": "嘉兴市住房公积金管理中心市本级",
        "line_no": "105335000023",
    },
    "工行": {
        "code": "102000",
        "name": "中国工商银行",
        "account": "1204062029022600171",
        "account_name": "钻蛇偏组哗憋剩哪毁倦执镜茴雀组哗憋剩哪此产泪东芹乡",
        "line_no": "102335006000",
    },
    "农行": {
        "code": "103000",
        "name": "中国农业银行",
        "account": "22401001046472133",
        "account_name": "眉胜收月连鸳添幼慊关嗣",
        "line_no": "103335030027",
    },
    "中行": {
        "code": "104000",
        "name": "中国银行",
        "account": "362358713007",
        "account_name": "嘉兴市住房公积金管理中心市本级",
        "line_no": "104335050331",
    },
    "交行": {
        "code": "301000",
        "name": "交通银行",
        "account": "334601000012015711526",
        "account_name": "媳汐值袖烁肮廖韦欲敢俊野卜万酗嗣澳临委狠撂默监悦淘",
        "line_no": "301335006016",
    },
    "中信银行": {
        "code": "302000",
        "name": "中信银行",
        "account": "7333010182600089295",
        "account_name": "娇铁功贮丁公括骑管徐冒忻",
        "line_no": "302335033301",
    },
    "兴业银行": {
        "code": "309000",
        "name": "兴业银行",
        "account": "358500100100456683",
        "account_name": "嘉兴市住房公积金中心",
        "line_no": "309335008501",
    },
    "招商银行": {
        "code": "308000",
        "name": "招商银行",
        "account": "755947571710714",
        "account_name": "公积金测试账户",
        "line_no": "308335073014",
    },
    "浦发银行": {
        "code": "310000",
        "name": "上海浦东发展银行",
        "account": "88010078801600001226",
        "account_name": "自动化测试245792",
        "line_no": "310335000016",
    },
    "邮储银行": {
        "code": "313009",
        "name": "中国邮政储蓄银行",
        "account": "933044013000433376",
        "account_name": "嘉兴市公积金测试",
        "line_no": "403335000332",
    },
    "嘉兴银行": {
        "code": "313095",
        "name": "嘉兴银行",
        "account": "903101201900025840",
        "account_name": "四川省资阳来粗援南专造磕匆庇鸽谣迅刎瘩区锦泉服装厂",
        "line_no": "313335087029",
    },
    "浙江农信": {
        "code": "313051",
        "name": "浙江省农村信用社联合社",
        "account": "201000287013493",
        "account_name": "公积金归集账户",
        "line_no": "402335010108",
    },
    "杭州银行": {
        "code": "313062",
        "name": "杭州银行",
        "account": "3301041060000663413",
        "account_name": "嘉兴公积金测试专用结算户",
        "line_no": "313335020012",
    },
    "宁波银行": {
        "code": "313001",
        "name": "宁波银行",
        "account": "86211110000008152",
        "account_name": "嘉兴公积金中心归集户",
        "line_no": "313335089018",
    },
    "浙商银行": {
        "code": "313041",
        "name": "浙商银行",
        "account": "3310010010120183052118",
        "account_name": "嘉兴公积金联调测试专户",
        "line_no": "316335000015",
    },
    "上海农商": {
        "code": "313112",
        "name": "上海农村商业银行",
        "account": "32611018010022772",
        "account_name": "嘉兴市住房管理服务中心嘉善分中心",
        "line_no": "316335000015",
    },
}

# 中心专户 ID（对应 addFkywDbfkZdzh 的「专户id」字段）
# 来源：用户提供的嘉兴中心专户管理数据；注意序号不连续（54 缺失）
CENTER_ACCOUNT_IDS: dict[str, str] = {
    "建行": "51", "工行": "52", "农行": "53", "中行": "55",
    "交行": "56", "中信银行": "57", "兴业银行": "58", "招商银行": "59",
    "浦发银行": "60", "邮储银行": "61", "嘉兴银行": "62", "浙江农信": "63",
    "杭州银行": "64", "宁波银行": "65", "浙商银行": "66", "上海农商": "67",
}
for _bank_name, _bank_id in CENTER_ACCOUNT_IDS.items():
    CENTER_ACCOUNTS[_bank_name]["id"] = _bank_id

# 支持按银行代码反向查找
_CODE_TO_NAME: dict[str, str] = {v["code"]: k for k, v in CENTER_ACCOUNTS.items()}


def resolve_bank(value: str) -> dict[str, str]:
    """按银行简称或银行代码查找中心专户记录。"""
    value = value.strip()
    if value in CENTER_ACCOUNTS:
        return CENTER_ACCOUNTS[value]
    if value in _CODE_TO_NAME:
        return CENTER_ACCOUNTS[_CODE_TO_NAME[value]]
    raise ValueError(f"无法识别的转出/转入银行：{value!r}；可用简称：{list(CENTER_ACCOUNTS)} 或代码：{list(_CODE_TO_NAME)}")


def build_zjdb_request(
    from_bank: str,
    to_bank: str,
    amount: float,
    trade_date: str,
    from_account_name: str = "",
    to_account_name: str = "",
    from_line_no: str = "",
    to_line_no: str = "",
    zhaiyao: str = "资金调拨",
    beizhu: str = "资金调拨",
    xtlx: str = "ZJJS",
    ywlx: str = "zjdb",
    jslx: str = "zz",
    jszjlx: str = "0202",
    zjdblx: str = "dbzz",
) -> dict[str, object]:
    """构造资金调拨请求体（复用单笔付款字段）。"""
    from_info = resolve_bank(from_bank)
    to_info = resolve_bank(to_bank)

    # 户名 / 联行号可被命令行覆盖；命令行未指定时从内置字典取
    from_name = (from_account_name or from_info["account_name"]).strip()
    to_name = (to_account_name or to_info["account_name"]).strip()
    from_line_no = (from_line_no or from_info.get("line_no", "")).strip()
    to_line_no = (to_line_no or to_info.get("line_no", "")).strip()

    return {
        "zxbh": "",
        "qdlx": "01",
        "qdbm": "gt",
        "jbjgbh": "",
        "jbjgmc": "",
        "wdbh": None,
        "wdmc": None,
        "xtlx": xtlx,
        "ywlx": ywlx,
        "ywslbh": digits(20),
        "jslsh": digits(20),
        "ywlsh": digits(20),
        "jszjlx": jszjlx,
        "jslx": jslx,
        "je": amount,
        "ywzh": "",
        "lxe": 0.00,
        "zhlx": "2",
        "yhdm": from_info["code"],
        "zhxxId": int(from_info["id"]) if str(from_info.get("id", "")).isdigit() else None,
        "yhzhhm": from_info["account"],
        "yhzhmc": from_name,
        "skzh": to_info["account"],
        "skhm": to_name,
        "skyhdm": to_info["code"],
        "skyhmc": to_info["name"],
        "skyhlhh": to_line_no,
        "zjlx": None,
        "zjh": None,
        "zjdblx": zjdblx,
        "zhaiyao": zhaiyao,
        "beizhu": beizhu,
        "jstjsj": now_text(),
        "jyzhlx": "1",
    }


def parse_args(argv: list[str]) -> argparse.Namespace:
    p = argparse.ArgumentParser(description="资金调拨测试脚本（复用单笔付款接口，xtlx=ZJJS, ywlx=zjdb）")
    p.add_argument("--openapi-url", default=DEFAULT_OPENAPI_URL)
    p.add_argument("--openapi-file", default="", help="本地 OpenAPI JSON，优先于 --openapi-url")
    p.add_argument("--output-dir", default=".codex-run/zjdb-test")
    p.add_argument("--mode", choices=["dry-run", "run"], default="dry-run")
    p.add_argument("--base-url", default="", help="临时覆盖服务地址（优先于 city-config）")
    p.add_argument("--city-config", default=str(DEFAULT_CITY_CONFIG))
    p.add_argument("--city", default="", help="city-config.json 中的城市键；不传用 defaultCity")
    p.add_argument("--trade-date", default=today_yyyymmdd())
    p.add_argument("--amount", type=float, required=True, help="调拨金额")
    p.add_argument("--from-bank", required=True, help="转出银行简称或代码，如 工行 / 102000")
    p.add_argument("--to-bank", required=True, help="转入银行简称或代码，如 建行 / 105000")
    p.add_argument("--from-account-name", default="", help="覆盖转出方中心专户户名")
    p.add_argument("--to-account-name", default="", help="覆盖转入方中心专户户名")
    p.add_argument("--from-lhh", default="", help="转出方联行号（可选）")
    p.add_argument("--to-lhh", default="", help="转入方联行号（可选）")
    p.add_argument("--zhaiyao", default="资金调拨", help="摘要")
    p.add_argument("--beizhu", default="资金调拨", help="备注")
    p.add_argument("--xtlx", default="ZJJS", help="系统类型，默认 ZJJS")
    p.add_argument("--ywlx", default="zjdb", help="业务类型，默认 zjdb")
    p.add_argument("--jslx", default="zz", help="结算类型，默认 zz")
    p.add_argument("--jszjlx", default="0202", help="资金类型，默认 0202")
    p.add_argument("--zjdblx", default="01",
                   help="资金调拨类型：01(调拨转账)/02(调拨付款)，按常量类枚举值传。"
                        "2026-08-28 实测传 01 成功(code:0)，此前'传01报错'结论已作废。")
    p.add_argument("--timeout", type=int, default=30)
    p.add_argument("--expect-http", default="2xx")
    p.add_argument("--header", action="append", default=[], help="额外请求头，格式 key:value")
    p.add_argument("--set-field", action="append", default=[], help="递归覆盖请求字段，格式 name=value")
    p.add_argument("--show-accounts", action="store_true", help="打印内置中心专户数据后退出")
    return p.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])

    if args.show_accounts:
        print(json.dumps(CENTER_ACCOUNTS, ensure_ascii=False, indent=2))
        return 0

    from_info = resolve_bank(args.from_bank)
    to_info = resolve_bank(args.to_bank)

    # 环境配置
    city, fixed_params, config_base_url = load_city_config(args.city_config, args.city)
    base_url = (args.base_url or "").strip() or config_base_url
    if args.mode == "run" and not base_url:
        raise SystemExit("run 模式需要 city-config.json 中的 serviceBaseUrl 或 --base-url")

    # 构造基础请求体
    request = build_zjdb_request(
        from_bank=args.from_bank,
        to_bank=args.to_bank,
        amount=args.amount,
        trade_date=args.trade_date,
        from_account_name=args.from_account_name,
        to_account_name=args.to_account_name,
        from_line_no=args.from_lhh,
        to_line_no=args.to_lhh,
        zhaiyao=args.zhaiyao,
        beizhu=args.beizhu,
        xtlx=args.xtlx,
        ywlx=args.ywlx,
        jslx=args.jslx,
        jszjlx=args.jszjlx,
        zjdblx=args.zjdblx,
    )

    # 应用城市固定参数与命令行覆盖
    request = apply_fixed_params(request, fixed_params or {})
    overrides = parse_field_overrides(args.set_field, args.xtlx, args.ywlx)
    request = apply_fixed_params(request, overrides)

    # 嘉兴环境：外层补 yhdm
    request = inject_city_trade_fields(request, TARGET_PATH, city, from_info["code"])

    # 组装用例
    case = {
        "name": f"资金调拨 {from_info['name']}({from_info['code']}) -> {to_info['name']}({to_info['code']})",
        "method": "POST",
        "path": TARGET_PATH,
        "tags": ["资金调拨", "ZJJS"],
        "city": city,
        "baseUrl": base_url,
        "request": request,
    }

    output_dir = Path(args.output_dir)
    cases_dir = write_case_files([case], output_dir)
    results = []

    # 打印关键信息供用户核对
    print(f"资金调拨用例已生成：{cases_dir}")
    print(f"  转出：{from_info['name']}({from_info['code']}) yhzhhm={from_info['account']} yhzhmc={request['yhzhmc']}")
    print(f"  转入：{to_info['name']}({to_info['code']}) skzh={to_info['account']} skhm={request['skhm']}")
    print(f"  金额：{request['je']}")
    print(f"  请求体：{output_dir / 'cases' / '01_资金调拨_*.json'}")

    if args.mode == "run":
        headers = parse_extra_headers(args.header)
        results.append(call_http(base_url, case, headers, args.timeout, args.expect_http))

    write_report(output_dir, [case], results, args.mode)
    print(f"报告：{output_dir / 'report.html'}")

    if results:
        r = results[0]
        status = "通过" if r.get("passed") else "失败"
        print(f"执行结果：{status}，HTTP {r.get('status')}，耗时 {r.get('elapsed_ms')}ms")
        if not r.get("passed"):
            print(f"错误/响应：{r.get('error') or r.get('response_preview')}")
        return 0 if r.get("passed") else 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
