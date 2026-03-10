package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
class Package {
    @JsonProperty("package_id")
    private String packageId;
    @JsonProperty("pickup")
    private Address pickup;
    @JsonProperty("sender")
    private Address sender;
    @JsonProperty("receiver")
    private Address receiver;
    @JsonProperty("parcels")
    private List<ShippingParcel> parcels;
    @JsonProperty("service_id")
    private int serviceId;
    @JsonProperty("type")
    private String type;
    @JsonProperty("additional_services")
    private AdditionalServices additionalServices;

    String getPackageId() {
        return packageId;
    }

    void setPackageId(String packageId) {
        this.packageId = packageId;
    }

    Address getPickup() {
        return pickup;
    }

    void setPickup(Address pickup) {
        this.pickup = pickup;
    }

    Address getSender() {
        return sender;
    }

    void setSender(Address sender) {
        this.sender = sender;
    }

    Address getReceiver() {
        return receiver;
    }

    void setReceiver(Address receiver) {
        this.receiver = receiver;
    }

    List<ShippingParcel> getParcels() {
        return parcels;
    }

    void setParcels(List<ShippingParcel> parcels) {
        this.parcels = parcels;
    }

    int getServiceId() {
        return serviceId;
    }

    void setServiceId(int serviceId) {
        this.serviceId = serviceId;
    }

    String getType() {
        return type;
    }

    void setType(String type) {
        this.type = type;
    }

    AdditionalServices getAdditionalServices() {
        return additionalServices;
    }

    void setAdditionalServices(AdditionalServices additionalServices) {
        this.additionalServices = additionalServices;
    }
}
