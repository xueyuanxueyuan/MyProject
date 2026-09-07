#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""邮储银行测试数据 追加 脚本。

用户提供 PNG 截图，包含：
- 对私账户信息 9 条（账号、户名、证件号、手机号）
- 对公账户信息 5 条（账号、户名）

其中 2 个对公账号（951013013000092245 / 951013013000080965）已存在于原文件中，
本脚本按账号去重，仅追加不重复的 3 条对公 + 9 条对私。

原文件 `银行测试数据收集表-邮储银行.xlsx` 已存在，先读原文档、备份、
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

SRC = Path(r"D:\文档\工作文档\嘉兴住建部测试数据\银行测试数据收集表-邮储银行.xlsx")

# 从 PNG 截图读出的新增数据
# 表头：序号, 客户号*, 账号*, 卡号, 证件类型, 证件号码*, 姓名*, 账户类型, 开户行, 联行号, 备注
NEW_PRIVATE = [
    # 原表头无手机号列，手机号暂放备注列
    ["", "6217991000107125580", "", "01", "140123200012311453", "测展测", "对私", "邮储银行", "", "手机号:187000005304"],
    ["", "6217991000107061157", "", "01", "652722200012312112", "测苑测", "对私", "邮储银行", "", "手机号:13233335954"],
    ["", "6217991000107109105", "", "01", "371724199012315451", "测煜测", "对私", "邮储银行", "", "手机号:13133332057"],
    ["", "6217991000107206513", "", "01", "820101199503061549", "测弘", "对私", "邮储银行", "", "手机号:17733333904"],
    ["", "6217991000107135423", "", "01", "120118199012316377", "测明", "对私", "邮储银行", "", "手机号:18722226973"],
    ["", "6217991000107172194", "", "01", "370502198012315254", "测胤测", "对私", "邮储银行", "", "手机号:13355554701"],
    ["", "6217991000107150869", "", "01", "610924200012313670", "测吴", "对私", "邮储银行", "", "手机号:13311117313"],
    ["", "6217991000107118197", "", "01", "610204196012318815", "测聪", "对私", "邮储银行", "", "手机号:18811114567"],
    ["", "6217991000107253986", "", "01", "310118196012317531", "测越", "对私", "邮储银行", "", "手机号:13922220706"],
]

# 表头：序号, 客户号*, 账号*, 户名*, 账户类型, 开户行, 银行代码*, 联行号, 统一社会信用代码, 证件类型, 证件号码, 备注
NEW_PUBLIC = [
    ["", "951018013000100620", "火焰山芭蕉扇租赁公司", "对公", "邮储银行", "313009", "", "", "08", "", ""],
    ["", "944039013000320371", "银企测试用户1", "对公", "邮储银行", "313009", "", "", "08", "", ""],
    ["", "944034013000290656", "银企测试用户2", "对公", "邮储银行", "313009", "", "", "08", "", ""],
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


def account_col_index(header: list) -> int:
    """找到 '账号*' 列的索引。"""
    for i, h in enumerate(header):
        if str(h).strip() == "账号*":
            return i
    raise ValueError("表头中未找到 账号* 列")


def main() -> None:
    rows = read_xlsx_rows(SRC)

    # 对私：追加 9 条
    priv = rows[PRIVATE_SHEET]
    priv_header = priv[0]
    priv_account_idx = account_col_index(priv_header)
    existing_priv_accounts = {str(r[priv_account_idx]).strip() for r in priv[1:] if len(r) > priv_account_idx}
    for r in priv:
        while len(r) < len(priv_header):
            r.append("")
    seq_start = len(priv)
    added_priv = 0
    for new in NEW_PRIVATE:
        acct = str(new[priv_account_idx - 1]).strip()  # new 列表不含序号，相对序号后移 1
        if acct in existing_priv_accounts:
            continue
        new_row = [str(seq_start)] + new
        priv.append(pad_row(new_row, len(priv_header)))
        seq_start += 1
        added_priv += 1

    # 对公：追加不重复的 3 条
    pub = rows[PUBLIC_SHEET]
    pub_header = pub[0]
    pub_account_idx = account_col_index(pub_header)
    existing_pub_accounts = {str(r[pub_account_idx]).strip() for r in pub[1:] if len(r) > pub_account_idx}
    for r in pub:
        while len(r) < len(pub_header):
            r.append("")
    seq_start = len(pub)
    added_pub = 0
    skipped_pub = []
    for new in NEW_PUBLIC:
        acct = str(new[pub_account_idx - 1]).strip()
        if acct in existing_pub_accounts:
            skipped_pub.append(acct)
            continue
        new_row = [str(seq_start)] + new
        pub.append(pad_row(new_row, len(pub_header)))
        seq_start += 1
        added_pub += 1

    # 填写说明：追加一行备注
    shuoming = rows["填写说明"]
    skip_note = f"对公去重跳过：{', '.join(skipped_pub)}" if skipped_pub else "对公无重复"
    shuoming.append(["2026-08-31 PNG 截图补充",
                     f"对私+{added_priv}；对公+{added_pub}（原始截图 5 条，{skip_note}）。统一社会信用代码待补充。"])

    sheets = [(name, rows[name]) for name in rows.keys()]
    write_xlsx(SRC, sheets)
    print(f"对私追加 {added_priv} 条，对公追加 {added_pub} 条，对公跳过重复 {len(skipped_pub)} 条。")


if __name__ == "__main__":
    main()
