#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""嘉兴银行 复测数据 合并 脚本。

源文件 `银行测试数据收集表_复测.xlsx` 提供复测（签约类接口）数据，按目标
`银行测试数据收集表-嘉兴银行.xlsx` 的表头做列映射后整体追加，保留目标原有行。

列映射要点：
- 对私：源 `客户号`→`客户号*`、`卡号*`→`账号*`、`手机号*`→`手机号`；账户类型源为空时补'对私'。
- 对公：源 `统一社会信用代码*`→`统一社会信用代码`、`组织机构号码*`→`组织机构号码`；
        源独有的「签约联系人证件类型/证件号码*/手机号*」3 列目标无对应列，并入备注保留。
- 中心：源与目标表头一致，直接按名映射。
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

SRC = Path(r"D:\文档\工作文档\嘉兴住建部测试数据\银行测试数据收集表_复测.xlsx")
TGT = Path(r"D:\文档\工作文档\嘉兴住建部测试数据\银行测试数据收集表-嘉兴银行.xlsx")

# 源列名 -> 目标列名（None 表示目标无此列，另作处理）
PRIVATE_MAP = {
    "客户号": "客户号*",
    "卡号*": "账号*",
    "证件类型": "证件类型",
    "证件号码*": "证件号码*",
    "姓名*": "姓名*",
    "账户类型": "账户类型",
    "开户行": "开户行",
    "联行号": "联行号",
    "多方协议号": "多方协议号",
    "手机号*": "手机号",
    "开户日期": "开户日期",
    "备注": "备注",
}
PUBLIC_MAP = {
    "客户号*": "客户号*",
    "账号*": "账号*",
    "户名*": "户名*",
    "账户类型": "账户类型",
    "开户行": "开户行",
    "联行号": "联行号",
    "多方协议号": "多方协议号",
    "统一社会信用代码*": "统一社会信用代码",
    "组织机构号码*": "组织机构号码",
    "机构名称": "机构名称",
    "法定代表人": "法定代表人",
    "开户日期": "开户日期",
    "备注": "备注",
}
CENTER_MAP = {
    "公积金客户号*": "公积金客户号*",
    "账号*": "账号*",
    "户名*": "户名*",
    "开户银行": "开户银行",
    "联行号": "联行号",
    "账户类型": "账户类型",
    "备注": "备注",
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


def pad_row(row: list, header_len: int) -> list:
    return (row + [""] * header_len)[:header_len]


def map_rows(src_rows, src_header, target_header, col_map, sheet_account_type, drop_to_note=None):
    """按列名映射源行到目标表头。drop_to_note: 源中需要并入备注的列名列表（按源列名）。"""
    src_idx = {name: i for i, name in enumerate(src_header)}
    out = []
    for r in src_rows:
        d = {}
        for sname, tname in col_map.items():
            i = src_idx.get(sname)
            d[tname] = r[i] if (i is not None and i < len(r)) else ""
        # 账户类型为空时补默认值
        if not d.get("账户类型", "").strip():
            d["账户类型"] = sheet_account_type
        # drop_to_note 列并入备注
        if drop_to_note:
            extra = []
            for sname in drop_to_note:
                i = src_idx.get(sname)
                if i is not None and i < len(r) and str(r[i]).strip():
                    extra.append(f"{sname}={r[i]}")
            if extra:
                note = d.get("备注", "")
                d["备注"] = (note + " | " if note.strip() else "") + " / ".join(extra)
        # 按目标表头顺序构造（序号列由 main 统一在行首补，这里排除避免重复导致整列错位）
        out.append([d.get(tname, "") for tname in target_header if tname != "序号"])
    return out


def main() -> None:
    src = read_xlsx_rows(SRC)
    tgt = read_xlsx_rows(TGT)

    # 对私
    tpriv_hdr = tgt[PRIVATE_SHEET][0]
    spriv = src[PRIVATE_SHEET]
    spriv_data = [r for i, r in enumerate(spriv) if i > 0 and any(str(c).strip() for c in r)]
    mapped_priv = map_rows(spriv_data, spriv[0], tpriv_hdr, PRIVATE_MAP, "对私",
                           drop_to_note=None)
    tpriv = tgt[PRIVATE_SHEET]
    for r in tpriv:
        while len(r) < len(tpriv_hdr):
            r.append("")
    seq = len(tpriv)
    for new in mapped_priv:
        seq += 1
        row = ([str(seq)] + new) if tpriv_hdr[0] == "序号" else new
        tpriv.append(pad_row(row, len(tpriv_hdr)))

    # 对公
    tpub_hdr = tgt[PUBLIC_SHEET][0]
    spub = src[PUBLIC_SHEET]
    spub_data = [r for i, r in enumerate(spub) if i > 0 and any(str(c).strip() for c in r)]
    mapped_pub = map_rows(spub_data, spub[0], tpub_hdr, PUBLIC_MAP, "对公",
                          drop_to_note=["签约联系人证件类型", "签约联系人证件号码*", "签约联系人手机号*"])
    tpub = tgt[PUBLIC_SHEET]
    for r in tpub:
        while len(r) < len(tpub_hdr):
            r.append("")
    seq = len(tpub)
    for new in mapped_pub:
        seq += 1
        row = ([str(seq)] + new) if tpub_hdr[0] == "序号" else new
        tpub.append(pad_row(row, len(tpub_hdr)))

    # 中心
    tcen_hdr = tgt[CENTER_SHEET][0]
    scen = src[CENTER_SHEET]
    scen_data = [r for i, r in enumerate(scen) if i > 0 and any(str(c).strip() for c in r)]
    mapped_cen = map_rows(scen_data, scen[0], tcen_hdr, CENTER_MAP, "专户", drop_to_note=None)
    tcen = tgt[CENTER_SHEET]
    for r in tcen:
        while len(r) < len(tcen_hdr):
            r.append("")
    seq = len(tcen)
    for new in mapped_cen:
        seq += 1
        row = ([str(seq)] + new) if tcen_hdr[0] == "序号" else new
        tcen.append(pad_row(row, len(tcen_hdr)))

    # 填写说明追加备注
    shuoming = tgt["填写说明"]
    shuoming.append(["2026-09-14 复测数据合并",
                     f"从 银行测试数据收集表_复测.xlsx 追加：中心+{len(mapped_cen)}、对私+{len(mapped_priv)}、对公+{len(mapped_pub)}（含签约联系人字段并入备注）。保留原行。"])

    sheets = [(name, tgt[name]) for name in tgt.keys()]
    write_xlsx(TGT, sheets)
    print(f"合并完成：中心+{len(mapped_cen)} 对私+{len(mapped_priv)} 对公+{len(mapped_pub)}")


if __name__ == "__main__":
    main()
