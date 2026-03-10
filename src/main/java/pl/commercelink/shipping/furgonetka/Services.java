package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

class Services {

    @JsonProperty("service_id")
    private Set<Integer> serviceId;

    Services(Set<Integer> serviceId) {
        this.serviceId = serviceId;
    }

    Set<Integer> getServiceId() {
        return serviceId;
    }
}