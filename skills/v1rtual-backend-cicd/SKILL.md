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
- The frontend is a separate repository and has its own deployment workflow. A backend branch carries the bare major name (`V1rtualSS`); frontend variants that share it append a style suffix (`V1rtualSS_sky`). Deploy both separately when the site and its API must change together.
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
   It runs `db/migrate.sh` from the uploaded `db/` directory **before** switching `current`, reading
   connection parameters from `/etc/v1rtual/application-prod.yml`. A missing `db/` directory or an
   unreadable production config aborts the release.
4. Add `assets/workflows/deploy.yml` as `.github/workflows/deploy.yml`. It uploads `app.jar` and `db/`.
5. Add GitHub Actions secrets. Use `SSH_KNOWN_HOSTS`, not blind `ssh-keyscan` during deployment.
6. Run `workflow_dispatch` for a chosen branch, then verify the backend endpoint. Keep deployment manual; all pushes already run CI.

`assets/` holds copies of files that live elsewhere (the workflow and the server-side script).
Keep them byte-identical to their deployed counterparts — nothing checks this automatically,
and the two drifted apart once already.

## Backend Configuration

Do not create a production configuration file in the repository. Copy `assets/server/application-prod.yml.example` to `/etc/v1rtual/application-prod.yml`, replace placeholders, and set owner to the service account with mode `0600`. The systemd unit loads it via `--spring.config.additional-location`.

## Verification And Rollback

- Backend: verify the JAR is non-empty, restart `spring_V1rtual.service`, check `systemctl is-active`, then request a configured health URL. Do not assume Actuator is publicly reachable.
- Rollback backend: stage the previous revision's JAR **and the current `db/` directory**, then run
  the deploy script. Migrations are idempotent, so the migration step is a no-op; rolling the code
  back deliberately does not roll the schema back.

  ```bash
  scp app.jar <host>:/tmp/v1rtual-backend-<previous-revision>.jar
  scp -r db <host>:/tmp/v1rtual-backend-<previous-revision>-db
  ssh <host> 'sudo /usr/local/sbin/v1rtual-deploy-backend <previous-revision>'
  ```

Run `scripts/validate-templates.sh` after editing this skill. It only validates bundled shell/YAML template structure; it does not contact a server.
