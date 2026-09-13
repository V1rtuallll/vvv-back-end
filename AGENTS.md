# AGENTS.md — vvv_backend（V1rtual 后端）

V1rtual 网站的 Spring Boot 后端。配对的**前端仓库是 `../vvv`**，两者是两个独立的 git 仓库。

## 权威文档（本文件不重复它们的内容）

| 文档 | 管什么 |
| --- | --- |
| `CICD规范.md` | 分支模型、CI/CD、发布顺序、回滚 |
| `skills/v1rtual-backend-cicd/SKILL.md` | 后端的构建与发布流程 |
| `skills/v1rtual-backend-cicd/references/server-contract.md` | 服务器路径、systemd、生产配置契约 |

只要改动影响 **API 契约、登录鉴权、资源访问、Nginx 路由、环境变量或发布脚本**，就必须同时阅读
`../vvv/skills/v1rtual-frontend-cicd/SKILL.md` 和它的 `references/server-contract.md`。

**不要把这些文档的内容抄进 AGENTS.md。** 抄了就会产生两份需要同步的规则。

## 工作方式

- **本地可以随便测。** `./mvnw test` / `./mvnw spring-boot:run` 随便跑，本地 MySQL 随便连。
- **不要用 SSH 连生产做验证或测试。** 生产凭据在本机 SSH 配置里，只用于用户**明确要求**的发布动作。
  任何"顺手连一下生产看看"的行为都不允许。
- **提交与推送遵从 `CICD规范.md`。** 尤其是：联动改动必须与 `../vvv` 使用**同名分支**，
  且两端 CI 都绿才算可上线；只推一边不能视为完成。
- **不要擅自执行 `git commit` / `git push` / 切分支。** 用户明确要求时才做。
- 改 `.github/workflows/` 或 `scripts/` 时，先在非 `main` 分支推送验证，再同步到 `main`。

## 提交规范

`CICD规范.md` 里**没有**写提交信息格式，但仓库历史实际遵循 **Conventional Commits**：

```
fix: enforce media and comment data integrity
refactor: split backend services and admin endpoints
docs: describe backend service boundaries
```

- 格式：`<type>: <英文描述>`，描述用小写开头、不加句号
- 常用 type：`feat` 新功能 / `fix` 修 bug / `refactor` 重构 / `test` 测试 / `docs` 文档 / `ci` 工作流 / `chore` 杂项
- **一个功能一个 commit**，不要把无关改动混在一起
- 描述用英文（与历史一致）

## 技术栈

Spring Boot 3.5.9、Java 21、MyBatis（**全部是注解 SQL，没有 XML**）、Lombok、MySQL 8、
阿里云 OSS SDK、jjwt、Spring Security。构建用 Maven Wrapper（`./mvnw`），生产是 systemd 托管的可执行 JAR。

## 目录结构

```
src/main/java/com/v1rtual/vvv_backend/
├── controller/       HTTP 层，含 admin/ 子包
├── service/          业务逻辑，含 admin/ gallery/ home/ media/ user/ 子包
├── mapper/           MyBatis Mapper 接口（注解 SQL）
├── entity/           数据库实体
├── dto/              入参对象（LoginDTO、RegisterDTO）
├── vo/               出参对象（Result、GalleryVO、HomeConfig*）
├── filter/           JwtAuthenticationFilter、MobileBlockFilter
├── security/         CurrentUserProvider、OwnerAccess
├── exception/        统一错误契约：GlobalExceptionHandler、ApiErrorController、401/403 处理器
├── config/           SecurityConfig、WebConfig、CorsConfig、OssConfig
└── util/             JwtUtil、OssUtil
```

## 代码约定

- 缩进 **2 空格**
- Lombok 用 `@Data` / `@Builder` / `@RequiredArgsConstructor` / `@Slf4j`；
  多依赖的类用构造器注入（`private final` 字段 + `@RequiredArgsConstructor`），不要用 `@Autowired` 字段注入
- 所有接口返回 `Result<T>`。`Result.error(msg)` 固定 500；错误响应统一由
  `exception/GlobalExceptionHandler` 处理，**`code` 与 HTTP 状态码一致**
  （400 / 401 / 403 / 404 / 405 / 409 / 413 / 500），响应体固定为
  `{code, msg, data: null}`
- **用户可见文案一律用中性表达**：陈述事实，不用「～」「哦」「啦」「呀」这类语气词，
  不用感叹号，不加颜文字。「密码不对哦～再想想？」应写作「密码错误」。
  **注释同样中性**，不写口语化的碎碎念
- Mapper 方法有多个参数时用 `@Param` 显式命名
- 新增 Mapper 方法写在接口里，用 `@Select` / `@Insert` / `@Update` 注解，
  **不要新建 XML**（`application.yml` 里的 `mapper-locations: classpath*:mappers/**/*.xml`
  指向的目录和实际存在的 `src/main/resources/mapper/` 名字不一致，而且两边都是空的）
- 权限判断一律在**服务端**依据 JWT 里的用户身份做，不要相信请求体里的用户 ID

## 命令

```bash
./mvnw test                     # 跑全部测试（不需要 MySQL，见下）
./mvnw test -Dtest=类名          # 跑单个测试类
./mvnw spring-boot:run          # 本地启动，端口 8848
./mvnw -B clean package         # 构建可执行 JAR 到 target/
```

⚠️ **不要用 `./mvnw test | tail` 这类管道判断成败** —— 管道会让退出码变成 `tail` 的（恒 0）。
要么不加管道，要么先 `set -o pipefail`，或者直接看 `target/surefire-reports/`。

## 测试要求

- 框架：JUnit 5 + Mockito，来自 `spring-boot-starter-test`
- **风格：用静态 `mock()` 手写构造，不用 `@ExtendWith(MockitoExtension)` / `@Mock` / `@InjectMocks`。**
  跟随现有测试的写法
- **新增或修改逻辑必须带测试**；纯样式、文案、错别字改动豁免
- 测试文件路径与主代码镜像，放在 `src/test/java/...` 下
- **`@SpringBootTest` 不需要 MySQL。** 已实测：把 datasource 指向一个连不上的地址
  （`SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:1/nonexistent ./mvnw test`），全部测试通过。
  原因是 HikariCP 连接池是惰性的，`contextLoads()` 从头到尾没请求过连接。
  但请注意：**一旦某个测试真的碰数据库，它就会失败** —— 那时需要给测试单独准备数据源，
  不要假设本地 MySQL 一定在跑
- `src/test/resources/` 不存在，测试会加载主 `application.yml`。加新测试时留意这一点

## 数据库迁移

本项目**没有 Flyway / Liquibase**，`deploy.yml` 里也没有迁移步骤（只做「构建 JAR → SSH 上传 →
重启 systemd」）。`db/` 目录就是替代品，**结构变更的唯一入口**，详见 `db/README.md`。

- 结构变更写成 `db/migrations/V<编号>__<英文短描述>.sql`，编号只增不改
- **已执行过的迁移绝对不能修改**：运行器记录文件的 SHA-256，内容不符会直接报错中止。
  要改结构就加更高编号
- 执行用 `db/migrate.sh`（幂等、按版本号顺序、支持 `--dry-run`）
- 验证各端结构是否一致用 `db/fingerprint.sh` 比对结构指纹
- **顺序是先执行迁移，再发布代码**：新代码可能引用新列，旧代码不会引用它，
  所以先加结构时线上是连续的；反过来会让新代码在旧结构上直接报错

## 已知陷阱

- **媒体元数据存在两份，且写路径不对称。** 上传时会同时往 `gallery` 表和对应的
  `photo` / `gif` / `video` / `music` 类型表写相同的 title/description/src，两表之间
  **没有外键、没有唯一约束**，全靠 `src` 字符串约定。而 `AdminMediaService.update`
  **只写类型表**，Gallery 列表读的却是 `gallery` 表 —— 所以后台改完标题，Gallery 页面不会变，
  而且不报错。给 Gallery 加编辑/删除接口时必须同时处理两张表。
- **`MobileBlockFilter` 早于 Spring Security 生效。** 它由 `config/WebConfig.java` 用
  `FilterRegistrationBean` 注册（`addUrlPatterns("/*")` + `setOrder(1)`），是 Servlet 容器级过滤器，
  所以 `SecurityConfig` 的 `permitAll` 白名单**绕不过它**。另外前端 `../vvv/index.html` 里
  还有一段功能重叠的内联拦截脚本。
- **`application.yml` 里有明文密钥**（数据库密码、OSS `access-key-secret`、JWT secret），
  已经提交进仓库历史。这是既有技术债，需要轮换；**不要再往里加新的明文密钥**。
  生产配置只放在服务器 `/etc/v1rtual/application-prod.yml`（不进 Git）。
- **`spring.servlet.multipart` 配的是单文件 20000MB / 单请求 500000MB。**
  `UploadValidator` 会把这个值当作上限来校验，所以"按配置值提示用户"意味着提示 20GB。
- `CommentMapper.insert` 的 SQL 里 `target_type` **硬编码为 `'gallery'`**，
  传 `#{targetType}` 不起作用。

## 不许做的事

- 不要把生产主机、端口、SSH 凭据、密钥、私钥提交进仓库
- 不要创建仓库内的生产配置文件（生产配置只在服务器的 `/etc/v1rtual/application-prod.yml`）
