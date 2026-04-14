# 加载 `doc/skills` 技能 - 步骤与结果

## 目标
加载项目内 `doc/skills` 目录下的所有技能文件，形成可复用清单。

## 执行步骤
1. 扫描 `doc/skills` 目录，定位所有技能文件与相关资源。
2. 逐个读取所有 `SKILL.md`，确认技能名称、用途、触发场景与关键约束。
3. 汇总形成加载结果，供后续会话直接引用。

## 已加载技能清单
- `doc/skills/gjj-remote-deploy/SKILL.md`
  - 名称：`gjj-remote-deploy`
  - 用途：将发布包上传到远端并执行 K8s 滚动重启。
- `doc/skills/gjj-build-deploy/SKILL.md`
  - 名称：`gjj-build-deploy`
  - 用途：执行编译打包并聚合本地 `release` 制品（不含远程发布）。
- `doc/skills/zzgjj-login/SKILL.md`
  - 名称：`zzgjj-login`
  - 用途：自动化登录枣庄公积金测试系统（验证码需用户手动输入）。
- `doc/skills/pingcode-ui-filter/SKILL.md`
  - 名称：`pingcode-ui-filter`
  - 用途：通过 CDP 自动化执行 PingCode 页面筛选操作。
- `doc/skills/browser-use/SKILL.md`
  - 名称：`browser-use`
  - 用途：通用浏览器自动化（导航、点击、输入、截图、抓取等）。
- `doc/skills/vfox-toolchain/SKILL.md`
  - 名称：`vfox-toolchain`
  - 用途：在构建前切换并校验 Java/Node/Maven 工具链版本。
- `doc/skills/python-db-encrypted-auth/SKILL.md`
  - 名称：`python-db-encrypted-auth`
  - 用途：使用加密配置进行 Python 数据库连接（含密钥安全要求）。
- `doc/skills/doc-converter/SKILL.md`
  - 名称：`doc-converter`
  - 用途：将 DOCX 转换为 Markdown，保留图表与基础格式。

## 备注
- `doc/skills/python-db-encrypted-auth/` 下还包含：
  - `scripts/encrypt_db_password.py`
  - `scripts/db_connect_demo.py`
  - `reference.md`
  - `config/db.secure.json`
- 以上资源已识别，可在对应技能执行时直接调用。
