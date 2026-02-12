# Spring Boot MQ Testing Simulator

A multi-module Spring Boot application for simulating IBM MQ message processing. It provides a web-based **Admin Console** for configuring queues and response mappings, and a headless **MQ Mock Server** that listens to IBM MQ queues and sends back configurable responses. Both services share a PostgreSQL database and communicate via lightweight HTTP notifications.

## Architecture

```
                          ┌──────────────────────┐
                          │     PostgreSQL        │
                          │    (shared DB)        │
                          └──────┬──────┬─────────┘
                                 │      │
              ┌──────────────────┘      └──────────────────┐
              │                                            │
   ┌──────────▼──────────┐                    ┌────────────▼────────────┐
   │   Admin Console     │   HTTP /refresh    │   MQ Mock Server        │
   │   (port 8080)       │ ─────────────────► │   (port 8090)           │
   │                     │                    │                         │
   │ • Web UI (Thymeleaf)│                    │ • IBM MQ JMS Listeners  │
   │ • Queue CRUD API    │                    │ • Message Matching      │
   │ • Mapping CRUD API  │                    │ • Response Generation   │
   │ • User/Auth mgmt    │                    │ • Namespace-scoped      │
   │ • No MQ dependency  │                    │ • No web UI             │
   └─────────────────────┘                    └────────────┬────────────┘
                                                           │
                                              ┌────────────▼────────────┐
                                              │       IBM MQ            │
                                              │  (Queue Manager QM1)    │
                                              └─────────────────────────┘
```

### Modules

| Module | Description | Port |
|--------|-------------|------|
| **common** | Shared library — JPA models, repositories, Flyway migrations | — |
| **admin-console** | Web UI + REST API for configuration. No IBM MQ dependency. | 8080 |
| **mq-mock-server** | Headless MQ listener service. Namespace-scoped via `--mqsim.namespace`. | 8090 |

## Features

### Core Message Processing
- **Dynamic JMS Listeners** — Automatically creates IBM MQ listeners based on queue configurations in PostgreSQL
- **Advanced Message Matching** — Rules-based matching with correlation ID regex, header matching, body regex, XPath, JSONPath, and mainframe field matching
- **Multiple Message Formats** — XML, JSON, and Mainframe/EBCDIC responses
- **Configurable Delays** — Fixed and variable response delays

### Header Management
- **Correlation ID Control** — Use request correlation ID, message ID, generate UUID, or set custom value
- **MQ-Specific Headers** — ENCODING, FORMAT, CodedCharsetId, Persistence, ReplyToQ
- **Dynamic Header Configuration** — Copy from request, fixed values, or generated values

### Multi-Tenancy
- **Namespace Isolation** — Each MQ Mock Server instance serves a single namespace
- **Multiple Instances** — Run separate mock servers per team/environment
- **Shared Admin Console** — Single UI manages all namespaces

### User Management
- **Role-Based Access** — Admin and User roles
- **Session-Based Auth** — Secure sessions with namespace switching
- **Admin Dashboard** — User and namespace management UI

## Prerequisites

- Docker and Docker Compose
- Java 17+ (for local development)
- Maven 3.6+ (for local development)

## Quick Start with Docker

### 1. Build the project

```bash
mvn clean package -DskipTests
```

### 2. Start all services

```bash
docker compose up -d
```

This starts 4 containers:
- **ibmmq** — IBM MQ queue manager (ports 1414, 9443)
- **postgres** — PostgreSQL database (port 5432)
- **admin-console** — Web UI and config API (port 8080)
- **mq-mock-default** — MQ listener for the `default` namespace (port 8090)

### 3. Wait for services to be healthy

```bash
# Watch container status
docker compose ps

# Follow admin-console logs
docker compose logs -f admin-console

# Follow mock server logs
docker compose logs -f mq-mock-default
```

IBM MQ takes ~60 seconds to initialize. The mock server waits for both MQ and PostgreSQL before starting.

### 4. Create queues on IBM MQ

Queues are NOT auto-created. Use the setup script or create them manually:

```bash
# Using the setup script
./scripts/setup-queues.sh

# Or manually
docker exec -it ibmmq runmqsc QM1 <<EOF
DEFINE QLOCAL('SIM.REQUEST.Q1') REPLACE
DEFINE QLOCAL('SIM.REQUEST.Q2') REPLACE
DEFINE QLOCAL('SIM.REPLY.DEFAULT') REPLACE
END
EOF
```

### 5. Access the applications

| Service | URL | Credentials |
|---------|-----|-------------|
| Admin Console UI | http://localhost:8080/ui | `admin` / `admin123` |
| Admin Panel | http://localhost:8080/admin/ui | `admin` / `admin123` |
| Mock Server Health | http://localhost:8090/health | — |
| Mock Server Status | http://localhost:8090/status | — |
| IBM MQ Console | https://localhost:9443 | `admin` / `admin` |

Default login credentials:
- **Admin**: `admin` / `admin123` (access: default, demo namespaces)
- **Demo User**: `demo` / `demo123` (access: demo namespace only)

## Running Locally (Development)

### Start infrastructure only

```bash
# Start just IBM MQ and PostgreSQL
docker compose up -d ibmmq postgres
```

### Run the Admin Console

```bash
mvn spring-boot:run -pl admin-console
```

Access at http://localhost:8080

### Run the MQ Mock Server

```bash
# Default namespace
mvn spring-boot:run -pl mq-mock-server

# Specific namespace
mvn spring-boot:run -pl mq-mock-server -Dspring-boot.run.arguments="--mqsim.namespace=demo"
```

Access health at http://localhost:8090/health

## Running Multiple Namespaces

Each MQ Mock Server instance is scoped to a single namespace. To serve multiple namespaces, run multiple instances:

### Via Docker Compose

Add a service block to `docker-compose.yml`:

```yaml
  mq-mock-demo:
    build:
      context: .
      dockerfile: mq-mock-server/Dockerfile
    container_name: mqsim-mock-demo
    ports:
      - "8091:8090"
    environment:
      - MQSIM_NAMESPACE=demo
      - IBM_MQ_QUEUE_MANAGER=QM1
      - IBM_MQ_CHANNEL=DEV.ADMIN.SVRCONN
      - IBM_MQ_CONN_NAME=ibmmq(1414)
      - IBM_MQ_USER=admin
      - IBM_MQ_PASSWORD=admin
      - DATABASE_URL=jdbc:postgresql://postgres:5432/mqsim
      - DATABASE_USERNAME=mqsim
      - DATABASE_PASSWORD=mqsim
      - DEFAULT_REPLY_QUEUE=sim.reply.default
      - SPRING_PROFILES_ACTIVE=docker
    depends_on:
      ibmmq:
        condition: service_healthy
      postgres:
        condition: service_healthy
    networks:
      - mqsim-network
```

Update `admin-console` environment to notify both servers:

```yaml
  - MOCK_SERVER_URLS=http://mq-mock-default:8090,http://mq-mock-demo:8090
```

### Via JAR

```bash
java -jar mq-mock-server/target/mq-simulator-mock-server-1.0.0.jar --mqsim.namespace=demo --server.port=8091
```

### Via Docker Run

```bash
docker run -e MQSIM_NAMESPACE=demo -e SERVER_PORT=8090 -p 8091:8090 mqsim-mock-server
```

## API Reference

### Queue Management (Admin Console — port 8080)

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

### Response Mapping Management (Admin Console — port 8080)

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

### Send Test Messages (MQ Mock Server — port 8090)

```bash
# Send a message to an MQ queue
curl -X POST http://localhost:8090/api/message/send \
  -H "Content-Type: application/json" \
  -d '{
    "queueName": "SIM.REQUEST.Q1",
    "body": "<order><id>12345</id></order>",
    "correlationId": "OK-12345",
    "replyToQueue": "SIM.REPLY.DEFAULT"
  }'
```

### Mock Server Control (MQ Mock Server — port 8090)

```bash
# Trigger listener refresh
curl -X POST http://localhost:8090/refresh

# Get listener status
curl http://localhost:8090/status

# Health check (includes MQ connection + namespace)
curl http://localhost:8090/health
```

## Environment Variables

### Admin Console

| Variable | Default | Description |
|----------|---------|-------------|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/appdb` | PostgreSQL JDBC URL |
| `DATABASE_USERNAME` | `appuser` | Database username |
| `DATABASE_PASSWORD` | `apppass` | Database password |
| `MOCK_SERVER_URLS` | `http://localhost:8090` | Comma-separated mock server URLs |

### MQ Mock Server

| Variable | Default | Description |
|----------|---------|-------------|
| `MQSIM_NAMESPACE` | `default` | Namespace this instance serves |
| `IBM_MQ_QUEUE_MANAGER` | `QM1` | IBM MQ Queue Manager name |
| `IBM_MQ_CHANNEL` | `DEV.ADMIN.SVRCONN` | IBM MQ channel |
| `IBM_MQ_CONN_NAME` | `localhost(1414)` | IBM MQ connection (host:port) |
| `IBM_MQ_USER` | `admin` | IBM MQ username |
| `IBM_MQ_PASSWORD` | `admin` | IBM MQ password |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/appdb` | PostgreSQL JDBC URL |
| `DATABASE_USERNAME` | `appuser` | Database username |
| `DATABASE_PASSWORD` | `apppass` | Database password |
| `DEFAULT_REPLY_QUEUE` | `sim.reply.default` | Default reply queue name |

## Project Structure

```
spring-boot-mq-simulator/
├── pom.xml                              # Parent POM (reactor)
├── docker-compose.yml
├── README.md
├── common/                              # Shared library JAR
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/mqsim/
│       │   ├── model/                   # JPA entities (7 classes)
│       │   └── repository/              # Spring Data repositories (4 interfaces)
│       └── resources/db/migration/
│           └── V1__initial_schema.sql
├── admin-console/                       # Web UI + Config API
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/
│       ├── java/com/mqsim/
│       │   ├── AdminConsoleApplication.java
│       │   ├── controller/              # REST + UI controllers
│       │   └── service/                 # UserService, SessionManager, MockServerNotificationService
│       └── resources/
│           ├── application.yml
│           └── templates/               # Thymeleaf HTML (dashboard, login, register, admin)
├── mq-mock-server/                      # Headless MQ listener
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/
│       ├── java/com/mqsim/
│       │   ├── MqMockServerApplication.java
│       │   ├── config/                  # IbmMqConfiguration, NamespaceConfig
│       │   ├── controller/              # RefreshController, HealthController, MessageController
│       │   ├── listener/                # DynamicMessageListener
│       │   └── service/                 # DynamicListenerService, ResponseService, matching services
│       └── resources/
│           └── application.yml
└── scripts/
    ├── setup-queues.sh                  # Create IBM MQ queues
    └── test-messages.sh                 # Send test messages
```

## Message Processing Flow

1. **Message Arrives** — JMS listener receives message on configured IBM MQ queue
2. **Namespace Scoping** — Only mappings for the mock server's namespace are considered
3. **Find Mapping** — Enabled mappings searched by priority (1 = highest)
4. **Pattern Matching** — Correlation ID regex, header values, body regex, XPath/JSONPath evaluated
5. **Apply Delay** — Fixed or variable delay applied before response
6. **Configure Headers** — Response headers set from fixed values or copied from request
7. **Generate Response** — XML, JSON, or Mainframe/EBCDIC message created
8. **Send Reply** — Response sent to JMS reply-to destination or default reply queue

## Troubleshooting

### Common Issues

1. **MQRC_UNKNOWN_OBJECT_NAME (2085)** — Queue does not exist on IBM MQ:
   ```bash
   docker exec -it ibmmq runmqsc QM1 <<EOF
   DEFINE QLOCAL('YOUR.QUEUE.NAME') REPLACE
   END
   EOF
   ```

2. **Invalid queue name** — IBM MQ queue names only allow: `A-Z`, `a-z`, `0-9`, `.`, `/`, `_`, `%` (max 48 chars). Hyphens are **not** allowed.

3. **Mock server not picking up new queues** — Trigger a refresh:
   ```bash
   curl -X POST http://localhost:8090/refresh
   # or via admin console
   curl -X POST http://localhost:8080/admin/queues/refresh
   ```

4. **IBM MQ connection failures**:
   ```bash
   docker logs ibmmq
   docker exec -it ibmmq dspmq
   docker exec -it ibmmq bash -c "echo 'DISPLAY QLOCAL(*)' | runmqsc QM1"
   ```

### Health Checks

| Endpoint | Service |
|----------|---------|
| http://localhost:8080/health | Admin Console |
| http://localhost:8090/health | MQ Mock Server (includes MQ status + namespace) |
| http://localhost:8090/status | MQ Mock Server listener status |
| https://localhost:9443 | IBM MQ Web Console |

## Related Resources

- [IBM MQ Documentation](https://www.ibm.com/docs/en/ibm-mq)
- [IBM MQ Spring Boot Starter](https://github.com/ibm-messaging/mq-jms-spring)
- [Spring JMS Reference](https://docs.spring.io/spring-framework/reference/integration/jms.html)
