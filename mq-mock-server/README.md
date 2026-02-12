# MQ Mock Server

Headless service that listens to IBM MQ queues and sends back configurable responses. Each instance is scoped to a single namespace. Run multiple instances to serve multiple namespaces.

## Prerequisites

- Java 17+
- Maven 3.6+
- PostgreSQL database
- IBM MQ queue manager

## Build

```bash
mvn clean package -DskipTests
```

## Run

```bash
# Via Maven (default namespace)
mvn spring-boot:run

# Via Maven (specific namespace)
mvn spring-boot:run -Dspring-boot.run.arguments="--mqsim.namespace=demo"

# Via JAR
java -jar target/mq-simulator-mock-server-1.0.0.jar

# Via JAR (specific namespace + port)
java -jar target/mq-simulator-mock-server-1.0.0.jar --mqsim.namespace=demo --server.port=8091
```

## Docker

```bash
docker build -t mqsim-mock-server .
docker run -p 8090:8090 \
  -e MQSIM_NAMESPACE=default \
  -e IBM_MQ_QUEUE_MANAGER=QM1 \
  -e IBM_MQ_CHANNEL=DEV.ADMIN.SVRCONN \
  -e IBM_MQ_CONN_NAME=mq-host(1414) \
  -e IBM_MQ_USER=admin \
  -e IBM_MQ_PASSWORD=admin \
  -e DATABASE_URL=jdbc:postgresql://host:5432/mqsim \
  -e DATABASE_USERNAME=mqsim \
  -e DATABASE_PASSWORD=mqsim \
  mqsim-mock-server
```

## Namespace Configuration

Each MQ Mock Server instance serves a single namespace. To serve multiple namespaces, run multiple instances on different ports:

```bash
# Default namespace on port 8090
java -jar target/mq-simulator-mock-server-1.0.0.jar --mqsim.namespace=default --server.port=8090

# Demo namespace on port 8091
java -jar target/mq-simulator-mock-server-1.0.0.jar --mqsim.namespace=demo --server.port=8091
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `MQSIM_NAMESPACE` | `default` | Namespace this instance serves |
| `IBM_MQ_QUEUE_MANAGER` | `QM1` | IBM MQ Queue Manager name |
| `IBM_MQ_CHANNEL` | `DEV.ADMIN.SVRCONN` | IBM MQ channel |
| `IBM_MQ_CONN_NAME` | `localhost(1414)` | IBM MQ connection (host and port) |
| `IBM_MQ_USER` | `admin` | IBM MQ username |
| `IBM_MQ_PASSWORD` | `admin` | IBM MQ password |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/appdb` | PostgreSQL JDBC URL |
| `DATABASE_USERNAME` | `appuser` | Database username |
| `DATABASE_PASSWORD` | `apppass` | Database password |
| `DEFAULT_REPLY_QUEUE` | `sim.reply.default` | Default reply queue name |

## API Reference

### Listener Control

```bash
# Trigger listener refresh
curl -X POST http://localhost:8090/refresh

# Get listener status
curl http://localhost:8090/status
```

### Send Test Messages

```bash
curl -X POST http://localhost:8090/api/message/send \
  -H "Content-Type: application/json" \
  -d '{
    "queueName": "SIM.REQUEST.Q1",
    "body": "<order><id>12345</id></order>",
    "correlationId": "OK-12345",
    "replyToQueue": "SIM.REPLY.DEFAULT"
  }'
```

### Health Check

```bash
# Health check (includes MQ connection status + namespace)
curl http://localhost:8090/health
```

## Message Processing Flow

1. JMS listener receives message on configured IBM MQ queue
2. Only mappings for this instance's namespace are considered
3. Enabled mappings searched by priority (1 = highest)
4. Pattern matching: correlation ID regex, header values, body regex, XPath/JSONPath
5. Delay applied (fixed or variable)
6. Response headers configured from fixed values or copied from request
7. XML, JSON, or Mainframe response generated
8. Reply sent to JMS reply-to destination or default reply queue
