package com.example.wordtrigger;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Клиент ИИ-сервиса с OpenAI-совместимым API (по умолчанию OpenRouter, бесплатные модели).
 * Настройки берутся из BuildConfig (см. app/build.gradle.kts и local.properties).
 *
 * Для OpenRouter список запасных моделей отправляется одним запросом (поле "models"), чтобы
 * повторные попытки не расходовали дневной лимит бесплатных запросов. Для остальных сервисов
 * запасные модели перебираются по очереди.
 *
 * Все ответы приходят в главном потоке. Любая ошибка сообщается через onError, чтобы вызывающий
 * код мог перейти на запасной вариант без ИИ.
 */
public final class LlmClient {

    public enum Kind { NOT_CONFIGURED, AUTH, LIMIT, NETWORK, SERVER, BAD_RESPONSE, TIMEOUT }

    public interface Callback {
        void onResult(String text);
        void onError(Kind kind, String detail);
    }

    private static final String TAG = "LLM";
    // Несколько потоков: с одним потоком проверка в упражнении стояла в очереди за фоновой подготовкой заданий,
    // и ответ приходил через минуты
    private static final ExecutorService EXEC = Executors.newFixedThreadPool(3);
    /** Сколько максимум ждём ответ: после этого приложение само переходит на простую проверку. */
    private static final long BUDGET_STRUCTURED_MS = 30_000;
    private static final long BUDGET_TEXT_MS = 60_000;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String CACHE_PREFS = "llm_cache";

    private LlmClient() {}

    public static boolean isConfigured() {
        return !BuildConfig.LLM_API_KEY.isEmpty();
    }

    private static List<String> models() {
        List<String> out = new ArrayList<>();
        for (String m : BuildConfig.LLM_MODELS.split(",")) {
            if (!m.trim().isEmpty()) out.add(m.trim());
        }
        return out;
    }

    /** Краткое описание настройки без самого ключа: для экрана настроек. */
    public static String configSummary() {
        String key = BuildConfig.LLM_API_KEY;
        String host = BuildConfig.LLM_BASE_URL.replace("https://", "").replace("http://", "");
        int slash = host.indexOf('/');
        if (slash > 0) host = host.substring(0, slash);
        List<String> m = models();
        return (key.isEmpty() ? "key: -" : "key: " + key.length() + " chars")
                + "\nhost: " + host + "\nmodel: " + (m.isEmpty() ? "-" : m.get(0));
    }

    /** Понятное объяснение ошибки на языке интерфейса. */
    public static String explain(Context c, Kind kind, String detail) {
        int res;
        switch (kind) {
            case NOT_CONFIGURED: res = R.string.ai_err_not_configured; break;
            case AUTH: res = R.string.ai_err_auth; break;
            case LIMIT: res = R.string.ai_err_limit; break;
            case NETWORK: res = R.string.ai_err_network; break;
            case TIMEOUT: res = R.string.ai_err_timeout; break;
            case BAD_RESPONSE: res = R.string.ai_err_bad_response; break;
            default: res = R.string.ai_err_server;
        }
        String d = detail == null ? "" : detail.replace('\n', ' ');
        if (d.length() > 160) d = d.substring(0, 160);
        return c.getString(res) + (d.isEmpty() ? "" : "\n(" + d + ")");
    }

    /** Проверка подключения: один короткий запрос без кэша. */
    public static void ping(Context context, Callback callback) {
        chat(context, "Reply with the single word: ok", "ping", 8, null, callback);
    }

    private static boolean isOpenRouter() {
        return BuildConfig.LLM_BASE_URL.contains("openrouter.ai");
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    /**
     * @param cacheKey если не null, ответ запоминается до конца дня и повторный запрос с тем же
     *                 ключом не тратит лимит сервиса
     */
    public static void chat(Context context, String system, String user, int maxTokens,
                            String cacheKey, Callback callback) {
        chat(context, system, user, maxTokens, cacheKey, false, callback);
    }

    /**
     * Для ответов-JSON (упражнения): если модель оставила content пустым, но ответ есть в её
     * «размышлениях», берём JSON оттуда, потому что вызывающий код всё равно вырезает из текста только JSON.
     */
    public static void chatStructured(Context context, String system, String user, int maxTokens,
                                      String cacheKey, Callback callback) {
        chat(context, system, user, maxTokens, cacheKey, true, callback);
    }

    private static void chat(Context context, String system, String user, int maxTokens,
                             String cacheKey, boolean allowReasoning, Callback callback) {
        final Context app = context.getApplicationContext();
        if (!isConfigured()) {
            callback.onError(Kind.NOT_CONFIGURED, "LLM_API_KEY is empty");
            return;
        }
        final String fullKey = cacheKey == null ? null : cacheKey + "#" + Integer.toHexString((system + user).hashCode());
        if (fullKey != null) {
            String cached = readCache(app, fullKey);
            if (cached != null) {
                callback.onResult(cached);
                return;
            }
        }
        EXEC.execute(() -> {
            try {
                String text = request(system, user, maxTokens, allowReasoning,
                        System.currentTimeMillis() + (allowReasoning ? BUDGET_STRUCTURED_MS : BUDGET_TEXT_MS));
                if (fullKey != null) writeCache(app, fullKey, text);
                MAIN.post(() -> callback.onResult(text));
            } catch (LlmException e) {
                Log.e(TAG, e.kind + ": " + e.getMessage());
                MAIN.post(() -> callback.onError(e.kind, e.getMessage()));
            } catch (java.net.SocketTimeoutException e) {
                Log.e(TAG, "Timeout", e);
                MAIN.post(() -> callback.onError(Kind.TIMEOUT, String.valueOf(e.getMessage())));
            } catch (Exception e) {
                Log.e(TAG, "Network error", e);
                MAIN.post(() -> callback.onError(Kind.NETWORK, String.valueOf(e.getMessage())));
            }
        });
    }

    // ---------- запрос ----------

    private static String request(String system, String user, int maxTokens, boolean allowReasoning, long deadline) throws Exception {
        List<String> models = models();
        if (models.isEmpty()) throw new LlmException(Kind.NOT_CONFIGURED, "LLM_MODELS is empty");

        // Перебираем модели сами (а не через поле "models" у OpenRouter): так следующая модель берётся
        // и тогда, когда первая ответила пустым текстом (reasoning-модели иногда тратят весь лимит на «размышления»)
        LlmException last = null;
        for (String model : models) {
            // бесплатные модели часто отвечают 5xx или пустотой «с первого раза»: пробуем до трёх раз с нарастающей паузой
            for (int attempt = 0; attempt < 3; attempt++) {
                if (deadline - System.currentTimeMillis() < 4000) throw new LlmException(Kind.TIMEOUT, "timeout, last: " + (last == null ? "-" : last.getMessage()));
                try {
                    return requestOnce(model, null, system, user, maxTokens, allowReasoning, deadline);
                } catch (LlmException e) {
                    last = e;
                    Log.w(TAG, model + " attempt " + (attempt + 1) + ": " + e.kind + " " + e.getMessage());
                    // неверный ключ перебором моделей не исправить
                    if (e.kind == Kind.AUTH || e.kind == Kind.NOT_CONFIGURED) throw e;
                    if (e.kind == Kind.LIMIT) break;        // лимит этой модели: сразу следующая
                    try { Thread.sleep(800L * (attempt + 1)); } catch (InterruptedException ignored) { }
                } catch (java.io.IOException io) {
                    // медленная или недоступная модель: не ждём её повторно, переходим к следующей
                    boolean slow = io instanceof java.net.SocketTimeoutException;
                    last = new LlmException(slow ? Kind.TIMEOUT : Kind.NETWORK, String.valueOf(io.getMessage()));
                    Log.w(TAG, model + ": " + last.kind + " " + io.getMessage());
                    break;
                }
            }
        }
        throw last;
    }

    private static String requestOnce(String model, List<String> allModels, String system,
                                      String user, int maxTokens, boolean allowReasoning, long deadline) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);
        if (allModels != null && allModels.size() > 1) body.put("models", new JSONArray(allModels));
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", system));
        messages.put(new JSONObject().put("role", "user").put("content", user));
        body.put("messages", messages);
        // запас на «размышления» моделей: без него ответ часто приходит пустым (content = null)
        body.put("max_tokens", Math.max(maxTokens * 2, 500));
        if (isOpenRouter()) body.put("reasoning", new JSONObject().put("effort", "low"));
        body.put("temperature", 0.4);

        HttpURLConnection conn = (HttpURLConnection) new URL(BuildConfig.LLM_BASE_URL + "/chat/completions").openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout((int) Math.max(4000, Math.min(deadline - System.currentTimeMillis(), 40_000)));
            conn.setRequestProperty("Authorization", "Bearer " + BuildConfig.LLM_API_KEY);
            conn.setRequestProperty("Content-Type", "application/json");
            if (!BuildConfig.LLM_PROJECT.isEmpty()) conn.setRequestProperty("OpenAI-Project", BuildConfig.LLM_PROJECT);
            if (isOpenRouter()) {
                conn.setRequestProperty("HTTP-Referer", "https://github.com/ssnbrd/WordTrigger");
                conn.setRequestProperty("X-OpenRouter-Title", "WordTrigger");
            }
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes("UTF-8"));
            }

            int code = conn.getResponseCode();
            Scanner sc = new Scanner(code < 400 ? conn.getInputStream() : conn.getErrorStream(), "UTF-8").useDelimiter("\\A");
            String response = sc.hasNext() ? sc.next() : "";
            if (code >= 400) throw new LlmException(kindForHttp(code), "HTTP " + code + " " + shorten(response));
            return parseContent(response, allowReasoning);
        } finally {
            conn.disconnect();
        }
    }

    private static Kind kindForHttp(int code) {
        if (code == 401 || code == 403) return Kind.AUTH;
        if (code == 402 || code == 429) return Kind.LIMIT;
        return Kind.SERVER;
    }

    private static String parseContent(String response, boolean allowReasoning) throws Exception {
        JSONObject json = new JSONObject(response);
        // OpenRouter иногда отвечает 200, но с полем error
        if (json.has("error")) {
            JSONObject err = json.optJSONObject("error");
            int code = err != null ? err.optInt("code", 0) : 0;
            throw new LlmException(code == 0 ? Kind.SERVER : kindForHttp(code), shorten(response));
        }
        JSONArray choices = json.optJSONArray("choices");
        if (choices == null || choices.length() == 0) throw new LlmException(Kind.BAD_RESPONSE, shorten(response));
        JSONObject message = choices.getJSONObject(0).optJSONObject("message");
        // org.json превращает JSON null в строку "null", поэтому проверяем отдельно
        String content = textOf(message, "content");
        if ("null".equalsIgnoreCase(content.trim())) content = "";
        // некоторые модели оставляют рассуждения в тексте
        content = content.replaceAll("(?s)<think>.*?</think>", "").trim();
        if (content.isEmpty() && allowReasoning && message != null) {
            String reasoning = textOf(message, "reasoning");
            if (reasoning.isEmpty()) reasoning = textOf(message, "reasoning_content");
            if (!reasoning.isEmpty()) content = reasoning.trim();
        }
        if (content.isEmpty()) {
            String finish = choices.getJSONObject(0).optString("finish_reason", "?");
            throw new LlmException(Kind.BAD_RESPONSE, "empty content (finish_reason=" + finish + ")");
        }
        return content;
    }

    /** Текст поля сообщения: строка или массив частей {"text": ...}; JSON null даёт пустую строку. */
    private static String textOf(JSONObject message, String field) {
        if (message == null || message.isNull(field)) return "";
        Object v = message.opt(field);
        if (v instanceof JSONArray) {
            StringBuilder sb = new StringBuilder();
            JSONArray arr = (JSONArray) v;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject part = arr.optJSONObject(i);
                if (part != null) sb.append(part.optString("text", ""));
            }
            return sb.toString();
        }
        return String.valueOf(v);
    }

    /** Достаёт первый JSON-объект из ответа модели (она может обернуть его в ```json ... ```). */
    public static JSONObject extractJson(String text) {
        try {
            int a = text.indexOf('{');
            int b = text.lastIndexOf('}');
            if (a < 0 || b <= a) return null;
            return new JSONObject(text.substring(a, b + 1));
        } catch (Exception e) {
            return null;
        }
    }

    /** То же для JSON-массива строк. */
    public static List<String> extractStringArray(String text) {
        List<String> out = new ArrayList<>();
        try {
            int a = text.indexOf('[');
            int b = text.lastIndexOf(']');
            if (a < 0 || b <= a) return out;
            JSONArray arr = new JSONArray(text.substring(a, b + 1));
            for (int i = 0; i < arr.length(); i++) out.add(arr.getString(i));
        } catch (Exception ignored) { }
        return out;
    }

    private static String shorten(String s) {
        return s.length() > 300 ? s.substring(0, 300) : s;
    }

    // ---------- кэш на день ----------

    private static String readCache(Context c, String key) {
        SharedPreferences p = c.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE);
        String v = p.getString(key, null);
        if (v == null) return null;
        int bar = v.indexOf('|');
        return bar > 0 && today().equals(v.substring(0, bar)) ? v.substring(bar + 1) : null;
    }

    private static void writeCache(Context c, String key, String text) {
        SharedPreferences p = c.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor e = p.edit();
        // кэш хранит только записи за сегодня
        for (String k : p.getAll().keySet()) {
            String v = p.getString(k, "");
            if (!v.startsWith(today() + "|")) e.remove(k);
        }
        e.putString(key, today() + "|" + text).apply();
    }

    private static final class LlmException extends Exception {
        final Kind kind;
        LlmException(Kind kind, String message) {
            super(message);
            this.kind = kind;
        }
    }
}
