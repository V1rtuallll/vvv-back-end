# 数据库迁移

本项目**没有 Flyway / Liquibase**，`.github/workflows/deploy.yml` 里也没有迁移步骤
（只做「构建 JAR → SSH 上传 → 重启 systemd」）。所以结构变更靠这里的脚本手工执行。

## 约定

- 文件名：`V<三位编号>__<英文短描述>.sql`，编号只增不改
- **已执行过的迁移文件不要修改**。要改就新加一个 `V00N`，否则线上与仓库记录会对不上
- 每个脚本自己往 `schema_migrations` 表里写一行记录，执行完可以查表确认
- 脚本会被重复执行时，先报的错通常就是「已经迁移过」，见每个文件头部的说明

## 查看某套库执行到哪一版

```sql
SELECT version, description, applied_at, applied_by
FROM schema_migrations ORDER BY version;
```

## 执行某个迁移

```bash
mysql -h <host> -u <user> -p <database> < V001__upload_idempotency_and_oss_cleanup.sql
```

## 与发布顺序的关系

结构变更通常**必须先于代码发布**：新代码可能引用新列，而旧代码不会引用它，
所以「先加结构、再发代码」时线上是连续的；反过来会让新代码在旧结构上直接报错。

本次（V001）的顺序是：

```
① 执行 V001 → ② 发布后端 → ③ 发布前端
```

前端必须最后发：它依赖新的单文件上传接口与 `/api/gallery/upload-limit`。
这与 `CICD规范.md` 的「后端接口变更时先发布后端并验证 API，再发布前端」一致。

## 已执行的迁移

| 版本 | 内容 | 本地开发库 | 生产库 |
| --- | --- | --- | --- |
| V001 | `gallery.client_upload_id`（上传幂等）+ `oss_cleanup_record` 表 | 2026-09-13 | 2026-09-13 |

> 本地开发库的这两处结构是在开发过程中直接建好的，改用本目录管理之后补记于此。
