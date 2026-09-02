package pl.commercelink.shipping.furgonetka;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FurgonetkaCarrierServicesTest {

    @ParameterizedTest
    @CsvSource({
            "InPost, inpost",
            "Paczkomat InPost, inpost",
            "DPD, dpd",
            "DPD Polska, dpd",
            "DHL, dhl",
            "GLS, gls",
            "UPS, ups",
            "FedEx, fedex",
            "Poczta Polska, poczta",
            "Pocztex, poczta",
            "Orlen, orlen",
            "Orlen Paczka, orlen",
            "RUCH, orlen",
            "Meest, meest",
            "Ambro Express, ambroexpress",
            "Xpress Delivery, xpress"
    })
    void mapsKnownCarrierNames(String carrier, String expected) {
        assertEquals(Optional.of(expected), FurgonetkaCarrierServices.serviceFor(carrier));
    }

    @Test
    void unknownBlankOrNullCarrierLeavesDetectionToFurgonetka() {
        assertTrue(FurgonetkaCarrierServices.serviceFor("Kurier własny").isEmpty());
        assertTrue(FurgonetkaCarrierServices.serviceFor("  ").isEmpty());
        assertTrue(FurgonetkaCarrierServices.serviceFor(null).isEmpty());
    }
}
