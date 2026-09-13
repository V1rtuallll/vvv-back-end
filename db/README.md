# 数据库迁移模块

本项目没有 Flyway / Liquibase。这个目录就是替代品，**结构变更的唯一入口**。

## 不变量

> **任何环境、任何起始状态，按版本号顺序执行完所有未执行的迁移之后，结构必须完全一致。**

这条不变量由 `fingerprint.sh` 验证，不靠人自觉。

## 目录

```
db/
├── README.md                本文件
├── migrate.sh               迁移运行器（幂等、按版本号顺序、带校验和锁）
├── fingerprint.sh           结构指纹，用来验证各端是否收敛
└── migrations/
    └── V001__upload_idempotency_and_oss_cleanup.sql
```

## 三个保证

**1. 已执行过的迁移绝对不能改**

运行器会记录每个迁移文件的 SHA-256。之后任何一次运行，只要文件内容与记录不符就直接报错中止：

```
❌ V001 的内容与执行时不一致。
   已执行过的迁移不允许修改——否则各端结构会永久分叉。
   需要改动就新增一个更高编号的迁移文件。
```

要改结构就加 `V002`、`V003`……，**永远不要回头改老文件**。

**2. 严格按版本号顺序**

文件名格式 `V<数字>__<描述>.sql`，运行器按数字升序执行（用 `sort -V`，所以 `V10` 排在 `V9` 之后，不是字典序）。

**3. 跑几次都安全**

已记录的版本不会重复执行。中断后重跑、并发前重跑、上线前重跑都没问题。

## 日常用法

```bash
# 本地开发库
MYSQL_PWD=<密码> db/migrate.sh
MYSQL_PWD=<密码> db/migrate.sh --dry-run     # 只看会执行什么

# 其它环境
DB_HOST=<主机> DB_PORT=3306 DB_NAME=vvv DB_USER=vvv MYSQL_PWD=<密码> db/migrate.sh
db/migrate.sh --defaults-file=/etc/v1rtual/my.cnf   # 连接参数从这个文件读
```

密码走 `MYSQL_PWD` 环境变量（mysql 客户端原生支持），不要写在命令行上，否则会被 `ps` 看到。

## 验证各端是否一致

```bash
db/fingerprint.sh                                   # 本机
DB_HOST=<生产主机> DB_USER=vvv MYSQL_PWD=<密码> db/fingerprint.sh
```

两边输出的**结构指纹**相同，就说明结构收敛了。不同的话加 `--full` 打印归一化清单再 diff。

指纹会抹平这些**不影响语义**的差异，让跨版本对比成为可能：

- 整型显示宽度（MySQL 8 的 `bigint` 与 5.6 的 `bigint(20)`）
- enum 值的逗号后空格写法
- **排序**：排序在脚本里用 `LC_ALL=C` 做，不用数据库的排序规则
  （8 的 `utf8mb4_0900_ai_ci` 与 5.6 的 `utf8mb4_unicode_ci` 对下划线的先后判断不同，
  会让同构的两套库指纹对不上）

## 新增一个迁移

1. 建文件 `migrations/V002__描述.sql`（编号取当下最大编号 +1）
2. 写 DDL。**不要**在里面写 `CREATE DATABASE` 或 `USE` —— 连接目标由调用方决定
3. 提交，然后对每个环境跑 `migrate.sh`
4. 跑 `fingerprint.sh` 确认各端指纹一致

迁移文件不需要自己往 `schema_migrations` 写记录，运行器会写；写了也不会冲突（用 `ON DUPLICATE KEY`）。

## 全新安装

`vvv.sql` / `vvv1.sql` 是完整快照（结构 + 数据），供全新环境使用：

```bash
mysql -h <host> -u <user> -p -e "CREATE DATABASE vvv CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -h <host> -u <user> -p vvv < vvv.sql
db/migrate.sh    # 补齐快照之后新增的迁移
```

这两个文件**刻意不含 `CREATE DATABASE` / `USE`**。以前有，结果是 `mysql 某个库 < vvv.sql`
会被文件自己切到 `vvv` 库、把目标库整个覆盖掉。

## 发布顺序

结构变更必须**先于**代码发布：新代码可能引用新列，而旧代码不会引用它，
所以「先加结构、再发代码」时线上是连续的；反过来新代码会在旧结构上直接报错。

这个顺序**由发布流程保证，不需要手动执行**：`deploy.yml` 把 `db/` 一起上传到服务器，
`/usr/local/sbin/v1rtual-deploy-backend` 在切换 `current` 之前运行 `db/migrate.sh`。
迁移失败就中止发布 —— 那时 `current` 还没切，旧版本继续服务。
迁移目录缺失、或读不到 `/etc/v1rtual/application-prod.yml`，同样拒绝发布。

```
发布后端：db/migrate.sh（自动）→ 切换 current → 重启服务 → 发布前端
```

与 `CICD规范.md` 的「后端接口变更时先发布后端并验证 API，再发布前端」一致。

连接参数从服务器上的 `/etc/v1rtual/application-prod.yml` 现读，密码既不进仓库也不进 CI。
回滚代码时把当前的 `db/` 一并 staging 过去即可：迁移是幂等的，已执行的会跳过；
**回滚代码不回滚结构**，这是刻意的。

## 已执行的迁移

| 版本 | 内容 | 本机开发库 | 生产库 |
| --- | --- | --- | --- |
| V001 | `gallery.client_upload_id`（上传幂等）+ `oss_cleanup_record` 表 | 2026-09-13 | 2026-09-13 |
