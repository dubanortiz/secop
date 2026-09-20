package com.auto.secop.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextNormalizerTest {

    @Test
    void foldsAccentsAndFindsPhrases() {
        String haystack = TextNormalizer.squash("AUNAR ESFUERZOS TÉCNICOS Y LOGÍSTICOS para el festival");
        assertThat(TextNormalizer.containsPhrase(haystack, TextNormalizer.squash("aunar esfuerzos"))).isTrue();
        assertThat(TextNormalizer.containsPhrase(haystack, TextNormalizer.squash("logística"))).isTrue();
        assertThat(TextNormalizer.stem("logisticos")).isEqualTo(TextNormalizer.stem("logistica"));
        assertThat(TextNormalizer.containsPhrase(haystack, TextNormalizer.squash("cultura"))).isFalse();
    }

    @Test
    void doesNotMatchCulturaInsideAgricultura() {
        String haystack = TextNormalizer.squash("servicios de agricultura familiar");
        assertThat(TextNormalizer.containsPhrase(haystack, TextNormalizer.squash("cultura"))).isFalse();
    }
}
