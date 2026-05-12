package com.ghpham11a.springboot.filter;

import java.util.List;
import java.util.regex.Pattern;

public final class PiiSanitizer {

    private static final String REDACTED = "\"***REDACTED***\"";

    // JSON field names to redact (case-insensitive match on the key)
    private static final List<String> SENSITIVE_FIELDS = List.of(
            "password", "passwd", "secret",
            "token", "accessToken", "refreshToken", "apiKey", "api_key",
            "ssn", "social_security", "socialSecurity",
            "email", "emailAddress", "email_address",
            "phone", "phoneNumber", "phone_number",
            "creditCard", "credit_card", "cardNumber", "card_number", "cvv", "cvc",
            "dob", "dateOfBirth", "date_of_birth",
            "address", "streetAddress", "street_address",
            "firstName", "first_name", "lastName", "last_name"
    );

    // Regex patterns for common PII formats (catches values even in non-sensitive fields)
    private static final List<PatternReplacement> VALUE_PATTERNS = List.of(
            // SSN: 123-45-6789
            new PatternReplacement(
                    Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"),
                    "***SSN***"),
            // Credit card: 13-19 digit numbers (with optional dashes/spaces)
            new PatternReplacement(
                    Pattern.compile("\\b(?:\\d[ -]*?){13,19}\\b"),
                    "***CARD***"),
            // Email embedded in free text
            new PatternReplacement(
                    Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"),
                    "***EMAIL***"),
            // US phone: (123) 456-7890, 123-456-7890, +1234567890
            new PatternReplacement(
                    Pattern.compile("\\b(?:\\+?1[-.]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.]?\\d{4}\\b"),
                    "***PHONE***")
    );

    // Matches a JSON key-value pair: "fieldName" : "value" or "fieldName" : 12345
    // Captures the key in group 1, replaces the value
    private static final Pattern JSON_FIELD_PATTERN = buildSensitiveFieldPattern();

    private PiiSanitizer() {
    }

    public static String sanitize(String payload) {
        if (payload == null || payload.isBlank()) {
            return payload;
        }

        // Step 1: Redact known sensitive JSON fields by name
        String result = JSON_FIELD_PATTERN.matcher(payload)
                .replaceAll(match -> {
                    String key = match.group(1);
                    return "\"" + key + "\"" + match.group(2) + REDACTED;
                });

        // Step 2: Scrub PII patterns in remaining values
        for (PatternReplacement pr : VALUE_PATTERNS) {
            result = pr.pattern.matcher(result).replaceAll(pr.replacement);
        }

        return result;
    }

    private static Pattern buildSensitiveFieldPattern() {
        String fields = String.join("|", SENSITIVE_FIELDS);
        // Matches: "fieldName" <optional whitespace> : <optional whitespace> <value>
        // Value can be a quoted string or a number/boolean/null
        String regex = "\"(" + fields + ")\"(\\s*:\\s*)(?:\"[^\"]*\"|\\d+|true|false|null)";
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    private record PatternReplacement(Pattern pattern, String replacement) {
    }
}
