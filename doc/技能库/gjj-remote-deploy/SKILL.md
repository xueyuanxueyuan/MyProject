---
name: "gjj-remote-deploy"
description: "Uploads Gjj artifacts to remote servers and restarts K8s. Invoke when user asks 发布/上传/重启, supports single-package deploy or full release deploy."
---

# Gjj Remote Deploy Skill

This skill automates the remote deployment (upload) of packaged artifacts to servers defined in `config/deploy_servers.json`, and performs a rolling restart of the corresponding Kubernetes (K8s) services to apply the new code.

## Prerequisites

1. The project must have already been built using the `gjj-build-deploy` skill, ensuring the artifacts are present in the `<region>/release/` directory.
2. The configuration file `config/deploy_servers.json` must be correctly populated with the target server credentials.
3. The remote server must have `kubectl` configured properly for the target namespace.

## Usage Instructions

### 1. Verify Target Region
Identify which region the user wants to upload (e.g., `zaozhuang`, `linyi`).

### 2. Upload Artifacts (SFTP)
Run `scripts/remote_deploy.py`.
- Full release deploy: pass only region, upload all files in `<region>/release`.
- Single package deploy: pass region and one or more specific file names.
- Built-in dedup: script compares target `host + port + default_path` and upload file signature. If both are identical to a previous successful upload record, it skips duplicate upload automatically.

```bash
python3 /home/source/Jetbrains/Probject/Gjj/scripts/remote_deploy.py <region>
python3 /home/source/Jetbrains/Probject/Gjj/scripts/remote_deploy.py <region> <file_name>
```

*Example for zaozhuang:*
```bash
python3 /home/source/Jetbrains/Probject/Gjj/scripts/remote_deploy.py zaozhuang
python3 /home/source/Jetbrains/Probject/Gjj/scripts/remote_deploy.py zaozhuang capinfo-gjj-busi-cwhs-jzgl-basic-svc-app.jar
```
*Note: If `paramiko` is missing, the script will attempt to install it automatically (or install via `sudo apt install -y python3-paramiko` if needed).*

### 3. Restart K8s Services (Rolling Restart)
Once upload is successful, use `scripts/remote_exec.py` for restart:
- Single package deploy: restart only the corresponding deployment.
- Full release deploy: restart all impacted deployments in one command.

*Example command for zaozhuang (namespace: `zzgjj-test`):*
```bash
python3 /home/source/Jetbrains/Probject/Gjj/scripts/remote_exec.py zaozhuang "kubectl rollout restart deployment capinfo-gjj-busi-cwhs-jzgl-basic-svc-depl capinfo-gjj-busi-jhgl-zjjh-basic-svc-depl capinfo-gjj-busi-jshs-gm-agg-svc-depl capinfo-gjj-busi-zjjs-lcgl-basic-svc-depl capinfo-gjj-busi-zjjs-sp-basic-svc-depl capinfo-gjj-busi-zjjs-ywgl-basic-svc-depl capinfo-gjj-busi-zjjs-zhgl-basic-svc-depl capinfo-gjj-frontend-jshs-gm-depl -n zzgjj-test"
```

*(Note: Adjust the deployments and namespace based on the target region's actual K8s configuration).*

### 4. Alternative: Browser-Based Restart (Optional)
If the user prefers to restart via the Kubesphere UI, use the `browser-use` skill to navigate to the console URL (e.g., `http://<host>:30880`), ask the user to log in, and then proceed.

## Example

User says: *"把打包好的枣庄项目上传到测试服务器并重启服务"*

**Actions to take:**
1. If user specifies a package, execute `python3 scripts/remote_deploy.py zaozhuang <file_name>`, otherwise run full deploy.
2. Wait for upload to complete successfully.
3. Execute single-deployment or multi-deployment restart accordingly.
4. Confirm upload and rollout status to the user.
