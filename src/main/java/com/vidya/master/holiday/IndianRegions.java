package com.vidya.master.holiday;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Indian states / union territories and their ISO 3166-2:IN codes. Accepts both the current codes
 * and the ones replaced in 2023 (CT→CG, OR→OD, TG→TS, UT→UK), because providers use either.
 */
public final class IndianRegions {

    private static final Map<String, String> NAME_TO_CODE = new LinkedHashMap<>();
    private static final Map<String, Set<String>> ALIASES = new HashMap<>();

    static {
        put("AN", "Andaman and Nicobar Islands", "Andaman & Nicobar Islands", "Andaman and Nicobar");
        put("AP", "Andhra Pradesh");
        put("AR", "Arunachal Pradesh");
        put("AS", "Assam");
        put("BR", "Bihar");
        put("CH", "Chandigarh");
        put("CG", "Chhattisgarh", "Chattisgarh");
        put("DH", "Dadra and Nagar Haveli and Daman and Diu", "Dadra and Nagar Haveli", "Daman and Diu");
        put("DL", "Delhi", "NCT of Delhi", "National Capital Territory of Delhi", "New Delhi");
        put("GA", "Goa");
        put("GJ", "Gujarat");
        put("HR", "Haryana");
        put("HP", "Himachal Pradesh");
        put("JK", "Jammu and Kashmir", "Jammu & Kashmir");
        put("JH", "Jharkhand");
        put("KA", "Karnataka");
        put("KL", "Kerala");
        put("LA", "Ladakh");
        put("LD", "Lakshadweep");
        put("MP", "Madhya Pradesh");
        put("MH", "Maharashtra");
        put("MN", "Manipur");
        put("ML", "Meghalaya");
        put("MZ", "Mizoram");
        put("NL", "Nagaland");
        put("OD", "Odisha", "Orissa");
        put("PY", "Puducherry", "Pondicherry");
        put("PB", "Punjab");
        put("RJ", "Rajasthan");
        put("SK", "Sikkim");
        put("TN", "Tamil Nadu");
        put("TS", "Telangana");
        put("TR", "Tripura");
        put("UP", "Uttar Pradesh");
        put("UK", "Uttarakhand", "Uttaranchal");
        put("WB", "West Bengal");
        ALIASES.put("CG", Set.of("CG", "CT"));
        ALIASES.put("OD", Set.of("OD", "OR"));
        ALIASES.put("TS", Set.of("TS", "TG"));
        ALIASES.put("UK", Set.of("UK", "UT"));
        ALIASES.put("DH", Set.of("DH", "DN", "DD"));
    }

    private IndianRegions() {
    }

    private static void put(String code, String... names) {
        for (String n : names) {
            NAME_TO_CODE.put(norm(n), code);
        }
        NAME_TO_CODE.put(norm(code), code);
    }

    /** "Maharashtra", "maharashtra", "MH", "IN-MH" → "MH". */
    public static Optional<String> code(String stateOrCode) {
        if (stateOrCode == null || stateOrCode.isBlank()) {
            return Optional.empty();
        }
        String s = stateOrCode.trim();
        if (s.toUpperCase(Locale.ROOT).startsWith("IN-")) {
            s = s.substring(3);
        }
        String n = norm(s);
        for (Map.Entry<String, Set<String>> a : ALIASES.entrySet()) {
            if (a.getValue().stream().anyMatch(x -> norm(x).equals(n))) {
                return Optional.of(a.getKey());
            }
        }
        return Optional.ofNullable(NAME_TO_CODE.get(n));
    }

    /** True when a provider region label ("MH", "in-mh", "Maharashtra", "OR") means this state code. */
    public static boolean matches(String providerRegion, String stateCode) {
        return code(providerRegion).map(stateCode::equals).orElse(false);
    }

    static String norm(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT);
        return n.replace("&", "and").replaceAll("[^a-z]", "");
    }
}
