#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把兴业银行 3 个对公测试账号追加进原 Excel 的 对公账户信息 工作表，保留全部已有数据。
原表 对公 只有表头（无数据行），本次在表头末尾（备注前）补充 '证件类型'、'证件号码' 两列，
用于保留用户数据中的 zjlx=08（否则引擎对公会默认 18）。
表头结构：序号, 客户号*, 账号*, 户名*, 账户类型, 开户行, 银行代码*, 联行号, 统一社会信用代码, 证件类型, 证件号码, 备注
"""
from __future__ import annotations
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from zjb_openapi_runner import (  # noqa: E402
    PUBLIC_SHEET, SPREADSHEET_NS, REL_NS, PACKAGE_REL_NS, html, zipfile,
    _xlsx_col_name, read_xlsx_rows,
)

SRC = Path(r"D:\文档\工作文档\嘉兴住建部测试数据\银行测试数据收集表-兴业银行.xlsx")

NEW_PUBLIC_ROWS = [
    ["1", "", "398000100100448300", "平度田梓晨鞋服有限公司", "对公", "兴业银行", "309000", "", "", "08", "", ""],
    ["2", "", "117000100101038309", "扬州谢立轩航空股份有限公司", "对公", "兴业银行", "309000", "", "", "08", "", ""],
    ["3", "", "206610100100467877", "平度林荣轩金融股份有限公司", "对公", "兴业银行", "309000", "", "", "08", "", ""],
]


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
            header = rows[0]
            if "证件类型" not in header:
                # 在 备注 前插入 证件类型、证件号码，保持与 浙江农信/邮储 对公表头一致
                header = header[:-1] + ["证件类型", "证件号码", header[-1]]
                rows[0] = header
            zh_idx = header.index("账号*")
            existing = {r[zh_idx] for r in rows[1:] if len(r) > zh_idx}
            appended = 0
            for new_row in NEW_PUBLIC_ROWS:
                if new_row[zh_idx] in existing:
                    print("已存在，跳过：", new_row[zh_idx])
                else:
                    rows.append(new_row)
                    appended += 1
                    existing.add(new_row[zh_idx])
            print(f"对公账户信息：已追加 {appended} 条（原表头无数据行）")
        sheets.append((name, rows))
    write_xlsx(SRC, sheets)
    print(f"已回写：{SRC}")
