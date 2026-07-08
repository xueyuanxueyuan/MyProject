# 20260705-jookdb打开并定位枣庄测试库-cap_gjj_jshs记录

## 1. 背景

用户要求通过 `cli-anything-local-tools` 技能打开本机 `jookdb`，并连接到枣庄测试库的 `cap_gjj_jshs`。

本次处理遵循“先探测、后调用、再留痕”的本机工具桥接流程。

## 2. 探测结果

### 2.1 应用安装信息

通过 `Get-StartApps` 与 `Get-AppxPackage` 确认本机已安装：

- 应用名称：`jookdb`
- 应用版本：`6.3.1.0`
- 应用 ID：`48802jooksoft.jookdb_avmxfwm0kw4xt!JOOKDB`
- 可执行入口：`VFS\jookdb\jookdb.exe`

结论：

- `jookdb` 不是常规 PATH 可执行程序
- 更适合通过开始菜单应用 ID 拉起，而不是假定存在通用 CLI

### 2.2 本地连接配置

读取 `C:\Users\Sour\AppData\Local\Packages\48802jooksoft.jookdb_avmxfwm0kw4xt\LocalCache\Roaming\jookdb\config.ini` 后，确认本机已存在如下枣庄分组：

- `枣庄测试`
- `枣庄联调`
- `枣庄生产`

其中 `枣庄测试` 的核心参数为：

- 数据库类型：`dm`
- 主机：`100.70.3.75`
- 端口：`5236`
- 用户：`HSGJJ`
- 密码：已保存为应用内部密文

### 2.3 项目侧库信息核对

同时读取枣庄后端项目配置文件：

- `zaozhuang/IdeaProject/capinfo-gjj-busi-jshs/capinfo-gjj-busi-zjjs-ywgl/capinfo-gjj-busi-zjjs-ywgl-basic-svc-app/src/main/resources/application-zaozhuang-dm.yml`

确认配置为：

```yaml
spring:
  datasource:
    url: jdbc:dm://100.70.3.75:5236?schema=CAP_GJJ_JSHS
    username: HSGJJ
```

结论：

- 本机 `jookdb` 中保存的 `枣庄测试` 连接，与项目中 `CAP_GJJ_JSHS` 的达梦测试库配置一致
- 用户所说“枣庄测试库的 `cap_gjj_jshs`”，可直接映射到 `jookdb` 中现有的 `枣庄测试` 连接

### 2.4 最近访问日志核验

读取当日日志：

- `C:\Users\Sour\AppData\Local\Packages\48802jooksoft.jookdb_avmxfwm0kw4xt\LocalCache\Roaming\jookdb\logs\20260705.log`

确认存在 `枣庄测试 Succ` 记录，例如：

```sql
select * from CWHS_YWLSB where jslsh = '20260705360000000611'
```

结论：

- `枣庄测试` 连接并非空配置，且今日已成功执行查询
- 可进一步佐证其连接可用

## 3. 启动结果

已执行：

```powershell
Start-Process explorer.exe "shell:AppsFolder\48802jooksoft.jookdb_avmxfwm0kw4xt!JOOKDB"
```

随后通过进程确认：

```powershell
Get-Process jookdb | Select-Object ProcessName, Id, StartTime, MainWindowTitle
```

返回结果表明：

- 进程名：`jookdb`
- 窗口标题：`jookdb`

可判定 `jookdb` 已被成功拉起。

## 4. 自动化边界

虽然已完成以下动作：

- 确认应用安装与入口
- 确认本地已有 `枣庄测试` 连接
- 确认该连接对应 `CAP_GJJ_JSHS`
- 成功拉起 `jookdb`

但本次未发现：

- 稳定可读的 `--help` / `--version` CLI 输出
- 可直接指定“打开某个已保存连接”的命令行参数
- 可由当前环境安全控制的桌面 GUI 自动化能力

因此，本次能力边界确认如下：

- 可稳定完成：探测安装、确认连接、拉起应用、确认目标连接已存在
- 暂不可稳定完成：替用户在 `jookdb` 桌面窗口内自动双击 `枣庄测试` 并进入查询页

## 5. 用户可直接执行的最后一步

由于 `jookdb` 已打开，且本机已存在目标连接，用户只需在应用内执行以下最短操作：

1. 在左侧连接树展开 `枣庄`
2. 双击 `枣庄测试`
3. 进入后即使用对应的 `CAP_GJJ_JSHS` 测试库

## 6. 产出

已新增单工具桥接说明：

- `doc/技能库/cli-anything-local-tools/jookdb-TOOL-HARNESS.md`

后续若需要继续深挖，可从以下方向继续：

1. 研究 `jookdb.exe` 是否存在隐藏启动参数，可直接指定已保存连接
2. 分析应用配置格式，确认是否可安全地预置更多连接项
3. 在具备桌面自动化能力后，再补充“受控 GUI 点击连接”方案
