package com.iitsm.domain.classification;

import com.iitsm.domain.model.CategorySource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Deterministic keyword-based {@link CategoryClassifier} (Phase 3 A2). Uses a static keyword table keyed by the
 * seeded category ids (V2__seed_reference_data.sql). Whole-word/phrase matching; title hits count double.
 * Returns empty for blank, unknown or ambiguous (tied) text and never throws. Independent of any other
 * classifier, so it can serve as a fallback.
 */
public final class KeywordClassifier implements CategoryClassifier {

    private static final int TITLE_WEIGHT = 2;

    private static final Map<String, List<String>> KEYWORDS = new LinkedHashMap<>();

    static {
        KEYWORDS.put("HARDWARE", List.of("laptop", "desktop", "monitor", "screen", "keyboard", "mouse", "printer",
                "scanner", "battery", "hardware", "docking station", "headset", "webcam", "motherboard",
                "overheating", "power supply", "projector"));
        KEYWORDS.put("SOFTWARE", List.of("software", "install", "installation", "license", "licence", "driver",
                "patch", "upgrade", "operating system", "windows update", "uninstall"));
        KEYWORDS.put("NETWORK", List.of("vpn", "network", "wifi", "wi fi", "internet", "router", "lan", "ethernet",
                "dns", "bandwidth", "latency", "connectivity", "connection", "packet loss", "proxy"));
        KEYWORDS.put("DATABASE", List.of("database", "db", "sql", "query", "queries", "oracle", "postgres",
                "postgresql", "mysql", "deadlock", "stored procedure", "data corruption"));
        KEYWORDS.put("CLOUD", List.of("cloud", "aws", "azure", "gcp", "virtual machine", "vm", "kubernetes",
                "docker", "container", "saas"));
        KEYWORDS.put("SECURITY", List.of("virus", "malware", "phishing", "phishing email", "ransomware", "breach", "hacked", "hack",
                "suspicious", "security", "firewall", "spam", "unauthorized access", "vulnerability", "antivirus"));
        KEYWORDS.put("AUTHENTICATION", List.of("password", "login", "log in", "sign in", "locked out",
                "account locked", "mfa", "2fa", "otp", "authentication", "sso", "credentials"));
        KEYWORDS.put("APPLICATION", List.of("email", "e mail", "outlook", "mail", "application", "app", "crm", "erp",
                "sharepoint", "teams", "excel", "browser", "web portal"));
        KEYWORDS.put("STORAGE", List.of("disk", "storage", "disk space", "out of space", "drive full", "nas", "san",
                "file server", "shared drive", "quota", "backup"));
    }

    @Override
    public Optional<CategorySuggestion> classify(String title, String description) {
        String titleText = normalize(title);
        String descText = normalize(description);
        if (titleText.isBlank() && descText.isBlank()) {
            return Optional.empty();
        }

        String bestId = null;
        int bestScore = 0;
        boolean tied = false;
        int total = 0;
        for (Map.Entry<String, List<String>> entry : KEYWORDS.entrySet()) {
            int score = 0;
            for (String keyword : entry.getValue()) {
                String needle = " " + keyword + " ";
                if (titleText.contains(needle)) score += TITLE_WEIGHT;
                if (descText.contains(needle)) score += 1;
            }
            total += score;
            if (score > bestScore) {
                bestScore = score;
                bestId = entry.getKey();
                tied = false;
            } else if (score > 0 && score == bestScore) {
                tied = true;
            }
        }
        if (bestId == null || tied) {
            return Optional.empty();
        }

        double share = (double) bestScore / total;
        double confidence = Math.round(Math.min(0.9, 0.4 + 0.1 * bestScore) * share * 100.0) / 100.0;
        return Optional.of(new CategorySuggestion(bestId, confidence,
                "Matched keywords for category " + bestId, CategorySource.KEYWORD));
    }

    private static String normalize(String text) {
        if (text == null) return "";
        String cleaned = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        return cleaned.isEmpty() ? "" : " " + cleaned + " ";
    }
}
