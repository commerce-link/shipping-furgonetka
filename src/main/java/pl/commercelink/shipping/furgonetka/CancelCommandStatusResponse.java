package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
class CancelCommandStatusResponse {

    @JsonProperty("status")
    private String status;

    @JsonProperty("errors")
    private List<Error> errors = new ArrayList<>();

    @JsonProperty("cancel_command_details")
    private List<Detail> details = new ArrayList<>();

    String getStatus() {
        return status;
    }

    List<Error> getErrors() {
        return errors == null ? List.of() : errors;
    }

    List<Detail> getDetails() {
        return details == null ? List.of() : details;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Detail {

        // a JSON number at Furgonetka; kept as text to compare with the package id we store
        @JsonProperty("package_id")
        private String packageId;

        @JsonProperty("cancel_success")
        private Boolean cancelSuccess;

        // how Furgonetka settled the package, e.g. "success"; logged only, its values are not documented
        @JsonProperty("success_message_type")
        private String successMessageType;

        String getPackageId() {
            return packageId;
        }

        String getSuccessMessageType() {
            return successMessageType;
        }

        boolean isCancelled() {
            return Boolean.TRUE.equals(cancelSuccess);
        }
    }
}
