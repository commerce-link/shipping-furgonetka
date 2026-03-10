package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.annotation.JsonProperty;

class CalculatePriceRequest {
    @JsonProperty("services")
    private Services services;
    @JsonProperty("package")
    private Package aPackage;

    void setServices(Services services) {
        this.services = services;
    }

    void setaPackage(Package aPackage) {
        this.aPackage = aPackage;
    }
}
