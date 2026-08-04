#!/usr/bin/env bash
set -euo pipefail

revision="${1:?revision is required}"
root="/www/wwwroot/vvv-back-end"
stage="/tmp/v1rtual-backend-${revision}.jar"
release="${root}/releases/${revision}"

case "$revision" in
  *[!A-Za-z0-9._-]*|'') echo "invalid revision" >&2; exit 2 ;;
esac

test -s "$stage"
install -d -o www -g www -m 0755 "${root}/releases"
test ! -e "$release"
install -d -o www -g www -m 0755 "$release"
install -o www -g www -m 0644 "$stage" "${release}/app.jar"
test -s "${release}/app.jar"

ln -s "releases/${revision}" "${root}/current.next"
mv -Tf "${root}/current.next" "${root}/current"
rm -f "$stage"

systemctl restart spring_V1rtual.service
systemctl is-active --quiet spring_V1rtual.service
current="$(readlink -f "${root}/current")"
find "${root}/releases" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\n' | sort -nr | tail -n +4 | cut -d' ' -f2- | while IFS= read -r old_release; do
  test "$old_release" = "$current" || rm -rf "$old_release"
done
