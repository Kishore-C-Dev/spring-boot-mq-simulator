#!/bin/bash

# Test script for sending various types of messages to the MQ simulator
# Usage: ./test-messages.sh

set -e

echo "🚀 Starting MQ Testing Simulator Test Suite"
echo "============================================="

# Check if services are running
echo "📋 Checking service status..."
if ! docker ps | grep -q "ibmmq"; then
    echo "❌ IBM MQ container not running. Please start with: docker compose up -d"
    exit 1
fi

if ! docker ps | grep -q "mqsim-backend"; then
    echo "❌ Backend container not running. Please start with: docker compose up -d"
    exit 1
fi

# Wait for services to be ready
echo "⏳ Waiting for services to be ready..."
timeout=60
while [ $timeout -gt 0 ]; do
    if curl -s http://localhost:8080/ready | grep -q "READY"; then
        echo "✅ Services are ready!"
        break
    fi
    echo "   Waiting... ($timeout seconds remaining)"
    sleep 2
    timeout=$((timeout-2))
done

if [ $timeout -le 0 ]; then
    echo "❌ Services failed to become ready within 60 seconds"
    exit 1
fi

echo ""
echo "🧪 Running Test Cases"
echo "===================="

# Test 1: OK response
echo "Test 1: Sending OK message..."
{
    echo "OK-12345"
    echo "<request><orderId>12345</orderId></request>"
    echo ""
    echo ""
} | docker exec -i ibmmq /opt/mqm/samp/bin/amqsput SIM.REQUEST.Q1 QM1 > /dev/null 2>&1

# Test 2: Error response  
echo "Test 2: Sending ERR message..."
{
    echo "ERR-99999"
    echo "<request><orderId>99999</orderId></request>"
    echo ""
    echo ""
} | docker exec -i ibmmq /opt/mqm/samp/bin/amqsput SIM.REQUEST.Q1 QM1 > /dev/null 2>&1

# Test 3: Mainframe response
echo "Test 3: Sending MF message..."
{
    echo "MF-1001"
    echo "MAINFRAME_REQUEST_DATA"
    echo ""
    echo ""
} | docker exec -i ibmmq /opt/mqm/samp/bin/amqsput SIM.REQUEST.Q2 QM1 > /dev/null 2>&1

# Test 4: Default response
echo "Test 4: Sending default message..."
{
    echo "DEFAULT-TEST"
    echo "<request>default test</request>"
    echo ""
    echo ""
} | docker exec -i ibmmq /opt/mqm/samp/bin/amqsput SIM.REQUEST.Q2 QM1 > /dev/null 2>&1

echo ""
echo "📨 Messages sent! Waiting 3 seconds for processing..."
sleep 3

echo ""
echo "📥 Retrieving Responses"
echo "====================="

echo "Getting responses from SIM.REPLY.DEFAULT queue:"
echo "------------------------------------------------"

# Get all responses
timeout=10
response_count=0
while [ $timeout -gt 0 ] && [ $response_count -lt 4 ]; do
    response=$(docker exec ibmmq /opt/mqm/samp/bin/amqsget SIM.REPLY.DEFAULT QM1 2>/dev/null | head -20)
    if [ -n "$response" ]; then
        response_count=$((response_count+1))
        echo "Response $response_count:"
        echo "$response"
        echo "---"
    fi
    timeout=$((timeout-1))
    if [ $response_count -lt 4 ] && [ $timeout -gt 0 ]; then
        sleep 1
    fi
done

if [ $response_count -eq 0 ]; then
    echo "❌ No responses received. Check the logs:"
    echo "   docker logs mqsim-backend"
else
    echo "✅ Retrieved $response_count response(s)"
fi

echo ""
echo "📊 Service Status"
echo "================"

echo "Listener Status:"
curl -s http://localhost:8080/admin/queues/status | jq '.' 2>/dev/null || echo "Status endpoint not accessible"

echo ""
echo "Queue Configurations:"
curl -s http://localhost:8080/admin/queues | jq 'length' 2>/dev/null | xargs echo "Found queues:"

echo ""
echo "Response Mappings:"
curl -s http://localhost:8080/admin/mappings | jq 'length' 2>/dev/null | xargs echo "Found mappings:"

echo ""
echo "🎉 Test Suite Complete!"
echo "======================="
echo ""
echo "🌐 Access the Web UI at: http://localhost:8080/ui"
echo "🔧 Check IBM MQ Console at: https://localhost:9443 (admin/passw0rd)"
echo "📋 View application logs: docker logs mqsim-backend"
echo ""