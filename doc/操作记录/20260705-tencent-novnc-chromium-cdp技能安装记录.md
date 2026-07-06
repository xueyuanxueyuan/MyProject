# 20260705-tencent-novnc-chromium-cdp技能安装记录

## 背景

用户要求安装 `tencent novnc chromium cdp` 技能，以实现自动操作浏览器。

## 检查过程

### 1. 工作区现状

已检索当前仓库，发现已有与浏览器自动化相关的经验文件：

- `doc/技能库/browser-use/SKILL.md`
- `doc/技能库/settlement-window-verifier/SKILL.md`
- `doc/技能库/pingcode-ui-filter/SKILL.md`

这些文档已包含：

- Chrome / Chromium 的 CDP 调试端口启动经验
- 接管已开启远程调试的浏览器窗口的方法
- 本地浏览器自动化的专项经验

但仓库中不存在同名 `tencent novnc chromium cdp` 技能。

### 2. 本机环境探测

探测结果如下：

- `docker`：未发现
- `chrome` / `chromium` PATH 命令：未发现
- `Edge` 可执行文件：已发现

已发现路径：

- `C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe`

结论：

- 当前机器可优先采用 **Edge(Chromium) + CDP** 模式
- 当前机器暂不具备直接启用 **Docker + noVNC 容器模式** 的明显条件

### 3. 外部参考

外部可找到以下相关参考形态：

- `chrome-novnc-cdp`
- `chrome-novnc`
- `chrome-cdp-skill`

这些资料说明：

- `noVNC` 适合作为远程浏览器可视化层
- `CDP` 适合作为浏览器自动化主控制层
- `Chromium` 家族浏览器可作为统一被控浏览器底座

## 安装策略

本次采用“本地桥接技能”安装方式，而非伪装成完整运行时安装：

1. 创建 `tencent-novnc-chromium-cdp` 技能主源文档
2. 创建 `.trae/skills` 本地入口
3. 增加运行时说明，明确当前机器下应优先走 `Edge/Chromium + CDP`
4. 保留后续扩展 noVNC 容器模式的空间

## 落地产物

- `doc/技能库/tencent-novnc-chromium-cdp/SKILL.md`
- `doc/技能库/tencent-novnc-chromium-cdp/RUNTIME-SETUP.md`
- `.trae/skills/tencent-novnc-chromium-cdp/SKILL.md`
- `doc/操作记录/20260705-tencent-novnc-chromium-cdp技能安装记录.md`

## 当前结果

- 当前仓库已具备 `tencent-novnc-chromium-cdp` 技能入口
- 当前机器下推荐的首选链路为：
  - `Edge(Chromium)` 启动
  - 开启 `--remote-debugging-port=9222`
  - 通过 CDP 客户端接管
- `noVNC` 相关能力当前仅完成技能层设计与说明，未完成真实运行时启用

## 后续建议

若用户下一步要求真正跑通自动化链路，可继续：

1. 先按 `RUNTIME-SETUP.md` 启动 Edge 的 CDP 模式
2. 再验证 `browser-use --connect state` 或其他 CDP 客户端是否可连
3. 若后续补齐 Docker，再追加 noVNC 容器运行模板

## 2026-07-05 CDP 自动化跑通记录

### 1. 启动结果

已使用独立用户数据目录启动 Edge CDP 实例：

- 浏览器：`C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe`
- 用户数据目录：`d:\Probject\Gjj\.cache\edge-cdp`
- 调试端口：`9222`
- 监听地址：`127.0.0.1:9222`
- 监听进程：`34020`

### 2. CDP 端点验证

`http://127.0.0.1:9222/json/version` 返回：

- `Browser`：`Edg/150.0.4078.48`
- `Protocol-Version`：`1.3`
- `webSocketDebuggerUrl`：已返回浏览器级 WebSocket 地址

`http://127.0.0.1:9222/json` 返回页面目标，包含：

- `about:blank`
- `https://example.com/`

### 3. 自动化客户端情况

本机当前未发现 `browser-use` 命令，且 Python/py 不可用。

因此本次使用 PowerShell/.NET `ClientWebSocket` 作为替代 CDP 客户端进行验证。

### 4. 实际自动化验收

通过 CDP HTTP 接口新建页面：

- `PUT http://127.0.0.1:9222/json/new?https://example.com`

随后连接页面级 WebSocket，并调用 `Runtime.evaluate` 执行页面脚本，读取结果为：

```json
{"id":1,"result":{"result":{"type":"string","value":"Example Domain|https://example.com/|Example Domain"}}}
```

该结果证明：

- CDP 端口已开启
- 页面目标可枚举
- 页面级 WebSocket 可连接
- 可通过 CDP 执行 JavaScript 并读取 DOM 结果

### 5. 当前结论

当前机器已经跑通 **Edge(Chromium) + CDP** 自动化链路。

后续若需要更高层封装，可继续补齐：

- `browser-use` 命令行客户端
- Node.js CDP 脚本客户端
- Docker/noVNC 可视化容器层
