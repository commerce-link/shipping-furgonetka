package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
class GetServiceProvidersResponse {

    @JsonProperty("services")
    private List<ServiceProvider> serviceProviders;

    // Getters and setters
    List<ServiceProvider> getServices() {
        return serviceProviders;
    }
}
