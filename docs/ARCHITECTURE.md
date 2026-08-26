# FDP V4 Docker Delivery Architecture

## 1. Product boundary

Codeup is the source of truth. FDP never becomes a source-code editing platform.

V1 runs on one Linux server and supports two delivery strategies:

```text
STATIC     -> shared files -> Nginx
CONTAINER  -> Docker image -> Docker container -> Nginx
```

Kubernetes is deliberately excluded from V1.

## 2. Why CONTAINER instead of NODE_SQLITE / JAVA / PYTHON

FDP should model deployment, not programming languages.

```text
Node.js  ─┐
Java     ─┤
Python   ─┼─ Dockerfile -> image -> CONTAINER
Go       ─┤
Others   ─┘
```

This keeps the FDP backend unchanged when a new application technology appears.

## 3. Git and monorepo layout

A delivery project contains:

```text
git_url
git_branch
project_directory
```

`project_directory` allows a service to live below the repository root, for example:

```text
repo: L2/IDP_AL
project_directory: poc/poc/l2-data-aggregation/l2-server
```

The Linux workspace remains disposable:

```text
/data/fdp/workspaces/<projectCode>
```

## 4. Container build and versioning

CONTAINER projects additionally define:

```text
dockerfile_path
docker_build_context
image_name
container_name
host_port
container_port
cpu_limit
memory_limit
host_data_path
container_data_path
health_check_path
```

Every successful Git sync produces a commit SHA. Docker images are tagged by that commit:

```text
fdp/l2-server:a82fc7317d2e
```

This gives deployment history a stable source-version/image-version relationship and creates a foundation for rollback later.

## 5. Network boundary

Containers never publish directly on the server's external interface:

```text
docker -p 127.0.0.1:3101:3000
```

Nginx is the only external entry point:

```text
Nginx :8090
  ├── /poc/static-a/ -> shared static files
  ├── /app/l2/       -> 127.0.0.1:3101
  └── /app/service/  -> 127.0.0.1:3102
```

## 6. Persistence

Containers are replaceable. Mutable data must live outside the container when persistence is required.

```text
Host:      /data/fdp/data/l2-server
Container: /app/data
```

The first V1 model supports one bind mount per project. It can later evolve to a dedicated volume table.

## 7. Deployment task pipeline

FDP keeps the Jenkins-inspired Task/Step/Log model.

STATIC:

```text
PREPARE -> GIT_SYNC -> STATIC_BUILD -> STATIC_PUBLISH -> ROUTE -> VERIFY
```

CONTAINER:

```text
PREPARE -> GIT_SYNC -> DOCKER_BUILD -> CONTAINER_REPLACE -> ROUTE -> VERIFY
```

`STATIC_BUILD` can be `SKIPPED`.

A CONTAINER task stores both `commit_id` and `image_tag`.

## 8. Resource control

Single-host Docker cannot scale horizontally across servers, but V1 still prevents one service from consuming the entire machine:

```text
--cpus <limit>
--memory <limit>
```

Kubernetes is a later orchestration strategy when one host is no longer enough.

## 9. Future K8S boundary

The upper-level FDP concepts should remain stable:

```text
Project
Git
Task
Step
Log
Commit
Image
Preview Path
```

Future K8S work should replace only the runtime strategy:

```text
Docker single host  -> current V1
Kubernetes          -> future multi-host orchestration
```

Do not introduce Pod/Deployment/Service/Ingress/Helm into V1.
