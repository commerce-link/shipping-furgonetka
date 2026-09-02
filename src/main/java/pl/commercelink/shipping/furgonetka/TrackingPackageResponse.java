package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
class TrackingPackageResponse {

    @JsonProperty("tracking")
    private List<TrackingEvent> tracking = new ArrayList<>();

    List<TrackingEvent> getTracking() {
        return tracking;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class TrackingEvent {

        @JsonProperty("state")
        private String state;

        @JsonProperty("status")
        private String status;

        @JsonProperty("datetime")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
        private OffsetDateTime datetime;

        String getState() {
            return state;
        }

        String getStatus() {
            return status;
        }

        OffsetDateTime getDatetime() {
            return datetime;
        }
    }
}
