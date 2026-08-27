# Financial Delivery Platform (FDP)

FDP 是基于 Codeup 的内部交付与预览平台。研发迭代只发生在 Codeup；FDP 负责 Linux 服务器上的源码同步、构建、发布、容器运行、日志和统一访问路由。

## V1 已确认技术路线

V1 **不使用 Kubernetes**，先在单台 Linux 服务器使用 Docker 把服务发布起来。

平台只定义两种交付方式：

- `STATIC`：POC/纯 HTML/前端构建产物，共享服务器静态资源。
- `CONTAINER`：代码交付项目，一个服务通过自己的 Dockerfile 构建为独立容器。Node.js、Java、Python、Go 等技术栈都由 Dockerfile 自己解决，FDP 不再为语言建立项目类型。

```text
Codeup
   │
   ├── STATIC
   │     └── optional build -> /data/fdp/sites -> Nginx
   │
   └── CONTAINER
         └── docker build -> image:<commit> -> docker run
                                      │
                                      └── 127.0.0.1:hostPort
                                                   │
                                                   ▼
                                                Nginx :8090
                                                   │
                                           /poc/* or /app/*
```

## Codeup 是唯一源码事实源

服务器上的 `/data/fdp/workspaces/<projectCode>` 只是部署工作副本，可以随时由 Codeup 重新拉取。

支持 monorepo 子目录：

```text
Git URL:       https://codeup.aliyun.com/L2/IDP_AL.git
Branch:        main
Credential:    公司 Codeup
Project Dir:   poc/poc/l2-data-aggregation/l2-server
Dockerfile:    Dockerfile
Build Context: .
```

FDP 会先验证 Git 地址和凭据，再同步仓库，进入 `Project Dir` 执行对应发布流程。

### Codeup 凭据模型

FDP V5 引入可复用 `source_credential`：

```text
Codeup Account
    │
    └── Source Credential
          ├── HTTPS Clone Username
          └── Personal Access Token
                 │
                 ├── Project A
                 ├── Project B
                 └── Project C
```

一个 Token 不等于一个仓库。Token 仍受 Codeup 账号本身的仓库访问权限和 Token scope 限制，因此同一账号有权限访问的多个项目可以复用同一个凭据。

项目只保存：

```text
git_url
git_branch
credential_id
```

Token 不写入 Git URL，也不会出现在部署日志中。运行 Git 时使用临时 `GIT_ASKPASS` 注入 HTTPS 克隆账号和 Token。

首次保存凭据前必须配置 32 字节 AES-GCM 主密钥：

```bash
export FDP_CREDENTIAL_KEY="$(openssl rand -base64 32)"
```

生产环境必须把这个值放进服务器的安全环境变量/secret 配置并持久保存。**如果更换该 Key，数据库里已有的 Token 将无法解密。**

新建项目推荐流程：

```text
填写 Git URL
   ↓
选择 / 新增 Codeup Credential
   ↓
测试 Codeup（git ls-remote）
   ↓
配置 Project Directory
   ↓
选择 STATIC / CONTAINER
   ↓
配置 Dockerfile、端口、Volume 等
   ↓
保存
   ↓
拉取并部署
```

## Docker 容器模型

每次 CONTAINER 发布：

```text
QUEUED
  ↓
PREPARE
  ↓
GIT_SYNC
  ↓
DOCKER_BUILD
  ↓
CONTAINER_REPLACE
  ↓
ROUTE
  ↓
VERIFY
  ↓
SUCCESS / FAILED
```

Image Tag 与 Codeup Commit 绑定，例如：

```text
Commit: a82fc7317d2e...
Image:  fdp/l2-server:a82fc7317d2e
```

容器默认：

- `--restart unless-stopped`
- 仅绑定 `127.0.0.1:<hostPort>:<containerPort>`，不直接暴露给客户。
- 支持 CPU / Memory limit。
- 支持一个宿主机目录到容器目录的持久化 Volume。
- 可选 HTTP health check。

SQLite 等运行数据应通过 Volume 放在宿主机，例如：

```text
/data/fdp/data/l2-server  ->  /app/data
```

删除/替换容器不会删除宿主机数据。

## STATIC 模型

STATIC 用于 POC 静态预览：

```text
Codeup
  ↓
Project Directory
  ↓
Build Command (optional)
  ↓
Build Output
  ↓
/data/fdp/sites
  ↓
Nginx Path
```

所有静态 POC 共享服务器资源，不需要一个 POC 一个容器。

## Jenkins 借鉴

FDP 不引入 Jenkins，但借鉴它的 Job/Build 模型：

- 一次发布 = 一个 `deployment_task`
- 每个 Task 有独立 `deployment_step`
- 状态：`QUEUED / RUNNING / SUCCESS / FAILED / SKIPPED`
- Git Commit、Docker Image Tag、步骤和日志都可追踪
- 同一个项目同一时间只允许一个部署任务运行

## Linux 服务器依赖

V1 需要：

```text
Git
Docker Engine
Nginx
rsync
curl
Java 17 (FDP backend)
MySQL (FDP metadata only)
```

Node/Java/Python 等业务运行时不要求安装在宿主机，它们应进入各自 Docker Image。STATIC 如果需要前端构建，则宿主机仍需安装该项目对应的构建工具，后续也可把静态构建容器化。

推荐环境变量：

```bash
export FDP_EXECUTION_ENABLED=true
export FDP_WORKSPACE_ROOT=/data/fdp/workspaces
export FDP_STATIC_ROOT=/data/fdp/sites
export FDP_DATA_ROOT=/data/fdp/data
export FDP_NGINX_CONFIG_FILE=/etc/nginx/conf.d/fdp.conf
export FDP_PUBLIC_PORT=8090
export FDP_CREDENTIAL_KEY='<固定的 Base64 32-byte key>'
```

SSH Git 地址仍然允许继续使用服务器 SSH Key；HTTPS Codeup Git 推荐使用 FDP Source Credential。

## 数据库

全新环境：

```bash
mysql -uroot -p < sql/fdp.sql
```

已经运行 V4 的环境：

```bash
mysql -uroot -p fdp < sql/migration_v5_codeup_credentials.sql
```

V5 新增：

- `source_credential`
- `delivery_project.credential_id`

旧项目的 `credential_id` 保持 `NULL`，因此已有 SSH / 服务器 credential-helper 发布不会被强制中断。

## 本地开发

默认 `FDP_EXECUTION_ENABLED=false`，所有 shell / Docker / Git 命令只记录为 DRY-RUN：

```bash
cd backend
mvn spring-boot:run
```

```bash
cd frontend
npm install
npm run dev
```

本地如需实际保存 Codeup Credential，仍需配置：

```bash
export FDP_CREDENTIAL_KEY="$(openssl rand -base64 32)"
```

当 `FDP_EXECUTION_ENABLED=false` 时，“测试 Codeup”返回 `DRY_RUN`，不会真的访问远端仓库。

## 当前不做

- Kubernetes / Helm
- 多服务器调度
- Docker Registry 管理
- Java / Node / Python 专用部署器
- 在线代码编辑、commit、push、merge
- Jenkins 式通用 Pipeline 设计器

K8S 预留为未来横向扩展阶段：当单机服务容量不足、需要多服务器调度和故障恢复时，再在现有 Docker Image 之上增加 Kubernetes 编排层。
