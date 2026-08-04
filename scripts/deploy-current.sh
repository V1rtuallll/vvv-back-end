#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" != "" && "${1:-}" != "--watch" ]]; then
  echo "Usage: $0 [--watch]" >&2
  exit 2
fi

command -v gh >/dev/null || { echo "GitHub CLI (gh) is required." >&2; exit 1; }
branch="$(git branch --show-current)"
revision="$(git rev-parse HEAD)"

if [[ -z "$branch" ]]; then
  echo "Deploy from a named branch, not a detached HEAD." >&2
  exit 1
fi

if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "Commit or stash local changes before deployment." >&2
  exit 1
fi

git push origin "$branch"
gh workflow run deploy.yml --ref "$branch"
echo "Backend deployment dispatched for $branch ($revision)."

if [[ "${1:-}" == "--watch" ]]; then
  sleep 2
  run_id="$(gh run list --workflow deploy.yml --branch "$branch" --commit "$revision" --limit 1 --json databaseId --jq '.[0].databaseId')"
  test -n "$run_id"
  gh run watch "$run_id" --exit-status
fi
