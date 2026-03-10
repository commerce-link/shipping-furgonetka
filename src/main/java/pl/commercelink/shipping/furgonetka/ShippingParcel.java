package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
class ShippingParcel {
    @JsonProperty("package_no")
    private String packageNo;
    @JsonProperty("description")
    private String description;
    @JsonProperty("width")
    private int width;
    @JsonProperty("depth")
    private int depth;
    @JsonProperty("height")
    private int height;
    @JsonProperty("weight")
    private int weight;
    @JsonProperty("value")
    private int value;
    @JsonProperty("tracking_url")
    private String trackingUrl;
    @JsonProperty("service")
    private String service;
    @JsonProperty("type")
    private String type;

    ShippingParcel() {
    }

    ShippingParcel(int width, int depth, int height, int weight, int value, String description, String type) {
        this.width = width;
        this.depth = depth;
        this.height = height;
        this.weight = weight;
        this.value = value;
        this.description = description;
        this.type = type;
    }

    String getPackageNo() {
        return packageNo;
    }

    String getService() {
        return service;
    }

    String getTrackingUrl() {
        return trackingUrl;
    }
}
