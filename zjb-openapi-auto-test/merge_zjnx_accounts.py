#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把浙江农信又一批测试账号整理进原 Excel（2026-08-18 14:12 用户提供）。
- 2 条对公：杭州浙行置业有限公司 / 昌茂有限公司（证件号=统一社会信用代码，证件类型=18）
- 1 条对私新增：左丘羽
- 1 条对私订正：账号 6230910199142668249 已存在为「周华」，现按用户新数据订正为「闽华」并补证件号
  原则：同一银行账号=同一账户，原地更新避免账号重复，不另起新行。
原文件先备份为 .bak。
"""
from __future__ import annotations
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from zjb_openapi_runner import (  # noqa: E402
    PRIVATE_SHEET, PUBLIC_SHEET, SPREADSHEET_NS, REL_NS, PACKAGE_REL_NS,
    html, zipfile, _xlsx_col_name, read_xlsx_rows,
)

SRC = Path(r"D:\文档\工作文档\嘉兴住建部测试数据\银行测试数据收集表-浙江农信.xlsx")

# 新增 对公（序号顺延为 4、5；统一社会信用代码填到 统一社会信用代码 与 证件号码 两列，证件类型=18）
NEW_PUBLIC = [
    ["4", "", "201000266794697", "杭州浙行置业有限公司", "对公", "浙江农信", "313051", "", "91330104MA2KDGWC5N", "18", "91330104MA2KDGWC5N", ""],
    ["5", "", "201000268046735", "昌茂有限公司", "对公", "浙江农信", "313051", "", "91330101788250960E", "18", "91330101788250960E", ""],
]
# 新增 对私 左丘羽（序号顺延为 6）
NEW_PRIVATE = [
    ["6", "", "6230910199131349397", "", "身份证", "452601198507069615", "左丘羽", "对私", "浙江农信", "", "fkyhdm=313051"],
]
# 订正：账号 6230910199142668249 由 周华 → 闽华，并补证件号
CORRECT_ACCOUNT = "6230910199142668249"
CORRECT_NAME = "闽华"
CORRECT_IDNO = "230108199001213914"


def write_xlsx(output_path: Path, sheets: list) -> None:
    with zipfile.ZipFile(output_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("[Content_Types].xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                    + "".join(f"<Override PartName=\"/xl/worksheets/sheet{i}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" for i in range(1, len(sheets) + 1))
                    + "</Types>")
        zf.writestr("_rels/.rels",
                    f"<Relationships xmlns=\"{PACKAGE_REL_NS}\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>")
        workbook_sheets = "".join(f"<sheet name=\"{html.escape(name, quote=True)}\" sheetId=\"{i}\" r:id=\"rId{i}\"/>" for i, (name, _) in enumerate(sheets, 1))
        zf.writestr("xl/workbook.xml", f"<workbook xmlns=\"{SPREADSHEET_NS}\" xmlns:r=\"{REL_NS}\"><sheets>{workbook_sheets}</sheets></workbook>")
        rel_items = "".join(f"<Relationship Id=\"rId{i}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet{i}.xml\"/>" for i in range(1, len(sheets) + 1))
        zf.writestr("xl/_rels/workbook.xml.rels", f"<Relationships xmlns=\"{PACKAGE_REL_NS}\">{rel_items}</Relationships>")
        for sheet_index, (_, rows) in enumerate(sheets, 1):
            row_xml = []
            for r_idx, row in enumerate(rows, 1):
                cells = []
                for c_idx, value in enumerate(row, 1):
                    ref, text = f"{_xlsx_col_name(c_idx)}{r_idx}", html.escape(str(value), quote=False)
                    cells.append(f"<c r=\"{ref}\" t=\"inlineStr\"><is><t>{text}</t></is></c>")
                row_xml.append(f"<row r=\"{r_idx}\">{''.join(cells)}</row>")
            zf.writestr(f"xl/worksheets/sheet{sheet_index}.xml",
                        f"<worksheet xmlns=\"{SPREADSHEET_NS}\"><sheetData>{''.join(row_xml)}</sheetData></worksheet>")


if __name__ == "__main__":
    raw = read_xlsx_rows(SRC)
    sheet_order = list(raw.keys())
    sheets = []

    for name in sheet_order:
        rows = [list(r) for r in raw[name]]
        if name == PUBLIC_SHEET:
            zh_idx = rows[0].index("账号*")
            existing = {r[zh_idx] for r in rows[1:] if len(r) > zh_idx}
            added = 0
            for row in NEW_PUBLIC:
                if row[zh_idx] in existing:
                    print("对公已存在，跳过：", row[zh_idx])
                else:
                    rows.append(row)
                    existing.add(row[zh_idx])
                    added += 1
            print(f"对公账户信息：追加 {added} 条")
        elif name == PRIVATE_SHEET:
            zh_idx = rows[0].index("账号*")
            name_idx = rows[0].index("姓名*")
            idno_idx = rows[0].index("证件号码*")
            idtype_idx = rows[0].index("证件类型")
            existing = {r[zh_idx] for r in rows[1:] if len(r) > zh_idx}
            # 1) 左丘羽 新增
            added = 0
            for row in NEW_PRIVATE:
                if row[zh_idx] in existing:
                    print("对私已存在，跳过：", row[zh_idx])
                else:
                    rows.append(row)
                    existing.add(row[zh_idx])
                    added += 1
            print(f"对私账户信息：追加 {added} 条")
            # 2) 6230910199142668249 订正（周华 -> 闽华 + 补证件号）
            corrected = False
            for r in rows[1:]:
                if len(r) > zh_idx and r[zh_idx] == CORRECT_ACCOUNT:
                    old_name = r[name_idx]
                    r[name_idx] = CORRECT_NAME
                    if len(r) > idno_idx:
                        r[idno_idx] = CORRECT_IDNO
                    if len(r) > idtype_idx:
                        r[idtype_idx] = "身份证"
                    print(f"对私订正：账号 {CORRECT_ACCOUNT} 户名 {old_name} -> {CORRECT_NAME}，补证件号 {CORRECT_IDNO}")
                    corrected = True
                    break
            if not corrected:
                print(f"! 未找到账号 {CORRECT_ACCOUNT}，未做订正")
        sheets.append((name, rows))

    write_xlsx(SRC, sheets)
    print(f"已回写：{SRC}")
