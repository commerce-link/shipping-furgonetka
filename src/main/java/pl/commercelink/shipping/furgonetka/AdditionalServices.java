package pl.commercelink.shipping.furgonetka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
class AdditionalServices {

    @JsonProperty("saturday_delivery")
    private boolean saturdayDelivery;

    @JsonProperty("documents_supply")
    private boolean documentsSupply;

    @JsonProperty("cod")
    private CashOnDelivery cashOnDelivery;

    void setSaturdayDelivery(boolean saturdayDelivery) {
        this.saturdayDelivery = saturdayDelivery;
    }

    void setDocumentsSupply(boolean documentsSupply) {
        this.documentsSupply = documentsSupply;
    }

    void setCashOnDelivery(CashOnDelivery cashOnDelivery) {
        this.cashOnDelivery = cashOnDelivery;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class CashOnDelivery {
        @JsonProperty("amount")
        private double amount;
        @JsonProperty("currency")
        private String currency = "PLN";
        @JsonProperty("iban")
        private String iban;
        @JsonProperty("name")
        private String name;
        @JsonProperty("swift")
        private String swift;

        CashOnDelivery() {}

        CashOnDelivery(double amount, String iban, String name, String swift) {
            this.amount = amount;
            this.iban = iban;
            this.name = name;
            this.swift = swift;
        }
    }
}
