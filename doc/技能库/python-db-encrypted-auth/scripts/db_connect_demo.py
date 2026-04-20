#!/usr/bin/env python3
import argparse
import json
import os
import subprocess
import sys
from pathlib import Path
from urllib.parse import parse_qs, urlparse

from cryptography.fernet import Fernet


def prepend_dm_native_libs() -> None:
    """Import dmPython 前注入达梦官方客户端库路径，避免 CODE:-70089 加密模块加载失败。"""
    dm_home = os.environ.get("DM_HOME", "").strip()
    candidates = []
    if dm_home:
        candidates.append(Path(dm_home) / "bin")
    candidates.append(Path("/home/source/snap/dm/dmdbms/bin"))
    for bin_dir in candidates:
        if (bin_dir / "libdmdpi.so").is_file():
            extra = str(bin_dir)
            old = os.environ.get("LD_LIBRARY_PATH", "")
            if extra not in old.split(os.pathsep):
                os.environ["LD_LIBRARY_PATH"] = extra + (os.pathsep + old if old else "")
            break


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Connect DB using encrypted config.")
    parser.add_argument("--config", default="config/db.secure.json")
    parser.add_argument("--key-env", default="DB_CONFIG_KEY")
    parser.add_argument(
        "--sql",
        default="",
        help="达梦/MySQL：连接成功后执行该 SQL（如 SELECT ...）；默认仅做 SELECT 1 探测",
    )
    parser.add_argument("--max-rows", type=int, default=50, help="--sql 时最多打印行数")
    parser.add_argument(
        "--engine",
        choices=("auto", "dmPython", "disql"),
        default="auto",
        help="达梦连接方式：auto 优先 dmPython，失败则回退 disql（与本机 DM 客户端一致）",
    )
    return parser.parse_args()


def load_config(path: str) -> dict:
    file_path = Path(path)
    if not file_path.exists():
        raise SystemExit(f"[ERROR] Config not found: {file_path}")
    return json.loads(file_path.read_text(encoding="utf-8"))


def save_config(path: str, conf: dict) -> None:
    Path(path).write_text(json.dumps(conf, ensure_ascii=False, indent=2), encoding="utf-8")


def decrypt_password(password_enc: str, key_env: str) -> str:
    key = os.getenv(key_env)
    if not key:
        raise SystemExit(f"[ERROR] Missing key env: {key_env}")
    fernet = Fernet(key.encode("utf-8"))
    return fernet.decrypt(password_enc.encode("utf-8")).decode("utf-8")


def encrypt_password(password: str, key_env: str) -> str:
    key = os.getenv(key_env)
    if not key:
        raise SystemExit(f"[ERROR] Missing key env: {key_env}")
    fernet = Fernet(key.encode("utf-8"))
    return fernet.encrypt(password.encode("utf-8")).decode("utf-8")


def parse_dm_jdbc_url(jdbc_url: str) -> dict:
    # Example: jdbc:dm://172.20.17.19:31595?schema=CAP_GJJ_ZJJS_LCGL
    if not jdbc_url.startswith("jdbc:dm://"):
        return {}
    parsed = urlparse(jdbc_url.replace("jdbc:", "", 1))
    result = {}
    if parsed.hostname:
        result["host"] = parsed.hostname
    if parsed.port:
        result["port"] = parsed.port
    query = parse_qs(parsed.query)
    if query.get("schema"):
        result["schema"] = query["schema"][0]
    return result


def default_dm_home() -> Path:
    return Path(os.environ.get("DM_HOME", "/home/source/snap/dm/dmdbms"))


def run_dm_disql(
    user: str,
    password: str,
    host: str,
    port: int,
    schema: str,
    sql: str,
    health_only: bool,
) -> None:
    """使用本机已安装的 disql（与静默安装客户端一致），避免 pip dmPython 与加密库不匹配。"""
    dm_home = default_dm_home()
    disql = dm_home / "bin" / "disql"
    if not disql.is_file():
        raise SystemExit(
            "[ERROR] 未找到 disql: %s。请安装达梦客户端并设置 DM_HOME，或见技能 reference.md。" % disql
        )
    env = os.environ.copy()
    bin_dir = str(dm_home / "bin")
    env["LD_LIBRARY_PATH"] = bin_dir + os.pathsep + env.get("LD_LIBRARY_PATH", "")
    env["PATH"] = bin_dir + os.pathsep + env.get("PATH", "")
    conn_str = "%s/%s@%s:%s" % (user, password, host, port)
    parts = []
    if schema and (sql or not health_only):
        parts.append("SET SCHEMA %s;" % schema)
    if health_only:
        parts.append("SELECT 1 AS ok FROM DUAL;")
    else:
        parts.append(sql.strip().rstrip(";") + ";")
    parts.append("exit;")
    script = "\n".join(parts)
    r = subprocess.run(
        [str(disql), conn_str],
        input=script,
        text=True,
        capture_output=True,
        env=env,
        timeout=120,
    )
    if r.stdout:
        print(r.stdout, end="")
    if r.stderr:
        print(r.stderr, file=sys.stderr, end="")
    if r.returncode != 0:
        raise SystemExit(r.returncode)


def connect_dm(conf: dict, password: str):
    prepend_dm_native_libs()
    try:
        import dmPython  # type: ignore
    except ImportError as exc:
        raise SystemExit("[ERROR] dmPython not installed. Run: pip install dmPython") from exc
    return dmPython.connect(
        user=conf["user"],
        password=password,
        server=conf["host"],
        port=int(conf.get("port", 5236)),
        autoCommit=True,
    )


def connect_mysql(conf: dict, password: str):
    try:
        import pymysql  # type: ignore
    except ImportError as exc:
        raise SystemExit("[ERROR] pymysql not installed. Run: pip install pymysql") from exc
    return pymysql.connect(
        host=conf["host"],
        port=int(conf.get("port", 3306)),
        user=conf["user"],
        password=password,
        database=conf["database"],
        connect_timeout=int(conf.get("connect_timeout", 5)),
        charset="utf8mb4",
        autocommit=True,
    )


def main() -> None:
    args = parse_args()
    conf = load_config(args.config)
    jdbc_overrides = parse_dm_jdbc_url(conf.get("jdbc_url", ""))
    dm_host = jdbc_overrides.get("host", conf.get("host"))
    dm_port = int(jdbc_overrides.get("port", conf.get("port", 5236)))
    dm_schema = jdbc_overrides.get("schema", conf.get("schema"))

    if conf.get("password_enc"):
        password = decrypt_password(conf["password_enc"], args.key_env)
    elif conf.get("password"):
        password = conf["password"]
        # 仅在已设置密钥环境变量时写回密文，避免无密钥时无法连接。
        if os.getenv(args.key_env):
            conf["password_enc"] = encrypt_password(password, args.key_env)
            conf.pop("password", None)
            save_config(args.config, conf)
            print("[OK] Plain password auto-encrypted and written back to config.")
        else:
            print("[INFO] Using plaintext password from config (set %s to enable encryption write-back)." % args.key_env, file=sys.stderr)
    else:
        raise SystemExit("[ERROR] Missing password field. Require `password_enc` or `password`.")

    db_type = conf.get("db_type", "dm").lower()
    sql = (args.sql or "").strip()

    if db_type == "dm" and args.engine == "disql":
        run_dm_disql(
            conf["user"],
            password,
            dm_host,
            dm_port,
            dm_schema or "",
            sql,
            health_only=not sql,
        )
        return

    conn = None
    if db_type == "dm" and args.engine == "auto":
        try:
            dm_conf = dict(conf)
            dm_conf["host"] = dm_host
            dm_conf["port"] = dm_port
            conn = connect_dm(dm_conf, password)
            if dm_schema:
                conn.current_schema = dm_schema
        except SystemExit as e:
            err = str(e.args[0]) if e.args else str(e)
            if "dmPython" in err:
                print("[WARN] dmPython 未安装或不可用，已自动改用 disql。", file=sys.stderr)
                run_dm_disql(
                    conf["user"],
                    password,
                    dm_host,
                    dm_port,
                    dm_schema or "",
                    sql,
                    health_only=not sql,
                )
                return
            raise
        except BaseException as e:
            if "70089" in str(e):
                print("[WARN] dmPython 加密模块异常，已自动改用 disql。", file=sys.stderr)
                run_dm_disql(
                    conf["user"],
                    password,
                    dm_host,
                    dm_port,
                    dm_schema or "",
                    sql,
                    health_only=not sql,
                )
                return
            raise
    elif db_type == "dm" and args.engine == "dmPython":
        dm_conf = dict(conf)
        dm_conf["host"] = dm_host
        dm_conf["port"] = dm_port
        conn = connect_dm(dm_conf, password)
        if dm_schema:
            conn.current_schema = dm_schema
    elif db_type == "mysql":
        conn = connect_mysql(conf, password)
    else:
        raise SystemExit(f"[ERROR] Unsupported db_type: {db_type}. Allowed: dm, mysql")

    try:
        with conn.cursor() as cursor:
            if db_type == "dm" and dm_schema and sql:
                cursor.execute(f"SET SCHEMA {dm_schema}")
            if sql:
                cursor.execute(sql)
                rows = cursor.fetchmany(args.max_rows + 1)
                cols = [d[0] for d in cursor.description] if cursor.description else []
                print("[OK] DB connection success.")
                print("COLUMNS:", ", ".join(cols))
                n = len(rows)
                if n > args.max_rows:
                    rows = rows[: args.max_rows]
                    print("[WARN] Showing first %d rows only." % args.max_rows)
                for i, row in enumerate(rows, 1):
                    print("ROW_%d:" % i, row)
            else:
                cursor.execute("SELECT 1")
                row = cursor.fetchone()
                print("[OK] DB connection success.")
                print(f"[OK] Health check result: {row[0] if row else 'N/A'}")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
