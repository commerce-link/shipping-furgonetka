package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.rest.client.RestApiWithRetry;
import pl.commercelink.shipping.api.ShipmentCancellation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FurgonetkaCancellationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String COMMAND_PATH = "/cancel-command/cmd-1";

    @Mock
    private RestApiWithRetry restApi;

    private Furgonetka furgonetka() {
        return new Furgonetka(restApi);
    }

    // shape copied from the sandbox answer of 2026-09-29 (package_id is a JSON number)
    private static CancelCommandStatusResponse response(String json) throws Exception {
        return OBJECT_MAPPER.readValue(json, CancelCommandStatusResponse.class);
    }

    private void answer(String json) throws Exception {
        when(restApi.fetchWithAuthRetry(eq(COMMAND_PATH), anyMap(), eq(CancelCommandStatusResponse.class)))
                .thenReturn(response(json));
    }

    @Test
    void cancelShipmentPutsCommandForOnePackageAndReturnsPendingWithItsUuid() throws Exception {
        // given
        when(restApi.fetchWithAuthRetry(eq("/packages/21353832/tracking"), anyMap(), eq(TrackingPackageResponse.class)))
                .thenReturn(new TrackingPackageResponse());
        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        when(restApi.putWithAuthRetry(path.capture(), body.capture(), eq(Void.class))).thenReturn(null);

        // when
        ShipmentCancellation result = furgonetka().cancelShipment("21353832");

        // then
        assertTrue(path.getValue().startsWith("/cancel-command/"));
        String uuid = path.getValue().substring("/cancel-command/".length());
        assertEquals(36, uuid.length());
        JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(body.getValue()));
        assertEquals(1, json.get("packages").size());
        assertEquals("21353832", json.get("packages").get(0).get("id").asText());
        assertEquals(ShipmentCancellation.Status.PENDING, result.status());
        assertEquals(uuid, result.commandId());
    }

    @Test
    void runningCommandIsPending() throws Exception {
        // given
        answer("{\"status\":\"running\",\"errors\":[],\"uuid\":\"cmd-1\"}");

        // when
        ShipmentCancellation result = furgonetka().checkShipmentCancellation("cmd-1", "21353832");

        // then
        assertEquals(ShipmentCancellation.Status.PENDING, result.status());
    }

    @Test
    void unknownStatusIsPending() throws Exception {
        // given
        answer("{\"status\":\"something-new\"}");

        // when / then
        assertEquals(ShipmentCancellation.Status.PENDING,
                furgonetka().checkShipmentCancellation("cmd-1", "21353832").status());
    }

    @Test
    void successfulCommandWithOurPackageCancelledSucceeds() throws Exception {
        // given
        answer("{\"status\":\"successful\",\"errors\":[],\"uuid\":\"cmd-1\",\"cancel_command_details\":"
                + "[{\"package_id\":21353832,\"cancel_success\":true,\"success_message_type\":\"success\",\"scheduled_cancel_date\":\"\"}]}");

        // when
        ShipmentCancellation result = furgonetka().checkShipmentCancellation("cmd-1", "21353832");

        // then
        assertEquals(ShipmentCancellation.Status.SUCCEEDED, result.status());
        assertEquals("cmd-1", result.commandId());
        assertTrue(result.otherCancelledPackageIds().isEmpty());
    }

    @Test
    void otherCancelledPackagesAreReported() throws Exception {
        // given
        answer("{\"status\":\"successful\",\"cancel_command_details\":["
                + "{\"package_id\":21353832,\"cancel_success\":true},"
                + "{\"package_id\":21353833,\"cancel_success\":true},"
                + "{\"package_id\":21353834,\"cancel_success\":false}]}");

        // when
        ShipmentCancellation result = furgonetka().checkShipmentCancellation("cmd-1", "21353832");

        // then
        assertEquals(ShipmentCancellation.Status.SUCCEEDED, result.status());
        assertEquals(List.of("21353833"), result.otherCancelledPackageIds());
    }

    @Test
    void ourPackageNotCancelledFailsWithFallbackMessage() throws Exception {
        // given
        answer("{\"status\":\"partial_success\",\"errors\":[],\"cancel_command_details\":"
                + "[{\"package_id\":21353832,\"cancel_success\":false}]}");

        // when
        ShipmentCancellation result = furgonetka().checkShipmentCancellation("cmd-1", "21353832");

        // then
        assertEquals(ShipmentCancellation.Status.FAILED, result.status());
        assertEquals("Furgonetka did not cancel package 21353832", result.error());
    }

    @Test
    void errorCommandFailsWithJoinedMessages() throws Exception {
        // given: sandbox answer for a package still in the basket
        answer("{\"status\":\"error\",\"errors\":[{\"path\":\"/packages/id/21353556\",\"message\":\"Przesyłka znajduje się w koszyku.\"},"
                + "{\"message\":\"Second reason\"}],\"uuid\":\"cmd-1\",\"cancel_command_details\":[]}");

        // when
        ShipmentCancellation result = furgonetka().checkShipmentCancellation("cmd-1", "21353556");

        // then
        assertEquals(ShipmentCancellation.Status.FAILED, result.status());
        assertEquals("Przesyłka znajduje się w koszyku.; Second reason", result.error());
    }
}
