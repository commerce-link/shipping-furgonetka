package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
class PickupDateProposalsResponse {

    @JsonProperty("packages")
    private List<PackageProposals> packages = new ArrayList<>();

    List<PackageProposals> getPackages() {
        return packages == null ? List.of() : packages;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class PackageProposals {
        @JsonProperty("package_id")
        private String packageId;
        @JsonProperty("proposals")
        private List<Proposal> proposals = new ArrayList<>();

        List<Proposal> getProposals() {
            return proposals == null ? List.of() : proposals;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Proposal {
        @JsonProperty("date")
        private String date;
        @JsonProperty("min_time")
        private String minTime;
        @JsonProperty("max_time")
        private String maxTime;
        @JsonProperty("available")
        private Boolean available;
        @JsonProperty("hash")
        private String hash;

        String getDate() { return date; }
        String getMinTime() { return minTime; }
        String getMaxTime() { return maxTime; }
        boolean isAvailable() { return Boolean.TRUE.equals(available); }
        String getHash() { return hash; }
    }
}
