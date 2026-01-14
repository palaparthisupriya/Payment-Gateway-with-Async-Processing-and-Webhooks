package com.gateway;

import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.util.Base64;

@Service
public class WebhookService {

    public void sendWebhook(String url, String secret, String payload) {
        try {
            // 1. Generate HMAC-SHA256 Signature
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            String signature = Base64.getEncoder().encodeToString(sha256_HMAC.doFinal(payload.getBytes()));

            // 2. Send the HTTP POST request
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("X-Gateway-Signature", signature) // Merchant uses this to verify
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(res -> System.out.println("Webhook sent! Response: " + res.statusCode()));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}