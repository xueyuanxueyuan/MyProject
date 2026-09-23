#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
gjj-codegen 配套工具：Gjj 工程架构基线扫描与规范体检。

用途
  1) scan  : 扫描工程，产出「架构画像」——模块清单、模块角色(api/app/busi)、
             包根、分层文件计数、技术栈版本、前端栈识别。用于生成代码前对齐现状。
  2) check : 按 Gjj 约定做规范体检，产出违规清单。用于「自动优化」环节定位改造点。

用法
  python gjj_arch_scan.py scan  --root <工程根> [--out 架构画像.md]
  python gjj_arch_scan.py check --root <工程根> [--out 规范体检.md] [--max-report 200]

约定来源（本文件不重复正文，详见技能主源）
  doc/技能库/gjj-codegen/SKILL.md
  doc/技能库/gjj-codegen/references/architecture-baseline.md

注意
  - 只读扫描，不修改任何源文件。
  - 输出统一 UTF-8 无 BOM。
  - check 为「线索」不是「事实」：命中项仍需人工/编译复核，不得直接宣称已修复。
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from collections import defaultdict
from pathlib import Path

# ---------------------------------------------------------------- 常量

SKIP_DIRS = {
    "target", "build", "dist", "node_modules", ".git", ".idea", ".vscode",
    ".svn", "out", "logs", "~", ".mvn", "coverage",
}

MODULE_ROLE_PATTERNS = [
    ("api", re.compile(r"-basic-svc-api-v\d+$|-api-v\d+$")),
    ("api", re.compile(r"-basic-svc-api$|-api$")),
    ("app", re.compile(r"-basic-svc-app$|-app$")),
    ("busi", re.compile(r"-basic-svc-busi$|-busi$")),
    ("core", re.compile(r"-core$|-common$")),
    ("agg", re.compile(r"-agg$|-web$")),
]

# 技术栈探测：pom.xml / package.json 中的 key -> 展示名
STACK_KEYS = [
    ("java.version", "Java"),
    ("maven.compiler.source", "Java(compiler)"),
    ("spring-boot.version", "Spring Boot"),
    ("spring-cloud.version", "Spring Cloud"),
    ("spring-cloud-alibaba.version", "Spring Cloud Alibaba"),
    ("mybatis.plus.version", "MyBatis-Plus"),
    ("hutool.version", "Hutool"),
    ("knife4j.version", "Knife4j"),
    ("swagger.version", "Swagger"),
    ("sharding-sphere.version", "ShardingSphere"),
    ("dameng.driver.version", "达梦驱动"),
    ("kingbase8.version", "Kingbase"),
    ("mysql.version", "MySQL"),
    ("redission.boot.version", "Redisson"),
]

LAYER_PATTERNS = {
    "Controller": re.compile(r"Controller\.java$"),
    "Service接口": re.compile(r"(?<!Impl)Service\.java$"),
    "Service实现": re.compile(r"ServiceImpl\.java$"),
    "领域服务Dm": re.compile(r"DmService(Impl)?\.java$"),
    "DO持久化对象": re.compile(r"DO\.java$"),
    "Mapper接口": re.compile(r"Mapper\.java$"),
    "MapperXML": re.compile(r"Mapper\.xml$"),
    "ReqDTO": re.compile(r"ReqDTO\.java$"),
    "RespDTO": re.compile(r"RespDTO\.java$"),
    "EvtDTO": re.compile(r"EvtDTO\.java$"),
    "FeignClient": re.compile(r"(FeignClient|Client)\.java$"),
    "常量Constant": re.compile(r"Constant\.java$"),
    "枚举Enum": re.compile(r"Enum\.java$"),
}

# ---------------------------------------------------------------- 工具


def iter_files(root: Path, suffixes: tuple[str, ...]):
    """遍历 root 下指定后缀文件，跳过构建产物目录。"""
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS and not d.startswith(".")]
        for name in filenames:
            if name.endswith(suffixes):
                yield Path(dirpath) / name


def read_text(path: Path, limit: int = 400_000) -> str:
    try:
        return path.read_text(encoding="utf-8", errors="ignore")[:limit]
    except OSError:
        return ""


def detect_module_role(name: str) -> str:
    for role, pat in MODULE_ROLE_PATTERNS:
        if pat.search(name):
            return role
    return "other"


def rel(root: Path, p: Path) -> str:
    try:
        return p.relative_to(root).as_posix()
    except ValueError:
        return p.as_posix()


def detect_package_root(java_file: Path) -> str:
    """从 java 文件路径中推断包根：取 java 目录之后到模块业务包之前的 4 段。"""
    parts = java_file.parts
    if "java" not in parts:
        return ""
    idx = len(parts) - 1 - parts[::-1].index("java")
    seg = parts[idx + 1:]
    return ".".join(seg[: min(6, len(seg))])


# ---------------------------------------------------------------- scan


def probe_stack(root: Path) -> dict[str, str]:
    """从 pom.xml / package.json 探测技术栈版本。"""
    found: dict[str, str] = {}
    for pom in list(root.glob("pom.xml")) + list(root.glob("*/*/pom.xml"))[:40]:
        text = read_text(pom, 200_000)
        for key, label in STACK_KEYS:
            if label in found:
                continue
            m = re.search(rf"<{re.escape(key)}>([^<]+)</{re.escape(key)}>", text)
            if m:
                found[label] = m.group(1).strip()
        m = re.search(r"<artifactId>spring-boot-starter-parent</artifactId>\s*<groupId>[^<]+</groupId>\s*<version>([^<]+)</version>", text)
        if m and "Spring Boot" not in found:
            found["Spring Boot"] = m.group(1).strip()
    for pkg in list(root.glob("package.json")) + list(root.glob("*/package.json"))[:20]:
        text = read_text(pkg, 200_000)
        for key, label in (("vue", "Vue"), ("element-plus", "Element Plus"),
                           ("element-ui", "Element UI"), ("vite", "Vite"),
                           ("typescript", "TypeScript"), ("pinia", "Pinia"),
                           ("vuex", "Vuex"), ("@vue/cli-service", "Vue CLI")):
            if label in found:
                continue
            m = re.search(rf'"{re.escape(key)}"\s*:\s*"([^"]+)"', text)
            if m:
                found[label] = m.group(1).strip()
    return dict(sorted(found.items()))


def scan(root: Path) -> str:
    modules: dict[str, dict] = {}
    layer_count: dict[str, dict[str, int]] = defaultdict(lambda: defaultdict(int))
    packages: dict[str, set[str]] = defaultdict(set)

    for pom in iter_files(root, ("pom.xml",)):
        mod_dir = pom.parent
        mod_name = mod_dir.name
        if mod_dir == root:
            continue
        role = detect_module_role(mod_name)
        java_files = list(iter_files(mod_dir, (".java",)))
        xml_files = list(iter_files(mod_dir, (".xml",)))
        modules[mod_name] = {
            "role": role,
            "path": rel(root, mod_dir),
            "java": len(java_files),
            "xml": len(xml_files),
        }
        for f in java_files:
            for layer, pat in LAYER_PATTERNS.items():
                if pat.search(f.name):
                    layer_count[mod_name][layer] += 1
        for f in java_files[:400]:
            pkg = detect_package_root(f)
            if pkg:
                packages[mod_name].add(pkg)

    lines: list[str] = []
    lines.append("# 架构画像（自动生成）")
    lines.append("")
    lines.append(f"- 扫描根：`{root.as_posix()}`")
    lines.append(f"- 模块数：**{len(modules)}**")
    lines.append("")
    lines.append("> 本文件由 `gjj_arch_scan.py scan` 生成，是代码生成前的现状基线，不是设计结论。")
    lines.append("")

    stack = probe_stack(root)
    if stack:
        lines.append("## 技术栈")
        lines.append("")
        lines.append("| 组件 | 版本 |")
        lines.append("|---|---|")
        for k, v in stack.items():
            lines.append(f"| {k} | {v} |")
        lines.append("")

    by_role: dict[str, list[str]] = defaultdict(list)
    for name, info in modules.items():
        by_role[info["role"]].append(name)

    lines.append("## 模块清单（按角色）")
    lines.append("")
    for role in ("api", "app", "busi", "core", "agg", "other"):
        names = sorted(by_role.get(role, []))
        if not names:
            continue
        lines.append(f"### {role}（{len(names)}）")
        lines.append("")
        lines.append("| 模块 | 路径 | Java | XML | 分层计数 |")
        lines.append("|---|---|---:|---:|---|")
        for name in names:
            info = modules[name]
            layers = layer_count.get(name, {})
            layer_txt = "、".join(f"{k}:{v}" for k, v in sorted(layers.items()) if v) or "—"
            lines.append(f"| `{name}` | `{info['path']}` | {info['java']} | {info['xml']} | {layer_txt} |")
        lines.append("")

    lines.append("## 包根")
    lines.append("")
    lines.append("| 模块 | 包根（示例） |")
    lines.append("|---|---|")
    for name in sorted(modules):
        pkgs = sorted(packages.get(name, set()))[:3]
        if pkgs:
            lines.append(f"| `{name}` | " + "、".join(f"`{p}`" for p in pkgs) + " |")
    lines.append("")
    return "\n".join(lines)


# ---------------------------------------------------------------- check


class Violation:
    def __init__(self, rule: str, level: str, path: str, detail: str):
        self.rule = rule
        self.level = level
        self.path = path
        self.detail = detail


def check(root: Path) -> list[Violation]:
    vs: list[Violation] = []

    for f in iter_files(root, (".java",)):
        text = read_text(f)
        if not text:
            continue
        r = rel(root, f)
        name = f.name

        # Controller 约定
        if name.endswith("Controller.java"):
            if "extends BaseController" not in text:
                vs.append(Violation("C1", "高", r, "Controller 未继承 BaseController"))
            m = re.search(r'@RequestMapping\(\s*"([^"]+)"', text)
            if m and not m.group(1).startswith("/api/v"):
                vs.append(Violation("C2", "中", r, f'@RequestMapping 前缀为 `{m.group(1)}`，非 `/api/v1/...`'))
            if re.search(r"public\s+(?!R<)\w[\w<>.,\s]*\s+\w+\s*\(", text) and "ResponseEntity" not in text:
                publics = re.findall(r"public\s+([A-Z]\w*(?:<[^;]*?>)?)\s+\w+\s*\(", text)
                # 排除异步/流式包装：CompletableFuture<R<T>>、ResponseEntity<R<T>> 等内含 R< 的仍视为合规
                bad = [p for p in publics if "R<" not in p and p not in ("String", "void", "ResponseEntity")]
                if bad:
                    vs.append(Violation("C3", "中", r, f"存在非统一响应返回类型：{sorted(set(bad))[:3]}"))
            if "@Tag(" not in text:
                vs.append(Violation("C4", "低", r, "缺少 Knife4j @Tag 类注解"))

        # Service 实现约定
        if name.endswith("ServiceImpl.java"):
            if "@Service" not in text:
                vs.append(Violation("S1", "高", r, "ServiceImpl 未标注 @Service"))
            if re.search(r"@Transactional\b", text) and "rollbackFor" not in text:
                vs.append(Violation("S2", "中", r, "@Transactional 未声明 rollbackFor = Exception.class"))

        # Mapper 约定
        if name.endswith("Mapper.java"):
            if "extends IBaseMapper" not in text and "extends BaseMapper" not in text:
                vs.append(Violation("M1", "高", r, "Mapper 未继承 IBaseMapper/BaseMapper"))
            if "@Mapper" in text:
                vs.append(Violation("M2", "低", r, "Mapper 上多余的 @Mapper（启动类已 @MapperScan）"))
            xml = f.with_suffix(".xml")
            if not xml.exists():
                vs.append(Violation("M3", "提示", r, "同目录无对应 Mapper.xml（简单 CRUD 可只靠 MP）"))

        # DO 约定
        if name.endswith("DO.java"):
            if "@TableName" not in text:
                vs.append(Violation("D1", "高", r, "DO 缺少 @TableName"))
            if "extends BaseDO" not in text:
                vs.append(Violation("D2", "中", r, "DO 未继承 BaseDO（缺少审计/逻辑删除字段）"))

        # DTO 目录归属
        if name.endswith("DTO.java"):
            low = r.lower()
            if "/dto/req/" not in low and "/dto/resp/" not in low and "/dto/evt/" not in low and "/dto/" in low:
                vs.append(Violation("T1", "低", r, "DTO 未落在 dto/req、dto/resp 或 dto/evt 目录"))

        # Feign 降级
        if re.search(r"@FeignClient", text):
            if "fallback" not in text and "fallbackFactory" not in text:
                vs.append(Violation("F1", "高", r, "@FeignClient 未配置 fallback / fallbackFactory"))

        if "Fallback" in name and re.search(r"return\s+null\s*;", text):
            vs.append(Violation("F2", "高", r, "降级实现出现 `return null`，应返回 R.fail(...) 或抛 BizException"))

        # 通用坏味道
        if "System.out.println" in text:
            vs.append(Violation("G1", "中", r, "存在 System.out.println，应改用 @Slf4j"))
        if re.search(r"catch\s*\([^)]*\)\s*\{\s*\}", text):
            vs.append(Violation("G2", "高", r, "空 catch 块，吞异常"))
        if "TODO" in text or "FIXME" in text:
            vs.append(Violation("G3", "提示", r, "存在 TODO/FIXME 占位"))

    return vs


def render_check(root: Path, vs: list[Violation], max_report: int) -> str:
    level_rank = {"高": 0, "中": 1, "低": 2, "提示": 3}
    vs.sort(key=lambda v: (level_rank.get(v.level, 9), v.rule, v.path))

    by_rule: dict[str, int] = defaultdict(int)
    by_level: dict[str, int] = defaultdict(int)
    for v in vs:
        by_rule[v.rule] += 1
        by_level[v.level] += 1

    lines: list[str] = []
    lines.append("# 规范体检报告（自动生成）")
    lines.append("")
    lines.append(f"- 扫描根：`{root.as_posix()}`")
    lines.append(f"- 命中总数：**{len(vs)}**（高 {by_level.get('高', 0)} / 中 {by_level.get('中', 0)} / 低 {by_level.get('低', 0)} / 提示 {by_level.get('提示', 0)}）")
    lines.append("")
    lines.append("> 本报告只提供**线索**。每条命中都必须回到源码人工确认，"
                 "修复后必须编译或运行真实验证，不得凭本报告直接宣称「已修复」。")
    lines.append("")

    rule_desc = {
        "C1": "Controller 未继承 BaseController", "C2": "RequestMapping 前缀非 /api/v1",
        "C3": "返回类型非统一 R<T>", "C4": "缺少 @Tag",
        "S1": "ServiceImpl 缺 @Service", "S2": "@Transactional 缺 rollbackFor",
        "M1": "Mapper 未继承 IBaseMapper", "M2": "多余 @Mapper", "M3": "无对应 Mapper.xml",
        "D1": "DO 缺 @TableName", "D2": "DO 未继承 BaseDO",
        "T1": "DTO 目录归属不规范",
        "F1": "Feign 缺降级", "F2": "降级 return null",
        "G1": "System.out.println", "G2": "空 catch", "G3": "TODO/FIXME",
    }
    if by_rule:
        lines.append("## 规则命中统计")
        lines.append("")
        lines.append("| 规则 | 说明 | 命中 |")
        lines.append("|---|---|---:|")
        for rule in sorted(by_rule, key=lambda x: (level_rank.get(vs[[v.rule for v in vs].index(x)].level, 9), x)):
            lines.append(f"| {rule} | {rule_desc.get(rule, '')} | {by_rule[rule]} |")
        lines.append("")

    lines.append(f"## 明细（最多 {max_report} 条）")
    lines.append("")
    lines.append("| 级别 | 规则 | 文件 | 说明 |")
    lines.append("|---|---|---|---|")
    for v in vs[:max_report]:
        lines.append(f"| {v.level} | {v.rule} | `{v.path}` | {v.detail} |")
    if len(vs) > max_report:
        lines.append("")
        lines.append(f"> 其余 {len(vs) - max_report} 条已省略，调大 `--max-report` 查看。")
    lines.append("")
    return "\n".join(lines)


# ---------------------------------------------------------------- main


def main() -> int:
    # Windows 控制台默认 GBK，中文帮助/报告输出会触发 UnicodeEncodeError（退出码 1），先统一 UTF-8
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8")
        except (AttributeError, ValueError):
            pass

    ap = argparse.ArgumentParser(description="Gjj 工程架构基线扫描与规范体检")
    ap.add_argument("mode", choices=("scan", "check"), help="scan=架构画像；check=规范体检")
    ap.add_argument("--root", required=True, help="工程根路径（如 D:/Probject/Gjj/prod/IdeaProject）")
    ap.add_argument("--out", default="", help="输出 Markdown 文件；缺省打印到标准输出")
    ap.add_argument("--max-report", type=int, default=200, help="check 模式明细最大条数")
    args = ap.parse_args()

    root = Path(args.root)
    if not root.exists():
        print(f"[错误] 路径不存在：{root}", file=sys.stderr)
        return 2

    if args.mode == "scan":
        content = scan(root)
    else:
        content = render_check(root, check(root), args.max_report)

    if args.out:
        out = Path(args.out)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(content, encoding="utf-8")
        print(f"[完成] 已写入：{out.as_posix()}（{len(content.splitlines())} 行）")
    else:
        print(content)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
