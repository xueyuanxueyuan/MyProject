#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""浦发银行测试数据 追加 脚本。

用户提供 JPG 截图，包含：
- 对私账户信息 2 条（账号、户名、证件号）
- 对公账户信息 2 条（户名、账号）

原文件 `银行测试数据收集表-浦发银行.xlsx` 已存在，先读原文档、备份、
按原文档表头追加回写，不新建、不丢原数据。
"""
from __future__ import annotations
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from zjb_openapi_runner import (  # noqa: E402
    CENTER_SHEET, PRIVATE_SHEET, PUBLIC_SHEET,
    read_xlsx_rows,
    SPREADSHEET_NS, REL_NS, PACKAGE_REL_NS, html, zipfile, _xlsx_col_name,
)

SRC = Path(r"D:\文档\工作文档\嘉兴住建部测试数据\银行测试数据收集表-浦发银行.xlsx")

# 从 JPG 截图读出的新增数据
# 表头：序号, 客户号*, 账号*, 卡号, 证件类型, 证件号码*, 姓名*, 账户类型, 开户行, 联行号, 备注
NEW_PRIVATE = [
    ["", "6217920302942854", "", "01", "410821195607074626", "数据管理平台", "对私", "浦发银行", "", ""],
    ["", "6217920302985788", "", "01", "360926200104242751", "数据管理平台", "对私", "浦发银行", "", ""],
]

# 表头：序号, 客户号*, 账号*, 户名*, 账户类型, 开户行, 银行代码*, 联行号, 统一社会信用代码, 证件类型, 证件号码, 备注
# 沿用原文件对公 自动化测试* 行的统一社会信用代码为空、证件类型=08 的写法
NEW_PUBLIC = [
    ["", "93010078801000057613", "自动化测试5756901", "对公", "浦发银行", "310000", "", "", "08", "", ""],
    ["", "93010078801300057611", "自动化测试3922798", "对公", "浦发银行", "310000", "", "", "08", "", ""],
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


def pad_row(row: list, header_len: int) -> list:
    """将行补齐/截断到表头长度，防止写入后列错位。"""
    return (row + [""] * header_len)[:header_len]


def main() -> None:
    rows = read_xlsx_rows(SRC)

    # 对私：追加 2 条
    priv = rows[PRIVATE_SHEET]
    priv_header = priv[0]
    for r in priv:
        while len(r) < len(priv_header):
            r.append("")
    seq_start = len(priv)
    for i, new in enumerate(NEW_PRIVATE, start=seq_start):
        new_row = [str(i)] + new
        priv.append(pad_row(new_row, len(priv_header)))

    # 对公：追加 2 条
    pub = rows[PUBLIC_SHEET]
    pub_header = pub[0]
    for r in pub:
        while len(r) < len(pub_header):
            r.append("")
    seq_start = len(pub)
    for i, new in enumerate(NEW_PUBLIC, start=seq_start):
        new_row = [str(i)] + new
        pub.append(pad_row(new_row, len(pub_header)))

    # 填写说明：追加一行备注
    shuoming = rows["填写说明"]
    shuoming.append(["2026-08-28 JPG 截图补充",
                     "对私+2（数据管理平台 6217920302942854/6217920302985788）；对公+2（自动化测试5756901/3922798）。统一社会信用代码待银行补充。"])

    sheets = [(name, rows[name]) for name in rows.keys()]
    write_xlsx(SRC, sheets)


if __name__ == "__main__":
    main()
