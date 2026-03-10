package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

class OrderPackageRequest {

    static class Label {
        @JsonProperty("page_format")
        private String pageFormat;

        @JsonProperty("file_format")
        private String fileFormat;

        // Getters and Setters
        String getPageFormat() {
            return pageFormat;
        }

        void setPageFormat(String pageFormat) {
            this.pageFormat = pageFormat;
        }

        String getFileFormat() {
            return fileFormat;
        }

        void setFileFormat(String fileFormat) {
            this.fileFormat = fileFormat;
        }
    }

    @JsonProperty("packages")
    private List<PackageId> packages;

    @JsonProperty("label")
    private Label label;

    @JsonProperty("only_order_pickup")
    private boolean onlyOrderPickup;

    @JsonProperty("skip_email_send")
    private boolean skipEmailSend;

    // Getters and Setters
    List<PackageId> getPackages() {
        return packages;
    }

    void setPackages(List<PackageId> packages) {
        this.packages = packages;
    }

    Label getLabel() {
        return label;
    }

    void setLabel(Label label) {
        this.label = label;
    }

    boolean isOnlyOrderPickup() {
        return onlyOrderPickup;
    }

    void setOnlyOrderPickup(boolean onlyOrderPickup) {
        this.onlyOrderPickup = onlyOrderPickup;
    }

    boolean isSkipEmailSend() {
        return skipEmailSend;
    }

    void setSkipEmailSend(boolean skipEmailSend) {
        this.skipEmailSend = skipEmailSend;
    }
}