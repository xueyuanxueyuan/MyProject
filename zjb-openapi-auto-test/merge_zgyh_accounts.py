#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""中国银行测试数据 追加/补全 脚本。

原文件（Codex 8-13 建）对私已含 李四海/王五岳/高坤贞 3 条但「证件号码*」为空；
用户本次重新提供这 3 条（带证件号码）+ 新增 1 条对公（丽水莲都金泽丽枝诊所）。
处理：
1. 对私 3 条：按「账号*」匹配，原地补全 证件类型=01、证件号码*（保留客户号/开户行/备注）。
2. 对公：原表头无 证件类型/证件号码 列 → 在「备注」前插入两列（与 浙江农信/邮储/兴业 对齐），再追加 1 条。
3. 中心专户/填写说明原样保留（说明文字更新为当前状态）。
写入方式：stdlib 重写（与原工具 write_xlsx 同源），read_xlsx_rows / load_bank_data 均可读。
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

SRC = Path(r"D:\文档\工作文档\嘉兴住建部测试数据\银行测试数据收集表-中国银行.xlsx")

# 用户本次提供的补充数据：账号 -> (证件类型, 证件号码)
PRIVATE_UPDATES = {
    "689957723415": ("01", "11011120010101111X"),   # 李四海
    "691257723436": ("01", "110111200110011111"),   # 王五岳
    "692557723529": ("01", "610103199808259349"),   # 高坤贞
}
NEW_PUBLIC_DICT = {
    "账号*": "393558713145",
    "户名*": "丽水莲都金泽丽枝诊所",
    "账户类型": "对公",
    "开户行": "中国银行",
    "银行代码*": "104000",
    "统一社会信用代码": "",
    "证件类型": "08",
    "证件号码": "",
    "备注": "",
}


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


def main() -> None:
    rows = read_xlsx_rows(SRC)

    # ---- 1. 对私：原地补全 证件类型/证件号码 ----
    priv = rows[PRIVATE_SHEET]
    header = priv[0]
    idx_acct, idx_zjlx, idx_zjh = header.index("账号*"), header.index("证件类型"), header.index("证件号码*")
    updated = 0
    for r in priv[1:]:
        if r[idx_acct] in PRIVATE_UPDATES:
            zjlx, zjh = PRIVATE_UPDATES[r[idx_acct]]
            r[idx_zjlx] = zjlx
            r[idx_zjh] = zjh
            updated += 1
    if updated != len(PRIVATE_UPDATES):
        raise SystemExit(f"对私匹配异常：期望更新 {len(PRIVATE_UPDATES)} 条，实际 {updated} 条")

    # ---- 2. 对公：表头插入 证件类型/证件号码（备注 前），再追加新行 ----
    pub = rows[PUBLIC_SHEET]
    pub_header = pub[0]
    if "证件类型" not in pub_header:
        bz = pub_header.index("备注")
        pub_header.insert(bz, "证件类型")
        pub_header.insert(bz + 1, "证件号码")
        for r in pub[1:]:
            r.insert(bz, "")
            r.insert(bz + 1, "")
    seq = str(len(pub))  # 数据行数 = len(pub)-1，新行序号 = len(pub)
    new_row = {h: (seq if h == "序号" else NEW_PUBLIC_DICT.get(h, "")) for h in pub_header}
    pub.append([new_row[h] for h in pub_header])

    # ---- 3. 填写说明：更新说明文字（原「仅包含对私」已不准确） ----
    shuoming = rows["填写说明"]
    for r in shuoming:
        if len(r) >= 2 and str(r[0]) == "说明":
            r[1] = "2026-08-19 补充：对私 3 条已补全证件号码；新增对公 1 条（丽水莲都金泽丽枝诊所，证件类型 08，统一社会信用代码暂空）。"

    # ---- 4. 回写（sheet 顺序保持原文件） ----
    sheets = [(name, rows[name]) for name in rows.keys()]
    write_xlsx(SRC, sheets)
    print(f"已回写：{SRC}")
    print(f"对私补全 {updated} 条；对公现 {len(pub) - 1} 条数据行（含新增 1 条）")


if __name__ == "__main__":
    main()
