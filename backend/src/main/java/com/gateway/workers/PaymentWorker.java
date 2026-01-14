package com.gateway;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.EnableScheduling; // Added
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

@Component
@EnableScheduling // REQUIRED: This tells Spring to actually run the @Scheduled tasks
public class PaymentWorker {

    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    private final Random random = new Random();

    @Scheduled(fixedDelay = 2000) // Reduced to 2s for faster dashboard updates
    public void processJobs() {
        // 1. Process Payments
        String paymentId = redisTemplate.opsForList().rightPop("payment_jobs");
        if (paymentId != null) {
            System.out.println("Worker found payment job: " + paymentId);
            processPayment(paymentId);
        }

        // 2. Process Refunds
        String refundId = redisTemplate.opsForList().rightPop("refund_jobs");
        if (refundId != null) {
            System.out.println("Worker found refund job: " + refundId);
            processRefund(refundId);
        }
    }

    private void processPayment(String paymentId) {
        try {
            Map<String, Object> payment = jdbcTemplate.queryForMap("SELECT * FROM payments WHERE id = ?", paymentId);
            String method = (String) payment.get("method");
            
            // Required Success Logic: 90% for UPI, 95% for Card
            double threshold = "upi".equalsIgnoreCase(method) ? 0.90 : 0.95;
            String finalStatus = (random.nextDouble() < threshold) ? "success" : "failed";

            jdbcTemplate.update("UPDATE payments SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", finalStatus, paymentId);
            System.out.println("Payment " + paymentId + " marked as: " + finalStatus);
            
            createWebhookEvent(payment.get("merchant_id"), "payment." + finalStatus, payment);
        } catch (Exception e) {
            System.err.println("Error processing payment: " + paymentId + " - " + e.getMessage());
        }
    }

    private void processRefund(String refundId) {
        try {
            Map<String, Object> refund = jdbcTemplate.queryForMap("SELECT * FROM refunds WHERE id = ?", refundId);
            
            jdbcTemplate.update("UPDATE refunds SET status = 'processed', processed_at = CURRENT_TIMESTAMP WHERE id = ?", refundId);
            System.out.println("Refund " + refundId + " processed successfully.");
            
            createWebhookEvent(refund.get("merchant_id"), "refund.processed", refund);
        } catch (Exception e) {
            System.err.println("Error processing refund: " + refundId + " - " + e.getMessage());
        }
    }

    private void createWebhookEvent(Object merchantId, String eventType, Object data) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                "event", eventType,
                "data", data,
                "created_at", System.currentTimeMillis()
            ));

            jdbcTemplate.update(
                "INSERT INTO webhook_logs (merchant_id, event, payload, status) VALUES (CAST(? AS UUID), ?, CAST(? AS JSONB), 'pending')",
                merchantId, eventType, payload
            );
        } catch (Exception e) {
            System.err.println("Webhook log error: " + e.getMessage());
        }
    }
}