# -*- coding: utf-8 -*-
from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

import zjb_openapi_runner as runner


class ZjbOpenapiRunnerTest(unittest.TestCase):
    def test_remove_json_comments_keeps_url_in_string(self) -> None:
        text = '{"url":"http://example.com/a//b","a":1,// comment\n"b":2,/*x*/"c":3}'
        parsed = json.loads(runner.remove_json_comments(text))
        self.assertEqual(parsed["url"], "http://example.com/a//b")
        self.assertEqual(parsed["b"], 2)
        self.assertEqual(parsed["c"], 3)

    def test_template_xlsx_can_be_read(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "template.xlsx"
            runner.write_template_xlsx(path)
            rows = runner.read_xlsx_rows(path)
            self.assertIn(runner.CENTER_SHEET, rows)
            self.assertIn(runner.PRIVATE_SHEET, rows)
            self.assertIn(runner.PUBLIC_SHEET, rows)
            self.assertEqual(rows[runner.PRIVATE_SHEET][0][2], "账号*")

    def test_build_case_replaces_bank_account_fields(self) -> None:
        bank = runner.BankData(
            center_accounts=[{"账号*": "CENTER001", "户名*": "中心户", "开户银行": "杭州银行"}],
            private_accounts=[{"账号*": "P001", "姓名*": "张三", "证件类型": "身份证", "证件号码*": "330100199001010011", "开户行": "杭州银行", "联行号": "313331000000", "手机号": "13800000000"}],
            public_accounts=[{"账号*": "C001", "户名*": "测试公司", "账户类型": "对公"}],
            source="银行测试数据收集表_杭州银行.xlsx",
        )
        operation = {
            "method": "POST", "path": "/api/v1/ywgl/khzhyxxCx", "summary": "客户账户有效查询", "tags": ["住建部"],
            "example": '{"ywslbh":"{{$fakerjs.String.numeric(length=20)}}","yhdm":"old","zhlx":"1","zh":"old","hm":"old","zjlx":"身份证","zjh":"old","jyrq":"20200101"}',
        }
        case = runner.build_case(operation, bank, "private", "313000", "", "20260713", None)
        req = case["request"]
        self.assertEqual(req["yhdm"], "313000")
        self.assertEqual(req["zh"], "P001")
        self.assertEqual(req["hm"], "张三")
        self.assertEqual(req["zjlx"], "01")
        self.assertEqual(req["zjh"], "330100199001010011")
        self.assertEqual(req["jyrq"], "20260713")
        self.assertEqual(len(req["ywslbh"]), 20)

    def test_city_fixed_params_override_request_fields(self) -> None:
        bank = runner.BankData(
            center_accounts=[],
            private_accounts=[{"账号*": "P001", "姓名*": "李四", "证件类型": "身份证", "证件号码*": "330100199001010011"}],
            public_accounts=[],
            source="bank.xlsx",
        )
        operation = {
            "method": "POST",
            "path": "/api/v1/ywgl/khzhyxxCx",
            "summary": "客户账户有效查询",
            "tags": ["住建部"],
            "example": '{"zxbh":"OLD-ZX","jbjgbh":"OLD-JB","jbjgmc":"OLD-NAME","qdbm":"old","wdbh":"old","wdmc":"old","mx":[{"ywjbjgbh":"old","ywqd":"old"}],"zhlx":"1","zh":"old","hm":"old"}',
        }
        fixed = {"zxbh": "330400000000000", "jbjgbh": "330401", "jbjgmc": "嘉兴管理部", "qdbm": "jx", "wdbh": None, "wdmc": None, "ywjbjgbh": "330401", "ywqd": "jx"}
        case = runner.build_case(operation, bank, "private", "", "", "20260713", None, fixed, "jiaxing", "http://test-service")
        req = case["request"]
        self.assertEqual(case["city"], "jiaxing")
        self.assertEqual(case["baseUrl"], "http://test-service")
        self.assertEqual(req["zxbh"], "330400000000000")
        self.assertEqual(req["jbjgbh"], "330401")
        self.assertEqual(req["jbjgmc"], "嘉兴管理部")
        self.assertEqual(req["qdbm"], "jx")
        self.assertIsNone(req["wdbh"])
        self.assertIsNone(req["wdmc"])
        self.assertEqual(req["mx"][0]["ywjbjgbh"], "330401")
        self.assertEqual(req["mx"][0]["ywqd"], "jx")

    def test_load_city_config_reads_service_base_url(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "city-config.json"
            path.write_text(json.dumps({
                "defaultCity": "c1",
                "defaultServiceBaseUrl": "http://default-service",
                "cities": {
                    "c1": {"serviceBaseUrl": "http://city-service", "fixedParams": {"zxbh": "1"}},
                    "c2": {"fixedParams": {"zxbh": "2"}},
                },
            }), encoding="utf-8")
            city, fixed, base_url = runner.load_city_config(path, "")
            self.assertEqual(city, "c1")
            self.assertEqual(fixed["zxbh"], "1")
            self.assertEqual(base_url, "http://city-service")
            city, fixed, base_url = runner.load_city_config(path, "c2")
            self.assertEqual(city, "c2")
            self.assertEqual(fixed["zxbh"], "2")
            self.assertEqual(base_url, "http://default-service")

    def test_city_fixed_params_add_missing_header_fields_only_to_header_like_node(self) -> None:
        payload = {
            "zjjsSFkywxxAddReqDTO": {"zxbh": "OLD", "qdbm": "old", "xtlx": "GJGL"},
            "skywPlmxAddReqDTOList": [{"ywjbjgbh": "old", "fkzh": "A001"}],
        }
        fixed = {"zxbh": "330400000000000", "qdbm": "gt", "hsjgbh": "0101", "ywjbjgbh": "0101"}
        result = runner.apply_fixed_params(payload, fixed)
        header = result["zjjsSFkywxxAddReqDTO"]
        detail = result["skywPlmxAddReqDTOList"][0]
        self.assertEqual(header["zxbh"], "330400000000000")
        self.assertEqual(header["qdbm"], "gt")
        self.assertEqual(header["hsjgbh"], "0101")
        self.assertNotIn("ywjbjgbh", header)
        self.assertEqual(detail["ywjbjgbh"], "0101")
        self.assertNotIn("hsjgbh", detail)

    def test_request_overrides_replace_nested_xtlx_ywlx_after_city_params(self) -> None:
        bank = runner.BankData(
            center_accounts=[],
            private_accounts=[{"账号*": "P001", "姓名*": "王五", "证件类型": "身份证", "证件号码*": "330100199001010011"}],
            public_accounts=[],
            source="bank.xlsx",
        )
        operation = {
            "method": "POST",
            "path": "/api/v1/ywgl/addSkywPlsk",
            "summary": "新增批量收款请求",
            "tags": ["住建部"],
            "example": '{"zjjsSFkywxxAddReqDTO":{"zxbh":"OLD","xtlx":"GJGL","ywlx":"11101"},"skywPlmxAddReqDTOList":[{"xtlx":"GJGL","ywlx":"11101","fkzh":"old"}]}',
        }
        case = runner.build_case(
            operation,
            bank,
            "private",
            "313331",
            "杭州银行",
            "20260713",
            None,
            {"zxbh": "330400000000000"},
            "jiaxing",
            "http://test-service",
            {"xtlx": "GDGL", "ywlx": "11305"},
        )
        req = case["request"]
        self.assertEqual(req["zjjsSFkywxxAddReqDTO"]["zxbh"], "330400000000000")
        self.assertEqual(req["zjjsSFkywxxAddReqDTO"]["xtlx"], "GDGL")
        self.assertEqual(req["zjjsSFkywxxAddReqDTO"]["ywlx"], "11305")
        self.assertEqual(req["skywPlmxAddReqDTOList"][0]["xtlx"], "GDGL")
        self.assertEqual(req["skywPlmxAddReqDTOList"][0]["ywlx"], "11305")


    def test_explicit_private_account_type_overrides_template_public_zhlx(self) -> None:
        bank = runner.BankData(
            center_accounts=[],
            private_accounts=[{"账号*": "P-PRIVATE", "姓名*": "对私张三", "证件类型": "身份证", "证件号码*": "330100199001010011", "开户行": "嘉兴银行"}],
            public_accounts=[{"账号*": "C-PUBLIC", "户名*": "对公公司", "账户类型": "对公", "开户行": "嘉兴银行"}],
            source="bank.xlsx",
        )
        operation = {
            "method": "POST",
            "path": "/api/v1/ywgl/addSkywPlsk",
            "summary": "新增批量收款请求",
            "tags": ["住建部"],
            "example": '{"zjjsSFkywxxAddReqDTO":{"xtlx":"GJGL","ywlx":"11101"},"skywPlmxAddReqDTOList":[{"fkzh":"old","fkhm":"old","zhlx":"2","zjlx":"18","zjh":"old"}]}',
        }
        case = runner.build_case(operation, bank, "private", "313095", "嘉兴银行", "20260715", None)
        detail = case["request"]["skywPlmxAddReqDTOList"][0]
        self.assertEqual(detail["fkzh"], "P-PRIVATE")
        self.assertEqual(detail["fkhm"], "对私张三")
        self.assertEqual(detail["zhlx"], "1")
        self.assertEqual(detail["zjlx"], "01")
        self.assertEqual(detail["zjh"], "330100199001010011")

    def test_jiaxing_batch_trade_injects_extra_root_yhdm(self) -> None:
        bank = runner.BankData(
            center_accounts=[],
            private_accounts=[{"账号*": "P001", "姓名*": "赵六", "证件类型": "身份证", "证件号码*": "330100199001010011"}],
            public_accounts=[],
            source="bank.xlsx",
        )
        operation = {
            "method": "POST",
            "path": "/api/v1/ywgl/addSkywPlsk",
            "summary": "新增批量收款请求",
            "tags": ["住建部"],
            "example": '{"zjjsSFkywxxAddReqDTO":{"xtlx":"GJGL","ywlx":"11101","yhdm":"old"},"skywPlmxAddReqDTOList":[{"fkzh":"old"}]}',
        }
        case = runner.build_case(operation, bank, "private", "302000", "中信银行", "20260714", None, {}, "jiaxing", "http://test-service")
        req = case["request"]
        self.assertEqual(req["yhdm"], "302000")
        self.assertEqual(req["zjjsSFkywxxAddReqDTO"]["yhdm"], "302000")

    def test_non_jiaxing_batch_trade_does_not_inject_extra_root_yhdm(self) -> None:
        payload = {"zjjsSFkywxxAddReqDTO": {"yhdm": "302000"}, "skywPlmxAddReqDTOList": []}
        result = runner.inject_city_trade_fields(payload, "/api/v1/ywgl/addSkywPlsk", "zaozhuang", "302000")
        self.assertNotIn("yhdm", result)

    def test_parse_field_overrides_accepts_shortcuts_and_set_field(self) -> None:
        overrides = runner.parse_field_overrides(["xtlx=GDGL", "wdbh=null", "jybs=2"], "DKGL", "11305")
        self.assertEqual(overrides["xtlx"], "DKGL")
        self.assertEqual(overrides["ywlx"], "11305")
        self.assertIsNone(overrides["wdbh"])
        self.assertEqual(overrides["jybs"], 2)

    def test_write_case_files_clears_stale_json_files(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            output_dir = Path(tmp)
            cases_dir = output_dir / "cases"
            cases_dir.mkdir()
            (cases_dir / "old_case.json").write_text("{}", encoding="utf-8")
            (cases_dir / "keep.txt").write_text("keep", encoding="utf-8")
            case = {"name": "接口A", "method": "POST", "path": "/a", "tags": [], "request": {}}
            runner.write_case_files([case], output_dir)
            self.assertFalse((cases_dir / "old_case.json").exists())
            self.assertTrue((cases_dir / "keep.txt").exists())
            self.assertEqual([p.name for p in cases_dir.glob("*.json")], ["01_接口A.json"])


if __name__ == "__main__":
    unittest.main()
