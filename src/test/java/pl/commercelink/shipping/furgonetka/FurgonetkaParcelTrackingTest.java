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
import pl.commercelink.shipping.api.ParcelTrackingRequest;
import pl.commercelink.shipping.api.ParcelTrackingSubscription;
import pl.commercelink.shipping.api.ShippingException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FurgonetkaParcelTrackingTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private RestApiWithRetry restApi;

    private Furgonetka furgonetka() {
        return new Furgonetka(restApi);
    }

    private static TrackingCommandStatusResponse status(String status, Integer packageId, String service, String... errors) {
        TrackingCommandStatusResponse response = new TrackingCommandStatusResponse();
        response.setStatus(status);
        response.setPackageId(packageId);
        response.setService(service);
        response.setErrors(java.util.Arrays.stream(errors).map(message -> {
            Error error = new Error();
            error.setMessage(message);
            return error;
        }).toList());
        return response;
    }

    @Test
    void supportsParcelTracking() {
        assertTrue(furgonetka().supportsParcelTracking());
    }

    @Test
    void trackParcelPutsCommandWithMappedServiceAndReturnsActiveWhenResolved() throws Exception {
        // given
        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        when(restApi.putWithAuthRetry(path.capture(), body.capture(), eq(CommandAcceptedResponse.class)))
                .thenReturn(new CommandAcceptedResponse());
        when(restApi.fetchWithAuthRetry(startsWith("/add-package-to-tracking-command/"), anyMap(), eq(TrackingCommandStatusResponse.class)))
                .thenReturn(status("successful", 21037943, "dpd"));

        // when
        ParcelTrackingSubscription result = furgonetka().trackParcel(
                new ParcelTrackingRequest("0000000123456789", "DPD Polska", "CommerceLink order-1"));

        // then
        assertTrue(path.getValue().startsWith("/add-package-to-tracking-command/"));
        String uuid = path.getValue().substring("/add-package-to-tracking-command/".length());
        assertEquals(36, uuid.length());
        JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(body.getValue()));
        assertEquals("0000000123456789", json.get("package_no").asText());
        assertEquals("dpd", json.get("service").asText());
        assertEquals("CommerceLink order-1", json.get("name").asText());
        assertEquals(ParcelTrackingSubscription.Status.ACTIVE, result.status());
        assertEquals("21037943", result.externalId());
        assertEquals("dpd", result.carrier());
        assertEquals(uuid, result.subscriptionId());
    }

    @Test
    void trackParcelOmitsServiceForUnknownCarrier() throws Exception {
        // given
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        when(restApi.putWithAuthRetry(any(), body.capture(), eq(CommandAcceptedResponse.class)))
                .thenReturn(new CommandAcceptedResponse());
        when(restApi.fetchWithAuthRetry(any(), anyMap(), eq(TrackingCommandStatusResponse.class)))
                .thenReturn(status("queueing", null, null));

        // when
        ParcelTrackingSubscription result = furgonetka().trackParcel(
                new ParcelTrackingRequest("CL123", "Kurier własny", "label"));

        // then
        JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(body.getValue()));
        assertTrue(json.get("service") == null || json.get("service").isNull());
        assertEquals(ParcelTrackingSubscription.Status.PENDING, result.status());
    }

    @Test
    void checkParcelTrackingTranslatesErrorAndUnresolvedCarrierToFailed() {
        // given
        when(restApi.fetchWithAuthRetry(eq("/add-package-to-tracking-command/cmd-1"), anyMap(), eq(TrackingCommandStatusResponse.class)))
                .thenReturn(status("error", null, null, "Nieprawidłowy numer"));
        when(restApi.fetchWithAuthRetry(eq("/add-package-to-tracking-command/cmd-2"), anyMap(), eq(TrackingCommandStatusResponse.class)))
                .thenReturn(status("successful", null, null));

        // when
        ParcelTrackingSubscription error = furgonetka().checkParcelTracking("cmd-1");
        ParcelTrackingSubscription unresolved = furgonetka().checkParcelTracking("cmd-2");

        // then
        assertEquals(ParcelTrackingSubscription.Status.FAILED, error.status());
        assertEquals("Nieprawidłowy numer", error.error());
        assertEquals(ParcelTrackingSubscription.Status.FAILED, unresolved.status());
        assertEquals("Furgonetka could not determine the carrier", unresolved.error());
        assertNull(unresolved.externalId());
    }

    @Test
    void partialSuccessWithPackageIdIsActive() {
        // given
        when(restApi.fetchWithAuthRetry(eq("/add-package-to-tracking-command/cmd-1"), anyMap(), eq(TrackingCommandStatusResponse.class)))
                .thenReturn(status("partial_success", 5, "dpd"));

        // when
        ParcelTrackingSubscription result = furgonetka().checkParcelTracking("cmd-1");

        // then
        assertEquals(ParcelTrackingSubscription.Status.ACTIVE, result.status());
        assertEquals("5", result.externalId());
        assertEquals("dpd", result.carrier());
    }

    @Test
    void checkParcelTrackingKeepsPendingWhileRunning() {
        // given
        when(restApi.fetchWithAuthRetry(any(), anyMap(), eq(TrackingCommandStatusResponse.class)))
                .thenReturn(status("running", null, null));

        // when
        ParcelTrackingSubscription result = furgonetka().checkParcelTracking("cmd-1");

        // then
        assertEquals(ParcelTrackingSubscription.Status.PENDING, result.status());
        assertEquals("cmd-1", result.subscriptionId());
    }

    @Test
    void httpFailuresSurfaceAsShippingException() {
        // given
        when(restApi.putWithAuthRetry(any(), any(), eq(CommandAcceptedResponse.class)))
                .thenThrow(new HttpClientException(429, "rate limited"));

        // then
        assertThrows(ShippingException.class, () -> furgonetka().trackParcel(
                new ParcelTrackingRequest("CL123", "DPD", "label")));
    }

    @Test
    void getTrackingEventsIsExposedThroughTheProviderContract() {
        // given
        TrackingPackageResponse response = new TrackingPackageResponse();
        when(restApi.fetchWithAuthRetry(eq("/packages/777/tracking"), anyMap(), eq(TrackingPackageResponse.class)))
                .thenReturn(response);

        // when
        List<pl.commercelink.shipping.api.TrackingEvent> events =
                ((pl.commercelink.shipping.api.ShippingProvider) furgonetka()).getTrackingEvents("777");

        // then
        assertTrue(events.isEmpty());
        verify(restApi).fetchWithAuthRetry(eq("/packages/777/tracking"), anyMap(), eq(TrackingPackageResponse.class));
    }
}
