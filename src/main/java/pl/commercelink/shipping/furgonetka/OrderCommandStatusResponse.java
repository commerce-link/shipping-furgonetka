package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
class OrderCommandStatusResponse {

    @JsonProperty("status")
    private String status;
    @JsonProperty("errors")
    private List<Error> errors = new ArrayList<>();
    // JSON numbers at Furgonetka; kept as text to compare with the package id we store
    @JsonProperty("successfully_ordered_packages")
    private List<String> successfullyOrderedPackages = new ArrayList<>();

    String getStatus() {
        return status;
    }

    List<Error> getErrors() {
        return errors == null ? List.of() : errors;
    }

    List<String> getSuccessfullyOrderedPackages() {
        return successfullyOrderedPackages == null ? List.of() : successfullyOrderedPackages;
    }
}
