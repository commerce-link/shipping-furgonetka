package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
class Address {
    @JsonProperty("name")
    private String name;
    @JsonProperty("company")
    private String company;
    @JsonProperty("street")
    private String street;
    @JsonProperty("postcode")
    private String postcode;
    @JsonProperty("city")
    private String city;
    @JsonProperty("country_code")
    private String countryCode;
    @JsonProperty("email")
    private String email;
    @JsonProperty("phone")
    private String phone;

    void setName(String name) {
        this.name = name;
    }

    void setCompany(String company) {
        this.company = company;
    }

    void setStreet(String street) {
        this.street = street;
    }

    void setPostcode(String postcode) {
        this.postcode = postcode;
    }

    void setCity(String city) {
        this.city = city;
    }

    void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    void setEmail(String email) {
        this.email = email;
    }

    void setPhone(String phone) {
        this.phone = phone;
    }
}
