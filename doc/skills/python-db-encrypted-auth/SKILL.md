---
name: python-db-encrypted-auth
description: Use Python to connect to databases with encrypted credentials stored in a secure config file. Apply when users ask for Python DB access, encrypted password configuration, key-safe secret handling, or secure AI-assisted database coding.
---

# Python DB Encrypted Auth (DaMeng Ready)

## 适用场景
- 用户要求使用 Python 连接数据库，同时不能明文保存数据库密码。
- 用户要求将认证信息放入加密配置文件，并要求密钥安全。
- 用户提到关键词：`python连接数据库`、`达梦数据库`、`DM8`、`加密配置`、`数据库密码`、`密钥管理`、`Fernet`、`dmPython`。

## 强制安全规则
1. 数据库密码必须以密文形式存储在配置文件中（如 `config/db.secure.json`）。
2. 解密密钥禁止写入代码、配置文件或 Git 仓库。
3. 解密密钥必须从环境变量或密钥管理系统读取（默认 `DB_CONFIG_KEY`）。
4. 任何日志、报错、调试输出中禁止打印明文密码和完整密钥。
5. 提交代码前必须确认 `.gitignore` 已忽略本地密钥文件和临时明文文件。

## 标准执行流程
1. 准备依赖：
   - 达梦：`pip install cryptography dmPython`
   - MySQL（可选兼容）：`pip install cryptography pymysql`
2. 初始化配置文件（已提供）：
   - `config/db.secure.json`
   - 你只需要修改：`host`、`user`、`password`
   - 若你已有 Java 配置 `jdbc:dm://...?...`，可直接填写 `jdbc_url`，脚本会自动解析 `host/port/schema`
3. 保存密钥（示例）：
   - `export DB_CONFIG_KEY='<generated_key>'`
4. 连接验证：
   - `python scripts/db_connect_demo.py --config config/db.secure.json`
5. 执行查询（技能脚本，默认 `--engine auto`：优先 `dmPython`，失败则自动用本机 `disql`）：
   - `python scripts/db_connect_demo.py --config config/db.secure.json --engine auto --sql "select * from 表 where 条件"`
   - 强制使用已安装的达梦客户端：`--engine disql`（需 `DM_HOME` 或默认 `/home/source/snap/dm/dmdbms` 下存在 `bin/disql`）
6. 若已设置 `DB_CONFIG_KEY`，首次连接可将明文 `password` 写回为 `password_enc`；未设置密钥时允许仅用明文连接（不写回配置文件）。

## AI 执行要求
- 在执行数据库连接代码前，若配置仅有 `password_enc`，必须检查 `DB_CONFIG_KEY`；若仅有明文 `password` 且无写回需求，可连接但不得把密码写入日志。
- 始终优先读取密文配置并运行解密逻辑，不允许回退到明文密码硬编码。
- 若用户给出明文密码，仅用于本地一次性加密，随后立即引导删除明文痕迹。
- 输出结果时仅展示连接状态和必要元数据，不输出敏感字段内容。
- 达梦场景优先识别 `schema` 或 `jdbc_url` 中的 `schema` 参数，并在连接后设置 `current_schema`。

## 输出格式
应用该技能时，输出必须包含：
- 使用的加密配置文件路径
- 使用的密钥来源（仅来源说明，不输出密钥值）
- 执行的连接验证命令
- 连接结果与失败时的修复建议

## 附加资料
- 详细说明见 [reference.md](reference.md)
- 可执行脚本见 `scripts/encrypt_db_password.py` 与 `scripts/db_connect_demo.py`
