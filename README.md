# Spring Boot MQ Testing Simulator

A comprehensive Spring Boot application for simulating RabbitMQ message processing with dynamic AMQP listeners, configurable response mappings, enhanced header management, and support for XML, JSON, and Mainframe (EBCDIC) message formats. Perfect for automated testing, development, and CI/CD integration.

## 🚀 Features

### Core Message Processing
- **Dynamic AMQP Listeners**: Automatically creates RabbitMQ listeners based on queue configurations stored in MongoDB
- **Advanced Message Matching**: Rules-based message matching with support for:
  - Correlation ID patterns (regex)
  - Header value matching
  - Body content regex
  - XPath matching for XML messages
  - JSONPath matching for JSON messages
  - Mainframe message field matching
- **Multiple Message Formats**: Full support for XML, JSON, and Mainframe/EBCDIC message responses
- **Configurable Delays**: Fixed and variable response delays for realistic testing scenarios

### Enhanced Header Management 🆕
- **Correlation ID Control**:
  - Use request correlation ID
  - Use request message ID as correlation ID
  - Generate new UUID
  - Set custom correlation ID value
- **Message ID Management**: Option to set custom message ID or omit entirely
- **MQ-Specific Headers**: Support for MQ headers like ENCODING, FORMAT, CodedCharsetId, Persistence, ReplyToQ
- **Dynamic Header Configuration**: Copy from request headers, set fixed values, or generate dynamic values

### User & Namespace Management 🆕
- **Multi-User Support**: Role-based access control (Admin/User roles)
- **Namespace Isolation**: Complete isolation between different teams/projects
- **Session-Based Authentication**: Secure user sessions with namespace switching
- **Admin Dashboard**: Dedicated admin interface for user and namespace management

### Web Interface & APIs
- **Modern Web UI**: Clean, responsive interface built with Thymeleaf + TailwindCSS
- **Complete REST API**: Full programmatic control for test automation
- **Real-time Updates**: Dynamic queue and mapping management
- **Docker Ready**: Full containerization with RabbitMQ and MongoDB

## 🏗️ Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   RabbitMQ      │    │   Spring Boot   │    │   MongoDB       │
│   (5672:5672)   │◄──►│   (8081:8081)   │◄──►│   (27017:27017) │
│   (15672:15672) │    │                 │    │                 │
│                 │    │ - AMQP Listeners│    │ - Queue Configs │
│ - Exchanges     │    │ - Match Engine  │    │ - Mappings      │
│ - Queues        │    │ - Response Gen  │    │ - Users/Auth    │
│ - Management UI │    │ - Header Config │    │ - Namespaces    │
│                 │    │ - User Sessions │    │ - Seed Data     │
│                 │    │ - Admin Panel   │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘

User Access Flow:
┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
│ Browser  │───►│  Login   │───►│Dashboard │───►│ Admin    │
│          │    │  Auth    │    │   UI     │    │ Panel    │
└──────────┘    └──────────┘    └──────────┘    └──────────┘
                      │              │              │
                      ▼              ▼              ▼
                ┌──────────┐    ┌──────────┐    ┌──────────┐
                │Namespace │    │ Queue &  │    │ User &   │
                │Selection │    │ Mapping  │    │Namespace │
                │          │    │ Management│    │Management│
                └──────────┘    └──────────┘    └──────────┘
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
   - **Simulator UI**: http://localhost:8081/ui
   - **Admin Panel**: http://localhost:8081/admin/ui
   - **Login Credentials**:
     - Admin: `admin` / `admin123` (access: default, demo)
     - Demo User: `demo` / `demo123` (access: demo only)
   - **RabbitMQ Management**: http://localhost:15672 (admin/passw0rd)
   - **Health Check**: http://localhost:8081/health

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

# Create a response mapping with enhanced header management
curl -X POST http://localhost:8081/admin/mappings \
  -H "Content-Type: application/json" \
  -d '{
    "queueName": "SIM.REQUEST.Q1",
    "namespace": "default",
    "priority": 1,
    "enabled": true,
    "match": {
      "correlationId": "^TEST-.*",
      "headers": {"X-Environment": "test"},
      "xpathRules": [
        {
          "xpath": "//orderId",
          "expectedValue": "12345",
          "matchType": "EQUALS"
        }
      ]
    },
    "response": {
      "type": "JSON",
      "jsonSuccessBody": "{\"status\":\"success\",\"orderId\":\"12345\"}",
      "jsonErrorBody": "{\"status\":\"error\",\"message\":\"Order not found\"}",
      "correlationIdConfig": "USE_REQUEST_MESSAGE_ID",
      "messageIdConfig": "CUSTOM_VALUE",
      "customMessageId": "MSG-12345",
      "mqHeaders": {
        "ENCODING": "UTF-8",
        "FORMAT": "MQFMT_STRING",
        "Persistence": "1"
      },
      "headerConfigs": {
        "X-Source": {
          "source": "FIXED",
          "fixedValue": "MQ_SIMULATOR"
        },
        "X-Request-ID": {
          "source": "COPY_FROM_REQUEST",
          "requestHeaderName": "X-Original-ID"
        }
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

### 3. Web UI Management

#### Dashboard (http://localhost:8081/ui)
1. **Login**: Use admin/admin123 or demo/demo123
2. **Namespace Selection**: Switch between available namespaces in top nav
3. **Queue Configurations** tab:
   - Add/edit/delete queue listeners
   - Set concurrency levels (e.g., "2-5" for 2 minimum, 5 maximum consumers)
   - Enable/disable queues
   - Namespace-isolated configurations
4. **Response Mappings** tab:
   - Create response rules with priority-based ordering
   - Configure advanced matching:
     - Correlation ID patterns (regex)
     - Header value matching
     - Body content regex
     - XPath rules for XML messages
     - JSONPath rules for JSON messages
     - Mainframe field matching
   - **Enhanced Header Management**:
     - **Correlation ID Control**: Choose from request correlation ID, request message ID, generate new UUID, or custom value
     - **Message ID Management**: Set custom message ID or omit entirely
     - **MQ-Specific Headers**: Configure ENCODING, FORMAT, CodedCharsetId, Persistence, ReplyToQ
     - **Dynamic Headers**: Copy from request headers, set fixed values, or use templates
   - Configure JSON/XML/Mainframe response bodies with success/error variants
   - Set fixed or variable delays

#### Admin Panel (http://localhost:8081/admin/ui) - Admin Users Only
1. **User Management**:
   - Create/edit/delete user accounts
   - Assign roles (Admin/User)
   - Manage namespace access permissions
2. **Namespace Management**:
   - Create/edit/delete namespaces
   - Assign users to namespaces
   - Isolate configurations between teams/projects

### 4. Authentication & Authorization 🆕

```bash
# Login via API (returns session cookie)
curl -c cookies.txt -d "username=admin&password=admin123&namespace=default" \
  -X POST http://localhost:8081/login

# Use authenticated session for API calls
curl -b cookies.txt http://localhost:8081/admin/queues

# Switch namespace in session
curl -b cookies.txt -d "namespace=demo" \
  -X POST http://localhost:8081/auth/switch-namespace

# Logout
curl -b cookies.txt -X POST http://localhost:8081/logout
```

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
  "namespace": "default",                        // 🆕 Namespace isolation
  "priority": 1,                                // 1-10, lower = higher priority
  "enabled": true,
  "match": {
    "correlationId": "^OK-.*",                  // Regex pattern
    "headers": {"X-Tenant": "acme"},            // Exact header matches
    "bodyRegex": ".*<OrderId>123</OrderId>.*",  // Body content regex
    "xpathRules": [                             // 🆕 XPath matching for XML
      {
        "xpath": "//orderId",
        "expectedValue": "12345",
        "matchType": "EQUALS"
      }
    ],
    "jsonPathRules": [                          // 🆕 JSONPath matching for JSON
      {
        "jsonPath": "$.order.id",
        "expectedValue": "12345",
        "matchType": "EQUALS"
      }
    ],
    "mainframeRules": [                         // 🆕 Mainframe field matching
      {
        "startPosition": 0,
        "length": 10,
        "expectedValue": "ORDER12345",
        "matchType": "EQUALS"
      }
    ]
  },
  "response": {
    "type": "JSON",                             // XML, JSON, or MAINFRAME

    // Enhanced response bodies with success/error variants
    "jsonSuccessBody": "{\"status\":\"success\",\"orderId\":\"12345\"}",
    "jsonErrorBody": "{\"status\":\"error\",\"message\":\"Order not found\"}",
    "xmlSuccessBody": "<result><status>OK</status></result>",
    "xmlErrorBody": "<result><error>FAILED</error></result>",
    "mainframeSuccessBody": "BASE64_ENCODED_SUCCESS_RESPONSE",
    "mainframeErrorBody": "BASE64_ENCODED_ERROR_RESPONSE",

    // 🆕 Enhanced correlation ID management
    "correlationIdConfig": "USE_REQUEST_MESSAGE_ID",  // USE_REQUEST_CORRELATION_ID, USE_REQUEST_MESSAGE_ID, GENERATE_NEW, CUSTOM_VALUE
    "customCorrelationId": "CUSTOM-CORR-ID",          // Used when correlationIdConfig = CUSTOM_VALUE

    // 🆕 Message ID management
    "messageIdConfig": "CUSTOM_VALUE",                // DONT_SET, CUSTOM_VALUE
    "customMessageId": "MSG-12345",                   // Used when messageIdConfig = CUSTOM_VALUE

    // 🆕 MQ-specific headers
    "mqHeaders": {
      "ENCODING": "UTF-8",
      "FORMAT": "MQFMT_STRING",
      "CodedCharsetId": "1208",
      "Persistence": "1",
      "ReplyToQ": "REPLY.QUEUE"
    },

    // Enhanced configurable headers
    "headerConfigs": {
      "X-Source": {
        "source": "FIXED",                      // FIXED, COPY_FROM_REQUEST, COPY_MESSAGE_ID, GENERATE_NEW_ID, CUSTOM_MESSAGE_ID
        "fixedValue": "MQ_SIMULATOR"
      },
      "X-Request-ID": {
        "source": "COPY_FROM_REQUEST",
        "requestHeaderName": "X-Original-ID"
      },
      "X-Correlation": {
        "source": "COPY_MESSAGE_ID"            // 🆕 Copy from request message ID
      },
      "X-Unique-ID": {
        "source": "GENERATE_NEW_ID"            // 🆕 Generate new UUID
      }
    },

    "overrideCorrelationId": "CUSTOM-ID"        // Legacy support (still works)
  },
  "delay": {
    "mode": "VARIABLE",                         // FIXED or VARIABLE
    "fixedMs": 120,                             // For FIXED mode
    "variableMinMs": 100,                       // For VARIABLE mode
    "variableMaxMs": 500                        // For VARIABLE mode
  }
}
```

## 🚀 Test Automation & CI/CD Integration

The MQ Simulator is designed for seamless integration into automated testing pipelines and CI/CD workflows.

### Benefits for Test Automation
- **🔄 Repeatable**: Same configurations produce consistent results
- **⚡ Fast**: No real MQ infrastructure delays or setup time
- **🛡️ Isolated**: Namespace-based separation for parallel test execution
- **💰 Cost-Effective**: No expensive MQ licenses required for testing
- **🎯 Realistic**: Proper MQ headers and message format simulation
- **📈 Scalable**: Handle high-volume testing scenarios

### CI/CD Pipeline Integration

#### Jenkins Pipeline Example
```groovy
pipeline {
    stages {
        stage('Setup Test Environment') {
            steps {
                script {
                    // Start MQ Simulator
                    sh 'docker-compose up -d mqsimulator'

                    // Wait for readiness
                    sh 'curl --retry 10 --retry-connrefused http://localhost:8081/health'

                    // Setup test namespace and configurations
                    sh '''
                        curl -X POST http://localhost:8081/admin/namespaces \\
                        -H "Content-Type: application/json" \\
                        -d '{"name":"ci-test","displayName":"CI Test Environment"}'

                        curl -X POST http://localhost:8081/admin/queues \\
                        -H "Content-Type: application/json" \\
                        -d @test-configs/queue-config.json

                        curl -X POST http://localhost:8081/admin/mappings \\
                        -H "Content-Type: application/json" \\
                        -d @test-configs/mapping-config.json
                    '''
                }
            }
        }

        stage('Run Integration Tests') {
            parallel {
                stage('Order Processing Tests') {
                    steps {
                        sh 'mvn test -Dtest.suite=OrderTests -Dmq.simulator.url=http://localhost:8081'
                    }
                }
                stage('Payment Processing Tests') {
                    steps {
                        sh 'mvn test -Dtest.suite=PaymentTests -Dmq.simulator.url=http://localhost:8081'
                    }
                }
            }
        }

        stage('Performance Tests') {
            steps {
                sh 'mvn test -Dtest.suite=LoadTests -Dmq.simulator.url=http://localhost:8081'
            }
        }

        stage('Cleanup') {
            steps {
                sh 'curl -X DELETE http://localhost:8081/admin/namespaces/ci-test'
                sh 'docker-compose down'
            }
        }
    }
}
```

#### GitHub Actions Example
```yaml
name: MQ Integration Tests
on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    services:
      mqsimulator:
        image: mqsim:latest
        ports:
          - 8081:8081
        env:
          MQ_ENABLED: true
          RABBITMQ_HOST: rabbitmq
      rabbitmq:
        image: rabbitmq:3.11-management
        ports:
          - 5672:5672
          - 15672:15672
        env:
          RABBITMQ_DEFAULT_USER: admin
          RABBITMQ_DEFAULT_PASS: passw0rd

    steps:
      - uses: actions/checkout@v3

      - name: Wait for services
        run: |
          timeout 60 bash -c 'until curl -f http://localhost:8081/health; do sleep 2; done'

      - name: Setup test environment
        run: |
          # Create test namespace
          curl -X POST http://localhost:8081/admin/namespaces \\
            -H "Content-Type: application/json" \\
            -d '{"name":"github-test","displayName":"GitHub Test"}'

          # Load test configurations
          curl -X POST http://localhost:8081/admin/queues \\
            -H "Content-Type: application/json" \\
            -d @.github/test-configs/queues.json

      - name: Run tests
        run: |
          mvn test -Dmq.simulator.url=http://localhost:8081
```

### Automated Test Scenarios

#### Python Test Framework Integration
```python
import pytest
import requests
import json
from concurrent.futures import ThreadPoolExecutor
import time

class MQSimulatorTestBase:
    def __init__(self, base_url="http://localhost:8081"):
        self.base_url = base_url
        self.session = requests.Session()

    def setup_test_environment(self, namespace="test"):
        """Setup isolated test environment"""
        # Create test namespace
        self.session.post(f"{self.base_url}/admin/namespaces",
                         json={"name": namespace, "displayName": f"Test {namespace}"})

        # Setup test queue
        self.session.post(f"{self.base_url}/admin/queues",
                         json={
                             "queueName": f"test.{namespace}.queue",
                             "namespace": namespace,
                             "enabled": True
                         })

    def configure_success_response(self, namespace="test", correlation_pattern="SUCCESS.*"):
        """Configure successful response mapping"""
        return self.session.post(f"{self.base_url}/admin/mappings", json={
            "queueName": f"test.{namespace}.queue",
            "namespace": namespace,
            "match": {"correlationId": correlation_pattern},
            "response": {
                "type": "JSON",
                "jsonSuccessBody": '{"status":"success","processed":true}',
                "correlationIdConfig": "USE_REQUEST_CORRELATION_ID"
            },
            "enabled": True,
            "priority": 1
        })

@pytest.fixture
def mq_simulator():
    simulator = MQSimulatorTestBase()
    namespace = f"test_{int(time.time())}"
    simulator.setup_test_environment(namespace)
    yield simulator, namespace
    # Cleanup
    simulator.session.delete(f"{simulator.base_url}/admin/namespaces/{namespace}")

def test_order_processing_success(mq_simulator):
    simulator, namespace = mq_simulator

    # Configure expected successful response
    simulator.configure_success_response(namespace, "ORDER-SUCCESS.*")

    # Send test message (your application logic)
    response = send_order_message(correlation_id="ORDER-SUCCESS-12345")

    # Verify response
    assert response.status_code == 200
    assert response.json()["status"] == "success"

def test_high_volume_processing(mq_simulator):
    simulator, namespace = mq_simulator

    # Configure for load testing
    simulator.configure_success_response(namespace, "LOAD.*")

    # Send concurrent messages
    def send_message(i):
        return send_order_message(correlation_id=f"LOAD-{i}")

    with ThreadPoolExecutor(max_workers=20) as executor:
        futures = [executor.submit(send_message, i) for i in range(100)]
        results = [f.result() for f in futures]

    # Verify all successful
    success_count = sum(1 for r in results if r.status_code == 200)
    assert success_count == 100
```

#### JUnit Integration Example
```java
@SpringBootTest
@TestMethodOrder(OrderAnnotation.class)
public class MQSimulatorIntegrationTest {

    @Autowired
    private OrderService orderService;

    private static final String SIMULATOR_URL = "http://localhost:8081";
    private static String testNamespace;

    @BeforeAll
    static void setupTestEnvironment() {
        testNamespace = "test_" + System.currentTimeMillis();

        // Create test namespace
        RestTemplate restTemplate = new RestTemplate();
        Map<String, String> namespace = Map.of(
            "name", testNamespace,
            "displayName", "Test Environment"
        );
        restTemplate.postForEntity(SIMULATOR_URL + "/admin/namespaces", namespace, String.class);

        // Configure test queue and mappings
        setupQueueConfiguration(restTemplate);
        setupResponseMappings(restTemplate);
    }

    @Test
    @Order(1)
    void testSuccessfulOrderProcessing() {
        // Test successful order processing
        OrderRequest request = new OrderRequest("ORDER-SUCCESS-12345", "Test Product");
        OrderResponse response = orderService.processOrder(request);

        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getOrderId()).isEqualTo("ORDER-SUCCESS-12345");
    }

    @Test
    @Order(2)
    void testErrorHandling() {
        // Test error scenarios
        OrderRequest request = new OrderRequest("ORDER-ERROR-99999", "Invalid Product");

        assertThrows(OrderProcessingException.class, () -> {
            orderService.processOrder(request);
        });
    }

    @Test
    @Order(3)
    void testPerformance() {
        // Performance test
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < 50; i++) {
            OrderRequest request = new OrderRequest("PERF-" + i, "Test Product");
            orderService.processOrder(request);
        }

        long duration = System.currentTimeMillis() - startTime;
        assertThat(duration).isLessThan(5000); // Should complete within 5 seconds
    }
}
```

### Docker Compose for Testing
```yaml
version: '3.8'
services:
  mqsimulator-test:
    image: mqsim:latest
    ports:
      - "8081:8081"
    environment:
      - MQ_ENABLED=true
      - RABBITMQ_HOST=rabbitmq
      - MONGO_URI=mongodb://mongo:27017/mqsim_test
    depends_on:
      - rabbitmq
      - mongo
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  test-runner:
    build: ./tests
    depends_on:
      mqsimulator-test:
        condition: service_healthy
    environment:
      - MQ_SIMULATOR_URL=http://mqsimulator-test:8081
    volumes:
      - ./test-results:/app/results
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
   curl http://localhost:8081/admin/queues

   # Check listener status
   curl http://localhost:8081/admin/queues/status

   # Refresh listeners manually
   curl -X POST http://localhost:8081/admin/queues/refresh

   # Check if MQ is enabled
   curl http://localhost:8081/health
   ```

3. **Messages Not Matching**:
   ```bash
   # Check mapping configurations
   curl http://localhost:8081/admin/mappings

   # Check application logs
   docker logs spring-boot-mq-simulator-backend-1

   # Verify correlation ID patterns
   # Use tools like regex101.com to test patterns

   # Test XPath/JSONPath expressions online
   # XPath: https://codebeautify.org/Xpath-Tester
   # JSONPath: https://jsonpath.com/
   ```

4. **Authentication Issues** 🆕:
   ```bash
   # Check if you're authenticated
   curl -c cookies.txt -d "username=admin&password=admin123&namespace=default" \\
     -X POST http://localhost:8081/login

   # Use session for subsequent requests
   curl -b cookies.txt http://localhost:8081/admin/queues

   # Check current user session
   curl -b cookies.txt http://localhost:8081/auth/current

   # Switch namespace if needed
   curl -b cookies.txt -d "namespace=demo" \\
     -X POST http://localhost:8081/auth/switch-namespace
   ```

5. **Namespace Issues** 🆕:
   ```bash
   # List available namespaces
   curl http://localhost:8081/admin/namespaces

   # Check user's namespace access
   curl -b cookies.txt http://localhost:8081/auth/current

   # Create new namespace (admin only)
   curl -b cookies.txt -X POST http://localhost:8081/admin/namespaces \\
     -H "Content-Type: application/json" \\
     -d '{"name":"test","displayName":"Test Environment"}'
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