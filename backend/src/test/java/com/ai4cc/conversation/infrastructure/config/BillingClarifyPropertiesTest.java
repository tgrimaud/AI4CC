package com.ai4cc.conversation.infrastructure.config;

import com.ai4cc.conversation.domain.model.valueobject.AnswerLanguage;
import com.ai4cc.conversation.domain.model.valueobject.BillingClarifyTopic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// TASK-BE-072 (OQ-043-b): the per-deployment billing sub-type list binds from the
// voice-support.conversation.billing-clarify.sub-types tree and maps to the domain value object. Uses
// the Spring Boot Binder directly (no Spring context) so the relaxed-binding contract and the graceful
// mapping (drop entries without an id, empty list on absence) are locked.
@DisplayName("BillingClarifyProperties (TASK-BE-072 sub-type config binding)")
class BillingClarifyPropertiesTest {

    private static final String PREFIX = "voice-support.conversation.billing-clarify";

    @Test
    @DisplayName("binds the configured sub-types and maps them to domain topics with FR/EN hints")
    void bindsAndMapsSubTypes() {
        Map<String, String> source = Map.of(
                PREFIX + ".sub-types[0].id", "higher-than-usual",
                PREFIX + ".sub-types[0].fr", "une facture plus élevée que d'habitude",
                PREFIX + ".sub-types[0].en", "a bill higher than usual",
                PREFIX + ".sub-types[1].id", "payment-or-refund",
                PREFIX + ".sub-types[1].fr", "un problème de paiement",
                PREFIX + ".sub-types[1].en", "a payment issue");

        List<BillingClarifyTopic> topics = bind(source).toTopics();

        assertEquals(2, topics.size());
        assertEquals("higher-than-usual", topics.get(0).id());
        assertEquals("une facture plus élevée que d'habitude", topics.get(0).hint(AnswerLanguage.FRENCH));
        assertEquals("a payment issue", topics.get(1).hint(AnswerLanguage.ENGLISH));
    }

    @Test
    @DisplayName("an absent sub-type list binds to an empty list (generic clarify fallback)")
    void absentListIsEmpty() {
        assertTrue(bind(Map.of(PREFIX + ".max-questions", "2")).toTopics().isEmpty(),
                "no sub-types configured ⇒ empty topics ⇒ generic TASK-BE-071 clarify prompt");
    }

    @Test
    @DisplayName("an entry without an id is dropped so a malformed config does not steer with a blank sub-type")
    void dropsEntriesWithoutId() {
        Map<String, String> source = Map.of(
                PREFIX + ".sub-types[0].fr", "sans id",
                PREFIX + ".sub-types[0].en", "no id",
                PREFIX + ".sub-types[1].id", "valid",
                PREFIX + ".sub-types[1].fr", "valide",
                PREFIX + ".sub-types[1].en", "valid");

        List<BillingClarifyTopic> topics = bind(source).toTopics();

        assertEquals(1, topics.size());
        assertEquals("valid", topics.get(0).id());
    }

    private BillingClarifyProperties bind(Map<String, String> source) {
        ConfigurationPropertySource propertySource = new MapConfigurationPropertySource(source);
        return new Binder(propertySource)
                .bind(PREFIX, BillingClarifyProperties.class)
                .orElseGet(() -> new BillingClarifyProperties(List.of()));
    }
}
