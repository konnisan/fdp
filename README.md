# Financial Delivery Platform (FDP)

FDP 是面向 **POC 阶段** 的 Codeup 驱动部署与预览管理平台。

它不是正式生产 CI/CD 平台，也不负责在服务器上继续开发业务代码。

## 核心边界

- **Codeup 是唯一源码事实源**：团队成员在 Codeup 仓库中迭代 POC。
- **FDP 只负责 Linux 服务器部署控制**：clone/pull、构建、运行、停止、重启、日志和预览路由。
- 当前支持两种 POC：
  - `STATIC`：FS 等系统产出的纯静态 HTML，或需要 Vite build 的静态页面。
  - `NODE_SQLITE`：Node.js 前后端 + SQLite 的可交互 POC。
- 服务器只需要一个对外端口，由 Nginx 根据 Path 将不同客户的 POC 分流。
- 客户确认后进入独立的 Spring Boot / MySQL 正式开发阶段，不属于 FDP 职责。

## 部署任务模型

FDP 借鉴 Jenkins 的 Job / Build 思路，但不引入 Jenkins 本身。

一次部署对应一个 `deployment_task`，后台异步执行；每个 Task 下面记录独立的 `deployment_step`：

```text
QUEUED
  ↓
PREPARE
  ↓
GIT_SYNC
  ↓
BUILD                 # 无构建命令时 SKIPPED
  ↓
PUBLISH_STATIC
或 START_NODE
  ↓
ROUTE
  ↓
VERIFY
  ↓
SUCCESS / FAILED
```

Step 状态统一为：`RUNNING / SUCCESS / FAILED / SKIPPED`。

同一个 POC 同一时间只允许一个部署任务运行，避免同时操作同一 Codeup 工作副本。

## 运行结构

```text
Codeup
  │
  ├── STATIC POC
  └── NODE_SQLITE POC
          │
          ▼
         FDP
   git clone / pull
   build / PM2
          │
          ▼
       Nginx :8090
   ├── /poc/customer-a/
   ├── /poc/customer-b/
   └── /poc/fs-static/
          │
          ▼
        客户预览
```

## 当前为什么不强制 Docker

当前 POC 技术栈固定为 `STATIC` 和 `Node.js + SQLite`，Linux + PM2 + Nginx 已经能提供进程隔离、内部端口和统一预览入口。

因此 **每一个 POC 当前不需要单独配置 Docker 容器**。Docker 以后可以作为新的运行方式加入，例如 `NODE_SQLITE_DOCKER`，但不应该成为当前项目的必选配置。

Kubernetes（K8S）属于容器编排层，主要解决多服务器、大量容器的调度、故障恢复、扩缩容和服务发现。当前 FDP 只有一台 Linux 服务器和少量 POC，不需要引入 K8S。

## 服务器目录

服务器工作目录只是 Codeup 的部署副本，不保存研发迭代状态。

```text
/data/fdp/
├── workspaces/   # Codeup 工作副本，可随时重新拉取
├── sites/        # STATIC 发布产物
└── data/         # NODE_SQLITE SQLite 运行数据，与 Git 工作区分离
```

## 本地开发

默认 `FDP_EXECUTION_ENABLED=false`，部署命令只做 DRY-RUN，方便 Windows 上调试 FDP UI/API。

```bash
cd backend
mvn spring-boot:run
```

```bash
cd frontend
npm install
npm run dev
```

前端：`http://localhost:5173`

后端：`http://localhost:8080`

## Linux 服务器

安装：Git、Node.js/npm、PM2、Nginx、rsync、MySQL（MySQL 仅保存 FDP 自身元数据）。

推荐环境变量：

```bash
export FDP_EXECUTION_ENABLED=true
export FDP_WORKSPACE_ROOT=/data/fdp/workspaces
export FDP_STATIC_ROOT=/data/fdp/sites
export FDP_DATA_ROOT=/data/fdp/data
export FDP_NGINX_CONFIG_FILE=/etc/nginx/conf.d/fdp-poc.conf
export FDP_PUBLIC_PORT=8090
```

Codeup 凭据不要写入数据库，使用服务器 SSH Key 或 Git credential helper。

## 数据库

新环境：

```bash
mysql -uroot -p < sql/fdp.sql
```

已经升级到 POC V2 的环境继续执行：

```bash
mysql -uroot -p < sql/migration_v3_deployment_pipeline.sql
```

## 当前明确不做

- Kubernetes / Helm
- 强制 Docker 化
- 正式 Spring Boot 项目发布
- 在线代码编辑
- Git commit / push / merge
- 企业 RBAC / 权限审计
- Jenkins 式通用 Pipeline 编排
