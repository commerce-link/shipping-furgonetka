package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
class PricingV2 {
    @JsonProperty("price_gross")
    private double priceGross;
    @JsonProperty("price_net")
    private double priceNet;

    // Getters and setters
    double getPriceGross() {
        return priceGross;
    }

    double getPriceNet() {
        return priceNet;
    }
}
