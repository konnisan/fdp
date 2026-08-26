# FDP POC Delivery Architecture

## 产品定义

FDP 是客户 POC 的服务器部署控制器。研发只发生在 Codeup；FDP 从不成为源码事实源。

## 唯一外部端口

```text
Nginx :8090
  ├── /poc/a/ -> STATIC 文件
  ├── /poc/b/ -> 127.0.0.1:3101
  └── /poc/c/ -> 127.0.0.1:3102
```

Node.js 端口仅在服务器内部监听，客户只访问 Nginx 的统一端口和 Path。

## 两种项目类型

### STATIC

用于 FS 产出的纯 HTML 或前端构建产物。FDP 同步 Codeup 后把静态资源发布到统一 sites 目录，再由 Nginx 暴露 Path。

### NODE_SQLITE

用于其他成员在 Codeup 持续迭代的快速交互 POC。FDP 同步源码、执行 Node 构建、用 PM2 管理进程，并通过 Nginx 反向代理。

## SQLite

SQLite 属于 POC 运行数据，不属于 FDP 元数据。运行文件放在 `data/<projectCode>/`，与 Git 工作区分离，避免重新部署覆盖客户演示数据。

## 安全边界

- Codeup Token/SSH Key 不落库。
- 不提供通用“执行任意 shell”HTTP API。
- projectCode、previewPath、internalPort 做唯一性约束。
- 工作目录只能位于 FDP 配置根目录下面。
