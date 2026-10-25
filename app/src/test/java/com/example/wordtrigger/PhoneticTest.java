package com.example.wordtrigger;

import static org.junit.Assert.*;

import org.junit.Test;

public class PhoneticTest {
    @Test public void rareWordsHeardApproximately() {
        String ref = "Корабли лавировали, лавировали, да не вылавировали";
        String heard = "корабли ловили вали ловили вали да не вылавировали";
        assertTrue(SpeechScorer.looseAccuracy(ref, heard) >= SpeechScorer.accuracy(ref, heard));
        assertTrue(SpeechScorer.phoneticSimilarity("лавировали", "ловили вали") >= 0.75);
    }

    @Test public void unrelatedSpeechStaysLow() {
        assertTrue(SpeechScorer.looseAccuracy("Бык тупогуб, тупогубенький бычок", "привет как дела сегодня хорошо") < 0.5);
    }

    @Test public void phoneticFolding() {
        assertEquals(SpeechScorer.phonetic("вода"), SpeechScorer.phonetic("фада"));
        assertEquals("па", SpeechScorer.phonetic("бо"));
    }

    @Test public void topicStemsFromTitle() {
        java.util.List<String> st = TopicScorer.stemsFromTitle("Приготовление овощного салата из огорода");
        assertTrue(st.contains("приготовлен") || st.contains("приготов"));
        assertTrue(TopicScorer.keywordsHit("я режу салат и овощи из своего огорода", st) >= 2);
        assertEquals(0, TopicScorer.keywordsHit("вчера ходил в кино с друзьями", st));
    }
}
