package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
class ServiceProvider {
    @JsonProperty("id")
    private int id;

    @JsonProperty("service")
    private String service;

    @JsonProperty("name")
    private String name;

    int getId() {
        return id;
    }

    String getService() {
        return service;
    }

    String getName() {
        return name;
    }
}
