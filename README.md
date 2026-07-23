# Second-hand Platform

Local development setup for the Spring Boot second-hand marketplace.

## Quick Start

Prerequisites: Docker Desktop, MySQL 8.0+ running locally, and the MySQL client. The bootstrap script uses local socket root access by default.

```bash
git clone https://github.com/kkyyds-hub/second-hand.git
cd second-hand
bash scripts/bootstrap-dev.sh
```

The command starts Redis, MongoDB, and RabbitMQ through Docker Compose; rebuilds the local `secondhand2` database; creates the least-privileged development MySQL account; and inserts MySQL/MongoDB fixture data.

Start the backend after initialization:

```bash
mvn -pl demo-service -am spring-boot:run
```

The backend uses `http://127.0.0.1:8080`. RabbitMQ Management is available at `http://127.0.0.1:15672` with `secondhand_mq` / `secondhand_mq_2026`.

## Demo Accounts

| Role | Mobile | Password |
| --- | --- | --- |
| Administrator | `13900000001` | `admin123` |
| Buyer | `13800000001` | `123456` |

All setup details, override variables, and validation commands are in [database/README.md](database/README.md). The development rebuild is destructive only to the local `secondhand2` database.
