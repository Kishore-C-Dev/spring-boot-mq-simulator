# Spring Boot MQ Testing Simulator

A comprehensive Spring Boot application for simulating IBM MQ message processing with dynamic JMS listeners, configurable response mappings, and support for both XML and Mainframe (EBCDIC) message formats.

## 🚀 Features

- **Dynamic JMS Listeners**: Automatically creates JMS listeners based on queue configurations stored in MongoDB
- **Flexible Response Mappings**: Rules-based message matching with support for correlation ID patterns, headers, and body regex
- **Multiple Message Formats**: Handles both XML (TextMessage) and Mainframe/EBCDIC (BytesMessage) responses
- **Configurable Delays**: Fixed and variable response delays for realistic testing scenarios
- **Admin Web UI**: Modern htmx + TailwindCSS interface for managing queues and mappings
- **REST API**: Complete REST endpoints for programmatic configuration
- **Docker Ready**: Full containerization with IBM MQ Developer Edition and MongoDB

## 🏗️ Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   IBM MQ        │    │   Spring Boot   │    │   MongoDB       │
│   (1414:1414)   │◄──►│   (8080:8080)   │◄──►│   (27017:27017) │
│                 │    │                 │    │                 │
│ Queue Manager:  │    │ - JMS Listeners │    │ - Queue Configs │
│ QM1             │    │ - Match Engine  │    │ - Mappings      │
│                 │    │ - Response Gen  │    │ - Seed Data     │
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

2. **Wait for Services** (1-2 minutes for IBM MQ to initialize):
   ```bash
   # Check service health
   docker compose ps
   
   # Watch logs
   docker compose logs -f backend
   ```

3. **Access Applications**:
   - **Simulator UI**: http://localhost:8080/ui
   - **IBM MQ Console**: https://localhost:9443 (admin/passw0rd)
   - **Health Check**: http://localhost:8080/health

## 🎯 Usage Examples

### 1. Testing with IBM MQ Tools

Send a test message using IBM MQ sample programs:

```bash
# Put a message to request queue
docker exec ibmmq /opt/mqm/samp/bin/amqsput SIM.REQUEST.Q1 QM1
# Enter: correlation ID "OK-12345" and any message body
# Press Enter twice to send

# Get response from reply queue  
docker exec ibmmq /opt/mqm/samp/bin/amqsget SIM.REPLY.DEFAULT QM1
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

1. **Message Arrives**: JMS listener receives message on configured queue
2. **Extract Metadata**: Correlation ID, headers, and body content extracted
3. **Find Mapping**: Enabled mappings searched by priority (1=highest)
4. **Pattern Matching**: Correlation ID regex, header values, and body regex evaluated
5. **Apply Delay**: Fixed or variable delay applied before response
6. **Generate Response**: XML (TextMessage) or MF (BytesMessage) created
7. **Send Reply**: Response sent to JMSReplyTo destination or default reply queue

## 🔧 Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `MQ_HOST` | `ibmmq` | IBM MQ hostname |
| `MQ_PORT` | `1414` | IBM MQ port |
| `MQ_QMGR` | `QM1` | Queue Manager name |
| `MQ_CHANNEL` | `DEV.APP.SVRCONN` | Server connection channel |
| `MQ_USER` | `app` | MQ user |
| `MQ_PASSWORD` | `passw0rd` | MQ password |
| `MONGO_URI` | `mongodb://mongo:27017/mqsim` | MongoDB connection string |
| `DEFAULT_REPLY_QUEUE` | `SIM.REPLY.DEFAULT` | Default reply queue name |

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
    "headers": {"Content-Type": "application/xml"},
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

### Integration Testing with Real MQ
```bash
# Start services
docker compose up -d

# Wait for readiness
curl http://localhost:8080/ready

# Send test message via MQ tools
docker exec ibmmq /opt/mqm/samp/bin/amqsput SIM.REQUEST.Q1 QM1

# Check response
docker exec ibmmq /opt/mqm/samp/bin/amqsget SIM.REPLY.DEFAULT QM1
```

### Load Testing
```bash
# Send multiple messages in parallel
for i in {1..10}; do
  echo "OK-$i" | docker exec -i ibmmq /opt/mqm/samp/bin/amqsput SIM.REQUEST.Q1 QM1 &
done
wait

# Check all responses
docker exec ibmmq /opt/mqm/samp/bin/amqsbcg SIM.REPLY.DEFAULT QM1
```

## 🚨 Troubleshooting

### Common Issues

1. **MQ Connection Failures**:
   ```bash
   # Check MQ container logs
   docker logs ibmmq
   
   # Verify queue manager is running
   docker exec ibmmq dspmq
   
   # Check channel status
   docker exec ibmmq runmqsc QM1 <<< "DISPLAY CHANNEL(DEV.APP.SVRCONN)"
   ```

2. **No Listeners Starting**:
   ```bash
   # Check queue configurations
   curl http://localhost:8080/admin/queues
   
   # Check listener status
   curl http://localhost:8080/admin/queues/status
   
   # Refresh listeners manually
   curl -X POST http://localhost:8080/admin/queues/refresh
   ```

3. **Messages Not Matching**:
   ```bash
   # Check mapping configurations
   curl http://localhost:8080/admin/mappings
   
   # Check application logs
   docker logs mqsim-backend
   
   # Verify correlation ID patterns
   # Use tools like regex101.com to test patterns
   ```

4. **Queue Not Found Errors**:
   ```bash
   # Create missing queues in MQ
   docker exec ibmmq runmqsc QM1 <<< "
   DEFINE QLOCAL(SIM.REQUEST.Q1) MAXDEPTH(5000)
   DEFINE QLOCAL(SIM.REPLY.DEFAULT) MAXDEPTH(5000)
   "
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
- **MQ Console**: https://localhost:9443 (admin/passw0rd)
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
        │   │   ├── config/         # MQ and Spring configuration
        │   │   ├── controller/     # REST APIs and UI controllers
        │   │   ├── listener/       # Dynamic JMS message listener
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

- [IBM MQ Documentation](https://www.ibm.com/docs/en/ibm-mq)
- [Spring JMS Reference](https://docs.spring.io/spring-framework/docs/current/reference/html/integration.html#jms)
- [HTMX Documentation](https://htmx.org/docs/)
- [TailwindCSS Documentation](https://tailwindcss.com/docs)