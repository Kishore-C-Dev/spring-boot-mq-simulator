# MQ Simulator Admin Console

Web UI and REST API for configuring queues, response mappings, users, and namespaces. This service has no IBM MQ dependency — it manages configuration in PostgreSQL and notifies MQ Mock Server instances to refresh their listeners.

## Prerequisites

- Java 17+
- Maven 3.6+
- PostgreSQL database

## Build

```bash
mvn clean package -DskipTests
```

## Run

```bash
# Via Maven
mvn spring-boot:run

# Via JAR
java -jar target/mq-simulator-admin-console-1.0.0.jar
```

Access at http://localhost:8080

## Docker

```bash
docker build -t mqsim-admin-console .
docker run -p 8080:8080 \
  -e DATABASE_URL=jdbc:postgresql://host:5432/mqsim \
  -e DATABASE_USERNAME=mqsim \
  -e DATABASE_PASSWORD=mqsim \
  -e MOCK_SERVER_URLS=http://mock-server:8090 \
  mqsim-admin-console
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/appdb` | PostgreSQL JDBC URL |
| `DATABASE_USERNAME` | `appuser` | Database username |
| `DATABASE_PASSWORD` | `apppass` | Database password |
| `MOCK_SERVER_URLS` | `http://localhost:8090` | Comma-separated MQ Mock Server URLs for refresh notifications |

## Default Credentials

| User | Password | Role |
|------|----------|------|
| `admin` | `admin123` | Admin |
| `demo` | `demo123` | User |

## API Reference

### Queue Management

```bash
# List all queues
curl http://localhost:8080/admin/queues

# Create a queue
curl -X POST http://localhost:8080/admin/queues \
  -H "Content-Type: application/json" \
  -d '{
    "queueName": "SIM.REQUEST.NEW",
    "namespace": "default",
    "concurrency": "1-2",
    "enabled": true
  }'

# Refresh mock server listeners
curl -X POST http://localhost:8080/admin/queues/refresh

# Get listener status from mock servers
curl http://localhost:8080/admin/queues/status
```

### Response Mapping Management

```bash
# List all mappings
curl http://localhost:8080/admin/mappings

# Create a response mapping
curl -X POST http://localhost:8080/admin/mappings \
  -H "Content-Type: application/json" \
  -d '{
    "queueName": "SIM.REQUEST.Q1",
    "namespace": "default",
    "priority": 1,
    "enabled": true,
    "match": {
      "correlationId": "^TEST-.*",
      "headers": {"X-Environment": "test"}
    },
    "response": {
      "type": "JSON",
      "jsonSuccessBody": "{\"status\":\"success\"}",
      "jsonErrorBody": "{\"status\":\"error\"}",
      "correlationIdConfig": "USE_REQUEST_MESSAGE_ID"
    },
    "delay": {
      "mode": "VARIABLE",
      "variableMinMs": 50,
      "variableMaxMs": 200
    }
  }'
```

### Health Check

```bash
curl http://localhost:8080/health
```

## Web UI

| Page | URL |
|------|-----|
| Dashboard | http://localhost:8080/ui |
| Admin Panel | http://localhost:8080/admin/ui |
