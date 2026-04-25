# Gjj（文档与 GitHub 同步说明）

本目录 **根级 Git 仓库** 仅跟踪 **`doc/`** 下的文档与规范，推送到 GitHub：`xueyuanxueyuan/MyProject`（远程树以当前中文目录为准，如 `doc/项目规范`、`doc/技能库`、`doc/提示词` 等）。

## 日常提交与推送

在 **本工程根目录**（`Gjj`）执行，不要只在 `doc/` 子目录里初始化另一个仓库：

```bash
cd /path/to/Gjj
git add doc/
git status
git commit -m "说明（AI生成提交）"
git push
```

## 说明

- 业务代码、前端等若不在 `doc/` 下，默认 **不会** 被提交（见根目录 `.gitignore`）。
- 文档目录重命名后，可执行 `python3 doc/项目规范/check_doc_paths.py` 扫描 README 与规范文档中的旧英文路径残留。
- 推送需能使用 SSH 访问 GitHub；若 HTTPS 被重置，请使用 `~/.ssh/config` 中通过 `ssh.github.com:443` 连接的配置。
