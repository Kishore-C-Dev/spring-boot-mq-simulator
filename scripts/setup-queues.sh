#!/bin/bash

# Script to set up additional queues in IBM MQ
# This ensures all required queues exist for the simulator

echo "🔧 Setting up IBM MQ Queues"
echo "============================"

# Check if MQ is running
if ! docker ps | grep -q "ibmmq"; then
    echo "❌ IBM MQ container not running. Please start with: docker compose up -d"
    exit 1
fi

echo "📋 Creating required queues..."

# Create required queues
docker exec ibmmq runmqsc QM1 << 'EOF'
* Define request queues
DEFINE QLOCAL(SIM.REQUEST.Q1) MAXDEPTH(5000) REPLACE
DEFINE QLOCAL(SIM.REQUEST.Q2) MAXDEPTH(5000) REPLACE
DEFINE QLOCAL(SIM.REQUEST.Q3) MAXDEPTH(5000) REPLACE

* Define reply queues
DEFINE QLOCAL(SIM.REPLY.DEFAULT) MAXDEPTH(5000) REPLACE

* Define dead letter queue
DEFINE QLOCAL(SIM.DEAD.LETTER) MAXDEPTH(1000) REPLACE

* Display created queues
DISPLAY QLOCAL(SIM.*)

* Display channel status
DISPLAY CHANNEL(DEV.APP.SVRCONN)

EOF

echo ""
echo "✅ Queue setup complete!"
echo ""
echo "📋 Created Queues:"
echo "  - SIM.REQUEST.Q1   (Request queue 1)"
echo "  - SIM.REQUEST.Q2   (Request queue 2)" 
echo "  - SIM.REQUEST.Q3   (Request queue 3)"
echo "  - SIM.REPLY.DEFAULT (Default reply queue)"
echo "  - SIM.DEAD.LETTER  (Dead letter queue)"
echo ""