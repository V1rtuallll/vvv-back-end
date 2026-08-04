---
name: v1rtual-cicd-deploy
description: Build, publish, and operate the V1rtual Vue/Vite frontend and Spring Boot backend without containers. Use when creating or changing GitHub Actions, Nginx, systemd, SSH/rsync releases, production configuration, rollbacks, or deployment validation for this two-repository application.
---

# V1rtual CI/CD Deploy

Deploy the Vue frontend as a Vite `dist` release served by Nginx. Deploy the Spring Boot backend as an executable JAR controlled by systemd. Treat `main` as the only production deployment branch. Never place credentials in Git, artifacts, workflow logs, or generated configuration.

## Release Model

- Frontend and backend are separate repositories and have separate workflows. A push to a repository's `main` deploys only that repository.
- Use an explicit `workflow_dispatch` input for a paired release. Do not make every frontend change restart the backend or vice versa.
- Upload an artifact to `/tmp`, verify it, copy it into a revisioned release directory, then atomically update `current`. Retain the three newest releases. Never delete `current` before a verified replacement exists.
- Roll back by repointing `current` to a previous release and reloading Nginx or restarting systemd.

## Before Generating Files

Collect these values. Stop before a production deploy if any mandatory value is unavailable.

| Value | Purpose |
| --- | --- |
| `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_PORT` | SSH destination |
| `SSH_PRIVATE_KEY`, `SSH_KNOWN_HOSTS` | GitHub Actions secrets |
| `DOMAIN_NAME` | Nginx `server_name` |
| `FRONTEND_ROOT`, `BACKEND_ROOT` | Release roots, default `/srv/v1rtual/frontend` and `/srv/v1rtual/backend` |
| `JAVA_BIN` | Java 21 executable on the server |
| `/etc/v1rtual/application-prod.yml` | Server-only backend configuration |

Read [server-contract.md](references/server-contract.md) before adding deployment files. Copy the relevant files from `assets/` into the target repositories, replace every `__PLACEHOLDER__`, and review the resulting diff before enabling `main` deployment.

## Installation Order

1. Create the server deploy user, release directories, and `/etc/v1rtual/application-prod.yml` with mode `0600`.
2. Install the Nginx virtual host and systemd service from `assets/server/`; validate Nginx before reloading.
3. Install the two server-side deploy scripts under `/usr/local/sbin/` and restrict their sudo permissions to the deploy user.
4. Add the relevant workflow from `assets/workflows/` to each repository as `.github/workflows/deploy.yml`.
5. Add GitHub Actions secrets. Use `SSH_KNOWN_HOSTS`, not blind `ssh-keyscan` during deployment.
6. Run `workflow_dispatch` first, verify the live frontend and backend health endpoint, then enable the `push` trigger for `main`.

## Backend Configuration

Do not create a production configuration file in the repository. Copy `assets/server/application-prod.yml.example` to `/etc/v1rtual/application-prod.yml`, replace placeholders, and set owner to the service account with mode `0600`. The systemd unit loads it via `--spring.config.additional-location`.

## Verification And Rollback

- Frontend: verify `index.html` exists in the release, run `nginx -t`, reload Nginx, then request the public URL.
- Backend: verify the JAR is non-empty, restart `v1rtual-backend`, check `systemctl is-active`, then request a configured health URL. Do not assume Actuator is publicly reachable.
- Rollback frontend: `sudo /usr/local/sbin/v1rtual-deploy-frontend <previous-revision>`.
- Rollback backend: `sudo /usr/local/sbin/v1rtual-deploy-backend <previous-revision>`.

Run `scripts/validate-templates.sh` after editing this skill. It only validates bundled shell/YAML template structure; it does not contact a server.
