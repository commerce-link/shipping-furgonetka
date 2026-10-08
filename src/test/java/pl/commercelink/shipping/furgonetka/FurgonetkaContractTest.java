package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.rest.client.RestApiWithRetry;
import pl.commercelink.shipping.api.*;
import pl.commercelink.shipping.api.testing.ShippingProviderContractTest;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FurgonetkaContractTest extends ShippingProviderContractTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock
    private RestApiWithRetry restApi;

    @BeforeEach
    void stubFurgonetka() throws Exception {
        when(restApi.postWithAuthRetry(eq("/packages"), any(), eq(Package.class)))
                .thenReturn(JSON.readValue("{\"package_id\":\"21480003\"}", Package.class));
        when(restApi.fetchWithAuthRetry(startsWith("/order-commands/"), anyMap(), eq(OrderCommandStatusResponse.class)))
                .thenReturn(JSON.readValue("{\"status\":\"waiting\"}", OrderCommandStatusResponse.class));
        when(restApi.fetchWithAuthRetry(eq("/packages/21480003"), anyMap(), eq(Package.class)))
                .thenReturn(JSON.readValue("{\"package_id\":\"21480003\",\"pickup_available\":true,\"parcels\":"
                        + "[{\"package_no\":\"0000889416460Q\",\"service\":\"dpd\"}]}", Package.class));
        when(restApi.postWithAuthRetry(eq("/packages/pickup-date-proposals"), any(), eq(PickupDateProposalsResponse.class)))
                .thenReturn(JSON.readValue("{\"packages\":[{\"package_id\":\"21480003\",\"proposals\":["
                        + "{\"date\":\"2026-10-07\",\"min_time\":\"09:00\",\"max_time\":\"17:00\",\"available\":true,\"hash\":\"b\"},"
                        + "{\"date\":\"2026-10-06\",\"min_time\":\"14:00\",\"max_time\":\"17:00\",\"available\":true,\"hash\":\"a\"}]}]}",
                        PickupDateProposalsResponse.class));
    }

    protected ShippingProvider provider() {
        return new Furgonetka(restApi);
    }

    protected ShipmentRequest sampleRequest() {
        ShipmentAddress a = new ShipmentAddress("Jan", null, "Marszałkowska 10", "00-590", "Warszawa", "PL", "t@e.pl", "500600700");
        return ShipmentRequest.builder().pickup(a).sender(a).receiver(a)
                .parcels(List.of(new Parcel(20, 20, 20, 1, 100, "test", "package"))).carrierId("11906596").build();
    }

    protected void completeCreation(String commandId) {
        try {
            when(restApi.fetchWithAuthRetry(eq("/order-commands/" + commandId), anyMap(), eq(OrderCommandStatusResponse.class)))
                    .thenReturn(JSON.readValue("{\"status\":\"successful\",\"successfully_ordered_packages\":[21480003]}",
                            OrderCommandStatusResponse.class));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    protected ShipmentCreation failCreation(String commandId) {
        try {
            when(restApi.fetchWithAuthRetry(eq("/order-commands/" + commandId), anyMap(), eq(OrderCommandStatusResponse.class)))
                    .thenReturn(JSON.readValue("{\"status\":\"error\",\"errors\":[{\"message\":\"Carrier rejected the package\"}]}",
                            OrderCommandStatusResponse.class));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return provider().checkShipmentCreation(commandId, "21480003");
    }
}
