package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedList;
import java.util.List;

class CancelPackageRequest {
    @JsonProperty("packages")
    private List<PackageId> packages = new LinkedList<>();

    CancelPackageRequest(String packageId) {
        this.packages.add(new PackageId(packageId));
    }

    List<PackageId> getPackages() {
        return packages;
    }
}
