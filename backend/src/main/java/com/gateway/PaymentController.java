package com.gateway;

import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") 
public class PaymentController {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private ObjectMapper objectMapper;

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    @PostMapping("/payments")
    public ResponseEntity<Object> createPayment(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody Map<String, Object> request) {

        String merchantId = "550e8400-e29b-41d4-a716-446655440000";
        System.out.println("Received payment request for amount: " + request.get("amount"));

        if (idempotencyKey != null) {
            try {
                List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                    "SELECT response_payload FROM idempotency_keys WHERE key = ? AND merchant_id = CAST(? AS UUID) AND expires_at > CURRENT_TIMESTAMP",
                    idempotencyKey, merchantId
                );
                if (!existing.isEmpty()) {
                    return ResponseEntity.ok(objectMapper.readValue(existing.get(0).get("response_payload").toString(), Object.class));
                }
            } catch (Exception ignored) {}
        }

        String paymentId = "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        
        // Save to DB
        jdbcTemplate.update(
            "INSERT INTO payments (id, merchant_id, amount, method, status) VALUES (?, CAST(? AS UUID), ?, ?, ?)",
            paymentId, merchantId, request.get("amount"), request.get("method"), "pending"
        );

        Map<String, Object> responseBody = Map.of("id", paymentId, "status", "pending", "amount", request.get("amount"));

        // Save Idempotency
        if (idempotencyKey != null) {
            try {
                String jsonResponse = objectMapper.writeValueAsString(responseBody);
                jdbcTemplate.update(
                    "INSERT INTO idempotency_keys (key, merchant_id, response_payload, expires_at) VALUES (?, CAST(? AS UUID), CAST(? AS JSONB), ?)",
                    idempotencyKey, merchantId, jsonResponse, Timestamp.from(Instant.now().plus(24, ChronoUnit.HOURS))
                );
            } catch (Exception ignored) {}
        }

        // Push to Redis
        redisTemplate.opsForList().leftPush("payment_jobs", paymentId);
        System.out.println("✅ Payment saved and queued: " + paymentId);
        
        return ResponseEntity.status(201).body(responseBody);
    }

    @GetMapping("/merchants/{merchantId}/stats")
    public ResponseEntity<Map<String, Object>> getMerchantStats(@PathVariable String merchantId) {
        try {
            Long totalSales = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM payments WHERE merchant_id = CAST(? AS UUID) AND status = 'success'", Long.class, merchantId);
            Long totalRefunds = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM refunds WHERE merchant_id = CAST(? AS UUID) AND status = 'processed'", Long.class, merchantId);

            Map<String, Object> stats = new HashMap<>();
            stats.put("total_sales", totalSales != null ? totalSales : 0);
            stats.put("total_refunds", totalRefunds != null ? totalRefunds : 0);
            stats.put("net_revenue", (totalSales != null ? totalSales : 0) - (totalRefunds != null ? totalRefunds : 0));
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}