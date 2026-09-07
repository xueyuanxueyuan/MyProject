#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把上海农商行新对私账户追加进【原路径】的 银行测试数据收集表-上海农商行.xlsx，
保留原文档全部已有数据。先由调用方备份原文件，本脚本只做追加与回写。"""
from __future__ import annotations
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from zjb_openapi_runner import (  # noqa: E402
    CENTER_SHEET, PRIVATE_SHEET, PUBLIC_SHEET,
    SPREADSHEET_NS, REL_NS, PACKAGE_REL_NS, html, zipfile, _xlsx_col_name,
    read_xlsx_rows,
)

SRC = Path(r"D:\文档\工作文档\嘉兴住建部测试数据\银行测试数据收集表-上海农商行.xlsx")

# 新对私账户（用户 2026-08-18 提供），按原表 对私账户信息 列顺序
NEW_PRIVATE_ROW = ["4", "000321684043", "6235529031002123688", "6235529031002123688",
                   "居民身份证", "330304199904147099", "洪行全测", "对私", "上海农商行",
                   "", "", "13792306346", "", ""]


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
    # 保留原始 sheet 顺序
    sheet_order = list(raw.keys())
    sheets = []
    for name in sheet_order:
        rows = [list(r) for r in raw[name]]  # 复制，避免改动原始解析结果
        if name == PRIVATE_SHEET:
            # 去重：若已存在相同 账号*/卡号/姓名，则跳过
            keys = {(r[2] if len(r) > 2 else "", r[3] if len(r) > 3 else "", r[6] if len(r) > 6 else "") for r in rows[1:]}
            dup = (NEW_PRIVATE_ROW[2], NEW_PRIVATE_ROW[3], NEW_PRIVATE_ROW[6]) in keys
            if dup:
                print("该账户已存在于原文档，跳过追加。")
            else:
                rows.append(NEW_PRIVATE_ROW)
                print("已追加新对私账户到原文档：", NEW_PRIVATE_ROW[6], NEW_PRIVATE_ROW[2])
        sheets.append((name, rows))
    write_xlsx(SRC, sheets)
    print(f"已回写：{SRC}")
