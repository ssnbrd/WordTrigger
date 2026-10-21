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
}
