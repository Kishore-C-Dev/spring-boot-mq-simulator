# Spring Boot MQ Testing Simulator

A comprehensive Spring Boot application for simulating IBM MQ message processing with dynamic JMS listeners, configurable response mappings, enhanced header management, and support for XML, JSON, and Mainframe (EBCDIC) message formats. Perfect for automated testing, development, and CI/CD integration.

## Features

### Core Message Processing
- **Dynamic JMS Listeners**: Automatically creates IBM MQ listeners based on queue configurations stored in MongoDB
- **Advanced Message Matching**: Rules-based message matching with support for:
  - Correlation ID patterns (regex)
  - Header value matching
  - Body content regex
  - XPath matching for XML messages
  - JSONPath matching for JSON messages
  - Mainframe message field matching
- **Multiple Message Formats**: Full support for XML, JSON, and Mainframe/EBCDIC message responses
- **Configurable Delays**: Fixed and variable response delays for realistic testing scenarios

### Enhanced Header Management
- **Correlation ID Control**: Use request correlation ID, request message ID, generate new UUID, or set custom value
- **Message ID Management**: Option to set custom message ID or omit entirely
- **MQ-Specific Headers**: Support for MQ headers like ENCODING, FORMAT, CodedCharsetId, Persistence, ReplyToQ
- **Dynamic Header Configuration**: Copy from request headers, set fixed values, or generate dynamic values

### User & Namespace Management
- **Multi-User Support**: Role-based access control (Admin/User roles)
- **Namespace Isolation**: Complete isolation between different teams/projects
- **Session-Based Authentication**: Secure user sessions with namespace switching
- **Admin Dashboard**: Dedicated admin interface for user and namespace management

### REST API for Message Insertion
- **Send Messages**: `POST /api/message/send` — Send messages to any IBM MQ queue with full control over correlation ID, reply-to queue, headers, and body content

### Web Interface & APIs
- **Modern Web UI**: Clean, responsive interface built with Thymeleaf + TailwindCSS
- **Complete REST API**: Full programmatic control for test automation
- **Real-time Updates**: Dynamic queue and mapping management
- **Docker Ready**: Full containerization with IBM MQ and MongoDB

## Architecture

```
+-------------------+    +-------------------+    +-------------------+
|   IBM MQ          |    |   Spring Boot     |    |   MongoDB         |
|   (1414:1414)     |<-->|   (8081:8081)     |<-->|   (27017:27017)   |
|   (9443:9443)     |    |                   |    |                   |
|                   |    | - JMS Listeners   |    | - Queue Configs   |
| - Queue Manager   |    | - Match Engine    |    | - Mappings        |
| - Queues          |    | - Response Gen    |    | - Users/Auth      |
| - Web Console     |    | - Header Config   |    | - Namespaces      |
|                   |    | - REST Message API|    |                   |
+-------------------+    +-------------------+    +-------------------+
```

## Prerequisites

- Docker and Docker Compose
- Java 17+ (for local development)
- Maven 3.6+ (for local development)
- IBM MQ queues must be pre-created on the queue manager (queues are NOT auto-created)

### IBM MQ Queue Name Rules

IBM MQ queue names can only contain: `A-Z`, `a-z`, `0-9`, `.`, `/`, `_`, `%` (max 48 characters). **Hyphens (`-`) are not allowed.**

## Quick Start

1. **Clone and Start**:
   ```bash
   git clone <repository-url>
   cd spring-boot-mq-simulator
   docker compose up -d
   ```

2. **Wait for Services** (60 seconds for IBM MQ to initialize):
   ```bash
   docker compose ps
   docker compose logs -f backend
   ```

3. **Create Queues on IBM MQ** (queues must be pre-created):
   ```bash
   docker exec -it ibmmq runmqsc QM1 <<EOF
   DEFINE QLOCAL('SIM.REQUEST.Q1') REPLACE
   DEFINE QLOCAL('SIM.REQUEST.Q2') REPLACE
   DEFINE QLOCAL('SIM.REPLY.DEFAULT') REPLACE
   DEFINE QLOCAL('IPE.DEBIT.REQUEST') REPLACE
   DEFINE QLOCAL('IPE.DEBIT.RESPONSE') REPLACE
   END
   EOF
   ```

4. **Access Applications**:
   - **Simulator UI**: http://localhost:8081/ui
   - **Admin Panel**: http://localhost:8081/admin/ui
   - **Login Credentials**:
     - Admin: `admin` / `admin123` (access: default, demo)
     - Demo User: `demo` / `demo123` (access: demo only)
   - **IBM MQ Web Console**: https://localhost:9443 (admin/admin)
   - **Health Check**: http://localhost:8081/health

## Sending Messages via REST API

Use the built-in REST endpoint to send messages to any IBM MQ queue:

```bash
# Send a basic message
curl -X POST http://localhost:8081/api/message/send \
  -H "Content-Type: application/json" \
  -d '{
    "queueName": "IPE.DEBIT.REQUEST",
    "body": "Test debit request message",
    "correlationId": "TEST-CORR-001",
    "replyToQueue": "IPE.DEBIT.RESPONSE"
  }'

# Send a message with custom headers
curl -X POST http://localhost:8081/api/message/send \
  -H "Content-Type: application/json" \
  -d '{
    "queueName": "SIM.REQUEST.Q1",
    "body": "<order><id>12345</id><type>purchase</type></order>",
    "correlationId": "OK-12345",
    "replyToQueue": "SIM.REPLY.DEFAULT",
    "persistent": true,
    "headers": {
      "X-Environment": "test",
      "X-Tenant": "acme"
    }
  }'

# Send a JSON message
curl -X POST http://localhost:8081/api/message/send \
  -H "Content-Type: application/json" \
  -d '{
    "queueName": "SIM.REQUEST.Q1",
    "body": "{\"requestId\":\"OK-12345\",\"operation\":\"test\"}",
    "correlationId": "OK-12345",
    "replyToQueue": "SIM.REPLY.DEFAULT"
  }'
```

### Request Body

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `queueName` | string | Yes | Target IBM MQ queue name |
| `body` | string | No | Message body content |
| `correlationId` | string | No | JMS Correlation ID |
| `replyToQueue` | string | No | MQMD ReplyTo Queue name |
| `persistent` | boolean | No | Set JMS delivery mode to persistent |
| `headers` | object | No | Custom JMS string properties (key-value pairs) |

### Response

```json
{
  "status": "success",
  "queueName": "IPE.DEBIT.REQUEST",
  "correlationId": "TEST-CORR-001",
  "replyToQueue": "IPE.DEBIT.RESPONSE"
}
```

## Queue & Mapping Management API

```bash
# Get all queue configurations
curl http://localhost:8081/admin/queues

# Create a new queue configuration
curl -X POST http://localhost:8081/admin/queues \
  -H "Content-Type: application/json" \
  -d '{
    "queueName": "SIM.REQUEST.NEW",
    "namespace": "default",
    "concurrency": "1-2",
    "enabled": true
  }'

# Create a response mapping
curl -X POST http://localhost:8081/admin/mappings \
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
      "jsonSuccessBody": "{\"status\":\"success\",\"orderId\":\"12345\"}",
      "jsonErrorBody": "{\"status\":\"error\",\"message\":\"Order not found\"}",
      "correlationIdConfig": "USE_REQUEST_MESSAGE_ID",
      "mqHeaders": {
        "ENCODING": "UTF-8",
        "FORMAT": "MQFMT_STRING"
      }
    },
    "delay": {
      "mode": "VARIABLE",
      "variableMinMs": 50,
      "variableMaxMs": 200
    }
  }'

# Refresh listeners after configuration changes
curl -X POST http://localhost:8081/admin/queues/refresh
```

## Message Processing Flow

1. **Message Arrives**: JMS listener receives message on configured IBM MQ queue
2. **Extract Metadata**: Correlation ID, headers, and body content extracted via JMS API
3. **Find Mapping**: Enabled mappings searched by priority (1=highest)
4. **Pattern Matching**: Correlation ID regex, header values, and body regex evaluated
5. **Apply Delay**: Fixed or variable delay applied before response
6. **Configure Headers**: Response headers set from fixed values or copied from request
7. **Generate Response**: XML, JSON, or Mainframe/EBCDIC message created
8. **Send Reply**: Response sent to JMS reply-to destination or default reply queue

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `IBM_MQ_QUEUE_MANAGER` | `QM1` | IBM MQ Queue Manager name |
| `IBM_MQ_CHANNEL` | `DEV.ADMIN.SVRCONN` | IBM MQ channel |
| `IBM_MQ_CONN_NAME` | `localhost(1414)` | IBM MQ connection name (host and port) |
| `IBM_MQ_USER` | `admin` | IBM MQ username |
| `IBM_MQ_PASSWORD` | `admin` | IBM MQ password |
| `MONGO_URI` | `mongodb://localhost:27017/mqsim` | MongoDB connection string |
| `MQ_ENABLED` | `true` | Enable/disable MQ functionality |
| `DEFAULT_REPLY_QUEUE` | `sim.reply.default` | Default reply queue name |

### Response Mapping Format

```json
{
  "queueName": "SIM.REQUEST.Q1",
  "namespace": "default",
  "priority": 1,
  "enabled": true,
  "match": {
    "correlationId": "^OK-.*",
    "headers": {"X-Tenant": "acme"},
    "bodyRegex": ".*<OrderId>123</OrderId>.*",
    "xpathRules": [
      { "xpath": "//orderId", "expectedValue": "12345", "matchType": "EQUALS" }
    ],
    "jsonPathRules": [
      { "jsonPath": "$.order.id", "expectedValue": "12345", "matchType": "EQUALS" }
    ],
    "mainframeRules": [
      { "startPosition": 0, "length": 10, "expectedValue": "ORDER12345", "matchType": "EQUALS" }
    ]
  },
  "response": {
    "type": "JSON",
    "jsonSuccessBody": "{\"status\":\"success\"}",
    "jsonErrorBody": "{\"status\":\"error\"}",
    "correlationIdConfig": "USE_REQUEST_MESSAGE_ID",
    "messageIdConfig": "CUSTOM_VALUE",
    "customMessageId": "MSG-12345",
    "mqHeaders": {
      "ENCODING": "UTF-8",
      "FORMAT": "MQFMT_STRING",
      "Persistence": "1"
    },
    "headerConfigs": {
      "X-Source": { "source": "FIXED", "fixedValue": "MQ_SIMULATOR" },
      "X-Request-ID": { "source": "COPY_FROM_REQUEST", "requestHeaderName": "X-Original-ID" }
    }
  },
  "delay": {
    "mode": "VARIABLE",
    "variableMinMs": 100,
    "variableMaxMs": 500
  }
}
```

## Troubleshooting

### Common Issues

1. **MQRC_UNKNOWN_OBJECT_NAME (2085)** — Queue does not exist on IBM MQ:
   ```bash
   # Create the missing queue
   docker exec -it ibmmq runmqsc QM1 <<EOF
   DEFINE QLOCAL('YOUR.QUEUE.NAME') REPLACE
   END
   EOF
   ```

2. **JMSCC0005: Invalid destination name** — Queue name contains invalid characters (e.g. hyphens):
   - IBM MQ queue names only allow: `A-Z`, `a-z`, `0-9`, `.`, `/`, `_`, `%`
   - Rename the queue replacing hyphens with dots (e.g. `IPE-DEBIT-REQUEST` -> `IPE.DEBIT.REQUEST`)

3. **No Listeners Starting**:
   ```bash
   curl http://localhost:8081/admin/queues
   curl http://localhost:8081/admin/queues/status
   curl -X POST http://localhost:8081/admin/queues/refresh
   ```

4. **IBM MQ Connection Failures**:
   ```bash
   # Check IBM MQ container
   docker logs ibmmq

   # Verify queue manager is running
   docker exec -it ibmmq dspmq

   # List queues
   docker exec -it ibmmq bash -c "echo 'DISPLAY QLOCAL(*)' | runmqsc QM1"
   ```

### Health Checks

- **Application**: http://localhost:8081/health
- **Readiness**: http://localhost:8081/ready
- **IBM MQ Web Console**: https://localhost:9443
- **Listener Status**: http://localhost:8081/admin/queues/status

## Project Structure

```
spring-boot-mq-simulator/
├── docker-compose.yml
├── README.md
└── backend/
    ├── Dockerfile
    ├── pom.xml
    └── src/main/
        ├── java/com/mqsim/
        │   ├── MqTestingSimulatorApplication.java
        │   ├── config/
        │   │   └── IbmMqConfiguration.java      # JMS/IBM MQ configuration
        │   ├── controller/
        │   │   ├── AdminController.java          # Admin REST API
        │   │   ├── AdminUiController.java        # Admin UI controller
        │   │   ├── AuthController.java           # Authentication controller
        │   │   ├── HealthController.java         # Health/readiness endpoints
        │   │   ├── MappingController.java        # Response mapping API
        │   │   ├── MessageController.java        # Message send REST API
        │   │   ├── QueueController.java          # Queue management API
        │   │   └── UiController.java             # Main UI controller
        │   ├── listener/
        │   │   └── DynamicMessageListener.java   # JMS message listener
        │   ├── model/                            # MongoDB entities
        │   ├── repository/                       # MongoDB repositories
        │   └── service/
        │       ├── DynamicListenerService.java   # JMS listener management
        │       ├── MessageMatchingService.java   # Message matching engine
        │       ├── ResponseService.java          # Response generation & sending
        │       └── ...                           # Other services
        └── resources/
            ├── application.yml
            └── templates/                        # Thymeleaf HTML templates
```

## Related Resources

- [IBM MQ Documentation](https://www.ibm.com/docs/en/ibm-mq)
- [IBM MQ Spring Boot Starter](https://github.com/ibm-messaging/mq-jms-spring)
- [Spring JMS Reference](https://docs.spring.io/spring-framework/reference/integration/jms.html)
