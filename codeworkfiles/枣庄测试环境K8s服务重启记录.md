# 枣庄测试环境 K8s 服务重启操作记录

## 1. 任务背景
在完成将构建好的业务 `.jar` 包和前端 `zip` 包推送至枣庄测试服务器 (`100.70.0.189`) 后，需要让服务器上的 Kubernetes 集群重新加载镜像并部署最新的业务代码。

## 2. 操作思路与执行步骤

### 2.1 获取控制台地址与登录
- **探测地址**：通过此前编写的远程命令脚本 (`remote_exec.py`)，自动读取到了 `kubesphere-system` 命名空间下控制台的服务端口，确认访问地址为：`http://100.70.0.189:30880`。
- **浏览器访问**：响应用户的偏好，调用本地火狐浏览器（`firefox`）自动弹出了 Kubesphere 登录页面，由用户手动输入账号密码进行安全登录。

### 2.2 批量滚动重启服务 (Rolling Restart)
在用户确认成功登录并检查无误后，通过底层调用 `remote_exec.py` 向远程服务器发送原生的 `kubectl rollout restart` 命令。
命令对 `zzgjj-test` 命名空间下的所有关联后端微服务和前端服务执行了重启操作，涉及的 Deployment 包括：
- `capinfo-gjj-busi-cwhs-jzgl-basic-svc-depl`
- `capinfo-gjj-busi-jhgl-zjjh-basic-svc-depl`
- `capinfo-gjj-busi-jshs-gm-agg-svc-depl`
- `capinfo-gjj-busi-zjjs-lcgl-basic-svc-depl`
- `capinfo-gjj-busi-zjjs-sp-basic-svc-depl`
- `capinfo-gjj-busi-zjjs-ywgl-basic-svc-depl`
- `capinfo-gjj-busi-zjjs-zhgl-basic-svc-depl`
- `capinfo-gjj-frontend-jshs-gm-depl`

## 3. 执行结果
所有服务的 `deployment.apps` 均响应了 `restarted`。
K8s 已经开始平滑地销毁旧 Pod 并拉起包含最新代码的新 Pod。整个发版流程（从本地打包、上传到远程重启）现已全部闭环。
