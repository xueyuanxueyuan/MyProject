# jookdb TOOL HARNESS

## 1. 工具基本信息

- 工具名称：`jookdb`
- 可执行文件名：`jookdb.exe`
- 典型安装路径：
  - `C:\Program Files\WindowsApps\48802jooksoft.jookdb_6.3.1.0_x64__avmxfwm0kw4xt\VFS\jookdb\jookdb.exe`
  - 开始菜单应用 ID：`48802jooksoft.jookdb_avmxfwm0kw4xt!JOOKDB`
- 调用方式：
  - 开始菜单应用拉起（优先）
  - 原生可执行文件启动
  - GUI 交互（当前仅能人工完成）

## 2. 探测命令

```powershell
Get-StartApps | Where-Object { $_.Name -match 'jook|db' } |
  Select-Object Name, AppID

Get-AppxPackage *jookdb* |
  Select-Object Name, PackageFullName, InstallLocation, Version

(Get-AppxPackage *jookdb* | Get-AppxPackageManifest).Package.Applications.Application |
  Select-Object Id, Executable, EntryPoint

Get-Process jookdb -ErrorAction SilentlyContinue |
  Select-Object ProcessName, Id, MainWindowTitle, Path
```

说明：
- `jookdb` 在本机不是 PATH 工具，也未在常规卸载注册表项中暴露。
- 需通过 `Get-StartApps` 与 `Get-AppxPackage` 探测其应用包信息。
- 当前未探测到稳定的 `--help` / `--version` 文本输出。

## 3. 只读能力

- 查看版本：`Get-AppxPackage *jookdb*`
- 查看状态：`Get-Process jookdb`
- 查看帮助：当前未发现稳定帮助命令，改读应用清单与本地配置文件
- 列出现有资源：读取 `config.ini`、日志与本地缓存目录

## 4. 最小可用调用

```powershell
Start-Process explorer.exe "shell:AppsFolder\48802jooksoft.jookdb_avmxfwm0kw4xt!JOOKDB"
```

说明：
- 这是当前验证通过的最小可用调用。
- 调用后可通过 `Get-Process jookdb` 二次确认程序已拉起。

## 5. 常见任务映射

| 用户意图 | 推荐命令 | 输出校验 |
|---|---|---|
| 打开 `jookdb` | `Start-Process explorer.exe "shell:AppsFolder\48802jooksoft.jookdb_avmxfwm0kw4xt!JOOKDB"` | `Get-Process jookdb` 返回进程 |
| 查安装位置 | `Get-AppxPackage *jookdb*` | 返回 `InstallLocation` |
| 查可执行入口 | `Get-AppxPackageManifest` | 返回 `Executable=VFS\jookdb\jookdb.exe` |
| 查已有连接 | 读取 `LocalCache\Roaming\jookdb\config.ini` | 返回分组与连接节 |
| 查最近执行 SQL | 读取 `LocalCache\Roaming\jookdb\logs\YYYYMMDD.log` | 返回连接名与 SQL 日志 |

## 6. 输出与验证

- 退出码预期：启动命令返回 `0`
- 成功输出关键字：`Get-Process jookdb` 返回 `MainWindowTitle=jookdb`
- 失败输出关键字：应用 ID 不存在、包未安装、路径无访问权限
- 产物文件路径检查：
  - `C:\Users\Sour\AppData\Local\Packages\48802jooksoft.jookdb_avmxfwm0kw4xt\LocalCache\Roaming\jookdb\config.ini`
  - `C:\Users\Sour\AppData\Local\Packages\48802jooksoft.jookdb_avmxfwm0kw4xt\LocalCache\Roaming\jookdb\logs\20260705.log`
- 二次验证命令：

```powershell
Get-Process jookdb -ErrorAction SilentlyContinue |
  Select-Object ProcessName, Id, StartTime, MainWindowTitle
```

## 7. 风险等级

- 只读：探测安装、读取配置、读取日志、启动应用
- 项目内写操作：无
- 系统级写操作：修改 `jookdb` 连接配置时会写入用户目录
- 需要管理员权限：通常不需要
- 需要联网：连接数据库时需要内网可达

## 8. 对代理的专用约束

- 是否必须先读取帮助再调用：是，若帮助不可用则至少先读应用清单与配置
- 是否必须先创建临时目录：否
- 是否要求绝对路径：建议是
- 是否支持 JSON 输出：否
- 是否存在长驻进程：是
- 是否需要用户额外审批：纯启动不需要；涉及写配置或执行 SQL 时应先说明

## 9. 已知问题与回退方案

- 不在 PATH 时怎么办：使用开始菜单应用 ID 或应用包绝对路径启动
- 帮助命令失败时怎么办：退回到 `Get-AppxPackageManifest` 与 `config.ini`
- GUI 必须打开时怎么办：先自动拉起应用，再由用户完成最后的点选
- 无头模式不可用时怎么办：停止假装可全自动操作，只保留“探测 + 启动 + 配置落档”能力

## 10. 真实探测结论

- 本机已安装 `jookdb 6.3.1.0`
- 应用入口为 `48802jooksoft.jookdb_avmxfwm0kw4xt!JOOKDB`
- 可执行文件为 `VFS\jookdb\jookdb.exe`
- 已验证启动命令可成功拉起 `jookdb`
- 已确认本机配置中存在分组 `枣庄`，且已保存 `枣庄测试`、`枣庄联调`、`枣庄生产`
- `枣庄测试` 连接参数为：
  - `type=dm`
  - `host=100.70.3.75`
  - `port=5236`
  - `userName=HSGJJ`
- 结合枣庄项目配置文件可确认该测试连接对应 `CAP_GJJ_JSHS`
- 已从当日日志确认 `枣庄测试` 连接可成功执行查询 SQL
