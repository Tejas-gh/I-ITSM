package com.iitsm.domain.classification;

import com.iitsm.domain.model.CategorySource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class KeywordClassifierTest {

    private final CategoryClassifier classifier = new KeywordClassifier();

    @ParameterizedTest
    @CsvSource({
            "VPN connection failing,,NETWORK",
            "Email not working,,APPLICATION",
            "Laptop screen broken,,HARDWARE",
            "Cannot reset my password,,AUTHENTICATION",
            "Server disk full,,STORAGE",
            "Phishing email received,,SECURITY",
            "Slow SQL query,,DATABASE",
            "AWS instance unreachable,,CLOUD",
            "Need to install new software,,SOFTWARE",
            "Problem,The VPN keeps dropping and wifi is slow,NETWORK"
    })
    void classify_representativeDescriptions_returnsExpectedSeededCategory(String title, String desc, String expected) {
        Optional<CategorySuggestion> result = classifier.classify(title, desc);
        assertTrue(result.isPresent());
        assertEquals(expected, result.get().categoryId());
        assertEquals(CategorySource.KEYWORD, result.get().source());
        assertTrue(result.get().confidence() > 0.0 && result.get().confidence() <= 1.0);
    }

    @Test
    void classify_isCaseAndPunctuationInsensitive() {
        assertEquals("NETWORK", classifier.classify("VPN!!! CONNECTION... failing", null).orElseThrow().categoryId());
    }

    @Test
    void classify_unknownDescription_returnsEmpty() {
        assertTrue(classifier.classify("Something odd happened", "Please help me").isEmpty());
    }

    @Test
    void classify_blankOrNullInput_returnsEmptyWithoutThrowing() {
        assertTrue(classifier.classify(null, null).isEmpty());
        assertTrue(classifier.classify("", "   ").isEmpty());
    }

    @Test
    void classify_ambiguousTie_returnsEmpty() {
        // one AUTHENTICATION keyword (login) vs one APPLICATION keyword (email), equal weight
        assertTrue(classifier.classify(null, "login email").isEmpty());
    }

    @Test
    void classify_titleOutweighsDescription() {
        assertEquals("NETWORK", classifier.classify("VPN down", "login email").orElseThrow().categoryId());
    }

    @Test
    void classify_matchesWholeWordsOnly() {
        // "lan" must not match inside "plan"; "app" must not match inside "happy"
        assertTrue(classifier.classify("Planning happy hour", null).isEmpty());
    }

    @Test
    void classify_isDeterministic() {
        assertEquals(classifier.classify("Printer jammed", "paper"), classifier.classify("Printer jammed", "paper"));
    }

    @Test
    void classify_onlyReturnsSeededCategoryIds() {
        var seeded = java.util.Set.of("HARDWARE", "SOFTWARE", "NETWORK", "DATABASE", "CLOUD", "SECURITY",
                "AUTHENTICATION", "APPLICATION", "STORAGE");
        for (String text : new String[]{"vpn", "laptop", "email", "password", "disk", "sql", "aws", "virus", "install"}) {
            assertTrue(seeded.contains(classifier.classify(text, null).orElseThrow().categoryId()));
        }
    }
}
