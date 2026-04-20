#!/usr/bin/env python3
import argparse
import json
import os
from pathlib import Path

from cryptography.fernet import Fernet


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Encrypt DB password into secure config.")
    parser.add_argument("--host", required=True)
    parser.add_argument("--port", type=int, default=None)
    parser.add_argument("--database", required=True)
    parser.add_argument("--user", required=True)
    parser.add_argument("--password", required=True)
    parser.add_argument("--out", default="config/db.secure.json")
    parser.add_argument("--db-type", default="dm", choices=["dm", "mysql"])
    parser.add_argument("--schema", default="")
    parser.add_argument("--jdbc-url", default="")
    parser.add_argument("--connect-timeout", type=int, default=5)
    parser.add_argument("--key-env", default="DB_CONFIG_KEY")
    parser.add_argument("--generate-key", action="store_true")
    return parser.parse_args()


def get_key(key_env: str, generate: bool) -> bytes:
    key = os.getenv(key_env)
    if key:
        return key.encode("utf-8")
    if generate:
        new_key = Fernet.generate_key()
        print(f"[INFO] Generated key. Set env before connect:")
        print(f"export {key_env}='{new_key.decode('utf-8')}'")
        return new_key
    raise SystemExit(f"[ERROR] Missing key env: {key_env}. Use --generate-key or set env first.")


def main() -> None:
    args = parse_args()
    key = get_key(args.key_env, args.generate_key)
    fernet = Fernet(key)
    password_enc = fernet.encrypt(args.password.encode("utf-8")).decode("utf-8")
    port = args.port if args.port else (5236 if args.db_type == "dm" else 3306)

    secure_config = {
        "db_type": args.db_type,
        "host": args.host,
        "port": port,
        "database": args.database,
        "user": args.user,
        "password_enc": password_enc,
        "connect_timeout": args.connect_timeout,
        "schema": args.schema,
        "jdbc_url": args.jdbc_url,
    }

    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(secure_config, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"[OK] Secure config written: {out_path}")
    print("[OK] Plain password was used only in-memory for encryption.")


if __name__ == "__main__":
    main()
