package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import pl.commercelink.provider.api.WebhookContext;
import pl.commercelink.provider.api.WebhookExecutor;
import pl.commercelink.provider.api.WebhookOutcome;
import pl.commercelink.provider.api.WebhookStatusResponse;
import pl.commercelink.shipping.api.ShippingException;
import pl.commercelink.shipping.api.ShippingWebhookResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

class FurgonetkaWebhookExecutor implements WebhookExecutor<ShippingWebhookResult> {

    static final String WEBHOOK_TOKEN_KEY = "webhookToken";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final WebhookStatusResponse STATUS_OK = new WebhookStatusResponse("OK");

    @Override
    public WebhookOutcome<ShippingWebhookResult> execute(String payload, WebhookContext ctx) {
        if (payload == null || payload.isBlank()) {
            return WebhookOutcome.of(null, STATUS_OK);
        }
        try {
            FurgonetkaWebhookPayload parsed = OBJECT_MAPPER.readValue(payload, FurgonetkaWebhookPayload.class);
            if (!checksumValid(parsed, ctx)) {
                return WebhookOutcome.of(null, STATUS_OK);
            }
            if (parsed.getTracking() == null || parsed.getTracking().getState() == null) {
                return WebhookOutcome.of(null, STATUS_OK);
            }
            Optional<LocalDateTime> datetime = parsed.getTracking().parsedDatetime();
            if (datetime.isEmpty()) {
                return WebhookOutcome.of(null, STATUS_OK);
            }
            ShippingWebhookResult.ShipmentState state = switch (parsed.getTracking().getState()) {
                case "collected" -> ShippingWebhookResult.ShipmentState.COLLECTED;
                case "delivered" -> ShippingWebhookResult.ShipmentState.DELIVERED;
                default -> ShippingWebhookResult.ShipmentState.OTHER;
            };
            ShippingWebhookResult result = new ShippingWebhookResult(
                    parsed.getPackageNo(), state, datetime.get());
            return WebhookOutcome.of(result, STATUS_OK);
        } catch (JsonProcessingException e) {
            throw new ShippingException("Failed to parse webhook payload", e);
        }
    }

    private static boolean checksumValid(FurgonetkaWebhookPayload parsed, WebhookContext ctx) {
        String token = ctx == null || ctx.providerConfig() == null ? null : ctx.providerConfig().get(WEBHOOK_TOKEN_KEY);
        if (token == null || token.isBlank()) {
            return true;
        }
        String expected = md5Hex(parsed.checksumInput(token));
        return parsed.getControl() != null && expected.equalsIgnoreCase(parsed.getControl());
    }

    static String md5Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new ShippingException("MD5 not available", e);
        }
    }
}
