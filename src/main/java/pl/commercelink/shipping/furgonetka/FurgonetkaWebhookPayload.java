package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@JsonIgnoreProperties(ignoreUnknown = true)
class FurgonetkaWebhookPayload {

    @JsonProperty("package_id")
    private String packageId;

    @JsonProperty("package_no")
    private String packageNo;

    @JsonProperty("partner_order_id")
    private String partnerOrderId;

    @JsonProperty("tracking")
    private Tracking tracking;

    public String getPackageNo() {
        return packageNo;
    }

    public Tracking getTracking() {
        return tracking;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Tracking {
        @JsonProperty("state")
        private String state;
        @JsonProperty("datetime")
        private String datetime;

        public String getState() {
            return state;
        }

        public LocalDateTime getDatetime() {
            return LocalDateTime.parse(datetime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
    }
}
