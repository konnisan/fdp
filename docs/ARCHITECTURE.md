# FDP Delivery Architecture

## 1. Positioning

FDP is an internal customer-preview and delivery control plane built around Codeup / Yunxiao Flow.

```text
Codeup / Flow = source + CI
Packages      = artifact storage / versioning
FDP           = CD / private-cloud delivery
```

The current runtime is one Linux server with Docker, Nginx and a shared MySQL. Kubernetes is deliberately out of scope for V1.

## 2. STATIC POC

```text
FS
  -> generate POC HTML
  -> Codeup static repository
  -> FDP git clone / fetch
  -> publish static files
  -> Nginx
  -> customer
```

STATIC output is already deployable; FDP does not build it.

## 3. Formal project

Formal projects are built entirely by Flow before FDP touches them.

```text
Developer
  -> Codeup Git
  -> Flow
       -> frontend build
       -> backend package/test
       -> docker build backend
       -> docker save backend image
       -> assemble delivery bundle
       -> upload Packages GENERIC
  -> successful Flow Packages artifact
  -> FDP
       -> select/recommend successful release
       -> download and verify
       -> publish frontend to Nginx static root
       -> docker load backend image
       -> replace backend container
       -> health check
       -> generate Nginx route
  -> customer preview
```

Flow may be maintained differently by each project manager, but its final output must follow the FDP delivery contract documented in `PIPELINE_ARTIFACT_DELIVERY.md`.

## 4. Runtime model

A formal project is not “three Docker containers”. The current simplified model is:

```text
Platform infrastructure
├── Nginx
├── shared MySQL
└── FDP

Project A
├── frontend dist -> /data/fdp/sites/project-a
└── backend       -> Docker container

Project B
├── frontend dist -> /data/fdp/sites/project-b
└── backend       -> Docker container
```

The frontend is static after Vue/Vite build, so a dedicated frontend container is unnecessary on this single-host preview platform.

The shared MySQL is platform-level infrastructure. Each project should use its own database/user even though the MySQL service is shared.

## 5. Application configuration versus environment configuration

Application-owned facts travel with the Flow artifact manifest:

```text
frontend archive name/root
backend image archive
backend image tag
containerPort
healthCheck
migration location (reserved)
```

FDP-owned environment facts are configured on the deployment host:

```text
pipelineId
packageRepoId
artifactName
previewPath
hostPort
containerName
envFile
```

Example:

```text
application: containerPort=8080
FDP:         hostPort=3201
```

FDP then runs:

```text
127.0.0.1:3201 -> backend container:8080
```

## 6. Version selection

FDP does not blindly deploy “whatever is newest in Packages”.

It reads recent successful Flow runs, resolves the configured Packages artifact from each run detail, and presents release candidates:

```text
latest successful artifact -> recommended
older successful artifacts -> selectable
```

The user explicitly chooses a release to deploy. Re-selecting an older successful release is the V1 lightweight application rollback path.

## 7. Nginx model

For an engineering project:

```text
previewPath=/financial-system
hostPort=3201
```

FDP generates the equivalent of:

```nginx
location ^~ /financial-system/api/ {
    proxy_pass http://127.0.0.1:3201/api/;
}

location ^~ /financial-system/ {
    root /data/fdp/sites;
    try_files $uri $uri/ /financial-system/index.html;
}
```

Only the unified Nginx public port is intended for customer access. Backend host ports are bound to `127.0.0.1`.

## 8. V1 deployment lifecycle

```text
FLOW_RELEASE_QUERY
  -> ARTIFACT_DOWNLOAD
  -> CHECKSUM_VERIFY
  -> BUNDLE_EXTRACT
  -> FRONTEND_PUBLISH
  -> DOCKER_LOAD
  -> OLD_CONTAINER_REMOVE
  -> BACKEND_START
  -> HEALTH_CHECK
  -> NGINX_REFRESH
  -> SUCCESS / FAILED history
```

This is a preview/delivery platform, not a high-availability production orchestrator. Short deployment downtime is acceptable, so V1 does not implement blue-green or rolling deployment.

## 9. Database direction

Shared MySQL is accepted for the current single-host preview environment.

Project database migrations may be included in the artifact contract, but V1 does **not** automatically execute them yet. Migration execution/history/rollback will be a separate implementation step after the artifact deployment path is stable.

## 10. Legacy container mode

The old FDP `CONTAINER` mode still clones source and runs `docker build` on the FDP host. It remains only as a compatibility path.

It is not the preferred formal-project architecture and should not be expanded into a second CI system.

## 11. Current exclusions

- Kubernetes / Helm
- multi-server scheduling
- frontend containers
- zero-downtime / blue-green release
- FDP-side formal-project compilation or Docker build
- automatic DB migration/rollback
- private registry pull (Harbor/ACR) in V1

A private registry and Kubernetes can be evaluated later if the platform grows beyond one Linux host.
