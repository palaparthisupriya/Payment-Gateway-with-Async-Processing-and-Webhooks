package com.gateway.workers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.*;

@Component
public class WebhookWorker {
    @Autowired private JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate = new RestTemplate();

    @Scheduled(fixedDelay = 10000)
    public void processWebhooks() {
        List<Map<String, Object>> pending = jdbcTemplate.queryForList(
            "SELECT wl.*, m.webhook_url, m.webhook_secret FROM webhook_logs wl " +
            "JOIN merchants m ON wl.merchant_id = m.id " +
            "WHERE wl.status = 'pending' AND (wl.next_retry_at IS NULL OR wl.next_retry_at <= CURRENT_TIMESTAMP) LIMIT 10"
        );

        for (Map<String, Object> webhook : pending) {
            deliver(webhook);
        }
    }

    private void deliver(Map<String, Object> log) {
        try {
            String payload = log.get("payload").toString();
            String secret = log.get("webhook_secret").toString();
            String url = log.get("webhook_url").toString();
            int attempts = ((Number) log.get("attempts")).intValue() + 1;

            String signature = generateHMAC(payload, secret);
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Gateway-Signature", signature);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            updateLog(log.get("id"), "success", attempts, response.getStatusCode().value(), response.getBody(), null);
        } catch (Exception e) {
            int attempts = ((Number) log.get("attempts")).intValue() + 1;
            if (attempts >= 5) {
                updateLog(log.get("id"), "failed", attempts, 500, e.getMessage(), null);
            } else {
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.MINUTE, attempts * 5); 
                updateLog(log.get("id"), "pending", attempts, 500, e.getMessage(), cal.getTime());
            }
        }
    }

    private String generateHMAC(String data, String key) throws Exception {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        sha256_HMAC.init(new SecretKeySpec(key.getBytes(), "HmacSHA256"));
        byte[] raw = sha256_HMAC.doFinal(data.getBytes());
        StringBuilder hex = new StringBuilder();
        for (byte b : raw) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private void updateLog(Object id, String status, int attempts, int code, String body, Date next) {
        jdbcTemplate.update(
            "UPDATE webhook_logs SET status = ?, attempts = ?, response_code = ?, response_body = ?, next_retry_at = ?, last_attempt_at = CURRENT_TIMESTAMP WHERE id = ?",
            status, attempts, code, body, next, id
        );
    }
}