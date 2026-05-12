import importlib.util
import unittest
from argparse import Namespace
from pathlib import Path
from unittest.mock import patch


MODULE_PATH = Path(__file__).with_name("db_connect_demo.py")
SPEC = importlib.util.spec_from_file_location("db_connect_demo", MODULE_PATH)
db_connect_demo = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(db_connect_demo)


def make_args():
    return Namespace(
        config="unused.json",
        key_env="DB_CONFIG_KEY",
        sql="",
        max_rows=50,
        engine="auto",
    )


def make_conf():
    return {
        "db_type": "dm",
        "host": "127.0.0.1",
        "port": 5236,
        "user": "u",
        "password": "p",
    }


class CodeError(Exception):
    def __init__(self, code):
        super().__init__(f"code={code}")
        self.code = code


class AutoFallbackTests(unittest.TestCase):
    def test_auto_fallback_when_dmpython_unavailable(self):
        with patch.object(db_connect_demo, "parse_args", return_value=make_args()), patch.object(
            db_connect_demo, "load_config", return_value=make_conf()
        ), patch.object(db_connect_demo, "is_dmpython_available", return_value=False), patch.object(
            db_connect_demo, "run_dm_disql"
        ) as run_disql, patch.object(
            db_connect_demo, "connect_dm"
        ) as connect_dm:
            db_connect_demo.main()
            run_disql.assert_called_once()
            connect_dm.assert_not_called()

    def test_auto_fallback_on_known_error_code(self):
        with patch.object(db_connect_demo, "parse_args", return_value=make_args()), patch.object(
            db_connect_demo, "load_config", return_value=make_conf()
        ), patch.object(db_connect_demo, "is_dmpython_available", return_value=True), patch.object(
            db_connect_demo, "connect_dm", side_effect=CodeError(70089)
        ), patch.object(
            db_connect_demo, "run_dm_disql"
        ) as run_disql:
            db_connect_demo.main()
            run_disql.assert_called_once()

    def test_auto_keeps_non_fallback_exception(self):
        with patch.object(db_connect_demo, "parse_args", return_value=make_args()), patch.object(
            db_connect_demo, "load_config", return_value=make_conf()
        ), patch.object(db_connect_demo, "is_dmpython_available", return_value=True), patch.object(
            db_connect_demo, "connect_dm", side_effect=RuntimeError("boom")
        ), patch.object(
            db_connect_demo, "run_dm_disql"
        ) as run_disql:
            with self.assertRaises(RuntimeError):
                db_connect_demo.main()
            run_disql.assert_not_called()

    def test_auto_does_not_swallow_keyboard_interrupt(self):
        with patch.object(db_connect_demo, "parse_args", return_value=make_args()), patch.object(
            db_connect_demo, "load_config", return_value=make_conf()
        ), patch.object(db_connect_demo, "is_dmpython_available", return_value=True), patch.object(
            db_connect_demo, "connect_dm", side_effect=KeyboardInterrupt()
        ), patch.object(
            db_connect_demo, "run_dm_disql"
        ) as run_disql:
            with self.assertRaises(KeyboardInterrupt):
                db_connect_demo.main()
            run_disql.assert_not_called()


if __name__ == "__main__":
    unittest.main()
