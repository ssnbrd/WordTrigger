package com.example.wordtrigger.speech;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Локальная (без ИИ) проверка осознанной речи: есть ли слова по теме и не превратилась ли речь
 * в набор повторов. Чистая Java, проверяется обычными unit-тестами.
 *
 * Это запасная проверка. Когда доступен ИИ, он оценивает связность и соответствие теме точнее,
 * а эти правила остаются как нижняя граница (минимум слов, разнообразие).
 */
public final class TopicScorer {
    private TopicScorer() {}

    /** Сколько разных ключевых основ из списка встретилось в речи (по началу слова: «выходн» → «выходные»). */
    public static int keywordsHit(String spoken, Collection<String> stems) {
        List<String> words = SpeechScorer.tokenize(spoken);
        int hit = 0;
        for (String stem : stems) {
            String st = SpeechScorer.tokenize(stem).isEmpty() ? "" : SpeechScorer.tokenize(stem).get(0);
            if (st.length() < 3) continue;
            for (String w : words) {
                if (w.startsWith(st)) {
                    hit++;
                    break;
                }
            }
        }
        return hit;
    }

    /**
     * Основы слов из самой темы («Приготовление овощного салата» → приготов, овощн, салат): так локальная
     * проверка работает и для тем, у которых нет готового списка ключевых слов, например придуманных ИИ.
     */
    public static List<String> stemsFromTitle(String title) {
        List<String> out = new java.util.ArrayList<>();
        for (String w : SpeechScorer.tokenize(title)) {
            if (w.length() < 4) continue;                     // предлоги и короткие слова не считаем
            String stem = w.substring(0, Math.max(4, w.length() - 2));
            if (!out.contains(stem)) out.add(stem);
        }
        return out;
    }

    /** Доля разных слов от всех, от 0 до 1. Обычная речь около 0,4–0,6, зацикленная ниже 0,3. */
    public static double lexicalDiversity(String spoken) {
        List<String> words = SpeechScorer.tokenize(spoken);
        if (words.isEmpty()) return 0;
        return (double) new java.util.HashSet<>(words).size() / words.size();
    }

    /** Какая доля троек слов подряд повторяется. Обычная речь до 0,1, зацикленная заметно выше. */
    public static double repeatedTrigramShare(String spoken) {
        List<String> w = SpeechScorer.tokenize(spoken);
        int total = w.size() - 2;
        if (total <= 0) return 0;
        Map<String, Integer> seen = new HashMap<>();
        int repeated = 0;
        for (int i = 0; i < total; i++) {
            String key = w.get(i) + " " + w.get(i + 1) + " " + w.get(i + 2);
            Integer n = seen.get(key);
            if (n != null) repeated++;
            seen.put(key, n == null ? 1 : n + 1);
        }
        return (double) repeated / total;
    }

    /** Достаточно ли разнообразна речь, чтобы считать её осмысленной. */
    public static boolean isVaried(String spoken) {
        return lexicalDiversity(spoken) >= 0.30 && repeatedTrigramShare(spoken) <= 0.25;
    }
}
