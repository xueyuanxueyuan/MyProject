# RUNTIME SETUP

## 1. 当前环境结论

本机探测结果：

- `docker`：未发现
- `chrome` / `chromium` PATH 命令：未发现
- `Edge`：已发现可执行文件

建议当前优先走：

- **Edge(Chromium) + CDP**

暂不直接启用：

- **Docker + noVNC + Chromium 容器模式**

## 2. 本机 CDP 启动命令

```powershell
"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" --remote-debugging-port=9222 --user-data-dir="d:\Probject\Gjj\.cache\edge-cdp"
```

说明：

- `--remote-debugging-port=9222`：开启 CDP 接口
- `--user-data-dir=...`：使用独立目录，避免污染日常浏览器配置

## 3. CDP 验证命令

### 如果已有 `browser-use`

```powershell
browser-use --connect state
```

或：

```powershell
browser-use --cdp-url http://127.0.0.1:9222 open https://example.com
```

### 如果后续接入其他 CDP 客户端

优先验证：

- `http://127.0.0.1:9222/json/version`
- `http://127.0.0.1:9222/json`

## 4. 容器 noVNC 模式说明

该模式适用于：

- 需要边看边调试
- 需要浏览器运行在隔离环境
- 需要 noVNC 远程观察浏览器画面

典型端口：

- `8080`：noVNC Web 入口
- `9222`：CDP 控制入口

但当前机器未发现 `docker`，因此本次不直接落地容器命令。

## 5. 后续扩展建议

若后续补齐 Docker，可再增加：

- `docker run` 启动模板
- `docker-compose.yml`
- noVNC 访问地址模板
- CDP 健康检查模板
