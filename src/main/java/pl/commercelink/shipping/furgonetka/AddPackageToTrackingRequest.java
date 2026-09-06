package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
class AddPackageToTrackingRequest {

    @JsonProperty("package_no")
    private final String packageNo;

    @JsonProperty("service")
    private final String service;

    @JsonProperty("name")
    private final String name;

    AddPackageToTrackingRequest(String packageNo, String service, String name) {
        this.packageNo = packageNo;
        this.service = service;
        this.name = name;
    }

    public String getPackageNo() {
        return packageNo;
    }

    public String getService() {
        return service;
    }

    public String getName() {
        return name;
    }
}
