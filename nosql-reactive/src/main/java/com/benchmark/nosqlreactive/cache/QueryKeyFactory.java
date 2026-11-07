package com.benchmark.nosqlreactive.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds the normalized key for catalog list queries. Filters are sorted and rendered in a
 * canonical form, then hashed, so that two semantically identical requests share a cache
 * entry regardless of parameter order.
 */
public final class QueryKeyFactory {

    private QueryKeyFactory() {
    }

    public static String catalogKey(String category,
                                    Double minPrice,
                                    Double maxPrice,
                                    Map<String, String> attributes,
                                    String sort,
                                    int page,
                                    int size) {
        StringBuilder canonical = new StringBuilder();
        canonical.append("category=").append(normalize(category));
        canonical.append("|minPrice=").append(minPrice == null ? "" : minPrice);
        canonical.append("|maxPrice=").append(maxPrice == null ? "" : maxPrice);
        canonical.append("|sort=").append(normalize(sort));
        canonical.append("|page=").append(page);
        canonical.append("|size=").append(size);

        if (attributes != null && !attributes.isEmpty()) {
            canonical.append("|attrs=");
            new TreeMap<>(attributes).forEach((k, v) -> canonical.append(k).append("=").append(v).append(";"));
        }

        return sha256Hex(canonical.toString());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.substring(0, 32);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }
}
