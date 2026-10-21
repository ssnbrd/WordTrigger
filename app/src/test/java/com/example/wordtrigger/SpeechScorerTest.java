package com.example.wordtrigger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class SpeechScorerTest {
    private static final String TWISTER = "Шла Саша по шоссе и сосала сушку";

    @Test public void exactMatchIsOne() {
        assertEquals(1.0, SpeechScorer.accuracy(TWISTER, "шла саша по шоссе и сосала сушку"), 1e-9);
    }
    @Test public void punctuationAndYoAreIgnored() {
        assertEquals(1.0, SpeechScorer.accuracy("Всё, что есть.", "все что есть"), 1e-9);
    }
    @Test public void missingWordsLowerScore() {
        double a = SpeechScorer.accuracy(TWISTER, "шла саша по шоссе");
        assertTrue(a > 0.4 && a < 0.6);
    }
    @Test public void smallRecognitionErrorIsHalfCredit() {
        double a = SpeechScorer.accuracy("раз два три четыре", "раз два три четыри");
        assertEquals(0.875, a, 1e-9);
    }
    @Test public void emptyIsZero() {
        assertEquals(0.0, SpeechScorer.accuracy(TWISTER, ""), 1e-9);
    }
    @Test public void fillerNotFoundInsideOtherWords() {
        assertEquals(0, SpeechScorer.countOccurrences("одну минуту обычную", Arrays.asList("ну")));
        assertEquals(3, SpeechScorer.countOccurrences("ну это типа ну да", Arrays.asList("ну", "типа")));
    }
    @Test public void multiWordFillers() {
        assertEquals(1, SpeechScorer.countOccurrences("ну как бы да", Arrays.asList("как бы")));
    }
    @Test public void synonymsMatchedOnce() {
        assertEquals(2, SpeechScorer.matchedPhrases("вроде как будто вроде",
                Arrays.asList("вроде", "как будто", "наподобие")).size());
    }
    @Test public void splitSynonyms() {
        assertEquals(Arrays.asList("вроде", "как будто", "наподобие"),
                SpeechScorer.splitSynonyms("вроде, как будто; наподобие..."));
    }
    @Test public void tempo() {
        assertEquals(2.0, SpeechScorer.wordsPerSecond("а б в г", 2.0), 1e-9);
    }

    @Test public void mergedWordsStillScoreHighByLetters() {
        double a = SpeechScorer.looseAccuracy(TWISTER, "шласаша по шоссе и сосала сушку");
        assertTrue("got " + a, a >= 0.95);
    }
    @Test public void twisterSaidTwiceIsStillAccurate() {
        String twice = "шла саша по шоссе и сосала сушку шла саша по шоссе и сосала сушку";
        assertTrue(SpeechScorer.substringSimilarity(TWISTER, twice) > 0.99);
        assertEquals(2, SpeechScorer.repeats(TWISTER, twice));
        assertEquals(1, SpeechScorer.repeats(TWISTER, "шла саша по шоссе и сосала сушку"));
    }
    @Test public void unrelatedSpeechScoresLow() {
        assertTrue(SpeechScorer.looseAccuracy(TWISTER, "сегодня хорошая погода и мы пошли гулять") < 0.5);
    }
    @Test public void missingHalfScoresAboutHalf() {
        double a = SpeechScorer.substringSimilarity(TWISTER, "шла саша по шоссе");
        assertTrue("got " + a, a > 0.4 && a < 0.65);
    }

    @Test public void wordFormsAreMatchedByStem() {
        assertTrue(SpeechScorer.sameWordForm("огромный", "огромная"));
        assertTrue(SpeechScorer.sameWordForm("огромные", "огромный"));
        assertTrue(!SpeechScorer.sameWordForm("красивый", "красный"));
        assertTrue(!SpeechScorer.sameWordForm("большой", "больной"));
        assertTrue(!SpeechScorer.sameWordForm("дом", "дым"));
    }
    @Test public void lenientSynonymMatchAcceptsInflections() {
        assertEquals(2, SpeechScorer.matchedPhrasesLenient("огромная и крупные",
                Arrays.asList("огромный", "крупный", "громадный")).size());
    }
    @Test public void candidatesSkipKnownTargetAndStopWords() {
        java.util.List<String> c = SpeechScorer.unmatchedCandidates(
                "ну это слово большой огромный величественный гигантский еще",
                Arrays.asList("огромный", "гигантский"), "большой",
                Arrays.asList("ну", "это", "слово", "еще"), 10);
        assertEquals(Arrays.asList("величественный"), c);
    }
}
