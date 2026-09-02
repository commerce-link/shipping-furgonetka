package pl.commercelink.shipping.furgonetka;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
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
        String compact = normalized.replaceAll("[^a-z0-9]", "");
        List<String> words = Arrays.stream(normalized.split("[^a-z0-9]+"))
                .filter(word -> !word.isEmpty())
                .toList();
        return BY_KEYWORD.entrySet().stream()
                .filter(entry -> matches(entry.getKey(), words, compact))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    // A keyword has to start a word ("DPD Polska", "Paczkomaty InPost") or the whole compacted name
    // ("X-press Couriers"). Plain substring matching would send every "... Express" carrier as xpress,
    // and a wrong service disables Furgonetka's own carrier detection.
    private static boolean matches(String keyword, List<String> words, String compact) {
        return words.stream().anyMatch(word -> word.startsWith(keyword)) || compact.startsWith(keyword);
    }
}
