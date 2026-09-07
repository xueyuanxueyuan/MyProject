#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把浙江农信 3 个对公测试账号追加进原 Excel 的 对公账户信息 工作表，保留全部已有数据。
原表 对公 只有表头，本次在表头末尾补充 '证件类型'、'证件号码' 两列，用于保留截图中的 zjlx=08。"""
from __future__ import annotations
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from zjb_openapi_runner import (  # noqa: E402
    PUBLIC_SHEET, SPREADSHEET_NS, REL_NS, PACKAGE_REL_NS, html, zipfile,
    _xlsx_col_name, read_xlsx_rows,
)

SRC = Path(r"D:\文档\工作文档\嘉兴住建部测试数据\银行测试数据收集表-浙江农信.xlsx")

NEW_PUBLIC_ROWS = [
    ["1", "", "201000293995639", "自动化杰省榨连外科技有限公司", "对公", "浙江农信", "313051", "", "", "08", "", ""],
    ["2", "", "201000294116725", "酸天络", "对公", "浙江农信", "313051", "", "", "08", "", ""],
    ["3", "", "201000294015804", "自动化智寓调焊刊科技有限公司", "对公", "浙江农信", "313051", "", "", "08", "", ""],
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
            # 扩展表头：保留原列，在 备注 前增加 证件类型、证件号码（原表只有表头，无历史数据行受影响）
            header = rows[0]
            if "证件类型" not in header:
                header = header[:-1] + ["证件类型", "证件号码", header[-1]]
                rows[0] = header
            # 去重：按 账号* 列
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
            print(f"对公账户信息：已追加 {appended} 条")
        sheets.append((name, rows))
    write_xlsx(SRC, sheets)
    print(f"已回写：{SRC}")
