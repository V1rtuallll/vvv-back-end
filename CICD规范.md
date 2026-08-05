# CICD规范

## 目标

每个 Git 分支是一套完整的网站版本。前端和后端是两个仓库，分支名应保持对应，例如前端与后端都使用 `V1rtualSS`。

## 分支

- `main`：当前默认线上版本的代码基线，可以由管理员直接推送。
- 其他分支：另一套完整网站。新分支应从已有包含 `.github/workflows/` 的分支创建，确保可以 CI 和部署。
- 需要让 `main` 指向某版本时，在对应仓库快进合并该分支：`git switch main && git merge --ff-only <branch> && git push origin main`。

## CI 与 CD

- 任意分支的 push 和 PR 自动运行 CI；前端生成 `dist`，后端生成 JAR，不更新服务器。
- CD 仅手动运行。GitHub 网页可在 Actions 的 `Deploy frontend` / `Deploy backend` 选择目标分支。
- 终端发布当前分支：`./scripts/deploy-current.sh`；加 `--watch` 会等待结果。
- 前后端独立发布。网站和 API 同时改变时，分别在两个仓库的同名分支执行发布。

## Skill 使用与双端联动

- 前端改动先阅读 `../vvv/skills/v1rtual-frontend-cicd/SKILL.md`；后端改动先阅读 `skills/v1rtual-backend-cicd/SKILL.md`。只要改动影响 API 契约、登录鉴权、资源访问、Nginx 路由、环境变量或发布脚本，两个 skill 和各自的 `references/server-contract.md` 都必须一起阅读。
- 联动改动必须使用同名分支，并在前后端仓库都确认该分支已推送、CI 成功。只推送其中一个仓库或只看其中一端的 CI，不能视为联动版本可上线。
- 后端接口、生产配置或 Nginx `/api` 路由变更时，先发布后端并验证 API；再发布前端并验证页面和 API。仅前端展示改动可只发布前端；仅后端内部改动可只发布后端。
- 本地开发前端运行在 `3001`，通过 `/api` 代理访问本地后端 `8848`。生产浏览器始终访问相对路径 `/api`，由 Nginx 转发到后端生产监听端口；不得将生产主机、端口、SSH 或密钥写入前端 `VITE_*` 环境变量。
- 联动发布完成后，至少验证 `https://v1rtual.top/`、关键 API（如 `/api/user/count`）、前端 Nginx `current` 版本和后端 `spring_V1rtual.service` 状态。回滚时按故障归属回滚单端；接口不兼容时必须回滚到相互兼容的一对前后端版本。

## 服务器与产物

- 前端构建产物为 `dist`，上传后发布到 `/www/wwwroot/vvv-front-end/releases/<commit>`，Nginx 指向 `current`。
- 后端构建产物为可执行 JAR，发布到 `/www/wwwroot/vvv-back-end/releases/<commit>/app.jar`，由 `spring_V1rtual.service` 重启。
- 每端保留最近 3 个发布版本；发布脚本原子更新 `current`，旧版本可用于回滚。
- 后端生产配置只在服务器 `/etc/v1rtual/application-prod.yml`，通过 `SPRING_PROFILES_ACTIVE=prod` 和 `SPRING_CONFIG_ADDITIONAL_LOCATION` 生效，不能提交到 Git。

## 改动后的操作

- 改普通代码：提交、推送，等 CI 成功；需要上线时再发布该分支。
- 改前端依赖或后端依赖：提交锁文件或 Maven 配置，让 CI 重新构建；成功后再发布。
- 改生产配置：先在服务器修改外置配置并验证，再发布需要它的代码；不得将密钥写入仓库。
- 改工作流或部署脚本：先在非 `main` 分支推送并验证 CI/CD，再同步到 `main`。
