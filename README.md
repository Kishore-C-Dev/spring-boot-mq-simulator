# Spring Boot MQ Testing Simulator

A comprehensive Spring Boot application for simulating RabbitMQ message processing with dynamic AMQP listeners, configurable response mappings, and support for both XML and Mainframe (EBCDIC) message formats.

## 🚀 Features

- **Dynamic AMQP Listeners**: Automatically creates RabbitMQ listeners based on queue configurations stored in MongoDB
- **Flexible Response Mappings**: Rules-based message matching with support for correlation ID patterns, headers, and body regex
- **Multiple Message Formats**: Handles both XML and Mainframe/EBCDIC message responses
- **Configurable Response Headers**: Set response headers with fixed values or copy from request headers
- **Configurable Delays**: Fixed and variable response delays for realistic testing scenarios
- **Admin Web UI**: Modern htmx + TailwindCSS interface for managing queues and mappings
- **REST API**: Complete REST endpoints for programmatic configuration
- **Docker Ready**: Full containerization with RabbitMQ and MongoDB

## 🏗️ Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   RabbitMQ      │    │   Spring Boot   │    │   MongoDB       │
│   (5672:5672)   │◄──►│   (8080:8080)   │◄──►│   (27017:27017) │
│   (15672:15672) │    │                 │    │                 │
│                 │    │ - AMQP Listeners│    │ - Queue Configs │
│ - Exchanges     │    │ - Match Engine  │    │ - Mappings      │
│ - Queues        │    │ - Response Gen  │    │ - Seed Data     │
│ - Management UI │    │ - Header Config │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## 📋 Prerequisites

- Docker and Docker Compose
- Java 17+ (for local development)
- Maven 3.6+ (for local development)

## 🚀 Quick Start

1. **Clone and Start**:
   ```bash
   git clone <repository-url>
   cd spring-boot-mq-simulator
   docker compose up -d
   ```

2. **Wait for Services** (30 seconds for RabbitMQ to initialize):
   ```bash
   # Check service health
   docker compose ps
   
   # Watch logs
   docker compose logs -f backend
   ```

3. **Access Applications**:
   - **Simulator UI**: http://localhost:8080/ui
   - **RabbitMQ Management**: http://localhost:15672 (admin/passw0rd)
   - **Health Check**: http://localhost:8080/health

## 🎯 Usage Examples

### 1. Testing with RabbitMQ API

Send a test message using RabbitMQ REST API:

```bash
# Put a message to request queue
curl -u admin:passw0rd -H "Content-Type: application/json" -X POST \
  http://localhost:15672/api/exchanges/%2F/amq.default/publish \
  -d '{
    "properties": {
      "correlation_id": "OK-12345",
      "reply_to": "sim.reply.default"
    },
    "routing_key": "SIM.REQUEST.Q1",
    "payload": "{\"requestId\":\"OK-12345\",\"operation\":\"test\"}",
    "payload_encoding": "string"
  }'

# Check response in reply queue via Management UI
# Go to http://localhost:15672 -> Queues -> sim.reply.default -> Get Messages
```

### 2. REST API Examples

```bash
# Get all queue configurations
curl http://localhost:8080/admin/queues

# Create a new queue configuration
curl -X POST http://localhost:8080/admin/queues \
  -H "Content-Type: application/json" \
  -d '{
    "queueName": "SIM.REQUEST.NEW",
    "concurrency": "1-2", 
    "enabled": true
  }'

# Create a response mapping
curl -X POST http://localhost:8080/admin/mappings \
  -H "Content-Type: application/json" \
  -d '{
    "queueName": "SIM.REQUEST.Q1",
    "priority": 1,
    "enabled": true,
    "match": {
      "correlationId": "^TEST-.*"
    },
    "response": {
      "type": "XML",
      "xmlBody": "<result>Success</result>"
    },
    "delay": {
      "mode": "FIXED",
      "fixedMs": 100
    }
  }'

# Refresh listeners after configuration changes
curl -X POST http://localhost:8080/admin/queues/refresh
```

### 3. Web UI Management

1. Navigate to http://localhost:8080/ui
2. Use the **Queue Configurations** tab to:
   - Add/edit/delete queue listeners
   - Set concurrency levels (e.g., "2-5" for 2 minimum, 5 maximum consumers)
   - Enable/disable queues
3. Use the **Response Mappings** tab to:
   - Create response rules
   - Configure correlation ID patterns (regex)
   - Set XML or Mainframe response bodies
   - Configure response headers (fixed values or copy from request)
   - Configure fixed or variable delays

## 📊 Default Seed Data

The application comes with pre-configured test data:

### Queue Configurations
- `SIM.REQUEST.Q1` - Concurrency: 2-5, Enabled
- `SIM.REQUEST.Q2` - Concurrency: 1-3, Enabled  
- `SIM.REQUEST.Q3` - Concurrency: 1, Disabled

### Response Mappings
- **Q1 Success**: Correlation ID `^OK-.*` → XML `<status>OK</status>` (120ms delay)
- **Q1 Error**: Correlation ID `^ERR-.*` → XML `<error>ABEND</error>` (300-800ms variable delay)
- **Q2 Mainframe**: Correlation ID `MF-1001` → Base64 EBCDIC response (200ms delay)
- **Q2 Default**: Correlation ID `.*` → XML `<response>DEFAULT</response>` (50ms delay)

## 🏃‍♂️ Message Processing Flow

1. **Message Arrives**: AMQP listener receives message on configured queue
2. **Extract Metadata**: Correlation ID, headers, and body content extracted
3. **Find Mapping**: Enabled mappings searched by priority (1=highest)
4. **Pattern Matching**: Correlation ID regex, header values, and body regex evaluated
5. **Apply Delay**: Fixed or variable delay applied before response
6. **Configure Headers**: Response headers set from fixed values or copied from request
7. **Generate Response**: XML or Mainframe/EBCDIC message created
8. **Send Reply**: Response sent to reply-to destination or default reply queue

## 🔧 Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `RABBITMQ_HOST` | `localhost` | RabbitMQ hostname |
| `RABBITMQ_PORT` | `5672` | RabbitMQ AMQP port |
| `RABBITMQ_USERNAME` | `admin` | RabbitMQ username |
| `RABBITMQ_PASSWORD` | `passw0rd` | RabbitMQ password |
| `MONGO_URI` | `mongodb://mongo:27017/mqsim` | MongoDB connection string |
| `MQ_ENABLED` | `true` | Enable/disable MQ functionality |
| `MQ_DEFAULT_REPLY_QUEUE` | `sim.reply.default` | Default reply queue name |

### Queue Configuration Format

```json
{
  "queueName": "SIM.REQUEST.Q1",
  "concurrency": "2-5",    // "min-max" or single number
  "enabled": true
}
```

### Response Mapping Format

```json
{
  "queueName": "SIM.REQUEST.Q1",
  "priority": 1,           // 1-10, lower = higher priority
  "enabled": true,
  "match": {
    "correlationId": "^OK-.*",                    // Regex pattern
    "headers": {"X-Tenant": "acme"},             // Exact header matches
    "bodyRegex": ".*<OrderId>123</OrderId>.*"    // Body content regex
  },
  "response": {
    "type": "XML",                               // XML or MF
    "xmlBody": "<status>OK</status>",            // For XML responses
    "mfBodyBase64": "BASE64_ENCODED_EBCDIC",     // For MF responses
    "headerConfigs": {                           // Configurable headers
      "X-Source": {
        "source": "FIXED",
        "fixedValue": "MQ_SIMULATOR"
      },
      "X-Request-ID": {
        "source": "COPY_FROM_REQUEST", 
        "requestHeaderName": "X-Original-ID"
      }
    },
    "overrideCorrelationId": "CUSTOM-ID"        // Optional
  },
  "delay": {
    "mode": "FIXED",        // FIXED or VARIABLE
    "fixedMs": 120,         // For FIXED mode
    "variableMinMs": 100,   // For VARIABLE mode
    "variableMaxMs": 500    // For VARIABLE mode
  }
}
```

## 🧪 Testing

### Unit Tests
```bash
cd backend
mvn test
```

### Integration Testing with RabbitMQ
```bash
# Start services
docker compose up -d

# Wait for readiness
curl http://localhost:8080/ready

# Send test message via RabbitMQ API
curl -u admin:passw0rd -H "Content-Type: application/json" -X POST \
  http://localhost:15672/api/exchanges/%2F/amq.default/publish \
  -d '{
    "properties": {"correlation_id": "TEST-123", "reply_to": "sim.reply.default"},
    "routing_key": "SIM.REQUEST.Q1",
    "payload": "test message",
    "payload_encoding": "string"
  }'

# Check response via Management UI
# http://localhost:15672 -> Queues -> sim.reply.default
```

### Load Testing
```bash
# Send multiple messages in parallel using RabbitMQ API
for i in {1..10}; do
  curl -s -u admin:passw0rd -H "Content-Type: application/json" -X POST \
    http://localhost:15672/api/exchanges/%2F/amq.default/publish \
    -d "{
      \"properties\": {\"correlation_id\": \"OK-$i\", \"reply_to\": \"sim.reply.default\"},
      \"routing_key\": \"SIM.REQUEST.Q1\",
      \"payload\": \"load test message $i\",
      \"payload_encoding\": \"string\"
    }" &
done
wait

# Check response queue depth
curl -s -u admin:passw0rd http://localhost:15672/api/queues/%2F/sim.reply.default | jq .messages
```

## 🚨 Troubleshooting

### Common Issues

1. **RabbitMQ Connection Failures**:
   ```bash
   # Check RabbitMQ container logs
   docker logs rabbitmq
   
   # Verify RabbitMQ is running
   curl -u admin:passw0rd http://localhost:15672/api/overview
   
   # Check queue status
   curl -u admin:passw0rd http://localhost:15672/api/queues
   ```

2. **No Listeners Starting**:
   ```bash
   # Check queue configurations
   curl http://localhost:8080/admin/queues
   
   # Check listener status
   curl http://localhost:8080/admin/queues/status
   
   # Refresh listeners manually
   curl -X POST http://localhost:8080/admin/queues/refresh
   
   # Check if MQ is enabled
   curl http://localhost:8080/health
   ```

3. **Messages Not Matching**:
   ```bash
   # Check mapping configurations
   curl http://localhost:8080/admin/mappings
   
   # Check application logs
   docker logs spring-boot-mq-simulator-backend-1
   
   # Verify correlation ID patterns
   # Use tools like regex101.com to test patterns
   ```

4. **Queue Auto-Creation Issues**:
   ```bash
   # Queues are created automatically by Spring AMQP
   # Check RabbitMQ Management UI for queue list
   # http://localhost:15672/#/queues
   
   # Manually create queue if needed via API
   curl -u admin:passw0rd -X PUT \
     http://localhost:15672/api/queues/%2F/SIM.REQUEST.Q1 \
     -H "Content-Type: application/json" \
     -d '{"durable": true}'
   ```

### Debug Mode

Enable debug logging by setting environment variable:
```bash
export SPRING_PROFILES_ACTIVE=debug
docker compose up backend
```

### Health Checks

- **Application**: http://localhost:8080/health
- **Readiness**: http://localhost:8080/ready  
- **RabbitMQ Management**: http://localhost:15672 (admin/passw0rd)
- **Listener Status**: http://localhost:8080/admin/queues/status

## 📁 Project Structure

```
spring-boot-mq-simulator/
├── docker-compose.yml              # Multi-container setup
├── README.md                       # This file
└── backend/
    ├── Dockerfile                  # Spring Boot container
    ├── pom.xml                     # Maven dependencies
    └── src/
        ├── main/
        │   ├── java/com/mqsim/
        │   │   ├── MqTestingSimulatorApplication.java
        │   │   ├── config/         # RabbitMQ and Spring configuration
        │   │   ├── controller/     # REST APIs and UI controllers
        │   │   ├── listener/       # Dynamic AMQP message listener
        │   │   ├── model/          # MongoDB entities
        │   │   ├── repository/     # MongoDB repositories
        │   │   └── service/        # Business logic services
        │   └── resources/
        │       ├── application.yml # App configuration
        │       └── templates/      # Thymeleaf HTML templates
        └── test/                   # Unit and integration tests
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Add tests for new functionality
4. Ensure all tests pass
5. Submit a pull request

## 📝 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🔗 Related Resources

- [RabbitMQ Documentation](https://www.rabbitmq.com/documentation.html)
- [Spring AMQP Reference](https://docs.spring.io/spring-amqp/docs/current/reference/html/)
- [Spring Boot AMQP](https://docs.spring.io/spring-boot/docs/current/reference/html/messaging.html#messaging.amqp)
- [HTMX Documentation](https://htmx.org/docs/)
- [TailwindCSS Documentation](https://tailwindcss.com/docs)