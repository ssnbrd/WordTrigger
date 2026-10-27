package com.example.wordtrigger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.wordtrigger.speech.TopicScorer;

import org.junit.Test;

import java.util.Arrays;

public class TopicScorerTest {
    private static final java.util.List<String> WEEKEND =
            Arrays.asList("выходн", "суббот", "воскресен", "отдых", "друзь", "гуля");

    @Test public void keywordsMatchByStem() {
        assertEquals(5, TopicScorer.keywordsHit(
                "в эти выходные мы с друзьями гуляли по городу а в воскресенье отдыхали", WEEKEND));
    }
    @Test public void offTopicHasNoKeywords() {
        assertEquals(0, TopicScorer.keywordsHit("квартальный отчёт по продажам готов", WEEKEND));
    }
    @Test public void loopedSpeechIsNotVaried() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 40; i++) sb.append("ну это типа ");
        assertFalse(TopicScorer.isVaried(sb.toString()));
    }
    @Test public void normalSpeechIsVaried() {
        String text = "в субботу я рано проснулся и пошёл гулять в парк там было тихо и немного прохладно "
                + "потом встретил друзей и мы зашли в кафе выпить кофе после обеда вернулся домой "
                + "посмотрел фильм а вечером в воскресенье помогал родителям на даче";
        assertTrue(TopicScorer.isVaried(text));
    }
}
