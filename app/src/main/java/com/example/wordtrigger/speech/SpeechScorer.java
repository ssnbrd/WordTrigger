package com.example.wordtrigger.speech;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SpeechScorer {
    private SpeechScorer() {}

    public static List<String> tokenize(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        String t = text.toLowerCase().replace('ё', 'е').replaceAll("[^\\p{L}\\p{Nd}\\s]", " ");
        for (String w : t.trim().split("\\s+")) {
            if (!w.isEmpty()) out.add(w);
        }
        return out;
    }

    public static double accuracy(String reference, String spoken) {
        List<String> ref = tokenize(reference);
        List<String> hyp = tokenize(spoken);
        if (ref.isEmpty()) return 0;
        if (hyp.isEmpty()) return 0;

        double[][] d = new double[ref.size() + 1][hyp.size() + 1];
        for (int i = 0; i <= ref.size(); i++) d[i][0] = i;
        for (int j = 0; j <= hyp.size(); j++) d[0][j] = j;
        for (int i = 1; i <= ref.size(); i++) {
            for (int j = 1; j <= hyp.size(); j++) {
                double sub = d[i - 1][j - 1] + wordCost(ref.get(i - 1), hyp.get(j - 1));
                double del = d[i - 1][j] + 1;
                double ins = d[i][j - 1] + 1;
                d[i][j] = Math.min(sub, Math.min(del, ins));
            }
        }
        return Math.max(0, 1.0 - d[ref.size()][hyp.size()] / ref.size());
    }

    private static double wordCost(String a, String b) {
        if (a.equals(b)) return 0;
        int maxLen = Math.max(a.length(), b.length());
        double relDist = (double) charDistance(a, b) / maxLen;
        return relDist <= 0.25 ? 0.5 : 1.0;
    }

    private static int charDistance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = cur; cur = tmp;
        }
        return prev[b.length()];
    }

    public static double wordsPerSecond(String spoken, double seconds) {
        if (seconds <= 0) return 0;
        return tokenize(spoken).size() / seconds;
    }

    public static int countOccurrences(String spoken, Collection<String> phrases) {
        List<String> words = tokenize(spoken);
        int count = 0;
        for (String phrase : phrases) {
            List<String> p = tokenize(phrase);
            if (p.isEmpty()) continue;
            for (int i = 0; i + p.size() <= words.size(); i++) {
                if (words.subList(i, i + p.size()).equals(p)) count++;
            }
        }
        return count;
    }

    public static Set<String> matchedPhrases(String spoken, Collection<String> known) {
        List<String> words = tokenize(spoken);
        Set<String> found = new LinkedHashSet<>();
        for (String phrase : known) {
            List<String> p = tokenize(phrase);
            if (p.isEmpty()) continue;
            for (int i = 0; i + p.size() <= words.size(); i++) {
                if (words.subList(i, i + p.size()).equals(p)) {
                    found.add(phrase.trim());
                    break;
                }
            }
        }
        return found;
    }

    public static List<String> splitSynonyms(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String s : raw.split("[,;]")) {
            String t = s.replace("...", "").trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private static String letters(String text) {
        StringBuilder sb = new StringBuilder();
        for (String w : tokenize(text)) sb.append(w);
        return sb.toString();
    }

    public static double substringSimilarity(String reference, String spoken) {
        String ref = letters(reference);
        String hyp = letters(spoken);
        if (ref.isEmpty() || hyp.isEmpty()) return 0;
        int m = ref.length();
        int n = hyp.length();

        int[] prev = new int[n + 1];
        int[] cur = new int[n + 1];
        for (int i = 1; i <= m; i++) {
            cur[0] = i;
            for (int j = 1; j <= n; j++) {
                int cost = ref.charAt(i - 1) == hyp.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        int best = Integer.MAX_VALUE;
        for (int j = 0; j <= n; j++) best = Math.min(best, prev[j]);
        return Math.max(0, 1.0 - (double) best / m);
    }

    public static String phonetic(String text) {
        StringBuilder sb = new StringBuilder();
        for (String w : tokenize(text)) {
            for (int i = 0; i < w.length(); i++) {
                char c = w.charAt(i);
                char m;
                switch (c) {
                    case 'ь': case 'ъ': continue;
                    case 'о': case 'я': m = 'а'; break;
                    case 'е': case 'э': m = 'и'; break;
                    case 'ю': m = 'у'; break;
                    case 'ы': m = 'и'; break;
                    case 'б': m = 'п'; break;
                    case 'в': m = 'ф'; break;
                    case 'г': m = 'к'; break;
                    case 'д': m = 'т'; break;
                    case 'ж': m = 'ш'; break;
                    case 'з': m = 'с'; break;
                    default: m = c;
                }
                if (sb.length() == 0 || sb.charAt(sb.length() - 1) != m) sb.append(m);
            }
        }
        return sb.toString();
    }

    public static double phoneticSimilarity(String reference, String spoken) {
        return substringSimilarity(phonetic(reference), phonetic(spoken));
    }

    public static double looseAccuracy(String reference, String spoken) {
        return Math.max(Math.max(accuracy(reference, spoken), substringSimilarity(reference, spoken)),
                phoneticSimilarity(reference, spoken));
    }

    public static int repeats(String reference, String spoken) {
        int ref = letters(reference).length();
        if (ref == 0) return 1;
        double ratio = (double) letters(spoken).length() / ref;
        return (int) Math.max(1, Math.min(3, Math.round(ratio)));
    }

    // СИНОНИМЫ
    public static boolean sameWordForm(String a, String b) {
        if (a.equals(b)) return true;
        int min = Math.min(a.length(), b.length());
        if (min < 4) return false;
        int common = 0;
        while (common < min && a.charAt(common) == b.charAt(common)) common++;
        return common >= Math.max(4, min - 2) && Math.abs(a.length() - b.length()) <= 3;
    }

    private static boolean phraseAt(List<String> words, int start, List<String> phrase) {
        for (int k = 0; k < phrase.size(); k++) {
            if (!sameWordForm(words.get(start + k), phrase.get(k))) return false;
        }
        return true;
    }

    /** Учитывает окончания слов  */
    public static Set<String> matchedPhrasesLenient(String spoken, Collection<String> known) {
        List<String> words = tokenize(spoken);
        Set<String> found = new LinkedHashSet<>();
        for (String phrase : known) {
            List<String> p = tokenize(phrase);
            if (p.isEmpty()) continue;
            for (int i = 0; i + p.size() <= words.size(); i++) {
                if (phraseAt(words, i, p)) {
                    found.add(phrase.trim());
                    break;
                }
            }
        }
        return found;
    }

    public static List<String> unmatchedCandidates(String spoken, Collection<String> known,
                                                   String targetWord, Collection<String> stopWords, int max) {
        List<String> knownTokens = new ArrayList<>();
        for (String k : known) knownTokens.addAll(tokenize(k));
        List<String> target = tokenize(targetWord);
        Set<String> stops = new LinkedHashSet<>();
        for (String w : stopWords) stops.addAll(tokenize(w));

        Set<String> out = new LinkedHashSet<>();
        for (String w : tokenize(spoken)) {
            if (w.length() < 3 || stops.contains(w)) continue;
            boolean skip = false;
            for (String k : knownTokens) if (sameWordForm(w, k)) { skip = true; break; }
            for (String t : target) if (sameWordForm(w, t)) { skip = true; break; }
            if (!skip) out.add(w);
            if (out.size() >= max) break;
        }
        return new ArrayList<>(out);
    }
}
