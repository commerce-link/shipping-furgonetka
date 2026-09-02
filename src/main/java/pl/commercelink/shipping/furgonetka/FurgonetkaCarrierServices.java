package pl.commercelink.shipping.furgonetka;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class FurgonetkaCarrierServices {

    private static final Map<String, String> BY_KEYWORD = new LinkedHashMap<>();

    static {
        BY_KEYWORD.put("inpost", "inpost");
        BY_KEYWORD.put("paczkomat", "inpost");
        BY_KEYWORD.put("dpd", "dpd");
        BY_KEYWORD.put("dhl", "dhl");
        BY_KEYWORD.put("gls", "gls");
        BY_KEYWORD.put("ups", "ups");
        BY_KEYWORD.put("fedex", "fedex");
        BY_KEYWORD.put("poczta", "poczta");
        BY_KEYWORD.put("pocztex", "poczta");
        BY_KEYWORD.put("orlen", "orlen");
        BY_KEYWORD.put("ruch", "orlen");
        BY_KEYWORD.put("meest", "meest");
        BY_KEYWORD.put("ambro", "ambroexpress");
        BY_KEYWORD.put("xpress", "xpress");
    }

    private FurgonetkaCarrierServices() {
    }

    static Optional<String> serviceFor(String carrierName) {
        if (carrierName == null || carrierName.isBlank()) {
            return Optional.empty();
        }
        String normalized = carrierName.toLowerCase(Locale.ROOT);
        return BY_KEYWORD.entrySet().stream()
                .filter(entry -> normalized.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst();
    }
}
