#!/usr/bin/env bash
#
# 数据库迁移运行器
#
# 不变量（这套东西存在的唯一理由）：
#   任何环境、任何起始状态，按版本号顺序执行完所有未执行的迁移之后，
#   结构必须完全一致。用 db/fingerprint.sh 对比各端即可验证。
#
# 三个保证：
#   1. 只增不改 —— 已执行过的迁移文件被改动（校验和变了）会直接报错中止
#   2. 按版本号顺序 —— 文件名 V<数字>__描述.sql，严格按数字升序执行
#   3. 幂等 —— 已记录的版本不会重复执行；重复跑是安全的
#
# 用法：
#   db/migrate.sh                      # 用默认连接（127.0.0.1 / root / vvv）
#   db/migrate.sh --dry-run            # 只报告会执行什么，不落任何改动
#   db/migrate.sh --defaults-file=/path/to/my.cnf   # 连接参数从这个文件读
#
# 连接参数也可用环境变量覆盖：
#   DB_HOST DB_PORT DB_NAME DB_USER MYSQL_PWD
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MIGRATIONS_DIR="$SCRIPT_DIR/migrations"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-vvv}"
DB_USER="${DB_USER:-root}"
DRY_RUN=0
CONN_ARGS=()

for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=1 ;;
    --defaults-file=*) CONN_ARGS+=("--defaults-file=${arg#*=}") ;;
    -h|--help)
      sed -n '2,30p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *) echo "未知参数：${arg}（用 --help 看用法）" >&2; exit 2 ;;
  esac
done

if [ ${#CONN_ARGS[@]} -eq 0 ]; then
  CONN_ARGS=(-h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER")
fi

# MYSQL_PWD 由客户端原生读取，避免把密码写进命令行被 ps 看到
mysql_db() { mysql "${CONN_ARGS[@]}" --batch --skip-column-names "$DB_NAME" "$@"; }

# macOS 是 shasum、Linux 是 sha256sum，两边都要能跑
sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

if ! mysql_db -e "SELECT 1;" >/dev/null 2>&1; then
  echo "❌ 连不上数据库 ${DB_NAME}（host=${DB_HOST} user=${DB_USER}）。" >&2
  echo "   检查连接参数，或改用 --defaults-file=<含 [client] 段的配置文件>。" >&2
  exit 1
fi

echo "目标库：$DB_NAME @ ${DB_HOST}:${DB_PORT}（用户 ${DB_USER}）"
[ "$DRY_RUN" = "1" ] && echo "模式：dry-run（不会改动任何东西）"
echo

# ---------------------------------------------------------------------------
# 记录表：属于迁移系统自身的基础设施，由运行器自己保证形状。
# 它不写成版本化迁移，否则会出现「要记录迁移得先有记录表」的循环。
# ---------------------------------------------------------------------------
SCHEMA_MIGRATIONS_DDL="
CREATE TABLE IF NOT EXISTS \`schema_migrations\` (
    \`version\` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '迁移编号，例如 V001',
    \`description\` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '这次迁移做了什么',
    \`checksum\` char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '迁移文件的 SHA-256，用于锁定已执行的内容',
    \`applied_at\` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
    \`applied_by\` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '执行者（数据库账号@主机）',
    PRIMARY KEY (\`version\`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '已执行的数据库迁移'"

if [ "$DRY_RUN" = "0" ]; then
  mysql_db -e "$SCHEMA_MIGRATIONS_DDL"
  # 早期版本建的表没有 checksum 列，补上（列已存在时报错可忽略）
  mysql_db -e "ALTER TABLE \`schema_migrations\`
      ADD COLUMN \`checksum\` char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL
      COMMENT '迁移文件的 SHA-256，用于锁定已执行的内容' AFTER \`description\`" 2>/dev/null || true
fi

# ---------------------------------------------------------------------------
# 逐个处理迁移文件
# ---------------------------------------------------------------------------
declare -a APPLIED_VERSIONS=() APPLIED_CHECKSUMS=()
# 读已执行列表在 dry-run 下也要做，否则会把已执行的迁移误报成「待执行」
while IFS=$'\t' read -r v c; do
  [ -n "${v:-}" ] && APPLIED_VERSIONS+=("$v") && APPLIED_CHECKSUMS+=("${c:-}")
done < <(mysql_db -e "SELECT version, IFNULL(checksum,'') FROM schema_migrations;" 2>/dev/null || true)

is_applied() {
  local target="$1" i
  for i in "${!APPLIED_VERSIONS[@]}"; do
    [ "${APPLIED_VERSIONS[$i]}" = "$target" ] && return 0
  done
  return 1
}
recorded_checksum() {
  local target="$1" i
  for i in "${!APPLIED_VERSIONS[@]}"; do
    [ "${APPLIED_VERSIONS[$i]}" = "$target" ] && { echo "${APPLIED_CHECKSUMS[$i]}"; return; }
  done
  echo ""
}

shopt -s nullglob
FILES=("$MIGRATIONS_DIR"/V*.sql)
shopt -u nullglob

if [ ${#FILES[@]} -eq 0 ]; then
  echo "migrations/ 下没有 V*.sql，无事可做。"
  exit 0
fi

NEW_COUNT=0
for file in $(printf '%s\n' "${FILES[@]}" | sort -V); do
  name="$(basename "$file")"
  version="${name%%__*}"
  description="$(printf '%s' "${name#*__}" | sed 's/\.sql$//; s/_/ /g')"
  checksum="$(sha256_of "$file")"

  if is_applied "$version"; then
    recorded="$(recorded_checksum "$version")"
    if [ -n "$recorded" ] && [ "$recorded" != "$checksum" ]; then
      echo "❌ $version 的内容与执行时不一致。" >&2
      echo "   已执行过的迁移不允许修改——否则各端结构会永久分叉。" >&2
      echo "   记录校验和：$recorded" >&2
      echo "   当前文件：  $checksum" >&2
      echo "   需要改动就新增一个更高编号的迁移文件。" >&2
      exit 1
    fi
    if [ "$DRY_RUN" = "0" ] && [ -z "$recorded" ]; then
      mysql_db -e "UPDATE schema_migrations SET checksum='$checksum' WHERE version='$version' AND (checksum IS NULL OR checksum='');"
      echo "  锁定 ${version}（补记校验和）"
    else
      echo "  跳过 ${version}（已执行）"
    fi
    continue
  fi

  if [ "$DRY_RUN" = "1" ]; then
    echo "  待执行 $version —— $description"
    NEW_COUNT=$((NEW_COUNT + 1))
    continue
  fi

  echo "  执行 $version —— $description"
  mysql "${CONN_ARGS[@]}" "$DB_NAME" < "$file"
  # 迁移文件可以自己写记录，也可以不写；这里统一补一条，重复键则只补校验和
  mysql_db -e "INSERT INTO schema_migrations (version, description, checksum, applied_by)
               VALUES ('$version', '$description', '$checksum', CONCAT(USER(), '@', @@hostname))
               ON DUPLICATE KEY UPDATE checksum = IFNULL(checksum, VALUES(checksum));"
  NEW_COUNT=$((NEW_COUNT + 1))
done

echo
if [ "$DRY_RUN" = "1" ]; then
  echo "dry-run 结束：$NEW_COUNT 个迁移待执行。"
else
  echo "完成：本次执行 $NEW_COUNT 个迁移。"
  echo "下一步可以用 db/fingerprint.sh 对比各端结构是否一致。"
fi
