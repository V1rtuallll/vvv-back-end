---
name: v1rtual-backend-cicd
description: Build, validate, and manually deploy the currently checked-out branch of the V1rtual Spring Boot backend through GitHub Actions, SSH, and systemd. Use for backend CI/CD changes, production configuration, release validation, or rollback.
---

# V1rtual Backend CI/CD

Deploy this repository's Spring Boot backend as an executable JAR controlled by systemd. Never place credentials in Git, artifacts, workflow logs, or generated configuration.

## Release Model

- Every branch push and pull request runs `.github/workflows/ci.yml`; CI builds the JAR but never changes the server.
- `.github/workflows/deploy.yml` is manual only. When asked to deploy, use the currently checked-out branch: `branch="$(git branch --show-current)"`; push it first, then run `gh workflow run deploy.yml --ref "$branch"`.
- Do not deploy an uncommitted working tree. GitHub Actions checks out the pushed commit selected by `--ref`.
- The frontend is a separate repository and has its own deployment workflow. Deploy it separately when both halves of a site branch must change.
- Upload an artifact to `/tmp`, verify it, copy it into a revisioned release directory, then atomically update `current`. Retain the three newest releases. Never delete `current` before a verified replacement exists.
- Roll back by repointing `current` to a previous release and reloading Nginx or restarting systemd.

## Before Generating Files

Collect these values. Stop before a production deploy if any mandatory value is unavailable.

| Value | Purpose |
| --- | --- |
| `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_PORT` | SSH destination |
| `SSH_PRIVATE_KEY`, `SSH_KNOWN_HOSTS` | GitHub Actions secrets |
| `BACKEND_ROOT` | Release root: `/www/wwwroot/vvv-back-end` |
| `JAVA_BIN` | Java 21 executable on the server |
| `/etc/v1rtual/application-prod.yml` | Server-only backend configuration |

Read [server-contract.md](references/server-contract.md) before changing deployment files. A deployable branch must contain `.github/workflows/deploy.yml`; create new site branches from a current branch that already has it.

## Installation Order

1. Create the server deploy user, release directories, and `/etc/v1rtual/application-prod.yml` with mode `0600`.
2. Install the systemd service from `assets/server/`.
3. Install the backend deploy script under `/usr/local/sbin/` and restrict its sudo permissions to the deploy user.
4. Add `assets/workflows/deploy.yml` as `.github/workflows/deploy.yml`.
5. Add GitHub Actions secrets. Use `SSH_KNOWN_HOSTS`, not blind `ssh-keyscan` during deployment.
6. Run `workflow_dispatch` for a chosen branch, then verify the backend endpoint. Keep deployment manual; all pushes already run CI.

## Backend Configuration

Do not create a production configuration file in the repository. Copy `assets/server/application-prod.yml.example` to `/etc/v1rtual/application-prod.yml`, replace placeholders, and set owner to the service account with mode `0600`. The systemd unit loads it via `--spring.config.additional-location`.

## Verification And Rollback

- Backend: verify the JAR is non-empty, restart `spring_V1rtual.service`, check `systemctl is-active`, then request a configured health URL. Do not assume Actuator is publicly reachable.
- Rollback backend: `sudo /usr/local/sbin/v1rtual-deploy-backend <previous-revision>`.

Run `scripts/validate-templates.sh` after editing this skill. It only validates bundled shell/YAML template structure; it does not contact a server.
