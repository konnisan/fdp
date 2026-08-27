# Windows 本机测试清单

Windows 的目标不是验证真实 Linux 发布，而是把 **UI、API、数据库、项目配置、部署任务模型和 DRY-RUN 流程** 提前验完。

## 1. 启动前

保持：

```text
FDP_EXECUTION_ENABLED=false
```

启动 MySQL，确认已经执行到 V4 数据库结构。

```powershell
cd backend
mvn spring-boot:run
```

```powershell
cd frontend
npm install
npm run dev
```

访问：

```text
Frontend: http://localhost:5173
Backend:  http://localhost:8080
```

## 2. 运行环境页

进入“运行环境”，确认：

- OS 显示 Windows。
- Mode 显示 `DRY_RUN`。
- Git / Java / curl 等根据本机真实情况显示可用或不可用。
- 如果安装了 Docker Desktop：Docker CLI 应显示可用；Docker Desktop 未启动时 Docker daemon 应显示不可用。
- Windows 没有 Nginx / rsync 时显示不可用属于正常现象。

## 3. 交付项目 CRUD

分别创建：

### STATIC

建议测试：

```text
projectCode: demo-static
projectType: STATIC
projectDirectory: .
buildCommand: 留空或 npm run build
buildOutput: dist
previewPath: /poc/demo-static
```

### CONTAINER

建议测试：

```text
projectCode: demo-container
projectType: CONTAINER
projectDirectory: poc/poc/l2-data-aggregation/l2-server
Dockerfile: Dockerfile
Build Context: .
Image: fdp/demo-container
Container: fdp-demo-container
Host Port: 3101
Container Port: 3000
CPU: 1
Memory: 512m
Preview Path: /app/demo-container
```

验证：新增、编辑、筛选、详情、删除，以及重复 projectCode / previewPath / hostPort 的后端校验。

## 4. 部署计划

进入项目详情点击“部署计划”。

确认：

- 显示 `DRY_RUN`。
- Project Directory 正确拼接到 workspace。
- STATIC 显示 STATIC_BUILD / STATIC_PUBLISH。
- CONTAINER 显示 DOCKER_BUILD / CONTAINER_REPLACE。
- Docker 端口映射是 `127.0.0.1:hostPort:containerPort`。
- Image Tag 使用 `<commit>` 占位。
- 未配置 Volume / Health Check 时能看到提示。

## 5. DRY-RUN 部署

点击“拉取并部署”。

Windows 下不会真正执行 Git / Docker / Nginx，但应该：

- 返回 Task ID。
- 页面自动等待任务从 QUEUED -> RUNNING -> SUCCESS/FAILED。
- deployment_task 有记录。
- deployment_step 有每个步骤。
- deployment_log 有 `[DRY-RUN]` 命令日志。
- CONTAINER 的 image_tag 应类似 `fdp/demo-container:dry-run`。

## 6. 部署中心

确认：

- QUEUED/RUNNING 时页面自动刷新。
- 最终进入 SUCCESS / FAILED。
- “执行详情”能看到 Step 和日志。
- 多次部署能按时间保留历史。

## 7. Windows 不要求通过的内容

以下留到 Linux：

- 真实 Codeup clone / fetch。
- `docker build`。
- `docker run`。
- `docker logs / restart / stop`。
- Nginx 配置写入与 reload。
- rsync 静态发布。
- 容器健康检查。
- 客户实际 Preview Path 访问。
- CPU / Memory limit 的实际约束。
- Volume 持久化与容器替换后数据保留。

## 8. 明天 Linux 第一轮验收顺序

```text
环境检查
  -> 数据库 V4
  -> FDP DRY_RUN
  -> 配置一个真实 Codeup 项目
  -> Deployment Plan
  -> 开启 LIVE
  -> Git Sync
  -> Docker Build
  -> Container Run
  -> Nginx Route
  -> Health Check
  -> 客户访问
```

不要一开始就同时测试多个项目。先用一个最简单的真实 CONTAINER 项目跑通完整链路，再测试 monorepo 子目录和 SQLite Volume。
