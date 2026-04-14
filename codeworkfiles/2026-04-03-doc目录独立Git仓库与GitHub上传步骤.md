# doc 目录独立 Git 仓库与 GitHub 上传

## 已完成（本地）

- 路径：`Gjj/doc/`
- 已 `git init -b main`，首提交含规范/需求/设计/评审/skills 等（42 个文件）
- 新增 `doc/.gitignore`：忽略 `skills/python-db-encrypted-auth/config/db.secure.json`（敏感配置）
- 新增 `doc/README.md`：说明仓库用途

## 你需要完成的步骤（推送到你的 GitHub）

1. 在 GitHub 网页新建仓库（例如名 `gjj-doc`），**不要**勾选「用 README 初始化」（避免与本地已有提交冲突）。
2. 在本机执行（将 `YOUR_USER` 与 `REPO` 换成你的用户名与仓库名）：

**HTTPS：**

```bash
cd /home/source/Jetbrains/Probject/Gjj/doc
git remote add origin https://github.com/YOUR_USER/REPO.git
git push -u origin main
```

**SSH：**

```bash
cd /home/source/Jetbrains/Probject/Gjj/doc
git remote add origin git@github.com:YOUR_USER/REPO.git
git push -u origin main
```

若首次 SSH 连接提示 `Host key verification failed`，可先执行：

```bash
ssh-keyscan -t rsa,ecdsa,ed25519 github.com >> ~/.ssh/known_hosts
```

3. （可选）安装 GitHub CLI 后一键创建并推送：

```bash
sudo apt install gh   # 或 sudo snap install gh
gh auth login
cd /home/source/Jetbrains/Probject/Gjj/doc
gh repo create REPO --public --source=. --remote=origin --push
```

## 说明

- 当前环境未安装 `gh`，且 SSH 对 `github.com` 未建立 known_hosts，无法在此环境替你完成登录与推送。
- 推送需使用你自己的 GitHub 账号与 Token/SSH 密钥。
