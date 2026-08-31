# Financial Delivery Platform (FDP)

FDP 是基于 Codeup 的内部交付与客户预览平台。

研发迭代、正式工程源码和 CI 构建都以 Codeup 为事实源；FDP 的职责是把可交付内容部署到公司私有云，并通过统一 Nginx 入口提供给客户预览。

> 当前确认的架构方向见：[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)

## 当前确认的两条交付链路

### 1. STATIC POC

FS 产出的静态 HTML 本身就是最终可部署内容，因此直接通过 Git 交付。

```text
FS
  ↓
生成静态 POC HTML
  ↓
push 到 Codeup
  ↓
FDP git clone / fetch / reset
  ↓
/data/fdp/sites
  ↓
Nginx
  ↓
客户预览
```

当前用于 FS 与 FDP 联调的 Codeup 仓库：

```text
https://codeup.aliyun.com/6038b0d9eb45243512067136/poc-html.git
```

这一链路中：

- FS：生成并上传静态 POC。
- Codeup：保存 POC 版本。
- FDP：下载、发布、配置 Nginx、验证并提供预览地址。
- FDP 不需要为 STATIC 执行 npm、Maven 或 Docker build。

### 2. 正式工程 / Pipeline Artifact

正式工程不再要求 FDP 自己从源码执行完整构建。

项目经理和开发团队自行维护 Codeup Pipeline 中的编译、测试、打包和镜像构建逻辑；FDP 打通 Codeup 工作流，取得成功构建后的产物，再部署到公司私有云。

```text
开发团队
  ↓
Codeup Git
  ↓
Codeup Pipeline
  ├── compile
  ├── test
  ├── package
  └── build artifact
          ↓
       构建产物
          ↓
         FDP
  ├── 查询成功构建
  ├── 下载 Artifact
  ├── 部署到私有云
  ├── 健康检查
  └── 配置 Nginx
          ↓
       客户预览
```

因此长期职责边界是：

```text
Codeup Pipeline = CI / 构建
FDP             = Delivery / 私有云部署
```

FDP 不应继续发展成第二套 CI 系统。

## 正式工程的产物方向

FDP 后续优先支持“已经可以部署”的 Pipeline Artifact：

```text
PIPELINE_ARTIFACT
  ├── DOCKER_IMAGE_TAR
  ├── JAR
  └── ARCHIVE
```

容器项目第一阶段建议由 Codeup Pipeline 产生镜像 tar：

```text
Codeup Pipeline
  ↓
docker build
  ↓
docker save app:<commit> -o app-image.tar
  ↓
Pipeline Artifact
```

FDP：

```text
下载 app-image.tar
  ↓
docker load
  ↓
替换私有云容器
  ↓
127.0.0.1:<hostPort>
  ↓
Nginx
  ↓
客户预览
```

未来如果建设私有镜像仓库，可以升级为：

```text
Codeup Pipeline -> Private Registry -> FDP docker pull -> Deploy
```

## 当前实现与目标方向

当前代码已经存在 `STATIC` 和本机 `CONTAINER` Docker build 能力。

其中：

- `STATIC` 是当前主线能力，继续使用。
- 本机 `CONTAINER` 构建可以暂时作为兼容能力保留。
- 新的正式工程主线不再继续强化本机 Docker build，而是转向 `Codeup Pipeline -> Artifact -> FDP Deploy`。

## Codeup 凭据

FDP 已支持可复用 `source_credential`：

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

Token 不写入 Git URL，也不应出现在部署日志中。HTTPS Git 使用临时 `GIT_ASKPASS` 注入凭据。

首次保存凭据前需要固定的 AES-GCM 主密钥：

```bash
export FDP_CREDENTIAL_KEY="$(openssl rand -base64 32)"
```

该值一旦用于加密 Codeup Token，就必须长期保持稳定，否则已有 Token 将无法解密。

## 私有云运行环境

当前单机阶段需要：

```text
Git
Docker Engine
Nginx
rsync
curl
Java 17+
MySQL
```

业务项目的 Node.js、Maven、Python 等构建环境原则上应由 Codeup Pipeline 提供，而不是继续堆到 FDP 部署服务器上。

推荐 FDP 运行目录：

```text
/data/fdp/
├── app.jar
├── logs/
├── workspaces/
├── sites/
├── data/
└── runtime/nginx/
```

当前服务器端口规划：

```text
3003 -> FDP 管理前端
3004 -> FDP 后端
3005 -> FDP 客户预览统一入口
```

## 数据库

全新环境使用最新全量 SQL：

```bash
mysql -uroot -p < sql/fdp.sql
```

不要在全新数据库上重复执行旧版本 migration。

## 本地开发

后端：

```bash
cd backend
mvn spring-boot:run
```

前端：

```bash
cd frontend
npm install
npm run dev
```

默认：

```text
FDP_EXECUTION_ENABLED=false
```

此时 Git / Docker / Shell 发布步骤为 DRY-RUN。

## 当前实施顺序

先把最短链路跑通，再逐步扩展：

```text
1. FS -> Codeup 静态 HTML
2. FDP 从 Codeup 拉取 STATIC
3. FDP -> Nginx -> 客户预览
4. Codeup Pipeline API 联调
5. 查询成功构建
6. 下载 Pipeline Artifact
7. FDP 部署正式工程到私有云
8. 最后再考虑 Webhook / 自动触发
```

当前阶段的重点是第 1～3 步。

## 当前不做

- Kubernetes / Helm
- 多服务器调度
- FDP 内部通用 CI Pipeline 设计器
- 在线源码编辑
- 替代 Codeup 的构建系统

后续如果私有云从单机扩展到多节点，再评估 Kubernetes 等运行时编排能力。
