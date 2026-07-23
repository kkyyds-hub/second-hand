# Development Data Stores

This directory contains the complete local-development data-store setup for the second-hand platform. All fixture data is fictional.

## What It Starts

`compose.yaml` starts the dependencies that are not present on a fresh machine:

- Redis at `127.0.0.1:6379`, database `2`.
- MongoDB at `127.0.0.1:27017`, database `demo`.
- RabbitMQ at `127.0.0.1:5672`, with its management UI at `http://127.0.0.1:15672`.

The project uses the existing local MySQL instance at `127.0.0.1:3306`. MySQL 8.0 or newer is required. The rebuild script deliberately drops and recreates only the `secondhand2` database.

## First-time Setup

When the local MySQL administrator can connect through its default socket, the complete bootstrap command is:

```bash
bash scripts/bootstrap-dev.sh
```

For an administrator that requires TCP/password authentication, provide credentials only in the current shell:

```bash
MYSQL_ADMIN_PROTOCOL=tcp \
MYSQL_ADMIN_HOST=127.0.0.1 \
MYSQL_ADMIN_PORT=3306 \
MYSQL_ADMIN_USER=root \
MYSQL_ADMIN_PASSWORD='local-root-password' \
bash scripts/bootstrap-dev.sh
```

The script starts the container services, waits for health checks, rebuilds `secondhand2`, provisions the local application account, and seeds MongoDB. The individual commands below are useful when one part needs to be rerun separately.

From the repository root, start Redis, MongoDB, and RabbitMQ:

```bash
docker compose up -d
docker compose ps
```

Wait until all three containers report `healthy`. The default RabbitMQ development account is `secondhand_mq` / `secondhand_mq_2026`. Override it before first startup with `SECONDHAND_RABBITMQ_USER` and `SECONDHAND_RABBITMQ_PASSWORD`; changing those variables after its volume has been initialized does not change the stored RabbitMQ account.

Import the relational schema through a privileged local MySQL account. The command prompts for a password rather than storing it in the repository. A Homebrew MySQL installation with local socket root access can omit `-p`.

```bash
mysql -u root -p < database/00_rebuild_secondhand2.sql
mysql -u root -p < database/02_create_dev_app_user.sql
```

The second command creates the local application principal `secondhand_dev` with password `secondhand_dev_2026` for both `localhost` and `127.0.0.1`. It has privileges only on `secondhand2`. Override the account before sourcing the script when needed:

```sql
SET @app_user = 'another_dev_user';
SET @app_password = 'another_dev_password';
SOURCE database/02_create_dev_app_user.sql;
```

Initialize the MongoDB collection and fixture messages:

```bash
mongosh --file database/01_seed_mongodb.js
```

`00_rebuild_secondhand2.sql` is intentionally repeatable. Running it again clears and rebuilds `secondhand2`; run `02_create_dev_app_user.sql` again afterward only when the application account must be created or its password changed.

## Demo Logins

| Role | Mobile | Password |
| --- | --- | --- |
| Administrator | `13900000001` | `admin123` |
| Buyer | `13800000001` | `123456` |
| Seller | `13700000001` | `123456` |

Passwords are stored as BCrypt hashes in the MySQL seed, never as plaintext.

## Application Configuration

`demo-service/src/main/resources/application-dev.yml` points at the above local services. All connection values can be overridden without editing tracked files:

```bash
export DEMO_MYSQL_HOST=127.0.0.1
export DEMO_MYSQL_PORT=3306
export DEMO_MYSQL_DATABASE=secondhand2
export DEMO_MYSQL_USERNAME=secondhand_dev
export DEMO_MYSQL_PASSWORD=secondhand_dev_2026
export DEMO_REDIS_HOST=127.0.0.1
export DEMO_REDIS_PORT=6379
export DEMO_MONGODB_URI=mongodb://127.0.0.1:27017/demo
export DEMO_RABBITMQ_HOST=127.0.0.1
export DEMO_RABBITMQ_PORT=5672
export DEMO_RABBITMQ_USERNAME=secondhand_mq
export DEMO_RABBITMQ_PASSWORD=secondhand_mq_2026
```

Start the backend from the repository root after dependencies are healthy:

```bash
mvn -pl demo-service -am spring-boot:run
```

## Verification

Static validation does not need services:

```bash
bash database/verify_rebuild.sh --static
```

After MySQL is rebuilt, validate it through the application account:

```bash
MYSQL_HOST=127.0.0.1 \
MYSQL_PORT=3306 \
MYSQL_USER=secondhand_dev \
MYSQL_PWD=secondhand_dev_2026 \
bash database/verify_rebuild.sh --mysql
```

For a full reset of the containerized services, stop and remove their development volumes before starting them again:

```bash
docker compose down -v
docker compose up -d
```

This does not delete MySQL data. `database/00_rebuild_secondhand2.sql` is the only repository command that clears MySQL development data.

## Troubleshooting

- If MySQL rejects TCP login, first run the privileged account script using the local administrator connection shown above. Do not change the MySQL root authentication method for this project.
- If port `6379`, `27017`, `5672`, or `15672` is occupied, stop the existing local service or override the Compose host port before startup and set the matching `DEMO_*` variable.
- If `mongosh` is unavailable on the host, run `docker compose exec mongodb mongosh --file /dev/stdin < database/01_seed_mongodb.js`.
