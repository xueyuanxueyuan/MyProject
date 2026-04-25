#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]

FULL_PATH_REPLACEMENTS = {
    "doc/rules": "doc/项目规范",
    "doc/prompt": "doc/提示词",
    "doc/records": "doc/操作记录",
    "doc/architecture": "doc/架构设计",
    "doc/analysis": "doc/需求分析",
    "doc/audit_reports": "doc/审计报告",
    "doc/demand": "doc/需求文档",
    "doc/design": "doc/设计文档",
    "doc/review": "doc/评审记录",
    "doc/skills": "doc/技能库",
}

README_PATH_REPLACEMENTS = {
    "`rules/`": "`项目规范/`",
    "`prompt/`": "`提示词/`",
    "`records/`": "`操作记录/`",
    "`architecture/`": "`架构设计/`",
    "`analysis/`": "`需求分析/`",
    "`audit_reports/`": "`审计报告/`",
    "`demand/`": "`需求文档/`",
    "`design/`": "`设计文档/`",
    "`review/`": "`评审记录/`",
    "`skills/`": "`技能库/`",
}

TARGET_FILES = [
    REPO_ROOT / "README.md",
    REPO_ROOT / "doc" / "README.md",
    REPO_ROOT / "doc" / ".gitignore",
]


def iter_project_rules() -> list[Path]:
    rules_dir = REPO_ROOT / "doc" / "项目规范"
    return sorted(rules_dir.rglob("*.md"))


def check_file(file_path: Path) -> list[str]:
    text = file_path.read_text(encoding="utf-8")
    findings: list[str] = []

    replacements = dict(FULL_PATH_REPLACEMENTS)
    if file_path.name == "README.md" and file_path.parent == REPO_ROOT / "doc":
        replacements.update(README_PATH_REPLACEMENTS)
    elif file_path == REPO_ROOT / "doc" / ".gitignore":
        replacements.update(
            {
                "skills/python-db-encrypted-auth/config/db.secure.json": (
                    "技能库/python-db-encrypted-auth/config/db.secure.json"
                )
            }
        )

    for legacy, current in replacements.items():
        if legacy in text:
            findings.append(f"{file_path.relative_to(REPO_ROOT)}: 发现旧路径 `{legacy}`，应改为 `{current}`")
    return findings


def main() -> int:
    findings: list[str] = []
    for target in TARGET_FILES + iter_project_rules():
        findings.extend(check_file(target))

    if findings:
        print("[ERROR] 检测到旧英文目录引用残留：", file=sys.stderr)
        for finding in findings:
            print(f"- {finding}", file=sys.stderr)
        return 1

    print("[OK] 未发现旧英文目录引用。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
