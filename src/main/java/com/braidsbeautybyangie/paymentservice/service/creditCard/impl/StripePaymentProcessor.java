package com.braidsbeautybyangie.paymentservice.service.creditCard.impl;

import com.braidsbeautybyangie.paymentservice.service.creditCard.CreditCardProcessorRemoteService;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pe.com.gamacommerce.corelibraryservicegamacommerce.aggregates.AppExceptions.CreditCardProcessorUnavailableException;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Implementación de procesador de pagos usando Stripe
 * Reemplaza al simulador anterior con integración real de Stripe
 */
@Service
@Slf4j
public class StripePaymentProcessor implements CreditCardProcessorRemoteService {

    @Value("${stripe.secret.key}")
    private String stripeSecretKey;

    @Value("${stripe.currency:usd}")
    private String currency;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeSecretKey;
        log.info("Stripe Payment Processor initialized successfully");
    }

    @Override
    public void process(BigInteger cardNumber, BigDecimal amount) {
        try {
            log.info("Processing payment with Stripe - Amount: {}", amount);

            // Convertir el monto a centavos (Stripe trabaja en centavos)
            long amountInCents = amount.multiply(new BigDecimal("100")).longValue();

            // Crear PaymentIntent
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency(currency)
                    .setDescription("AngieBraids - Order Payment")
                    .putMetadata("card_last4", getLastFourDigits(cardNumber))
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build()
                    )
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);

            log.info("Payment Intent created successfully - ID: {}, Status: {}",
                    intent.getId(), intent.getStatus());

            // Validar que el pago fue exitoso
            if (!"succeeded".equals(intent.getStatus()) &&
                    !"requires_payment_method".equals(intent.getStatus()) &&
                    !"requires_confirmation".equals(intent.getStatus())) {
                throw new CreditCardProcessorUnavailableException(
                        new Throwable("Payment failed with status: " + intent.getStatus()));
            }

        } catch (StripeException e) {
            log.error("Stripe payment processing failed: {}", e.getMessage(), e);
            throw new CreditCardProcessorUnavailableException(
                    new Throwable("Credit card processing unavailable: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error processing payment: {}", e.getMessage(), e);
            throw new CreditCardProcessorUnavailableException(
                    new Throwable("Unexpected error: " + e.getMessage()));
        }
    }

    /**
     * Crea un PaymentIntent y retorna el client secret para el frontend
     */
    public String createPaymentIntent(BigDecimal amount, String description) {
        try {
            long amountInCents = amount.multiply(new BigDecimal("100")).longValue();

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency(currency)
                    .setDescription(description)
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build()
                    )
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);
            log.info("PaymentIntent created - ID: {}", intent.getId());

            return intent.getClientSecret();

        } catch (StripeException e) {
            log.error("Failed to create PaymentIntent: {}", e.getMessage(), e);
            throw new CreditCardProcessorUnavailableException(
                    new Throwable("Failed to create payment intent: " + e.getMessage()));
        }
    }

    /**
     * Confirma un PaymentIntent existente
     */
    public PaymentIntent confirmPaymentIntent(String paymentIntentId) {
        try {
            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);

            if ("requires_confirmation".equals(intent.getStatus())) {
                intent = intent.confirm();
            }

            log.info("PaymentIntent confirmed - ID: {}, Status: {}",
                    intent.getId(), intent.getStatus());

            return intent;

        } catch (StripeException e) {
            log.error("Failed to confirm PaymentIntent: {}", e.getMessage(), e);
            throw new CreditCardProcessorUnavailableException(
                    new Throwable("Failed to confirm payment: " + e.getMessage()));
        }
    }

    /**
     * Verifica el estado de un PaymentIntent
     */
    public String getPaymentStatus(String paymentIntentId) {
        try {
            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
            return intent.getStatus();
        } catch (StripeException e) {
            log.error("Failed to retrieve PaymentIntent status: {}", e.getMessage(), e);
            return "failed";
        }
    }

    /**
     * Obtiene los últimos 4 dígitos de la tarjeta para logging seguro
     */
    private String getLastFourDigits(BigInteger cardNumber) {
        String cardStr = cardNumber.toString();
        if (cardStr.length() >= 4) {
            return cardStr.substring(cardStr.length() - 4);
        }
        return "****";
    }
}