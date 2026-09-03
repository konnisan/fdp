# Financial Delivery Platform (FDP)

FDP 是基于 Codeup / 云效 Flow 的内部交付与客户预览平台。

研发源码和 CI 构建以 Codeup / Flow 为事实源；FDP 不重复建设 CI，而是把已经可部署的内容交付到公司 Linux 私有云，并通过统一 Nginx 入口提供客户预览。

详细文档：

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- [`docs/PIPELINE_ARTIFACT_DELIVERY.md`](docs/PIPELINE_ARTIFACT_DELIVERY.md)

## 两条交付链路

### 1. STATIC POC

```text
FS
  -> 生成静态 HTML
  -> Codeup poc-html
  -> FDP clone / fetch
  -> /data/fdp/sites
  -> Nginx
  -> 客户预览
```

FDP 不为 STATIC 执行 npm、Maven 或 Docker build。

当前联调仓库：

```text
https://codeup.aliyun.com/6038b0d9eb45243512067136/poc-html.git
```

### 2. 正式工程 / Flow Packages

```text
开发人员
  -> Codeup Git
  -> Yunxiao Flow
       - npm build
       - mvn package
       - test
       - docker build backend
       - docker save backend image
       - package frontend dist
       - assemble FDP delivery bundle
  -> Packages GENERIC
  -> FDP
       - 查询成功 Flow Run
       - 推荐最新成功制品版本
       - 允许选择历史成功版本
       - 下载 / 校验制品
       - 前端静态发布
       - docker load 后端镜像
       - 停旧 / 启新 backend container
       - health check
       - Nginx route
  -> 客户预览
```

职责边界：

```text
Codeup / Flow = source + CI / build
Packages      = artifact storage / versioning
FDP           = CD / private-cloud delivery
```

FDP 正式工程主线不执行 `npm build`、`mvn package` 或 `docker build`。

## 正式工程运行模型

当前是单 Linux Server，不引入 Kubernetes。

```text
Linux Server
├── FDP
├── Nginx
├── Shared MySQL
├── project-a backend Docker
├── project-b backend Docker
└── /data/fdp/sites/<project>/   <- 各工程 frontend dist
```

正式项目的 Vue 前端已经是静态资源，因此直接由宿主机 Nginx 提供；只有后端应用运行在 Docker。

后端端口只绑定本机：

```text
127.0.0.1:<hostPort> -> container:<containerPort>
```

客户只访问 FDP 管理的统一 Nginx 入口。

## V1 正式工程制品约定

Flow 向 Packages GENERIC 上传一个 `.tgz` / `.tar.gz` 交付包。解包后：

```text
fdp-manifest.yml
frontend.tar.gz
backend-image.tar
database/                 # 可选；V1 暂不自动执行 migration
```

示例 manifest：

```yaml
app:
  code: financial-system

frontend:
  archive: frontend.tar.gz
  root: .

backend:
  imageArchive: backend-image.tar
  image: financial-system-backend:20260903-007
  containerPort: 8080
  healthCheck: /actuator/health

database:
  migrations: database/
```

应用自身参数由项目仓库 / Flow 制品声明；环境参数在 FDP 中配置：

```text
pipelineId
packageRepoId
artifactName
previewPath
hostPort
containerName
envFile
```

`envFile` 推荐放在 Linux，例如：

```text
/data/fdp/env/financial-system.env
```

用于 MySQL 地址、数据库名、用户名、密码等部署环境信息，不进入源码或 Packages。

## Yunxiao 配置

`backend/.env`：

```properties
FDP_YUNXIAO_ENABLED=true
FDP_YUNXIAO_DOMAIN=openapi-rdc.aliyuncs.com
FDP_YUNXIAO_ORGANIZATION_ID=6038b0d9eb45243512067136
FDP_YUNXIAO_TOKEN=
FDP_YUNXIAO_PAGE_SIZE=30
```

`FDP_YUNXIAO_TOKEN` 留空时会复用 `FDP_STATIC_CODEUP_TOKEN`。该 PAT 需要 Flow / Packages 读取权限。

不要把 Token 写入 Git URL 或提交到仓库。

## 私有云运行环境

Linux 主机当前需要：

```text
Git
Docker Engine
Nginx
rsync
curl
tar
Java 17+
MySQL
```

推荐运行目录：

```text
/data/fdp/
├── app.jar
├── logs/
├── workspaces/
├── artifacts/
├── sites/
├── data/
├── env/
└── runtime/nginx/
```

推荐环境变量：

```properties
FDP_WORKSPACE_ROOT=/data/fdp/workspaces
FDP_ARTIFACT_ROOT=/data/fdp/artifacts
FDP_STATIC_ROOT=/data/fdp/sites
FDP_DATA_ROOT=/data/fdp/data
FDP_PUBLIC_PORT=3005
FDP_NGINX_CONFIG_FILE=/data/fdp/runtime/nginx/fdp-routes.conf
```

当前端口规划：

```text
3003 -> FDP 管理前端
3004 -> FDP Spring Boot 后端
3005 -> 客户预览统一 Nginx 入口
```

## 数据库升级

全新环境：

```bash
mysql -uroot -p < sql/fdp.sql
```

已有 FDP 数据库升级到 Pipeline Artifact Delivery：

```bash
mysql -uroot -p fdp < sql/migration_v6_pipeline_artifact_delivery.sql
```

已有数据库只执行 V6 migration，不要重新执行全量 `fdp.sql`。

## 页面

```text
静态预览       -> FS / Codeup STATIC
流水线与制品   -> 只读查看 Flow / Packages
工程制品交付   -> 绑定 Flow + Packages、选择版本、部署到 Linux
容器项目       -> 旧本机构建兼容模式
访问入口       -> 客户访问入口
部署中心       -> 旧 deployment task 查看
运行环境       -> Linux / Docker / Nginx / rsync / curl 状态
```

## 当前不做

- Kubernetes / Helm
- 多服务器调度
- 蓝绿发布 / 零停机
- FDP 内正式工程 CI
- 前端 Docker
- 自动数据库 migration / rollback
- Harbor / ACR 镜像 pull

当单服务器模式不能满足需求时，再评估 Kubernetes、多节点调度和私有镜像仓库。
