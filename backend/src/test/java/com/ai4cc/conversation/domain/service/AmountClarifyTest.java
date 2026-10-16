package com.ai4cc.conversation.domain.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AmountClarifyTest {

    @Test
    void scenario_phrases_that_name_nothing_ask_first() {
        // GIVEN the invoice phrasings from the billing scenarios
        String[] phrases = {
                "je ne comprends pas un montant sur ma facture",
                "Pourquoi ma facture a augmenté ?",
                "ma facture est plus élevée que d'habitude",
                "il y a un problème avec ma facture",
                "je ne reconnais pas un prélèvement sur ma facture",
                "j'ai un service facturé en trop sur ma facture",
                "c'est quoi cette ligne sur ma facture",
                "I don't understand an amount on my bill",
                "Why did my bill change this month?",
                "my bill is higher than usual",
                "there is a charge I don't recognise on my bill",
                "aucune idée",
                "still no idea"
        };

        // WHEN each phrase is read for a concrete amount or service
        // THEN none of them is concrete, so the assistant asks
        for (String phrase : phrases) {
            assertThat(AmountClarify.hasConcreteFocus(phrase)).as(phrase).isFalse();
        }
    }

    @Test
    void a_named_amount_or_service_is_concrete() {
        // GIVEN phrases that point at one amount or one service
        // WHEN they are read
        // THEN the billing chain can explain that part
        assertThat(AmountClarify.hasConcreteFocus("le 29,99")).isTrue();
        assertThat(AmountClarify.hasConcreteFocus("le bundle")).isTrue();
        assertThat(AmountClarify.hasConcreteFocus("le roaming")).isTrue();
        assertThat(AmountClarify.hasConcreteFocus("41,31 euros sur ma facture")).isTrue();
    }
}
