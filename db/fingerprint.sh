#!/usr/bin/env bash
#
# 数据库结构指纹
#
# 用途：验证「所有端结构一致」这条不变量。在每套库上跑一次，比对输出的指纹即可。
# 应用迁移之后跑一次，就能确认各端收敛到了同一个结构。
#
#   db/fingerprint.sh                                  # 本机开发库
#   DB_HOST=<生产主机> DB_USER=vvv MYSQL_PWD=xxx db/fingerprint.sh
#   db/fingerprint.sh --full                           # 顺便打印归一化后的完整清单，便于 diff
#
# 跨版本归一化：本机开发库是 MySQL 8、生产是 MySQL 5.6，两者对整型的显示宽度
# （bigint(20) 与 bigint）和 enum 的逗号后空格写法不同，但语义完全一样。
# 指纹会把这些差异抹平，只保留真正影响结构的部分。
#
set -euo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-vvv}"
DB_USER="${DB_USER:-root}"
SHOW_FULL=0
CONN_ARGS=()

for arg in "$@"; do
  case "$arg" in
    --full) SHOW_FULL=1 ;;
    --defaults-file=*) CONN_ARGS+=("--defaults-file=${arg#*=}") ;;
    -h|--help) sed -n '2,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "未知参数：$arg" >&2; exit 2 ;;
  esac
done

if [ ${#CONN_ARGS[@]} -eq 0 ]; then
  CONN_ARGS=(-h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER")
fi

mysql_db() { mysql "${CONN_ARGS[@]}" --batch --skip-column-names "$DB_NAME" "$@"; }

TABLES_SQL="
SELECT CONCAT_WS('|', TABLE_NAME, ENGINE, TABLE_COLLATION)
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME;"

COLUMNS_SQL="
SELECT CONCAT_WS('|', TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE,
                 IFNULL(COLUMN_DEFAULT, '<null>'), COLUMN_KEY, IFNULL(COLLATION_NAME, '<none>'))
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME, COLUMN_NAME;"

INDEXES_SQL="
SELECT CONCAT_WS('|', TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME)
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;"

FKS_SQL="
SELECT CONCAT_WS('|', TABLE_NAME, CONSTRAINT_NAME, COLUMN_NAME,
                 REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME)
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL
ORDER BY TABLE_NAME, CONSTRAINT_NAME, COLUMN_NAME;"

normalize() {
  # 1) 去掉整型显示宽度：bigint(20) → bigint
  # 2) enum/set 的逗号后空格统一去掉
  # 3) 折叠多余空格
  sed -E \
    -e 's/(bigint|int|smallint|tinyint|mediumint)\([0-9]+\)/\1/g' \
    -e "s/, '/','/g" \
    -e 's/[[:space:]]+/ /g' \
    | sed -E 's/[[:space:]]+$//'
}

# 排序交给本机的 LC_ALL=C，不用数据库的排序规则：MySQL 8 的 utf8mb4_0900_ai_ci 与
# 5.6 的 utf8mb4_unicode_ci 对下划线和字母的先后判断不同，会让同构的两套库指纹对不上。
sort_sections() { LC_ALL=C sort; }

NORMALIZED="$(
  {
    echo "# tables"
    mysql_db -e "$TABLES_SQL" | normalize | sort_sections
    echo "# columns"
    mysql_db -e "$COLUMNS_SQL" | normalize | sort_sections
    echo "# indexes"
    mysql_db -e "$INDEXES_SQL" | normalize | sort_sections
    echo "# foreign keys"
    mysql_db -e "$FKS_SQL" | normalize | sort_sections
  }
)"

# macOS 是 shasum、Linux 是 sha256sum，两边都要能跑
HASH="$(
  if command -v sha256sum >/dev/null 2>&1; then
    printf '%s\n' "$NORMALIZED" | sha256sum | awk '{print $1}'
  else
    printf '%s\n' "$NORMALIZED" | shasum -a 256 | awk '{print $1}'
  fi
)"
TABLES="$(mysql_db -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE();")"

echo "库：$DB_NAME @ ${DB_HOST}:${DB_PORT}"
echo "表数：$TABLES"
echo "结构指纹：$HASH"

if [ "$SHOW_FULL" = "1" ]; then
  echo
  echo "--- 归一化结构清单 ---"
  printf '%s\n' "$NORMALIZED"
fi
