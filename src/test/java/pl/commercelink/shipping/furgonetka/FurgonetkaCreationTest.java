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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FurgonetkaCreationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock
    private RestApiWithRetry restApi;

    private Furgonetka furgonetka() {
        return new Furgonetka(restApi);
    }

    private static ShipmentRequest request() {
        ShipmentAddress address = new ShipmentAddress("Jan Nadawca", null, "Marszałkowska 10", "00-590", "Warszawa", "PL",
                "test@example.com", "500600700");
        return ShipmentRequest.builder().pickup(address).sender(address).receiver(address)
                .parcels(List.of(new Parcel(20, 20, 20, 1, 100, "test", "package"))).carrierId("11906596").build();
    }

    private static <T> T json(String body, Class<T> type) throws Exception {
        return JSON.readValue(body, type);
    }

    @Test
    void createShipmentOrdersThePackageUnderTheCallersCommandAndReturnsPendingWithItsId() throws Exception {
        // given
        when(restApi.postWithAuthRetry(eq("/packages"), any(), eq(Package.class)))
                .thenReturn(json("{\"package_id\":\"21480003\"}", Package.class));
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        when(restApi.putWithAuthRetry(eq("/order-commands/cmd-1"), body.capture(), eq(Void.class))).thenReturn(null);

        // when
        ShipmentCreation result = furgonetka().createShipment(request(), "cmd-1");

        // then
        assertEquals(CommandStatus.PENDING, result.status());
        assertEquals("21480003", result.externalId());
        JsonNode sent = JSON.readTree(JSON.writeValueAsString(body.getValue()));
        assertEquals("21480003", sent.get("packages").get(0).get("id").asText());
        assertFalse(sent.get("only_order_pickup").asBoolean());
        verify(restApi, never()).putWithAuthRetry(startsWith("/pickup-commands/"), any(), any());
    }

    @Test
    void createShipmentRefusedByFurgonetkaThrowsWithTheHttpCause() {
        // given
        when(restApi.postWithAuthRetry(eq("/packages"), any(), eq(Package.class)))
                .thenThrow(new HttpClientException(400, "{\"errors\":[{\"message\":\"Nieprawidłowy kod pocztowy\"}]}"));

        // when
        ShippingException e = assertThrows(ShippingException.class, () -> furgonetka().createShipment(request(), "cmd-1"));

        // then
        assertInstanceOf(HttpClientException.class, e.getCause());
    }

    @Test
    void createShipmentWhoseOrderCommandGotNoAnswerIsPendingWithTheKnownPackage() throws Exception {
        // given: the package exists, the order command call timed out (it may or may not have reached Furgonetka)
        when(restApi.postWithAuthRetry(eq("/packages"), any(), eq(Package.class)))
                .thenReturn(json("{\"package_id\":\"21480003\"}", Package.class));
        when(restApi.putWithAuthRetry(eq("/order-commands/cmd-1"), any(), eq(Void.class)))
                .thenThrow(new RuntimeException("HTTP request failed"));

        // when
        ShipmentCreation result = furgonetka().createShipment(request(), "cmd-1");

        // then
        assertEquals(CommandStatus.PENDING, result.status());
        assertEquals("21480003", result.externalId());
    }

    @Test
    void checkOfASuccessfulCommandReadsTheParcelsAndWhetherAPickupIsNeeded() throws Exception {
        // given
        when(restApi.fetchWithAuthRetry(eq("/order-commands/cmd-1"), anyMap(), eq(OrderCommandStatusResponse.class)))
                .thenReturn(json("{\"status\":\"successful\",\"errors\":[],\"successfully_ordered_packages\":[21480003]}",
                        OrderCommandStatusResponse.class));
        when(restApi.fetchWithAuthRetry(eq("/packages/21480003"), anyMap(), eq(Package.class)))
                .thenReturn(json("{\"package_id\":\"21480003\",\"pickup_available\":true,\"parcels\":[{\"package_no\":"
                        + "\"0000889416460Q\",\"service\":\"dpd\",\"tracking_url\":\"https://t/1\"}]}", Package.class));

        // when
        ShipmentCreation result = furgonetka().checkShipmentCreation("cmd-1", "21480003");

        // then
        assertEquals(CommandStatus.SUCCEEDED, result.status());
        ShipmentResult.ShipmentParcelResult parcel = result.result().parcels().get(0);
        assertEquals("0000889416460Q", parcel.trackingNo());
        assertEquals("dpd", parcel.carrier());
        assertTrue(parcel.pickupRequired());
    }

    @Test
    void checkOfAPackageWhoseCourierFurgonetkaBookedCarriesThePickupNumberAndNeedsNoPickup() throws Exception {
        // given: shape of a DPD customer return read from the sandbox (pickup booked with the order command)
        when(restApi.fetchWithAuthRetry(eq("/order-commands/cmd-1"), anyMap(), eq(OrderCommandStatusResponse.class)))
                .thenReturn(json("{\"status\":\"successful\",\"errors\":[],\"successfully_ordered_packages\":[21486850]}",
                        OrderCommandStatusResponse.class));
        when(restApi.fetchWithAuthRetry(eq("/packages/21486850"), anyMap(), eq(Package.class)))
                .thenReturn(json("{\"package_id\":\"21486850\",\"pickup_available\":false,"
                        + "\"pickup_number\":\"APP/CRIN/13023761\",\"pickup_date\":null,\"state\":\"ordered\","
                        + "\"service\":\"dpd\",\"type\":\"package\",\"parcels\":[{\"package_no\":\"0000014898901T\","
                        + "\"service\":\"dpd\",\"state\":\"ordered\"}]}", Package.class));

        // when
        ShipmentCreation result = furgonetka().checkShipmentCreation("cmd-1", "21486850");

        // then
        ShipmentResult.ShipmentParcelResult parcel = result.result().parcels().get(0);
        assertEquals("0000014898901T", parcel.trackingNo());
        assertFalse(parcel.pickupRequired());
        assertEquals("APP/CRIN/13023761", parcel.pickupNumber());
    }

    @Test
    void checkOfAPackageWithABlankPickupNumberStillNeedsAPickup() throws Exception {
        // given
        when(restApi.fetchWithAuthRetry(eq("/order-commands/cmd-1"), anyMap(), eq(OrderCommandStatusResponse.class)))
                .thenReturn(json("{\"status\":\"successful\",\"successfully_ordered_packages\":[21480003]}",
                        OrderCommandStatusResponse.class));
        when(restApi.fetchWithAuthRetry(eq("/packages/21480003"), anyMap(), eq(Package.class)))
                .thenReturn(json("{\"package_id\":\"21480003\",\"pickup_available\":true,\"pickup_number\":\"\","
                        + "\"parcels\":[{\"package_no\":\"X1\"}]}", Package.class));

        // when
        ShipmentCreation result = furgonetka().checkShipmentCreation("cmd-1", "21480003");

        // then
        ShipmentResult.ShipmentParcelResult parcel = result.result().parcels().get(0);
        assertTrue(parcel.pickupRequired());
        assertNull(parcel.pickupNumber());
    }

    @Test
    void packageSentToFurgonetkaDoesNotCarryAPickupNumber() throws Exception {
        // given
        Package pkg = json("{\"package_id\":\"1\",\"pickup_number\":\"APP/CRIN/1\"}", Package.class);

        // when
        JsonNode sent = JSON.readTree(JSON.writeValueAsString(pkg));

        // then
        assertFalse(sent.has("pickup_number"));
    }

    @Test
    void checkWithoutExternalIdReadsOrderedPackageFromCommand() throws Exception {
        // given
        when(restApi.fetchWithAuthRetry(eq("/order-commands/cmd-1"), anyMap(), eq(OrderCommandStatusResponse.class)))
                .thenReturn(json("{\"status\":\"successful\",\"successfully_ordered_packages\":[21480003]}",
                        OrderCommandStatusResponse.class));
        when(restApi.fetchWithAuthRetry(eq("/packages/21480003"), anyMap(), eq(Package.class)))
                .thenReturn(json("{\"package_id\":\"21480003\",\"pickup_available\":false,\"parcels\":[{\"package_no\":\"X1\"}]}",
                        Package.class));

        // when
        ShipmentCreation result = furgonetka().checkShipmentCreation("cmd-1", null);

        // then
        assertEquals("21480003", result.externalId());
        assertFalse(result.result().parcels().get(0).pickupRequired());
    }

    @Test
    void checkOfAFailedCommandJoinsFurgonetkasMessages() throws Exception {
        // given
        when(restApi.fetchWithAuthRetry(eq("/order-commands/cmd-1"), anyMap(), eq(OrderCommandStatusResponse.class)))
                .thenReturn(json("{\"status\":\"error\",\"errors\":[{\"message\":\"Wystąpił błąd podczas komunikacji z API "
                        + "przewoźnika\"}]}", OrderCommandStatusResponse.class));

        // when
        ShipmentCreation result = furgonetka().checkShipmentCreation("cmd-1", "21480106");

        // then
        assertEquals(CommandStatus.FAILED, result.status());
        assertEquals("Wystąpił błąd podczas komunikacji z API przewoźnika", result.error());
    }

    @Test
    void checkOfACommandFurgonetkaDoesNotKnowYetStaysPending() {
        // given
        when(restApi.fetchWithAuthRetry(eq("/order-commands/cmd-1"), anyMap(), eq(OrderCommandStatusResponse.class)))
                .thenThrow(new HttpClientException(400, "{\"errors\":[{\"code\":\"commandNotExists\"}]}"));

        // when
        ShipmentCreation result = furgonetka().checkShipmentCreation("cmd-1", "21480003");

        // then: an unanswered command may still be saved, so failing it would invite a second paid package
        assertEquals(CommandStatus.PENDING, result.status());
        assertEquals("21480003", result.externalId());
    }

    @Test
    void checkOfARunningCommandIsPending() throws Exception {
        // given
        when(restApi.fetchWithAuthRetry(eq("/order-commands/cmd-1"), anyMap(), eq(OrderCommandStatusResponse.class)))
                .thenReturn(json("{\"status\":\"waiting\"}", OrderCommandStatusResponse.class));

        // when / then
        assertEquals(CommandStatus.PENDING, furgonetka().checkShipmentCreation("cmd-1", "21480003").status());
    }
}
