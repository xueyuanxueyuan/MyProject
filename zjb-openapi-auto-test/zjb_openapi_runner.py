#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""住建部 OpenAPI 通用自动化测试工具。零第三方依赖。"""
from __future__ import annotations

import argparse, dataclasses, datetime as dt, html, json, random, re, sys, time, urllib.error, urllib.request, zipfile
from pathlib import Path
from typing import Any, Iterable
import xml.etree.ElementTree as ET

DEFAULT_OPENAPI_URL = "https://openapi.apipost.net/swagger/v3/63952d5f0088000?locale=zh-cn"
DEFAULT_EXCEL = r"D:\文档\工作文档\嘉兴住建部测试数据\银行测试数据收集表_杭州银行.xlsx"
DEFAULT_CITY_CONFIG = Path(__file__).with_name("city-config.json")
SPREADSHEET_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
PACKAGE_REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
CENTER_SHEET, PRIVATE_SHEET, PUBLIC_SHEET = "中心专户信息", "对私账户信息", "对公账户信息"
UNIQUE_FIELD_NAMES = {"ywslbh", "jslsh", "ywlsh", "mxlsh", "plxmbh", "qysqbh"}
ACCOUNT_FIELD_NAMES = {"zh", "fkzh", "skzh"}
NAME_FIELD_NAMES = {"hm", "fkhm", "skhm"}
BANK_CODE_FIELD_NAMES = {"yhdm", "fkyhdm", "skyhdm"}
BANK_NAME_FIELD_NAMES = {"yhmc", "fkyhmc", "skyhmc"}
LINE_NO_FIELD_NAMES = {"lhh", "kkyhlhh", "fkyhlhh", "skyhlhh"}
HEADER_INJECTION_MARKERS = {"zxbh", "jbjgbh", "qdlx", "qdbm"}
HEADER_INJECTABLE_FIXED_FIELD_NAMES = {"hsjgbh", "hsjgmc", "jsqd"}
TRADE_INIT_PATHS = {"/api/v1/ywgl/addSkywDbsk", "/api/v1/ywgl/addSkywPlsk", "/api/v1/ywgl/addFkywDbfk", "/api/v1/ywgl/addFkywPlfk"}
ID_TYPE_MAP = {"身份证": "01", "居民身份证": "01", "统一社会信用代码": "18", "组织机构代码": "10"}


def now_text() -> str:
    return dt.datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def today_yyyymmdd() -> str:
    return dt.datetime.now().strftime("%Y%m%d")


def digits(length: int = 20) -> str:
    seed = dt.datetime.now().strftime("%y%m%d%H%M%S%f") + str(random.randint(0, 999999)).zfill(6)
    return seed[-length:] if len(seed) >= length else seed + "".join(str(random.randint(0, 9)) for _ in range(length - len(seed)))


def safe_file_name(value: str) -> str:
    value = re.sub(r"[\\/:*?\"<>|\s]+", "_", value.strip())
    return (re.sub(r"_+", "_", value).strip("_") or "case")[:120]


def remove_json_comments(text: str) -> str:
    result, i, in_string, escape = [], 0, False, False
    while i < len(text):
        ch = text[i]; nxt = text[i + 1] if i + 1 < len(text) else ""
        if in_string:
            result.append(ch)
            if escape: escape = False
            elif ch == "\\": escape = True
            elif ch == '"': in_string = False
            i += 1; continue
        if ch == '"':
            in_string = True; result.append(ch); i += 1; continue
        if ch == "/" and nxt == "/":
            while i < len(text) and text[i] not in "\r\n": i += 1
            result.append("\n"); continue
        if ch == "/" and nxt == "*":
            i += 2
            while i + 1 < len(text) and not (text[i] == "*" and text[i + 1] == "/"):
                if text[i] in "\r\n": result.append("\n")
                i += 1
            i += 2; continue
        result.append(ch); i += 1
    return "".join(result)


def parse_json_example(example: Any, op_name: str) -> Any:
    if example in (None, ""): return {}
    if isinstance(example, (dict, list)): return example
    text = re.sub(r",\s*([}\]])", r"\1", remove_json_comments(str(example)).strip())
    try:
        return json.loads(text)
    except json.JSONDecodeError as exc:
        around = text[max(0, exc.pos - 120): exc.pos + 120]
        raise ValueError(f"{op_name} 示例 JSON 解析失败：{exc}. 附近内容：{around}") from exc


def load_openapi(source_url: str | None, source_file: str | None) -> dict[str, Any]:
    if source_file:
        return json.loads(Path(source_file).read_text(encoding="utf-8"))
    req = urllib.request.Request(source_url or DEFAULT_OPENAPI_URL, headers={"User-Agent": "Gjj-Zjb-OpenAPI-Test/1.0"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def iter_operations(spec: dict[str, Any], tag_filter: str = "", summary_filter: str = "") -> Iterable[dict[str, Any]]:
    for raw_path, item in spec.get("paths", {}).items():
        if not isinstance(item, dict): continue
        path = raw_path if raw_path.startswith("/") else "/" + raw_path
        for method, op in item.items():
            if method.lower() not in {"get", "post", "put", "delete", "patch"} or not isinstance(op, dict): continue
            summary, tags = op.get("summary") or "", op.get("tags") or []
            haystack = " ".join([summary, path, *map(str, tags)])
            if tag_filter and tag_filter not in haystack: continue
            if summary_filter and summary_filter not in haystack: continue
            content = op.get("requestBody", {}).get("content", {})
            app_json = content.get("application/json", {}) if isinstance(content, dict) else {}
            yield {"method": method.upper(), "path": path, "summary": summary or path, "tags": tags, "example": app_json.get("example")}

def load_city_config(config_file: str | Path, city: str = "") -> tuple[str, dict[str, Any], str]:
    """Load selected city config. Return city code, fixedParams and serviceBaseUrl."""
    path = Path(config_file)
    if not path.exists():
        return city, {}, ""
    data = json.loads(path.read_text(encoding="utf-8-sig"))
    cities = data.get("cities", {})
    selected_city = city or data.get("defaultCity", "")
    if not selected_city:
        return "", {}, str(data.get("defaultServiceBaseUrl") or data.get("serviceBaseUrl") or data.get("baseUrl") or "").strip()
    if selected_city not in cities:
        available = ", ".join(sorted(cities)) or "none"
        raise ValueError(f"city config not found: {selected_city}; available: {available}")
    city_node = cities[selected_city]
    fixed_params = city_node.get("fixedParams", {})
    if not isinstance(fixed_params, dict):
        raise ValueError(f"city {selected_city} fixedParams must be an object")
    service_base_url = str(city_node.get("serviceBaseUrl") or data.get("defaultServiceBaseUrl") or data.get("serviceBaseUrl") or data.get("baseUrl") or "").strip()
    return selected_city, fixed_params, service_base_url


def load_city_fixed_params(config_file: str | Path, city: str = "") -> tuple[str, dict[str, Any]]:
    """Backward-compatible helper for callers that only need city fixed parameters."""
    selected_city, fixed_params, _ = load_city_config(config_file, city)
    return selected_city, fixed_params


def apply_fixed_params(payload: Any, fixed_params: dict[str, Any]) -> Any:
    """Recursively override request fields that match fixed city parameter names."""
    if not fixed_params:
        return payload
    if isinstance(payload, list):
        return [apply_fixed_params(item, fixed_params) for item in payload]
    if isinstance(payload, dict):
        result = {key: apply_fixed_params(fixed_params[key], fixed_params) if key in fixed_params else apply_fixed_params(value, fixed_params) for key, value in payload.items()}
        if HEADER_INJECTION_MARKERS.intersection(payload):
            for key in HEADER_INJECTABLE_FIXED_FIELD_NAMES.intersection(fixed_params):
                result.setdefault(key, fixed_params[key])
        return result
    return payload


def parse_override_value(raw_value: str) -> Any:
    """Parse a CLI override value; JSON scalars are supported, plain text stays text."""
    value = raw_value.strip()
    if value.lower() == "null":
        return None
    if value.lower() == "true":
        return True
    if value.lower() == "false":
        return False
    if re.fullmatch(r"-?\d+", value):
        return int(value)
    if re.fullmatch(r"-?\d+\.\d+", value):
        return float(value)
    if (value.startswith('"') and value.endswith('"')) or (value.startswith("'") and value.endswith("'")):
        return value[1:-1]
    return raw_value


def parse_field_overrides(values: list[str], xtlx: str = "", ywlx: str = "") -> dict[str, Any]:
    """Parse repeated --set-field name=value plus --xtlx/--ywlx shortcuts."""
    overrides: dict[str, Any] = {}
    for item in values or []:
        if "=" not in item:
            raise ValueError(f"???????? name=value?????{item}")
        name, raw_value = item.split("=", 1)
        name = name.strip()
        if not name:
            raise ValueError(f"???????????{item}")
        overrides[name] = parse_override_value(raw_value)
    if xtlx:
        overrides["xtlx"] = xtlx
    if ywlx:
        overrides["ywlx"] = ywlx
    return overrides


def _xlsx_col_name(index: int) -> str:
    name = ""
    while index:
        index, rem = divmod(index - 1, 26); name = chr(65 + rem) + name
    return name


def _xlsx_col_index(cell_ref: str) -> int:
    m = re.match(r"([A-Z]+)", cell_ref or "A"); value = 0
    for ch in (m.group(1) if m else "A"): value = value * 26 + ord(ch) - 64
    return value


def _xml_text(element: ET.Element, ns: dict[str, str]) -> str:
    return "".join(t.text or "" for t in element.findall(".//a:t", ns))

def read_xlsx_rows(path: str | Path) -> dict[str, list[list[str]]]:
    xlsx_path = Path(path)
    if not xlsx_path.exists(): raise FileNotFoundError(f"Excel 文件不存在：{xlsx_path}")
    ns = {"a": SPREADSHEET_NS, "r": REL_NS}
    with zipfile.ZipFile(xlsx_path) as zf:
        names, shared = set(zf.namelist()), []
        if "xl/sharedStrings.xml" in names:
            root = ET.fromstring(zf.read("xl/sharedStrings.xml"))
            shared = [_xml_text(si, ns).strip() for si in root.findall("a:si", ns)]
        workbook = ET.fromstring(zf.read("xl/workbook.xml"))
        rels = ET.fromstring(zf.read("xl/_rels/workbook.xml.rels"))
        rel_map = {rel.attrib["Id"]: rel.attrib["Target"] for rel in rels}
        sheets = []
        sheet_nodes = workbook.find("a:sheets", ns)
        for sheet in ([] if sheet_nodes is None else sheet_nodes):
            rel_id = sheet.attrib.get(f"{{{REL_NS}}}id")
            target = rel_map.get(rel_id or "", "").lstrip("/")
            sheet_path = target if target.startswith("xl/") else "xl/" + target
            sheets.append((sheet.attrib.get("name", "Sheet"), sheet_path.replace("xl//", "xl/")))

        def cell_value(cell: ET.Element) -> str:
            cell_type = cell.attrib.get("t")
            if cell_type == "inlineStr": return _xml_text(cell, ns).strip()
            value_node = cell.find("a:v", ns)
            if value_node is None: return ""
            raw = (value_node.text or "").strip()
            if cell_type == "s":
                try: return shared[int(raw)].strip()
                except (ValueError, IndexError): return raw
            return raw

        result: dict[str, list[list[str]]] = {}
        for sheet_name, sheet_path in sheets:
            if sheet_path not in names: continue
            sheet_xml = ET.fromstring(zf.read(sheet_path)); rows = []
            for row in sheet_xml.findall(".//a:sheetData/a:row", ns):
                values, current_col = [], 1
                for cell in row.findall("a:c", ns):
                    col = _xlsx_col_index(cell.attrib.get("r", "A1"))
                    while current_col < col: values.append(""); current_col += 1
                    values.append(cell_value(cell)); current_col += 1
                while values and values[-1] == "": values.pop()
                rows.append(values)
            result[sheet_name] = rows
        return result


def _rows_to_dicts(rows: list[list[str]]) -> list[dict[str, str]]:
    if not rows: return []
    headers = [str(h).strip() for h in rows[0]]; result = []
    for row in rows[1:]:
        item = {headers[i]: (str(row[i]).strip() if i < len(row) else "") for i in range(len(headers)) if headers[i]}
        if any(v for k, v in item.items() if k != "序号"): result.append(item)
    return result


@dataclasses.dataclass
class BankData:
    center_accounts: list[dict[str, str]]
    private_accounts: list[dict[str, str]]
    public_accounts: list[dict[str, str]]
    source: str

    @property
    def bank_name(self) -> str:
        for rows in (self.center_accounts, self.private_accounts, self.public_accounts):
            for row in rows:
                for key in ("开户银行", "开户行"):
                    if row.get(key): return row[key]
        stem = Path(self.source).stem
        m = re.search(r"_(.+?)(?:银行)?$", stem)
        return (m.group(1) + ("银行" if "银行" not in m.group(1) else "")) if m else ""

    def accounts(self, account_type: str) -> list[dict[str, str]]:
        if account_type == "public": return self.public_accounts
        if account_type == "both": return self.private_accounts + self.public_accounts
        return self.private_accounts

    def center_account(self) -> dict[str, str]:
        return self.center_accounts[0] if self.center_accounts else {}


def load_bank_data(excel_path: str | Path) -> BankData:
    rows = read_xlsx_rows(excel_path)
    return BankData(_rows_to_dicts(rows.get(CENTER_SHEET, [])), _rows_to_dicts(rows.get(PRIVATE_SHEET, [])), _rows_to_dicts(rows.get(PUBLIC_SHEET, [])), str(excel_path))


def normalize_account_type(value: str | None, default_type: str) -> str:
    text = str(value or "").strip().replace("１", "1").replace("２", "2")
    if text in {"2", "对公", "public"}: return "public"
    if text in {"1", "对私", "private"}: return "private"
    return default_type


def account_value(account: dict[str, str], keys: Iterable[str]) -> str:
    for key in keys:
        if account.get(key): return account[key]
    return ""


def context_for_account(bank_data: BankData, account_type: str, index: int, bank_code: str, bank_name_override: str) -> dict[str, str]:
    accounts = bank_data.accounts(account_type) or bank_data.accounts("public" if account_type == "private" else "private")
    if not accounts: raise ValueError("Excel 中没有可用的对手账户")
    account, center = accounts[index % len(accounts)], bank_data.center_account()
    bank_name = bank_name_override or account_value(account, ["开户行", "开户银行"]) or bank_data.bank_name
    line_no = account_value(account, ["联行号"]) or account_value(center, ["联行号"])
    return {
        "account_type": "2" if account_type == "public" else "1",
        "account_no": account_value(account, ["账号*", "账号", "卡号"]),
        "account_name": account_value(account, ["姓名*", "姓名", "户名*", "户名", "机构名称"]),
        "id_type": ID_TYPE_MAP.get(account_value(account, ["证件类型"]), account_value(account, ["证件类型"]) or ("18" if account_type == "public" else "01")),
        "id_no": account_value(account, ["证件号码*", "证件号码", "统一社会信用代码", "组织机构号码"]),
        "phone": account_value(account, ["手机号"]),
        "bank_code": bank_code, "bank_name": bank_name, "line_no": line_no,
        "center_account_no": account_value(center, ["账号*", "账号"]),
        "center_account_name": account_value(center, ["户名*", "户名"]),
    }

def replace_dynamic_value(value: Any, trade_date: str) -> Any:
    if not isinstance(value, str): return value
    if "{{$fakerjs.String.numeric" in value:
        m = re.search(r"length\s*=\s*(\d+)", value); return digits(int(m.group(1)) if m else 20)
    if "{{$mockjs.now()" in value: return now_text()
    if value == "{{today}}": return trade_date
    return value


def field_replacement(field_name: str, old_value: Any, ctx: dict[str, str], trade_date: str, amount: float | None) -> Any:
    name = field_name.strip()
    if name in UNIQUE_FIELD_NAMES:
        m = re.search(r"(\d{16,})", str(old_value or "")); return digits(len(m.group(1)) if m else 20)
    if name in ACCOUNT_FIELD_NAMES and ctx["account_no"]: return ctx["account_no"]
    if name in NAME_FIELD_NAMES and ctx["account_name"]: return ctx["account_name"]
    if name == "zjh" and ctx["id_no"]: return ctx["id_no"]
    if name == "zjlx" and ctx["id_type"]: return ctx["id_type"]
    if name in {"sjhm", "mobile", "phone"} and ctx["phone"]: return ctx["phone"]
    if name in BANK_CODE_FIELD_NAMES and ctx["bank_code"]: return ctx["bank_code"]
    if name in BANK_NAME_FIELD_NAMES and ctx["bank_name"]: return ctx["bank_name"]
    if name in LINE_NO_FIELD_NAMES: return ctx["line_no"]
    if name == "yhzhhm" and ctx["center_account_no"]: return ctx["center_account_no"]
    if name == "zhlx": return ctx["account_type"]
    if name == "jyrq": return trade_date
    if name == "jstjsj": return now_text()
    if name == "je" and amount is not None and isinstance(old_value, (int, float, str)): return amount
    return replace_dynamic_value(old_value, trade_date)


def normalize_batch_totals(node: dict[str, Any]) -> None:
    for list_key in ("skywPlmxAddReqDTOList", "fkywPlmxAddReqDTOList"):
        details = node.get(list_key)
        if not isinstance(details, list) or not details: continue
        total = 0.0
        for item in details:
            if isinstance(item, dict):
                try: total += float(item.get("je", 0) or 0)
                except (TypeError, ValueError): pass
        header = node.get("zjjsSFkywxxAddReqDTO")
        if isinstance(header, dict):
            header["jybs"] = len(details)
            if total: header["je"] = int(total) if total.is_integer() else total


def enrich_payload(payload: Any, bank_data: BankData, account_type: str, bank_code: str, bank_name: str, trade_date: str, amount: float | None) -> Any:
    default_type = "private" if account_type == "both" else account_type
    def visit(node: Any, current_type: str, index: int) -> Any:
        if isinstance(node, list): return [visit(item, current_type, i) for i, item in enumerate(node)]
        if isinstance(node, dict):
            node_type = normalize_account_type(node.get("zhlx"), current_type)
            ctx, new_node = context_for_account(bank_data, node_type, index, bank_code, bank_name), {}
            for key, value in node.items():
                new_node[key] = visit(value, node_type, index) if isinstance(value, (dict, list)) else field_replacement(key, value, ctx, trade_date, amount)
            normalize_batch_totals(new_node)
            return new_node
        return replace_dynamic_value(node, trade_date)
    return visit(payload, default_type, 0)


def inject_city_trade_fields(payload: Any, operation_path: str, city: str, bank_code: str) -> Any:
    """Add city-specific extra fields required by transaction initiation endpoints."""
    if city != "jiaxing" or not bank_code or operation_path not in TRADE_INIT_PATHS:
        return payload
    if not isinstance(payload, dict):
        return payload
    payload.setdefault("yhdm", bank_code)
    return payload

def build_case(operation: dict[str, Any], bank_data: BankData, account_type: str, bank_code: str, bank_name: str, trade_date: str, amount: float | None, fixed_params: dict[str, Any] | None = None, city: str = "", base_url: str = "", request_overrides: dict[str, Any] | None = None) -> dict[str, Any]:
    raw_payload = parse_json_example(operation.get("example"), operation["summary"])
    request = enrich_payload(raw_payload, bank_data, account_type, bank_code, bank_name, trade_date, amount)
    request = apply_fixed_params(request, fixed_params or {})
    request = apply_fixed_params(request, request_overrides or {})
    request = inject_city_trade_fields(request, operation["path"], city, bank_code)
    return {"name": operation["summary"], "method": operation["method"], "path": operation["path"], "tags": operation.get("tags", []), "city": city, "baseUrl": base_url, "request": request}


def parse_extra_headers(values: list[str]) -> dict[str, str]:
    headers = {"Content-Type": "application/json"}
    for item in values:
        if ":" not in item: raise ValueError(f"请求头格式应为 Name:Value，实际为：{item}")
        name, value = item.split(":", 1); headers[name.strip()] = value.strip()
    return headers


def status_expected(status: int, expression: str) -> bool:
    for part in [p.strip() for p in (expression or "2xx").split(",") if p.strip()]:
        if re.fullmatch(r"\dxx", part) and status // 100 == int(part[0]): return True
        if "-" in part:
            start, end = [int(x) for x in part.split("-", 1)]
            if start <= status <= end: return True
        if part.isdigit() and status == int(part): return True
    return False


def call_http(base_url: str, case: dict[str, Any], headers: dict[str, str], timeout: int, expected: str) -> dict[str, Any]:
    url = base_url.rstrip("/") + "/" + case["path"].lstrip("/")
    body = json.dumps(case["request"], ensure_ascii=False).encode("utf-8")
    req, started = urllib.request.Request(url, data=body, headers=headers, method=case["method"]), time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            response_body, status = resp.read().decode("utf-8", errors="replace"), resp.status
            ok = status_expected(status, expected); error = "" if ok else f"HTTP 状态码 {status} 不符合期望 {expected}"
    except urllib.error.HTTPError as exc:
        response_body, status = exc.read().decode("utf-8", errors="replace"), exc.code
        ok = status_expected(status, expected); error = "" if ok else f"HTTP 状态码 {status} 不符合期望 {expected}"
    except Exception as exc:
        response_body, status, ok, error = "", None, False, repr(exc)
    return {"name": case["name"], "method": case["method"], "path": case["path"], "url": url, "status": status, "passed": ok, "elapsed_ms": round((time.perf_counter() - started) * 1000, 2), "error": error, "response_preview": response_body[:2000]}


def write_case_files(cases: list[dict[str, Any]], output_dir: Path) -> Path:
    cases_dir = output_dir / "cases"; cases_dir.mkdir(parents=True, exist_ok=True)
    for old_case in cases_dir.glob("*.json"):
        old_case.unlink()
    for idx, case in enumerate(cases, 1):
        (cases_dir / f"{idx:02d}_{safe_file_name(case['name'])}.json").write_text(json.dumps(case, ensure_ascii=False, indent=2), encoding="utf-8")
    return cases_dir

def write_report(output_dir: Path, cases: list[dict[str, Any]], results: list[dict[str, Any]], mode: str) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    passed = sum(1 for r in results if r.get("passed")) if results else len(cases)
    failed = len(results) - passed if results else 0
    report = {"generated_at": now_text(), "mode": mode, "case_count": len(cases), "passed": passed, "failed": failed, "results": results}
    (output_dir / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    rows = []
    source = results or [{"passed": True, "name": c["name"], "method": c["method"], "path": c["path"], "status": "-", "elapsed_ms": "-", "error": "未发起 HTTP 调用", "response_preview": ""} for c in cases]
    for r in source:
        status_text = "通过" if r.get("passed") else "失败"
        detail = r.get("error") or r.get("response_preview") or ""
        rows.append("<tr>" + f"<td>{html.escape(status_text)}</td><td>{html.escape(str(r['name']))}</td><td>{html.escape(str(r['method']))}</td><td>{html.escape(str(r['path']))}</td><td>{html.escape(str(r.get('status')))}</td><td>{html.escape(str(r.get('elapsed_ms')))}</td><td><pre>{html.escape(detail)}</pre></td>" + "</tr>")
    html_text = f"""<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\"><title>住建部接口自动化测试报告</title><style>body{{font-family:Arial,'Microsoft YaHei',sans-serif;margin:24px}}table{{border-collapse:collapse;width:100%}}td,th{{border:1px solid #ddd;padding:8px;vertical-align:top}}th{{background:#f5f5f5}}pre{{white-space:pre-wrap;max-height:260px;overflow:auto}}</style></head><body><h1>住建部接口自动化测试报告</h1><p>生成时间：{html.escape(report['generated_at'])}；模式：{html.escape(mode)}；用例数：{len(cases)}；通过：{passed}；失败：{failed}</p><table><thead><tr><th>结果</th><th>接口</th><th>方法</th><th>路径</th><th>HTTP状态</th><th>耗时ms</th><th>错误/响应预览</th></tr></thead><tbody>{''.join(rows)}</tbody></table></body></html>"""
    (output_dir / "report.html").write_text(html_text, encoding="utf-8")


def write_template_xlsx(output_path: str | Path) -> None:
    output = Path(output_path); output.parent.mkdir(parents=True, exist_ok=True)
    sheets = [("填写说明", [["公积金接口测试数据收集表"], ["请银行按三个工作表填写测试数据。带 * 的字段为必填。"]]), (CENTER_SHEET, [["序号", "公积金客户号*", "账号*", "户名*", "开户银行", "联行号", "账户类型", "备注"], ["1", "", "", "", "", "", "专户", ""]]), (PRIVATE_SHEET, [["序号", "客户号*", "账号*", "卡号", "证件类型", "证件号码*", "姓名*", "账户类型", "开户行", "联行号", "多方协议号", "手机号", "开户日期", "备注"], ["1", "", "", "", "身份证", "", "", "对私", "", "", "", "", "", ""]]), (PUBLIC_SHEET, [["序号", "客户号*", "账号*", "户名*", "账户类型", "开户行", "联行号", "多方协议号", "统一社会信用代码", "组织机构号码", "机构名称", "法定代表人", "开户日期", "备注"], ["1", "", "", "", "对公", "", "", "", "", "", "", "", "", ""]])]
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" + "".join(f"<Override PartName=\"/xl/worksheets/sheet{i}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" for i in range(1, len(sheets)+1)) + "</Types>")
        zf.writestr("_rels/.rels", f"<Relationships xmlns=\"{PACKAGE_REL_NS}\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>")
        workbook_sheets = "".join(f"<sheet name=\"{html.escape(name, quote=True)}\" sheetId=\"{i}\" r:id=\"rId{i}\"/>" for i, (name, _) in enumerate(sheets, 1))
        zf.writestr("xl/workbook.xml", f"<workbook xmlns=\"{SPREADSHEET_NS}\" xmlns:r=\"{REL_NS}\"><sheets>{workbook_sheets}</sheets></workbook>")
        rel_items = "".join(f"<Relationship Id=\"rId{i}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet{i}.xml\"/>" for i in range(1, len(sheets)+1))
        zf.writestr("xl/_rels/workbook.xml.rels", f"<Relationships xmlns=\"{PACKAGE_REL_NS}\">{rel_items}</Relationships>")
        for sheet_index, (_, rows) in enumerate(sheets, 1):
            row_xml = []
            for r_idx, row in enumerate(rows, 1):
                cells = []
                for c_idx, value in enumerate(row, 1):
                    ref, text = f"{_xlsx_col_name(c_idx)}{r_idx}", html.escape(str(value), quote=False)
                    cells.append(f"<c r=\"{ref}\" t=\"inlineStr\"><is><t>{text}</t></is></c>")
                row_xml.append(f"<row r=\"{r_idx}\">{''.join(cells)}</row>")
            zf.writestr(f"xl/worksheets/sheet{sheet_index}.xml", f"<worksheet xmlns=\"{SPREADSHEET_NS}\"><sheetData>{''.join(row_xml)}</sheetData></worksheet>")


def parse_args(argv: list[str]) -> argparse.Namespace:
    p = argparse.ArgumentParser(description="住建部 OpenAPI 通用自动化测试工具")
    p.add_argument("--openapi-url", default=DEFAULT_OPENAPI_URL); p.add_argument("--openapi-file", default="")
    p.add_argument("--excel", default=DEFAULT_EXCEL); p.add_argument("--output-dir", default=".codex-run/zjb-openapi-test")
    p.add_argument("--mode", choices=["dry-run", "run", "template"], default="dry-run"); p.add_argument("--template-output", default="")
    p.add_argument("--base-url", default=""); p.add_argument("--tag-filter", default=""); p.add_argument("--summary-filter", default="")
    p.add_argument("--city-config", default=str(DEFAULT_CITY_CONFIG), help="city fixed parameter config file")
    p.add_argument("--city", default="", help="city code in config; default uses defaultCity")
    p.add_argument("--account-type", choices=["private", "public", "both"], default="private")
    p.add_argument("--bank-code", default=""); p.add_argument("--bank-name", default=""); p.add_argument("--trade-date", default=today_yyyymmdd())
    p.add_argument("--amount", type=float, default=None); p.add_argument("--timeout", type=int, default=30); p.add_argument("--expect-http", default="2xx")
    p.add_argument("--header", action="append", default=[])
    p.add_argument("--set-field", action="append", default=[], help="override request field recursively, format: name=value")
    p.add_argument("--xtlx", default="", help="shortcut for --set-field xtlx=value")
    p.add_argument("--ywlx", default="", help="shortcut for --set-field ywlx=value")
    return p.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:]); output_dir = Path(args.output_dir)
    if args.mode == "template":
        template_path = Path(args.template_output or (output_dir / "银行测试数据收集表_模板.xlsx")); write_template_xlsx(template_path); print(f"已生成 Excel 模板：{template_path}"); return 0
    spec, bank_data = load_openapi(args.openapi_url, args.openapi_file or None), load_bank_data(args.excel)
    city, fixed_params, config_base_url = load_city_config(args.city_config, args.city)
    base_url = (args.base_url or "").strip() or config_base_url
    request_overrides = parse_field_overrides(args.set_field, args.xtlx, args.ywlx)
    if args.mode == "run" and not base_url: raise SystemExit("run mode requires serviceBaseUrl in city-config.json or --base-url")
    operations = list(iter_operations(spec, args.tag_filter, args.summary_filter))
    if not operations: raise SystemExit("No API matched; check --tag-filter / --summary-filter")
    cases = [build_case(op, bank_data, args.account_type, args.bank_code, args.bank_name, args.trade_date, args.amount, fixed_params, city, base_url, request_overrides) for op in operations]
    cases_dir, results = write_case_files(cases, output_dir), []
    if args.mode == "run":
        headers = parse_extra_headers(args.header)
        for case in cases: results.append(call_http(base_url, case, headers, args.timeout, args.expect_http))
    write_report(output_dir, cases, results, args.mode)
    print(f"已生成 {len(cases)} 个用例：{cases_dir}"); print(f"报告：{output_dir / 'report.html'}")
    if results:
        failed = [r for r in results if not r.get("passed")]; print(f"执行结果：通过 {len(results) - len(failed)}，失败 {len(failed)}"); return 1 if failed else 0
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
