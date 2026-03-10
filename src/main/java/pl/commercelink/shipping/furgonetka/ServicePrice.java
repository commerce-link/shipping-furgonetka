package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
class ServicePrice {
    @JsonProperty("service_id")
    private int serviceId;

    @JsonProperty("service")
    private String service;

    @JsonProperty("available")
    private boolean available;

    @JsonProperty("errors")
    private List<Error> errors = new LinkedList<>();

    @JsonProperty("pricing")
    private PricingV2 pricing;

    int getServiceId() {
        return serviceId;
    }

    String getService() {
        return service;
    }

    boolean isAvailable() {
        return available;
    }

    List<Error> getErrors() {
        return errors;
    }

    PricingV2 getPricing() {
        return pricing;
    }
}
