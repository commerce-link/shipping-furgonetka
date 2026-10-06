package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.rest.client.HttpClientException;
import pl.commercelink.rest.client.RestApiWithRetry;
import pl.commercelink.shipping.api.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FurgonetkaPickupTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final PickupWindow WINDOW =
            new PickupWindow(LocalDate.of(2026, 10, 6), LocalTime.of(14, 0), LocalTime.of(17, 0), "dec58008");

    @Mock
    private RestApiWithRetry restApi;

    private Furgonetka furgonetka() {
        return new Furgonetka(restApi);
    }

    private static String proposal(String date, String from, String to, boolean available, String hash) {
        return "{\"date\":\"" + date + "\",\"min_time\":\"" + from + "\",\"max_time\":\"" + to + "\",\"available\":"
                + available + ",\"hash\":\"" + hash + "\"}";
    }

    @Test
    void windowsAreTheAvailableOnesCommonToAllPackagesSorted() throws Exception {
        // given
        String body = "{\"packages\":["
                + "{\"package_id\":\"1\",\"proposals\":[" + proposal("2026-10-07", "09:00", "17:00", true, "b") + ","
                + proposal("2026-10-06", "14:00", "17:00", true, "a") + "," + proposal("2026-10-08", "09:00", "17:00", false, "c") + "]},"
                + "{\"package_id\":\"2\",\"proposals\":[" + proposal("2026-10-06", "14:00", "17:00", true, "a") + ","
                + proposal("2026-10-08", "09:00", "17:00", true, "c") + "," + proposal("2026-10-07", "09:00", "17:00", true, "b") + "]}]}";
        ArgumentCaptor<Object> request = ArgumentCaptor.forClass(Object.class);
        when(restApi.postWithAuthRetry(eq("/packages/pickup-date-proposals"), request.capture(), eq(PickupDateProposalsResponse.class)))
                .thenReturn(JSON.readValue(body, PickupDateProposalsResponse.class));

        // when
        List<PickupWindow> windows = furgonetka().pickupWindows(List.of("1", "2"), LocalDate.of(2026, 10, 6), 3);

        // then
        assertEquals(List.of("a", "b"), windows.stream().map(PickupWindow::token).toList());
        assertEquals(LocalTime.of(14, 0), windows.get(0).from());
        JsonNode sent = JSON.readTree(JSON.writeValueAsString(request.getValue()));
        assertEquals("2026-10-06", sent.get("ready_date").asText());
        assertEquals(3, sent.get("days_ahead").asInt());
        assertEquals(2, sent.get("packages").size());
    }

    @Test
    void orderPickupSendsOneCommandForAllPackagesWithTheWindow() throws Exception {
        // given
        ArgumentCaptor<Object> request = ArgumentCaptor.forClass(Object.class);
        when(restApi.putWithAuthRetry(eq("/pickup-commands/cmd-1"), request.capture(), eq(Void.class))).thenReturn(null);

        // when
        PickupOrder order = furgonetka().orderPickup(List.of("1", "2"), WINDOW, "cmd-1");

        // then
        assertEquals(CommandStatus.PENDING, order.status());
        JsonNode sent = JSON.readTree(JSON.writeValueAsString(request.getValue()));
        assertEquals(2, sent.get("packages").size());
        assertEquals("2026-10-06", sent.get("pickup_date").get("date").asText());
        assertEquals("14:00", sent.get("pickup_date").get("min_time").asText());
        assertEquals("17:00", sent.get("pickup_date").get("max_time").asText());
        assertEquals("dec58008", sent.get("pickup_date").get("hash").asText());
    }

    @Test
    void orderPickupRefusedThrows() {
        // given
        when(restApi.putWithAuthRetry(eq("/pickup-commands/cmd-1"), any(), eq(Void.class)))
                .thenThrow(new HttpClientException(400, "{\"errors\":[{\"message\":\"Termin niedostępny\"}]}"));

        // when / then
        assertThrows(ShippingException.class, () -> furgonetka().orderPickup(List.of("1"), WINDOW, "cmd-1"));
    }

    @Test
    void orderPickupWithServerErrorStaysPending() {
        // given
        when(restApi.putWithAuthRetry(eq("/pickup-commands/cmd-1"), any(), eq(Void.class)))
                .thenThrow(new HttpClientException(500, "boom"));

        // when
        PickupOrder order = furgonetka().orderPickup(List.of("1"), WINDOW, "cmd-1");

        // then
        assertEquals(CommandStatus.PENDING, order.status());
        assertEquals("cmd-1", order.commandId());
    }

    @Test
    void orderPickupWithTimeoutStaysPending() {
        // given
        when(restApi.putWithAuthRetry(eq("/pickup-commands/cmd-1"), any(), eq(Void.class)))
                .thenThrow(new RuntimeException("timeout"));

        // when
        PickupOrder order = furgonetka().orderPickup(List.of("1"), WINDOW, "cmd-1");

        // then
        assertEquals(CommandStatus.PENDING, order.status());
        assertEquals("cmd-1", order.commandId());
    }

    @Test
    void checkOfASuccessfulPickupGivesItsId() throws Exception {
        // given
        when(restApi.fetchWithAuthRetry(eq("/pickup-commands/cmd-1"), anyMap(), eq(PickupCommandStatusResponse.class)))
                .thenReturn(JSON.readValue("{\"status\":\"successful\",\"errors\":[],\"pickup_details\":[{\"pickup_id\":"
                        + "\"20261006800071\",\"package_ids\":[21480003,21480004]}]}", PickupCommandStatusResponse.class));

        // when
        PickupOrder order = furgonetka().checkPickupOrder("cmd-1");

        // then
        assertEquals(CommandStatus.SUCCEEDED, order.status());
        assertEquals("20261006800071", order.pickupId());
        assertEquals(List.of("21480003", "21480004"), order.externalIds());
    }

    @Test
    void checkOfAPartialPickupListsThePackagesOfEveryDetail() throws Exception {
        // given
        when(restApi.fetchWithAuthRetry(eq("/pickup-commands/cmd-1"), anyMap(), eq(PickupCommandStatusResponse.class)))
                .thenReturn(JSON.readValue("{\"status\":\"partial_success\",\"errors\":[],\"pickup_details\":["
                        + "{\"pickup_id\":\"20261006800071\",\"package_ids\":[21480003]},"
                        + "{\"pickup_id\":\"20261006800072\",\"package_ids\":[21480005]}]}",
                        PickupCommandStatusResponse.class));

        // when
        PickupOrder order = furgonetka().checkPickupOrder("cmd-1");

        // then
        assertEquals(CommandStatus.SUCCEEDED, order.status());
        assertEquals("20261006800071", order.pickupId());
        assertEquals(List.of("21480003", "21480005"), order.externalIds());
    }

    @Test
    void checkOfAFailedPickupCarriesTheMessage() throws Exception {
        // given
        when(restApi.fetchWithAuthRetry(eq("/pickup-commands/cmd-1"), anyMap(), eq(PickupCommandStatusResponse.class)))
                .thenReturn(JSON.readValue("{\"status\":\"error\",\"errors\":[{\"message\":\"Brak możliwości podjazdu\"}]}",
                        PickupCommandStatusResponse.class));

        // when
        PickupOrder order = furgonetka().checkPickupOrder("cmd-1");

        // then
        assertEquals(CommandStatus.FAILED, order.status());
        assertEquals("Brak możliwości podjazdu", order.error());
    }

    @Test
    void checkOfAnUnknownPickupCommandFails() {
        // given
        when(restApi.fetchWithAuthRetry(eq("/pickup-commands/cmd-1"), anyMap(), eq(PickupCommandStatusResponse.class)))
                .thenThrow(new HttpClientException(400, "{\"errors\":[{\"code\":\"commandNotExists\"}]}"));

        // when / then
        assertEquals(CommandStatus.FAILED, furgonetka().checkPickupOrder("cmd-1").status());
    }
}
