package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
class Error {

    @JsonProperty("message")
    private String message;

    String getMessage() {
        return message;
    }
}
