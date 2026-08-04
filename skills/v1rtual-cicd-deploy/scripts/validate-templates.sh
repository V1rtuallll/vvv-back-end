#!/usr/bin/env bash
set -euo pipefail

skill_dir="$(cd "$(dirname "$0")/.." && pwd)"

for script in "$skill_dir"/assets/server/v1rtual-deploy-*.sh; do
  bash -n "$script"
done

for workflow in "$skill_dir"/assets/workflows/*.yml; do
  rg -q '^name:' "$workflow"
  rg -q 'workflow_dispatch:' "$workflow"
  rg -q 'SSH_KNOWN_HOSTS' "$workflow"
done

rg -q 'application-prod.yml' "$skill_dir/assets/server/v1rtual-backend.service"
printf 'Deployment templates passed static validation.\n'
