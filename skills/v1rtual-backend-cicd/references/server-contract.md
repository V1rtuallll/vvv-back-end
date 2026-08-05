# Server Contract

## Required Layout

```
/www/wwwroot/vvv-back-end/releases/<git-sha>/app.jar
/www/wwwroot/vvv-back-end/current -> releases/<git-sha>
/etc/v1rtual/application-prod.yml
```

Start the backend through `/www/wwwroot/vvv-back-end/current/app.jar`. A release is immutable after `current` switches to it.

## Server Accounts

Use the existing `www` service account for the backend process and a constrained `deploy` account for CI SSH. Let `deploy` run only this command through sudo:

```
/usr/local/sbin/v1rtual-deploy-backend *
```

Adapt binary paths to the target distribution. Do not grant unrestricted passwordless sudo.

## GitHub Secrets

| Secret | Description |
| --- | --- |
| `DEPLOY_HOST` | Server address |
| `DEPLOY_USER` | Restricted SSH deploy user |
| `DEPLOY_SSH_PORT` | SSH port |
| `SSH_PRIVATE_KEY` | Dedicated deploy key |
| `SSH_KNOWN_HOSTS` | Pinned server host key line |
| `DEPLOY_HEALTH_URL` | Optional backend health URL, only if safely reachable |

The repository's `production` Environment may be used for visibility, but the existing credentials are repository secrets. A manual workflow dispatch is the deployment gate.

## Production Configuration

`/etc/v1rtual/application-prod.yml` must contain production MySQL, OSS, and JWT settings. It is neither committed nor copied by Actions. Keep an example in source only. Before the first deploy, ensure the database schema exists and the configured Java version is 21.

The backend listener must match the Nginx `/api/` reverse-proxy target. At the verified personal-site deployment, both use `127.0.0.1:8080`. Local development may use a different listener, currently `8848`; do not change the production listener through the frontend Vite configuration.

## Expected Triggers

CI runs on every branch push and pull request. Deploy is `workflow_dispatch` only: select the branch that represents the complete site you want on the server.
