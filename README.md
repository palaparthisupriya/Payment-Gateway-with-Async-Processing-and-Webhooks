# Resilient Payment Gateway

A high-performance, fault-tolerant payment processing system built with **Java Spring Boot**, **PostgreSQL**, **Redis**, and **Docker**. This gateway supports idempotent requests, asynchronous processing, and automated webhook notifications.

---

## 🚀 Setup Instructions

### Prerequisites
* **Docker & Docker Compose**
* **Java 17+** (for local development)
* **Maven 3.8+** (for local builds)

### Quick Start
1. **Clone the repository:**
   ```bash
   git clone <your-repo-url>
   cd resilient-pay-gateway
2. Launch the entire stack:
   docker-compose up --build -d
3. Access the Interfaces:

Merchant Dashboard: http://localhost:3000

Customer Checkout: http://localhost:3001

API Health Check: http://localhost:8000/api/v1/health
## 🛠 Environment Variable Configuration
The system uses environment variables defined in the docker-compose.yml or a .env file:
Variable,Description,Default
DB_URL,PostgreSQL connection string,jdbc:postgresql://db:5432/payment_gateway
REDIS_HOST,Redis server address,redis
WEBHOOK_RETRY_LIMIT,Max attempts for failed webhooks,5
IDEMPOTENCY_EXPIRY,Cache duration for requests (hours),24
## 📡 API Endpoint Documentation
1. Create Payment
POST /api/v1/payments

Headers: Idempotency-Key: <unique_string>

Body: ```json { "amount": 100, "method": "upi" }

Response: 201 Created - Returns payment ID and pending status.

2. Refund Payment
POST /api/v1/payments/{id}/refunds

Body: ```json { "amount": 50, "reason": "item_return" }

Note: Logic prevents refunds that exceed the original payment amount.

3. Merchant Stats
GET /api/v1/merchants/{merchant_id}/stats

Response: Returns aggregated data: total_sales, total_refunds, and net_revenue.
## 🛡 Resiliency & Testing Instructions
Testing Idempotency
Send the same Idempotency-Key in the header for two identical requests. The system will recognize the key and return the cached response from the database instead of creating a duplicate payment.

Testing Fault Tolerance (Worker Recovery)
Stop the background worker: docker stop gateway_worker

Create a payment via the API or Checkout UI.

Verify the queue: Check Redis to see the job waiting: docker exec -it redis_gateway redis-cli LLEN payment_jobs

Restart the worker: docker start gateway_worker

Verify status: The payment status in the database will move from pending to success automatically.
## 🪝 Webhook Integration Guide
The gateway sends an asynchronous POST request to the merchant's configured webhook_url upon status changes.

Security & Verification
Each request includes an X-Gateway-Signature header. This is an HMAC-SHA256 hash of the JSON payload signed with the merchant's webhook_secret.

Verification Example (Node.js):
const crypto = require('crypto');

const signature = req.headers['x-gateway-signature'];
const hmac = crypto.createHmac('sha256', process.env.WEBHOOK_SECRET);
const expectedSignature = hmac.update(JSON.stringify(req.body)).digest('hex');

if (signature === expectedSignature) {
    console.log("Webhook verified!");
}
## 📦 SDK Integration Guide (Quick Start)
To integrate this gateway into your own application:

Initialize Request: Generate a UUID for the Idempotency-Key for every new transaction attempt.

Handle Redirect: Send the customer to the Checkout UI: http://localhost:3001/?amount=100.

Webhook Listener: Create a public endpoint to receive and verify status updates to update your order database.

## 💎 Bonus Artifacts
* **Architecture:**
 Located in `/artifacts/architecture_diagram.png`
* **API Spec:** 
OpenAPI 3.0 spec available in `/artifacts/openapi.json`
* **Visual Proof:** 
Screenshots of the dashboard and webhook logs are in `/artifacts/`