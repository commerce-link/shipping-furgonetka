package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
class FurgonetkaWebhookPayload {

    private static final DateTimeFormatter LOCAL_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @JsonProperty("package_id")
    private String packageId;

    @JsonProperty("package_no")
    private String packageNo;

    @JsonProperty("partner_order_id")
    private String partnerOrderId;

    @JsonProperty("tracking")
    private Tracking tracking;

    @JsonProperty("control")
    private String control;

    public String getPackageId() {
        return packageId;
    }

    public String getPackageNo() {
        return packageNo;
    }

    public String getPartnerOrderId() {
        return partnerOrderId;
    }

    public Tracking getTracking() {
        return tracking;
    }

    public String getControl() {
        return control;
    }

    String checksumInput(String token) {
        Tracking t = tracking == null ? new Tracking() : tracking;
        return nullToEmpty(packageId) + nullToEmpty(packageNo) + nullToEmpty(partnerOrderId)
                + nullToEmpty(t.state) + nullToEmpty(t.description) + nullToEmpty(t.datetime) + nullToEmpty(t.branch)
                + nullToEmpty(token);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Tracking {
        @JsonProperty("state")
        private String state;
        @JsonProperty("description")
        private String description;
        @JsonProperty("datetime")
        private String datetime;
        @JsonProperty("branch")
        private String branch;

        public String getState() {
            return state;
        }

        public String getDescription() {
            return description;
        }

        public String getDatetime() {
            return datetime;
        }

        Optional<LocalDateTime> parsedDatetime() {
            if (datetime == null || datetime.isBlank()) {
                return Optional.empty();
            }
            try {
                return Optional.of(LocalDateTime.parse(datetime, LOCAL_FORMAT));
            } catch (DateTimeParseException e) {
                try {
                    return Optional.of(OffsetDateTime.parse(datetime).toLocalDateTime());
                } catch (DateTimeParseException e2) {
                    return Optional.empty();
                }
            }
        }
    }
}
