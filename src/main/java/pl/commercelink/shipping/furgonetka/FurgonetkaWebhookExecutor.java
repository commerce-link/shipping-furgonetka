package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import pl.commercelink.provider.api.WebhookContext;
import pl.commercelink.provider.api.WebhookExecutor;
import pl.commercelink.provider.api.WebhookOutcome;
import pl.commercelink.provider.api.WebhookStatusResponse;
import pl.commercelink.shipping.api.ShippingException;
import pl.commercelink.shipping.api.ShippingWebhookResult;

class FurgonetkaWebhookExecutor implements WebhookExecutor<ShippingWebhookResult> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final WebhookStatusResponse STATUS_OK = new WebhookStatusResponse("OK");

    @Override
    public WebhookOutcome<ShippingWebhookResult> execute(String payload, WebhookContext ctx) {
        if (payload == null || payload.isBlank()) {
            return WebhookOutcome.of(null, STATUS_OK);
        }
        try {
            FurgonetkaWebhookPayload parsed = OBJECT_MAPPER.readValue(payload, FurgonetkaWebhookPayload.class);
            ShippingWebhookResult.ShipmentState state = switch (parsed.getTracking().getState()) {
                case "collected" -> ShippingWebhookResult.ShipmentState.COLLECTED;
                case "delivered" -> ShippingWebhookResult.ShipmentState.DELIVERED;
                default -> ShippingWebhookResult.ShipmentState.OTHER;
            };
            ShippingWebhookResult result = new ShippingWebhookResult(
                    parsed.getPackageNo(), state, parsed.getTracking().getDatetime());
            return WebhookOutcome.of(result, STATUS_OK);
        } catch (JsonProcessingException e) {
            throw new ShippingException("Failed to parse webhook payload", e);
        }
    }
}
