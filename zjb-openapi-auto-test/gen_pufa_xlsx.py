#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成浦发银行测试数据文档（新建，原路径下本不存在，无丢数据风险）。
表结构镜像已认可的「银行测试数据收集表-邮储银行.xlsx」：
- 对私账户信息：序号, 客户号*, 账号*, 卡号, 证件类型, 证件号码*, 姓名*, 账户类型, 开户行, 联行号, 备注
- 对公账户信息：序号, 客户号*, 账号*, 户名*, 账户类型, 开户行, 银行代码*, 联行号, 统一社会信用代码, 证件类型, 证件号码, 备注
- 中心专户信息：序号, 公积金客户号*, 账号*, 户名*, 开户银行, 联行号, 账户类型, 备注
浦发银行代码 310000 来自 doc 操作记录银行代码对照表（310000=上海浦东发展银行），运行时用 --bank-code 310000 传入 yhdm。
"""
from __future__ import annotations
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from zjb_openapi_runner import (  # noqa: E402
    CENTER_SHEET, PRIVATE_SHEET, PUBLIC_SHEET,
    SPREADSHEET_NS, REL_NS, PACKAGE_REL_NS, html, zipfile, _xlsx_col_name,
)

OUT = Path(r"D:\文档\工作文档\嘉兴住建部测试数据\银行测试数据收集表-浦发银行.xlsx")

PRIVATE_HEADER = ["序号", "客户号*", "账号*", "卡号", "证件类型", "证件号码*", "姓名*", "账户类型", "开户行", "联行号", "备注"]
PUBLIC_HEADER  = ["序号", "客户号*", "账号*", "户名*", "账户类型", "开户行", "银行代码*", "联行号", "统一社会信用代码", "证件类型", "证件号码", "备注"]
CENTER_HEADER  = ["序号", "公积金客户号*", "账号*", "户名*", "开户银行", "联行号", "账户类型", "备注"]

# 3 对私（证件类型 01=身份证，齐全），6 对公（证件类型 08）
PRIVATE_ROWS = [
    ["1", "", "6217920311465517", "", "01", "500114196402183882", "数据管理平台", "对私", "浦发银行", "", ""],
    ["2", "", "6217920311465228", "", "01", "410503198005043923", "数据管理平台", "对私", "浦发银行", "", ""],
    ["3", "", "6217920311465137", "", "01", "410622197106177496", "数据管理平台", "对私", "浦发银行", "", ""],
]
PUBLIC_ROWS = [
    # 数据管理平台(08)但号码为身份证格式：照原样录入 统一社会信用代码，并提示用户核对
    ["1", "", "6217920311465145", "数据管理平台", "对公", "浦发银行", "310000", "", "460000198103253994", "08", "", ""],
    # 自动化测试 3 条：对公，USCC 暂空
    ["2", "", "88010078801700043097", "自动化测试420202", "对公", "浦发银行", "310000", "", "", "08", "", ""],
    ["3", "", "88010078801500043098", "自动化测试395691", "对公", "浦发银行", "310000", "", "", "08", "", ""],
    ["4", "", "88010078801400043105", "自动化测试936485", "对公", "浦发银行", "310000", "", "", "08", "", ""],
    # 数据管理平台(08)但号码为身份证格式：照原样录入 统一社会信用代码，并提示用户核对
    ["5", "", "6217920311465285", "数据管理平台", "对公", "浦发银行", "310000", "", "360300197112021878", "08", "", ""],
    ["6", "", "6217920311465194", "数据管理平台", "对公", "浦发银行", "310000", "", "341600197912081979", "08", "", ""],
]
CENTER_ROWS = [
    ["1", "", "", "", "浦发银行", "", "专户", ""],
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
    if OUT.exists():
        raise SystemExit(f"目标文件已存在，中止以防覆盖：{OUT}\n如需覆盖请先删除或备份。")
    sheets = [
        ("填写说明", [["公积金接口测试数据收集表（浦发银行）"],
                   ["浦发银行代码 310000。带 * 字段为必填。对公中 自动化测试* 3 条统一社会信用代码为空；数据管理平台(08) 3 条所带号码为身份证格式，需与银行核对是否应配统一社会信用代码。"]]),
        (CENTER_SHEET, [CENTER_HEADER] + CENTER_ROWS),
        (PRIVATE_SHEET, [PRIVATE_HEADER] + PRIVATE_ROWS),
        (PUBLIC_SHEET, [PUBLIC_HEADER] + PUBLIC_ROWS),
    ]
    write_xlsx(OUT, sheets)
    print(f"已生成：{OUT}")
    print(f"对私 {len(PRIVATE_ROWS)} 条 / 对公 {len(PUBLIC_ROWS)} 条")
