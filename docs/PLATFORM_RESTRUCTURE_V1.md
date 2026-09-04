# FDP Profile V1

FDP is a lightweight deployment control plane. It does not replace Codeup, Yunxiao Flow or Packages.

## Product model

Top-level navigation is reduced to four areas:

1. 平台总览
2. 项目中心
3. 部署中心
4. 系统集成

Project detail owns the project-level surfaces:

- 概览
- 版本与制品
- 运行单元
- 路由与数据
- 部署记录

## Deployment Profiles

### STATIC

For generated HTML / static POC projects.

- Source: Codeup
- Runtime: host Nginx static files
- Container: none
- Database: none

### LIGHTWEIGHT

For fast full-stack POC projects such as Node.js + SQLite.

- Source: Codeup
- Runtime: one project-owned Docker container in V1
- Persistence: host path mounted into the container, suitable for SQLite
- Route: Nginx proxies the project preview path to the container

### STANDARD

For formal frontend/backend projects.

- CI: Yunxiao Flow
- Artifact: Yunxiao Packages
- Frontend: published as host Nginx static files
- Backend: project-owned Docker image/container
- Database: normally shared/external MySQL with project-specific schema/account
- FDP does not rebuild formal project source on the deployment server

STANDARD V1 keeps the existing `fdp-manifest.yml + delivery bundle` implementation for compatibility. The UI and project model are intentionally structured so a future frontend-artifact + backend-artifact release model can replace the bundle without changing the navigation model.

### CUSTOM

For advanced project-specific Docker configuration.

CUSTOM V1 supports one structured Docker runtime unit using the existing delivery_project fields. The platform intentionally does not accept arbitrary `docker run` shell text. Multi-container runtime units are a follow-up extension.

## Windows development mode

Windows is supported as a development and configuration-validation environment.

Keep:

```properties
FDP_EXECUTION_ENABLED=false
```

With execution disabled:

- Source project deployment commands are recorded/exposed as DRY-RUN operations.
- Docker/Nginx commands are not executed.
- STANDARD projects can read Yunxiao Flow/Packages, versions, history and deployment plans.
- STANDARD container restart/stop/remove/log actions return DRY-RUN command output through `ArtifactRuntimeService`.
- Formal Packages deployment itself remains Linux-only and the UI prevents users from treating Windows as a production deployment host.

Recommended Windows startup:

```powershell
# backend
cd backend
mvn spring-boot:run

# frontend (another terminal)
cd frontend
npm ci
npm run dev
```

Default development endpoints:

- frontend: http://localhost:5173
- backend: http://localhost:8080
- Vite proxies `/api` to the backend.

## Existing database upgrade

Run only:

```sql
sql/migration_v7_deployment_profiles.sql
```

Fresh databases can use `sql/fdp.sql`.

## Design references

The product direction borrows selected concepts rather than cloning any one platform:

- Portainer: container lifecycle/status/log mental model.
- Coolify: project/environment/application-oriented information architecture.
- Dokploy: deployment-focused project experience and version-centric operations.

FDP keeps its own boundary: Codeup + Flow + Packages remain the build side, while FDP is the deployment/runtime side.
