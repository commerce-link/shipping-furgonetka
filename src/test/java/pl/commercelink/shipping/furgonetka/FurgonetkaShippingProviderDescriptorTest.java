package pl.commercelink.shipping.furgonetka;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pl.commercelink.provider.api.AuthConfig;
import pl.commercelink.provider.api.ProviderField;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FurgonetkaShippingProviderDescriptorTest {

    @AfterEach
    void clearOverride() {
        System.clearProperty("FURGONETKA_API_URL");
    }

    @Test
    void defaultsToProductionApiUrl() {
        // when
        AuthConfig.OAuth2 auth = (AuthConfig.OAuth2) new FurgonetkaShippingProviderDescriptor().authConfig();

        // then
        assertEquals("https://api.furgonetka.pl", auth.apiUrl());
    }

    @Test
    void systemPropertyOverridesApiUrl() {
        // given
        System.setProperty("FURGONETKA_API_URL", "https://api.sandbox.furgonetka.pl");

        // when
        AuthConfig.OAuth2 auth = (AuthConfig.OAuth2) new FurgonetkaShippingProviderDescriptor().authConfig();

        // then
        assertEquals("https://api.sandbox.furgonetka.pl", auth.apiUrl());
    }

    @Test
    void exposesOptionalWebhookTokenField() {
        // when
        ProviderField field = new FurgonetkaShippingProviderDescriptor().configurationFields().stream()
                .filter(f -> f.key().equals("webhookToken"))
                .findFirst()
                .orElseThrow();

        // then
        assertEquals(ProviderField.FieldType.PASSWORD, field.type());
        assertFalse(field.required());
        assertTrue(new FurgonetkaShippingProviderDescriptor().configurationFields().stream()
                .anyMatch(f -> f.key().equals("apiUrl")));
    }
}
