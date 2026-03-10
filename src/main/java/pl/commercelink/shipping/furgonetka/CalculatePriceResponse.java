package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
class CalculatePriceResponse {
    @JsonProperty("services_prices")
    private List<ServicePrice> servicesPrices;

    // Getters and setters
    List<ServicePrice> getServicesPrices() {
        return servicesPrices;
    }
}