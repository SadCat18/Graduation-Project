# SkateHub Backup, Restore, and Rollback

## Scope

Back up these production assets before every release and on a daily schedule:

- MySQL database.
- Redis data volume or AOF file.
- `upload/` persistent file directory.
- The deployed commit sha and image tags.

## MySQL Backup

Run from the server with values from `deploy/docker/.env`:

```bash
set -a
. deploy/docker/.env
set +a
mkdir -p backups/mysql
docker exec se1-mysql mysqldump \
  -uroot -p"${MYSQL_ROOT_PASSWORD}" \
  --single-transaction --routines --triggers \
  "${MYSQL_DATABASE}" > "backups/mysql/${MYSQL_DATABASE}-$(date +%Y%m%d-%H%M%S).sql"
```

Keep at least 7 daily backups and 4 weekly backups. Store one copy outside the CVM.

## MySQL Restore Drill

Restore only into a disposable test database first:

```bash
docker exec -i se1-mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" \
  -e "CREATE DATABASE IF NOT EXISTS skatehub_restore_test DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
docker exec -i se1-mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" skatehub_restore_test \
  < backups/mysql/skate_exchange-YYYYMMDD-HHMMSS.sql
```

After validation, restore production during a maintenance window and only after stopping writes.

## Upload Directory Backup

```bash
mkdir -p backups/upload
tar -czf "backups/upload/upload-$(date +%Y%m%d-%H%M%S).tar.gz" upload
```

Restore with:

```bash
tar -xzf backups/upload/upload-YYYYMMDD-HHMMSS.tar.gz
```

## Redis Backup

Redis runs with AOF enabled. Back up the Docker volume regularly:

```bash
mkdir -p backups/redis
docker run --rm \
  -v se1_redis_data:/data:ro \
  -v "$(pwd)/backups/redis:/backup" \
  alpine tar -czf "/backup/redis-$(date +%Y%m%d-%H%M%S).tar.gz" -C /data .
```

## Release Rollback

Record the commit sha before deployment:

```bash
git rev-parse HEAD
```

Rollback steps:

1. Stop writes or put the service in maintenance mode.
2. Restore the previous image tag or checkout the previous commit sha.
3. Restore MySQL only if the failed release ran destructive migrations.
4. Restore `upload/` only if the release modified file storage behavior.
5. Start services and verify `/actuator/health`.

Flyway migrations are forward-only by default. Any destructive migration must include a separate restore plan and a verified database backup.

## Monitoring Checks

- `/actuator/health` may be proxied through Nginx.
- `/actuator/prometheus` is enabled in the backend but must not be exposed publicly.
- Review backend logs for authentication failures, upload failures, rate-limit hits, and AI call failures.
