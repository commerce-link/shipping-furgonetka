package pl.commercelink.shipping.furgonetka;

import pl.commercelink.provider.api.AuthConfig;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.rest.client.RestApiWithRetry;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.shipping.api.ShippingProviderDescriptor;

import java.util.List;
import java.util.Map;

import static pl.commercelink.provider.api.ProviderField.FieldType.PASSWORD;
import static pl.commercelink.provider.api.ProviderField.FieldType.TEXT;

public class FurgonetkaShippingProviderDescriptor implements ShippingProviderDescriptor {

    private static final String DEFAULT_API_URL = "https://api.furgonetka.pl";

    @Override
    public String name() {
        return "furgonetka";
    }

    @Override
    public String displayName() {
        return "Furgonetka";
    }

    @Override
    public AuthConfig authConfig() {
        return new AuthConfig.OAuth2(
                DEFAULT_API_URL,
                "/oauth/token",
                "/oauth/token",
                29L * 24 * 60 * 60,
                "application/vnd.furgonetka.v1+json");
    }

    @Override
    public List<ProviderField> configurationFields() {
        return List.of(
                new ProviderField("apiUrl", "API URL", TEXT, true, DEFAULT_API_URL),
                new ProviderField("username", "API Username", TEXT, true, ""),
                new ProviderField("password", "API Password", PASSWORD, true, ""),
                new ProviderField("clientId", "Client ID", TEXT, true, ""),
                new ProviderField("clientSecret", "Client Secret", PASSWORD, true, ""));
    }

    @Override
    public ShippingProvider create(Map<String, String> configuration) {
        throw new UnsupportedOperationException("Furgonetka requires OAuth2 context");
    }

    @Override
    public ShippingProvider create(Map<String, String> configuration, Map<String, Object> context) {
        RestApiWithRetry restApi = (RestApiWithRetry) context.get("restApi");
        return new Furgonetka(restApi);
    }
}
