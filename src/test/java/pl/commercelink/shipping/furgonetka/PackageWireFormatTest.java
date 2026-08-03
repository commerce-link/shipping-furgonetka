package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackageWireFormatTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void receiverCarriesPointCodeUnderThePointKey() throws Exception {
        // given
        Address receiver = new Address();
        receiver.setName("Jan Kowalski");
        receiver.setStreet("Prosta 1");
        receiver.setPostcode("00-001");
        receiver.setCity("Warszawa");
        receiver.setCountryCode("PL");
        receiver.setPoint("KRA01M");

        // when
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(receiver));

        // then
        assertEquals("KRA01M", json.get("point").asText());
        assertEquals("Prosta 1", json.get("street").asText());
        assertEquals("PL", json.get("country_code").asText());
    }

    @Test
    void addressWithoutPointSerialisesPointAsNull() throws Exception {
        // given
        Address receiver = new Address();
        receiver.setStreet("Prosta 1");

        // when
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(receiver));

        // then
        assertTrue(json.get("point").isNull());
    }
}
