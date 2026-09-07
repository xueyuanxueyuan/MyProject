#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成浙商银行测试数据文档（新建，原路径下本不存在，无丢数据风险）。
表结构镜像已认可的「银行测试数据收集表-邮储银行.xlsx」：
- 对私账户信息：序号, 客户号*, 账号*, 卡号, 证件类型, 证件号码*, 姓名*, 账户类型, 开户行, 联行号, 备注
- 对公账户信息：序号, 客户号*, 账号*, 户名*, 账户类型, 开户行, 银行代码*, 联行号, 统一社会信用代码, 证件类型, 证件号码, 备注
- 中心专户信息：序号, 公积金客户号*, 账号*, 户名*, 开户银行, 联行号, 账户类型, 备注
浙商银行代码 313041 来自 doc 操作记录银行代码对照表（313041=浙商银行），运行时用 --bank-code 313041 传入 yhdm。
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

OUT = Path(r"D:\文档\工作文档\嘉兴住建部测试数据\银行测试数据收集表-浙商银行.xlsx")

PRIVATE_HEADER = ["序号", "客户号*", "账号*", "卡号", "证件类型", "证件号码*", "姓名*", "账户类型", "开户行", "联行号", "备注"]
PUBLIC_HEADER  = ["序号", "客户号*", "账号*", "户名*", "账户类型", "开户行", "银行代码*", "联行号", "统一社会信用代码", "证件类型", "证件号码", "备注"]
CENTER_HEADER  = ["序号", "公积金客户号*", "账号*", "户名*", "开户银行", "联行号", "账户类型", "备注"]

# 3 对私（证件类型 01=身份证，齐全），3 对公（证件类型 08，USCC 暂空）
PRIVATE_ROWS = [
    ["1", "", "6223093310082449417", "", "01", "510901200608136208", "吴瑞", "对私", "浙商银行", "", ""],
    ["2", "", "6223093310082449425", "", "01", "431021198612202754", "葛明", "对私", "浙商银行", "", ""],
    ["3", "", "6223093310082449433", "", "01", "31010920080323089X", "马娜", "对私", "浙商银行", "", ""],
]
PUBLIC_ROWS = [
    ["1", "", "3310010010120183052407", "翔安HAD鑫博腾飞网络有限公司", "对公", "浙商银行", "313041", "", "", "08", "", ""],
    ["2", "", "3310010010120183052538", "南湖JXO思优科技有限公司", "对公", "浙商银行", "313041", "", "", "08", "", ""],
    ["3", "", "75691101011112", "苏州区盟链数字科技有限公司", "对公", "浙商银行", "313041", "", "", "08", "", ""],
]
CENTER_ROWS = [
    ["1", "", "", "", "浙商银行", "", "专户", ""],
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
        ("填写说明", [["公积金接口测试数据收集表（浙商银行）"],
                   ["浙商银行代码 313041。带 * 字段为必填。对公账户统一社会信用代码暂为空，需补充后才能发通对公签约/交易。"]]),
        (CENTER_SHEET, [CENTER_HEADER] + CENTER_ROWS),
        (PRIVATE_SHEET, [PRIVATE_HEADER] + PRIVATE_ROWS),
        (PUBLIC_SHEET, [PUBLIC_HEADER] + PUBLIC_ROWS),
    ]
    write_xlsx(OUT, sheets)
    print(f"已生成：{OUT}")
    print(f"对私 {len(PRIVATE_ROWS)} 条 / 对公 {len(PUBLIC_ROWS)} 条")
