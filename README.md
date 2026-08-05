# V1rtual Backend

V1rtual 个人网站后端，当前分支版本为 `V1rtualSS`。

- Frontend: [../vvv](../vvv)
- Website: [https://v1rtual.top/](https://v1rtual.top/)

## 功能

- 用户登录、JWT 鉴权、个人资料、头像、用户名和密码维护。
- 首页配置、随机内容、Gallery 内容读取。
- 图片、视频、GIF、音频等媒体的上传和管理。
- Gallery 的列表、点赞、评论和评论点赞。
- 管理员资源上传、OSS 同步、资源检索与首页编排。

## API 模块

| Path | 内容 |
| --- | --- |
| `/api/auth` | 登录 |
| `/api/user` | 用户资料、头像、密码、访客计数 |
| `/api/home` | 首页配置、随机内容、完整内容条目 |
| `/api/gallery` | 媒体、列表、点赞、评论 |
| `/api/oss` | OSS 上传 |
| `/api/admin` | 资源同步、资源管理、首页配置 |

## 技术栈

| 类别 | 组件 |
| --- | --- |
| Runtime | Java 21 |
| Framework | Spring Boot 3 |
| Security | Spring Security、JWT |
| Persistence | MyBatis、MySQL |
| Media | Aliyun OSS |
| Build | Maven Wrapper |

## 目录

```text
src/main/
├── java/com/v1rtual/vvv_backend/
│   ├── config/        # Security、CORS、OSS、Web 配置
│   ├── controller/    # API 入口
│   ├── filter/        # JWT 与访问拦截
│   ├── mapper/        # MyBatis 映射
│   ├── service/       # 用户、Gallery、资源同步
│   ├── entity/        # 用户、媒体、互动、首页配置
│   └── vo/            # 请求与响应对象
└── resources/         # 配置示例、静态拦截页
```

## 本地运行

要求：Java 21、MySQL、可用的 OSS 配置。

```bash
cp src/main/resources/application-example.yml src/main/resources/application.yml
./mvnw spring-boot:run
```

本地服务端口为 `8848`。

前端开发服务器运行在 `3001`，并将 `/api` 代理到此服务：

```text
http://localhost:3001/api -> http://127.0.0.1:8848
```

## 生产配置与发布

线上 Nginx 将 `/api/` 转发给 `127.0.0.1:8080`；后端 JAR 由 `spring_V1rtual.service` 运行。

生产配置位于服务器 `/etc/v1rtual/application-prod.yml`，包含数据库、OSS 和 JWT 设置，不提交到仓库。

每个分支代表一套完整网站版本。push 和 PR 只执行 CI 构建；部署由 GitHub Actions 手动选择分支执行。改动 API、鉴权、资源字段、环境或 Nginx 路由时，前后端应使用同名分支，分别通过 CI 后再联合验证。

发布细节见 [CICD规范.md](CICD规范.md) 和 [skills/v1rtual-backend-cicd](skills/v1rtual-backend-cicd/SKILL.md)。
