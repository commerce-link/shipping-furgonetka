package pl.commercelink.shipping.furgonetka;

import org.junit.jupiter.api.Test;
import pl.commercelink.provider.api.WebhookContext;
import pl.commercelink.provider.api.WebhookOutcome;
import pl.commercelink.shipping.api.ShippingWebhookResult;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class FurgonetkaWebhookExecutorTest {

    private static final String TOKEN = "cltest-token-123";
    private static final String DELIVERED_PAYLOAD =
            "{\"package_id\":21037944,\"package_no\":\"CLTEST20260902A\",\"partner_order_id\":\"0\","
            + "\"tracking\":{\"datetime\":\"2026-09-02 13:30:00\",\"state\":\"delivered\",\"description\":\"Dor\\u0119czono\",\"branch\":\"\"},"
            + "\"control\":\"9984a99f622f4e1c26ebd035319c2454\"}";
    private static final String TEST_PAYLOAD =
            "{\"package_id\":123456,\"package_no\":\"003203043026\",\"partner_order_id\":\"\","
            + "\"tracking\":{\"state\":\"collected\",\"description\":\"Dor\\u0119czona\",\"datetime\":\"2026-09-02 12:39:24\",\"branch\":\"Warszawa\"},"
            + "\"control\":\"b53dd14e2afa0135bbd2bcd927301744\"}";

    private final FurgonetkaWebhookExecutor executor = new FurgonetkaWebhookExecutor();

    private static WebhookContext context(String token) {
        return new WebhookContext(Map.of(), token == null ? Map.of() : Map.of("webhookToken", token));
    }

    @Test
    void mapsDeliveredPayloadWhenChecksumMatches() {
        // when
        WebhookOutcome<ShippingWebhookResult> outcome = executor.execute(DELIVERED_PAYLOAD, context(TOKEN));

        // then
        assertNotNull(outcome.result());
        assertEquals("CLTEST20260902A", outcome.result().trackingNo());
        assertEquals(ShippingWebhookResult.ShipmentState.DELIVERED, outcome.result().state());
        assertEquals(LocalDateTime.of(2026, 9, 2, 13, 30, 0), outcome.result().datetime());
    }

    @Test
    void mapsCollectedTestPayloadWithBranchAndEmptyPartnerOrderId() {
        // when
        WebhookOutcome<ShippingWebhookResult> outcome = executor.execute(TEST_PAYLOAD, context(TOKEN));

        // then
        assertNotNull(outcome.result());
        assertEquals(ShippingWebhookResult.ShipmentState.COLLECTED, outcome.result().state());
        assertEquals("003203043026", outcome.result().trackingNo());
    }

    @Test
    void ignoresPayloadWhenChecksumDoesNotMatchConfiguredToken() {
        // when
        WebhookOutcome<ShippingWebhookResult> outcome = executor.execute(DELIVERED_PAYLOAD, context("other-token"));

        // then
        assertNull(outcome.result());
        assertNotNull(outcome.responseBody());
    }

    @Test
    void skipsVerificationWhenNoTokenConfigured() {
        // when
        WebhookOutcome<ShippingWebhookResult> outcome = executor.execute(DELIVERED_PAYLOAD, context(null));

        // then
        assertNotNull(outcome.result());
    }

    @Test
    void acceptsIsoOffsetDatetimeAsFallback() {
        // given
        String payload = DELIVERED_PAYLOAD.replace("2026-09-02 13:30:00", "2026-09-02T13:30:00+02:00");

        // when
        WebhookOutcome<ShippingWebhookResult> outcome = executor.execute(payload, context(null));

        // then
        assertEquals(LocalDateTime.of(2026, 9, 2, 13, 30, 0), outcome.result().datetime());
    }
}
