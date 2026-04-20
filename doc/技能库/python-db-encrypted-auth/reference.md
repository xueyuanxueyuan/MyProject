# Python 数据库加密连接参考

## 1) 密文配置格式（示例）
```json
{
  "db_type": "dm",
  "host": "127.0.0.1",
  "port": 5236,
  "database": "gjj_demo",
  "schema": "CAP_GJJ_ZJJS_LCGL",
  "jdbc_url": "jdbc:dm://172.20.17.19:31595?schema=CAP_GJJ_ZJJS_LCGL",
  "user": "demo_user",
  "password_enc": "gAAAAAB...",
  "connect_timeout": 5
}
```

说明：
- `password_enc` 为 Fernet 密文（Base64 字符串）。
- 配置中不允许出现 `password` 明文字段。
- 为了“开箱即用”，也支持首次使用 `password` 明文字段；连接脚本会在首次成功执行前自动加密并回写为 `password_enc`。
- 若存在 `jdbc_url`，脚本会优先从 `jdbc_url` 解析 `host/port/schema`，覆盖同名字段。

## 1.1) 开箱即用配置文件
- 已提供：`config/db.secure.json`
- 你只需修改这 3 项即可：
  - `host`
  - `user`
  - `password`
- 其余默认值：`db_type=dm`、`port=5236`、`connect_timeout=5`
- 若与 Java 配置对齐，建议直接填写 `jdbc_url` 与 `password`，减少手工拆分错误。

## 2) 密钥管理建议
- 默认从环境变量读取：`DB_CONFIG_KEY`
- 推荐在开发机使用 shell 环境变量或密钥管理器注入。
- 禁止写入：
  - `application.yml`
  - `*.properties`
  - `*.json`
  - Git 提交记录

## 3) 依赖建议
- 必选：`cryptography`（加解密）
- 达梦（推荐）：`dmPython`
- MySQL（可选兼容）：`pymysql`

## 4) 失败排查清单
1. `DB_CONFIG_KEY` 未设置或拼写错误。
2. `password_enc` 与密钥不匹配（密钥轮换后未重加密）。
3. 主机、端口、库名或用户错误。
4. 数据库网络不可达或白名单未开通。
5. 连接参数（超时、字符集）未按数据库要求配置。
6. 达梦客户端驱动未正确安装（`import dmPython` 失败）。

## 5) 推荐 .gitignore 增补
```gitignore
# local secrets
.env
config/*.plain.json
config/*local*.json
```

## 6) AI 编程约束
- AI 生成数据库连接代码时，默认读取密文配置并走解密流程。
- AI 不得将明文密码写入源码、测试用例、日志、注释、文档示例。
- 如必须演示，使用占位符：`<password>`、`<DB_CONFIG_KEY>`。
