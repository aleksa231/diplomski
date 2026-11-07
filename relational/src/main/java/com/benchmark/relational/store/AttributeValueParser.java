package com.benchmark.relational.store;

/**
 * Coerces a dynamic attribute filter value (always received as a String) into the most specific
 * JSON-comparable type: Integer, then Double, then Boolean, otherwise the raw String.
 */
public final class AttributeValueParser {

    private AttributeValueParser() {
    }

    public static Object parse(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return Integer.valueOf(trimmed);
        } catch (NumberFormatException ignored) {
            // fall through
        }
        try {
            return Double.valueOf(trimmed);
        } catch (NumberFormatException ignored) {
            // fall through
        }
        if (trimmed.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        if (trimmed.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }
        return value;
    }
}
