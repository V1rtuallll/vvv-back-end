#!/usr/bin/env bash
set -euo pipefail
revision="${1:?revision is required}"
root=/www/wwwroot/vvv-back-end
stage="/tmp/v1rtual-backend-${revision}.jar"
release="$root/releases/$revision"
case "$revision" in *[!A-Za-z0-9._-]*|'') exit 2;; esac
test -s "$stage"
install -d -o www -g www -m 0755 "$root/releases"
if test ! -e "$release"; then
  install -d -o www -g www -m 0755 "$release"
  install -o www -g www -m 0644 "$stage" "$release/app.jar"
fi
test -s "$release/app.jar"

# ---------------------------------------------------------------------------
# 数据库迁移：必须排在切换 current 之前。
#
# 新代码可能引用新列，旧代码不会引用它，所以「先结构、后代码」线上是连续的；
# 反过来会让新代码直接跑在旧结构上报错。把迁移放进发布脚本而不是让人手动跑，
# 是为了让这个顺序由代码保证，而不是靠记得。
#
# 迁移失败就在这里中止 —— current 还没切，旧版本仍在提供服务。
# 迁移器幂等：没有待执行的迁移时什么都不做，重复发布是安全的。
# ---------------------------------------------------------------------------
db_stage="/tmp/v1rtual-backend-${revision}-db"
# 迁移目录必须存在：宁可发布失败，也不要出现「代码换了、结构没跟上」的线上状态
if [ ! -d "$db_stage" ]; then
  echo "缺少迁移目录 ${db_stage}，拒绝在未迁移的情况下发布" >&2
  exit 1
fi

prod_cfg="/etc/v1rtual/application-prod.yml"
if [ ! -r "$prod_cfg" ]; then
  echo "读不到生产配置 ${prod_cfg}，无法迁移" >&2
  exit 1
fi

# 连接参数只存在于服务器上，既不进仓库也不进 CI。
# 清理顺序有讲究：先去 \r（生产配置是 CRLF 行尾，不去掉会让密码多一个字符、连接必然失败），
# 再去空白，最后去引号并再修一次尾部空白。
# 刻意不剥 # 注释：密码里可能就有 #，剥了会静默改坏它；真带注释时报连接失败更安全。
datasource_block="$(sed -n '/^ *datasource:/,/^[A-Za-z]/p' "$prod_cfg")"
datasource_value() {
  printf '%s\n' "$datasource_block" \
    | grep -m1 -E "^ *$1:" \
    | sed -E "s/^ *$1: *//; s/\r//g; s/^[[:space:]]+//; s/[[:space:]]+\$//; s/^[\"']//; s/[\"']\$//; s/[[:space:]]+\$//"
}
db_url="$(datasource_value url)"
DB_USER="$(datasource_value username)"
MYSQL_PWD="$(datasource_value password)"
db_target="${db_url#jdbc:mysql://}"
db_host_port="${db_target%%/*}"
db_name="${db_target#*/}"
DB_NAME="${db_name%%\?*}"
DB_HOST="${db_host_port%%:*}"
if [ "$db_host_port" = "$DB_HOST" ]; then
  DB_PORT=3306
else
  DB_PORT="${db_host_port##*:}"
fi
export DB_NAME DB_HOST DB_PORT DB_USER MYSQL_PWD

if [ -z "$DB_NAME" ] || [ -z "$DB_HOST" ] || [ -z "$DB_USER" ]; then
  echo "从 ${prod_cfg} 解析数据库连接参数失败，拒绝在未迁移的情况下发布" >&2
  exit 1
fi

echo "==> 执行数据库迁移（${DB_NAME} @ ${DB_HOST}）"
bash "${db_stage}/migrate.sh"
rm -rf "$db_stage"
unset MYSQL_PWD

ln -s "releases/$revision" "$root/current.next"
mv -Tf "$root/current.next" "$root/current"
rm -f "$stage"
systemctl restart spring_V1rtual.service
systemctl is-active --quiet spring_V1rtual.service
current="$(readlink -f "$root/current")"
find "$root/releases" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\n' | sort -nr | tail -n +4 | cut -d' ' -f2- | while IFS= read -r old; do test "$old" = "$current" || rm -rf "$old"; done
