# Pipeline Artifact Delivery

FDP 的正式工程主线是：

```text
Codeup Git
  -> Yunxiao Flow
  -> build / test / frontend build / backend docker build
  -> Packages GENERIC
  -> FDP
  -> Linux Docker + Nginx
  -> customer preview
```

职责边界：

```text
Flow     = CI / build deployable artifact
Packages = artifact storage and versioning
FDP      = CD / private-cloud delivery
```

FDP 不再对正式工程执行 `npm build`、`mvn package` 或 `docker build`。

## V1 Packages 制品约定

每一个可部署版本上传一个通用制品，例如：

```text
financial-system-delivery
version: 20260903-007
```

上传文件本身必须是 `.tgz` / `.tar.gz`，解包后根目录：

```text
fdp-manifest.yml
frontend.tar.gz
backend-image.tar
database/                 # 可选；当前版本只随包保留，暂不自动执行
```

`frontend.tar.gz` 解开后应直接出现 `index.html`，或者通过 manifest 的 `frontend.root` 指向包含 `index.html` 的目录。

`backend-image.tar` 必须由 Flow 完成：

```bash
docker build -t financial-system-backend:20260903-007 .
docker save financial-system-backend:20260903-007 -o backend-image.tar
```

FDP 只执行 `docker load` 和 `docker run`。

## fdp-manifest.yml

应用仓库维护应用自身参数，示例：

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

当前 V1 实际读取：

- `frontend.archive`
- `frontend.root`
- `backend.imageArchive`
- `backend.image`
- `backend.containerPort`
- `backend.healthCheck`

`database` 节点先保留，后续再增加 migration 执行器。

## FDP 环境配置

FDP 页面只保存环境侧参数：

```text
pipelineId
packageRepoId
artifactName
previewPath
hostPort
containerName
envFile
```

例如：

```text
previewPath=/financial-system
hostPort=3201
containerName=fdp-financial-system-backend
envFile=/data/fdp/env/financial-system.env
```

`envFile` 用于 MySQL 地址、账号、密码等服务器环境变量，不进入 Codeup 或 Packages。

示例：

```properties
MYSQL_HOST=127.0.0.1
MYSQL_PORT=3306
MYSQL_DATABASE=financial_system
MYSQL_USERNAME=financial_system_user
MYSQL_PASSWORD=change-me
```

如果共享 MySQL 运行在 Docker 网络中，也可以使用对应容器名作为 `MYSQL_HOST`；实际值由运行环境决定。

## FDP Linux 部署顺序

```text
1. 查询 Flow SUCCESS runs
2. 从 run detail 的 ARTIFACTSV2 / ARTIFACTS 解析 Packages artifact
3. 推荐最新成功 run 对应版本
4. 用户也可选择历史成功 run
5. 下载 Packages downloadUrl
6. MD5 校验（Flow 返回时）
7. 解包 delivery bundle
8. 读取 fdp-manifest.yml
9. 发布 frontend 到 FDP_STATIC_ROOT/<previewPath>
10. docker load backend-image.tar
11. 停止并删除旧 backend container
12. docker run 新 backend，端口绑定到 127.0.0.1:<hostPort>
13. health check
14. 更新 Nginx
15. 记录部署版本和历史
```

平台是客户预览/交付环境，不要求零停机；因此 V1 使用“停旧 -> 启新”，不实现蓝绿发布或滚动升级。

## Nginx 路由

假设：

```text
previewPath=/financial-system
hostPort=3201
```

FDP 生成逻辑等价于：

```nginx
location ^~ /financial-system/api/ {
    proxy_pass http://127.0.0.1:3201/api/;
}

location ^~ /financial-system/ {
    root /data/fdp/sites;
    try_files $uri $uri/ /financial-system/index.html;
}
```

因此正式前端构建时必须考虑部署 base path，接口也应使用项目路径下的 `/api/`。FDP 不为多个项目提供共享的全局 `/api/`，避免不同项目后端冲突。

## 当前不做

- Kubernetes / Helm
- 多节点调度
- 蓝绿发布 / 零停机
- FDP 内执行 Docker build
- 自动数据库 migration / rollback
- ACR / Harbor pull 模式

后续如果从单 Linux Server 扩展到多节点，再评估 Kubernetes 或私有镜像仓库。
