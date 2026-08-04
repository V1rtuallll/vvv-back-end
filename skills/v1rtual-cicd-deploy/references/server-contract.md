# Server Contract

## Required Layout

```
/srv/v1rtual/frontend/releases/<git-sha>/
/srv/v1rtual/frontend/current -> releases/<git-sha>
/srv/v1rtual/backend/releases/<git-sha>/app.jar
/srv/v1rtual/backend/current -> releases/<git-sha>
/etc/v1rtual/application-prod.yml
```

Point Nginx at `/srv/v1rtual/frontend/current`. Start the backend through `/srv/v1rtual/backend/current/app.jar`. A release is immutable after `current` switches to it.

## Server Accounts

Use a non-login `v1rtual` service account for the backend process and a constrained `deploy` account for CI SSH. Let `deploy` run only these commands through sudo:

```
/usr/local/sbin/v1rtual-deploy-frontend *
/usr/local/sbin/v1rtual-deploy-backend *
/bin/systemctl restart v1rtual-backend
/bin/systemctl status v1rtual-backend
/usr/sbin/nginx -t
/bin/systemctl reload nginx
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

Set repository environment protection on `production` to require approval before deployment when the site becomes important enough to need a manual gate.

## Production Configuration

`/etc/v1rtual/application-prod.yml` must contain production MySQL, OSS, and JWT settings. It is neither committed nor copied by Actions. Keep an example in source only. Before the first deploy, ensure the database schema exists and the configured Java version is 21.

## Expected Triggers

The workflows include `workflow_dispatch` and an optional `push` trigger for `main`. Start with manual dispatch. Once proven, uncomment `push.branches: [main]` to make merge/push to `main` deploy automatically.
