# Financial Delivery Platform (FDP)

FDP 是面向 **POC 阶段** 的 Codeup 驱动部署与预览管理平台。

它不是正式生产 CI/CD 平台，也不负责在服务器上继续开发业务代码。

## 核心边界

- **Codeup 是唯一源码事实源**：团队成员在 Codeup 仓库中迭代 POC。
- **FDP 只负责服务器部署控制**：clone/pull、构建、运行、停止、重启、日志和预览路由。
- 支持两种 POC：
  - `STATIC`：FS 等系统产出的纯静态 HTML，或需要 Vite build 的静态页面。
  - `NODE_SQLITE`：Node.js 前后端 + SQLite 的可交互 POC。
- 服务器只需要一个对外端口，由 Nginx 根据 Path 将不同客户的 POC 分流。
- 客户确认后进入独立的 Spring Boot / MySQL 正式开发阶段，不属于 FDP 职责。

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

旧 V1 环境升级：

```bash
mysql -uroot -p < sql/migration_v2_poc_delivery.sql
```

## 当前明确不做

- Kubernetes / Helm
- Docker 编排
- 正式 Spring Boot 项目发布
- 在线代码编辑
- Git commit / push / merge
- 企业 RBAC / 权限审计
- Jenkins 式通用 Pipeline 编排
