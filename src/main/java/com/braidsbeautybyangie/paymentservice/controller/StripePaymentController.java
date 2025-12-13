package com.braidsbeautybyangie.paymentservice.controller;

import com.braidsbeautybyangie.paymentservice.model.response.ApiResponse;
import com.braidsbeautybyangie.paymentservice.service.creditCard.impl.StripePaymentProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Controlador REST para operaciones de pago con Stripe
 * Expone endpoints para crear y confirmar PaymentIntents desde el frontend
 */
@RestController
@RequestMapping("/v1/payment-service/api/v1/stripe")
@RequiredArgsConstructor
@Slf4j
public class StripePaymentController {

    private final StripePaymentProcessor stripeProcessor;

    /**
     * Crea un PaymentIntent y retorna el client secret
     * El frontend usará este secret para completar el pago con Stripe Elements
     *
     * POST /api/v1/stripe/create-payment-intent
     * Body: {
     *   "amount": 10.99,
     *   "description": "Order #12345"
     * }
     */
    @Value("${STRIPE_PUBLISHABLE_KEY}")
    private String stripePublishableKey;
    @PostMapping("/create-payment-intent")
    public ResponseEntity<ApiResponse<Map<String, String>>> createPaymentIntent(
            @RequestBody Map<String, Object> request) {

        try {
            BigDecimal amount = new BigDecimal(request.get("amount").toString());
            String description = request.getOrDefault("description", "AngieBraids Order").toString();

            log.info("Creating PaymentIntent - Amount: {}, Description: {}", amount, description);

            String clientSecret = stripeProcessor.createPaymentIntent(amount, description);

            return ResponseEntity.ok(ApiResponse.<Map<String, String>>builder()
                    .code("200")
                    .message("Payment intent created successfully")
                    .data(Map.of(
                            "clientSecret", clientSecret,
                            "publishableKey", stripePublishableKey
                    ))
                    .build());

        } catch (Exception e) {
            log.error("Error creating payment intent: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.<Map<String, String>>builder()
                    .code("400")
                    .message("Failed to create payment intent: " + e.getMessage())
                    .build());
        }
    }

    /**
     * Verifica el estado de un PaymentIntent
     *
     * GET /api/v1/stripe/payment-status/{paymentIntentId}
     */
    @GetMapping("/payment-status/{paymentIntentId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> getPaymentStatus(
            @PathVariable String paymentIntentId) {

        try {
            log.info("Checking payment status for: {}", paymentIntentId);
            String status = stripeProcessor.getPaymentStatus(paymentIntentId);

            return ResponseEntity.ok(ApiResponse.<Map<String, String>>builder()
                    .code("200")
                    .message("Payment status retrieved")
                    .data(Map.of(
                            "paymentIntentId", paymentIntentId,
                            "status", status
                    ))
                    .build());

        } catch (Exception e) {
            log.error("Error retrieving payment status: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.<Map<String, String>>builder()
                    .code("400")
                    .message("Failed to retrieve payment status: " + e.getMessage())
                    .build());
        }
    }

    /**
     * Endpoint de configuración para obtener la publishable key
     *
     * GET /api/v1/stripe/config
     */
    @GetMapping("/config")
    public ResponseEntity<ApiResponse<Map<String, String>>> getStripeConfig() {
        return ResponseEntity.ok(ApiResponse.<Map<String, String>>builder()
                .code("200")
                .message("Stripe configuration")
                .data(Map.of("publishableKey", stripePublishableKey))
                .build());
    }

    /**
     * Webhook para recibir eventos de Stripe
     * Esto te permitirá manejar eventos asíncronos como pagos exitosos
     *
     * POST /api/v1/stripe/webhook
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        try {
            log.info("Received Stripe webhook event");
            // TODO: Implementar verificación de firma y procesamiento de eventos
            // Ver: https://stripe.com/docs/webhooks/signatures

            return ResponseEntity.ok("Webhook received");

        } catch (Exception e) {
            log.error("Error processing webhook: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Webhook processing failed");
        }
    }
}