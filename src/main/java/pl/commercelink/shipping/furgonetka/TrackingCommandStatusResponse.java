package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
class TrackingCommandStatusResponse {

    @JsonProperty("status")
    private String status;

    @JsonProperty("package_id")
    private Integer packageId;

    @JsonProperty("service")
    private String service;

    @JsonProperty("errors")
    private List<Error> errors = new ArrayList<>();

    String getStatus() {
        return status;
    }

    void setStatus(String status) {
        this.status = status;
    }

    Integer getPackageId() {
        return packageId;
    }

    void setPackageId(Integer packageId) {
        this.packageId = packageId;
    }

    String getService() {
        return service;
    }

    void setService(String service) {
        this.service = service;
    }

    List<Error> getErrors() {
        return errors == null ? List.of() : errors;
    }

    void setErrors(List<Error> errors) {
        this.errors = errors;
    }
}
