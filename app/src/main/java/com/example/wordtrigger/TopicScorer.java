package com.example.wordtrigger;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TopicScorer {
    private TopicScorer() {}

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

    public static double lexicalDiversity(String spoken) {
        List<String> words = SpeechScorer.tokenize(spoken);
        if (words.isEmpty()) return 0;
        return (double) new java.util.HashSet<>(words).size() / words.size();
    }

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

    public static boolean isVaried(String spoken) {
        return lexicalDiversity(spoken) >= 0.30 && repeatedTrigramShare(spoken) <= 0.25;
    }
}
