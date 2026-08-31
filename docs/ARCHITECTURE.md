# FDP Delivery Architecture

## 1. Product positioning

FDP is an internal delivery and customer-preview control platform built around Codeup.

The core boundary is:

- Codeup is the source-code and CI source of truth.
- FS produces static POC deliverables.
- Project managers own the detailed configuration of formal-project Codeup pipelines.
- FDP does not become a source-code editing platform and does not own formal-project CI logic.
- FDP connects Codeup delivery outputs to the company's private-cloud runtime and exposes a unified customer preview entry.

The current target is a single private-cloud Linux host with Nginx and Docker. Kubernetes is not required in the first stage.

## 2. Two delivery paths

FDP supports two fundamentally different delivery paths.

### 2.1 STATIC POC

Static POC HTML is already a deployable artifact, so it can be delivered directly from Git.

```text
FS
  -> generate static POC HTML
  -> push to Codeup static repository
  -> FDP git clone / fetch / reset
  -> publish files to private-cloud static directory
  -> generate Nginx route
  -> customer preview
```

Responsibilities:

```text
FS      : generate and upload POC HTML
Codeup  : persist source/deliverable history
FDP     : download, publish, route, verify, expose preview URL
```

FDP does not run npm, Maven, Docker build, or other application build steps for this path.

Initial integration repository:

```text
https://codeup.aliyun.com/6038b0d9eb45243512067136/poc-html.git
```

The repository may contain multiple POC deliveries, for example:

```text
poc-html/
  <build-id-a>/index.html
  <build-id-b>/index.html
```

## 3. Formal project delivery

Formal application projects use Codeup Pipeline as the CI boundary.

The detailed pipeline implementation is managed by each project manager/team. FDP only needs to integrate the workflow and consume its successful build output.

```text
Developer / Project Manager
          -> Codeup Git
          -> Codeup Pipeline
               - compile
               - test
               - package
               - build deployable artifact
          -> successful pipeline artifact
          -> FDP queries pipeline/build result
          -> FDP downloads artifact
          -> FDP deploys artifact into private cloud
          -> health check
          -> Nginx route
          -> customer preview
```

The architectural boundary is therefore:

```text
Codeup Pipeline = CI / build ownership
FDP             = delivery / private-cloud deployment ownership
```

FDP should not require project managers to duplicate pipeline build details in FDP.

## 4. Formal-project artifact model

FDP should consume deployable outputs instead of rebuilding source code itself.

Supported artifact forms can evolve gradually:

```text
PIPELINE_ARTIFACT
  - DOCKER_IMAGE_TAR
  - JAR
  - ARCHIVE

Future:
  - PRIVATE_REGISTRY_IMAGE
```

For the container-first formal-project path, the preferred short-term deliverable is a Docker image artifact produced by Codeup Pipeline.

Example:

```text
Codeup Pipeline
  -> docker build
  -> docker save app:<commit> -o app-image.tar
  -> publish pipeline artifact

FDP
  -> download app-image.tar
  -> docker load
  -> replace target container
  -> bind container to 127.0.0.1:<hostPort>
  -> health check
  -> publish Nginx preview route
```

This keeps Node.js, Maven, Python and other application build runtimes out of the private-cloud deployment host.

## 5. FDP project model direction

The delivery model should describe artifact source and deployment behavior rather than programming language.

Recommended high-level strategies:

```text
STATIC_GIT
PIPELINE_ARTIFACT
```

`STATIC_GIT` needs fields such as:

```text
git_url
git_branch
credential_id
project_directory
build_output
preview_path
```

`PIPELINE_ARTIFACT` should evolve toward fields such as:

```text
codeup_repository
codeup_pipeline_id
pipeline_name
artifact_name
artifact_type
deployment_environment
container_name
host_port
container_port
health_check_path
preview_path
```

The project manager owns the internal pipeline stages and commands; FDP only records what is required to locate a successful build and deploy its artifact.

## 6. Deployment task model

FDP keeps the Task / Step / Log execution model.

STATIC POC:

```text
PREPARE
  -> GIT_SYNC
  -> STATIC_PUBLISH
  -> ROUTE
  -> VERIFY
```

Formal project:

```text
PREPARE
  -> PIPELINE_QUERY
  -> ARTIFACT_DOWNLOAD
  -> ARTIFACT_INSTALL / IMAGE_LOAD
  -> RUNTIME_REPLACE
  -> HEALTH_CHECK
  -> ROUTE
  -> VERIFY
```

Useful deployment states include:

```text
QUEUED
BUILDING
BUILD_SUCCESS
DOWNLOADING
DEPLOYING
DEPLOYED
FAILED
```

## 7. Private-cloud network boundary

The private-cloud runtime should not expose business containers directly.

Example:

```text
docker run -p 127.0.0.1:3101:8080 ...
```

Nginx remains the external entry point:

```text
Customer
   -> Nginx unified public port
       -> /poc/<id>/        -> static files
       -> /app/<project>/   -> 127.0.0.1:<hostPort>
```

This keeps customer preview routing under FDP control.

## 8. Current implementation versus target direction

The current FDP code already contains a local `CONTAINER` flow that can clone source and execute Docker build on the FDP host.

That capability may remain temporarily as a compatibility path, but it is no longer the preferred formal-project architecture.

New formal-project work should prioritize:

```text
Codeup Pipeline integration
  -> successful build discovery
  -> artifact download
  -> private-cloud deployment
```

Do not continue expanding FDP into a second CI system.

## 9. Implementation order

The agreed implementation sequence is incremental:

1. Complete `FS -> Codeup -> FDP STATIC -> Nginx preview`.
2. Stabilize Codeup credentials and static deployment lifecycle.
3. Investigate and connect Codeup Pipeline APIs.
4. Query successful pipeline runs and enumerate downloadable artifacts.
5. Download a selected artifact to the private cloud.
6. Deploy the artifact and expose it through FDP/Nginx.
7. Add optional automation/webhook triggering only after the manual flow is reliable.

This sequence keeps the first customer-preview path simple while preserving the correct long-term boundary between Codeup CI and FDP delivery.
