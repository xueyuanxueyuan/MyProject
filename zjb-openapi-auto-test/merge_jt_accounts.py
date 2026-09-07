#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""交通银行测试数据 追加 脚本。

用户提供 HEIC 截图，包含：
- 中心专户信息 1 条（公积金客户号/账号/户名/联行号）
- 对私账户信息 2 条（账号、证件类型 01、证件号码、姓名）
- 对公账户信息 1 条（客户号/户名/证件信息）；对公的「账号*」因截图列溢出模糊，
  脚本按"客户号 16 位 + 账号 18 位"的既有模式拆分，并在填写说明中标注待用户确认。

原文件 `银行测试数据收集表-交通银行.xlsx` 已存在（Codex 8-14 建），故先读原文档、
备份、按原文档表头追加回写，不新建、不丢原数据。
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

SRC = Path(r"D:\文档\工作文档\嘉兴住建部测试数据\银行测试数据收集表-交通银行.xlsx")

# 从 HEIC 截图读出的新增数据
NEW_PRIVATE = [
    # 序号, 客户号*, 账号*, 卡号, 证件类型, 证件号码*, 姓名*, 账户类型, 开户行, 联行号, 多方协议号, 手机号, 开户日期, 备注
    ["", "6222620110101515667", "", "01", "53032519930222173X", "伍维", "对私", "交通银行", "", "", "", "", ""],
    ["", "6222620110101515972", "", "01", "612401199511065055", "熊曝颖", "对私", "交通银行", "", "", "", "", ""],
]
# 对公：客户号 34 位截图疑似客户号+账号拼接；按既有 16+18 拆分，待确认
NEW_PUBLIC = [
    # 序号, 客户号*, 账号*, 户名*, 账户类型, 开户行, 联行号, 多方协议号, 统一社会信用代码, 组织机构号码, 机构名称, 法定代表人, 开户日期, 备注
    ["0115680030521100", "600098013000197869", "岗登再僳蔓辽潦寡擇馆汹瞬狮模", "对公", "交通银行", "", "", "XG1101070JEERBXM2Q", "01110690999", "", "", "", ""],
]
NEW_CENTER = [
    # 序号, 公积金客户号*, 账号*, 户名*, 开户银行, 联行号, 账户类型, 备注
    ["0115680044515028", "296069010018000219071", "旦睡肩莫证丹舅刚酒旋哲必形", "", "01296710999", "专户", ""],
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

    # 对私：追加 2 条；补齐表头长度
    priv = rows[PRIVATE_SHEET]
    priv_header = priv[0]
    for r in priv:
        while len(r) < len(priv_header):
            r.append("")
    seq_start = len(priv)
    for i, new in enumerate(NEW_PRIVATE, start=seq_start):
        new_row = [str(i)] + new
        priv.append(pad_row(new_row, len(priv_header)))

    # 对公：追加 1 条；补齐表头长度
    pub = rows[PUBLIC_SHEET]
    pub_header = pub[0]
    for r in pub:
        while len(r) < len(pub_header):
            r.append("")
    seq_start = len(pub)
    for i, new in enumerate(NEW_PUBLIC, start=seq_start):
        new_row = [str(i)] + new
        pub.append(pad_row(new_row, len(pub_header)))

    # 中心专户：追加 1 条
    center = rows[CENTER_SHEET]
    center_header = center[0]
    for r in center:
        while len(r) < len(center_header):
            r.append("")
    seq_start = len(center)
    for i, new in enumerate(NEW_CENTER, start=seq_start):
        new_row = [str(i)] + new
        center.append(pad_row(new_row, len(center_header)))

    # 填写说明：追加一行备注
    shuoming = rows["填写说明"]
    shuoming.append(["2026-08-25 HEIC 截图补充",
                     "中心+1、对私+2、对公+1。对公账号列疑似客户号(0115680030521100)+账号(600098013000197869)拼接，请与银行确认拆分是否正确。"])

    sheets = [(name, rows[name]) for name in rows.keys()]
    write_xlsx(SRC, sheets)
    print(f"已回写：{SRC}")
    print(f"中心 {len(center) - 1} 条 / 对私 {len(priv) - 1} 条 / 对公 {len(pub) - 1} 条")


if __name__ == "__main__":
    main()
