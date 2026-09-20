package app.morphe.extension.chmate;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.Html;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Reflection names below are verified against the supplied 0.8.10.191 dev DEX. */
public final class ProgrammableNg {
    static final String PREFS = "haiagaru.programmable-ng.v1";
    private static Context context;
    private static volatile boolean enabled;
    private static volatile NgScriptEngine titleEngine = new NgScriptEngine("");
    private static volatile NgScriptEngine bodyEngine = new NgScriptEngine("");
    private static final Map<Object, Set<Integer>> responseMatches = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<String, NgScriptEngine.Input> threads = Collections.synchronizedMap(
            new LinkedHashMap<String, NgScriptEngine.Input>(128, .75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, NgScriptEngine.Input> entry) {
                    return size() > 3000;
                }
            });
    static volatile List<NgScriptEngine.Input> lastTitles = Collections.emptyList();
    static volatile List<NgScriptEngine.Input> lastBodies = Collections.emptyList();
    static volatile String hookError = "";

    public static void initialize(Context supplied) {
        if (supplied == null || context != null || android.os.Build.VERSION.SDK_INT < 26) return;
        try {
            if (!"0.8.10.191 dev".equals(supplied.getPackageManager().getPackageInfo(supplied.getPackageName(), 0).versionName)) return;
            Context app = supplied.getApplicationContext();
            context = app == null ? supplied : app;
            reload();
        } catch (Exception e) { report(e); }
    }
    static void reload() {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        titleEngine = new NgScriptEngine(prefs.getString("title", ""));
        bodyEngine = new NgScriptEngine(prefs.getString("body", ""));
        enabled = prefs.getBoolean("enabled", false);
        responseMatches.clear();
        hookError = "";
    }
    public static void addSettingsButton(LinearLayout parent, Activity activity) {
        if (context == null) return;
        Button button = new Button(activity);
        button.setText("プログラマブルNG（191 dev・エッヂ）");
        button.setOnClickListener(view -> NgSettings.show(activity));
        parent.addView(button);
    }
    static Object field(Object owner, String name) throws ReflectiveOperationException {
        return owner.getClass().getField(name).get(owner);
    }
    private static String boardUrl(Object urlInfo) throws ReflectiveOperationException {
        return (String) urlInfo.getClass().getMethod("i").invoke(urlInfo);
    }
    private static boolean edge(String board) {
        return board != null && board.matches("https?://bbs\\.eddibb\\.cc(?::(?:80|443))?/liveedge/?");
    }
    private static String key(String board, long id) { return board + "#" + id; }
    private static void report(Exception e) {
        hookError = "191 devとの接続に失敗：" + e.getClass().getSimpleName() + ": " + e.getMessage();
        Log.w("HaiagaruNG", "Programmable NG hook failed", e);
    }

    /** Called after stock title filters; retains native NG-list reveal behavior. */
    public static ArrayList<?> filterThreads(Object fragment, ArrayList<?> candidates, Object unused, ArrayList<Object> hidden) {
        if (candidates == null || context == null) return candidates;
        try {
            Object urlInfo = field(fragment, "e");
            if (urlInfo == null) return candidates;
            String board = boardUrl(urlInfo);
            if (!edge(board)) return candidates;
            long now = System.currentTimeMillis();
            List<NgScriptEngine.Input> inputs = new ArrayList<>();
            for (Object row : candidates) {
                Object data = field(row, "a");
                long id = ((Number) field(data, "e")).longValue();
                NgScriptEngine.Input input = NgMetadata.thread((String) field(data, "c"), board, id,
                        ((Number) field(row, "d")).intValue(), now);
                threads.put(key(board, id), input);
                inputs.add(input);
            }
            lastTitles = inputs;
            if (!enabled) return candidates;
            boolean[] results = titleEngine.evaluate(inputs);
            ArrayList<Object> kept = new ArrayList<>();
            ArrayList<Object> excluded = new ArrayList<>();
            for (int i = 0; i < candidates.size(); i++) {
                (results[i] ? excluded : kept).add(candidates.get(i));
            }
            if (excluded.isEmpty()) return candidates;
            // Only mutate the native NG list once the complete batch succeeds.
            if (hidden != null) hidden.addAll(excluded);
            return kept;
        } catch (Exception e) { report(e); return candidates; }
    }

    /** Run once when the native response adapter rebuilds, not once per rendered row. */
    public static void prepareResponses(Object adapter) {
        responseMatches.remove(adapter);
        if (context == null) return;
        try {
            Object state = field(adapter, "M");
            Object urlInfo = field(state, "f");
            if (urlInfo == null) return;
            String board = boardUrl(urlInfo);
            if (!edge(board)) return;
            Number idValue = (Number) field(urlInfo, "e");
            if (idValue == null) return;
            long id = idValue.longValue();
            List<?> responses = (List<?>) field(adapter, "K");
            if (responses == null) return;
            String rawTitle = (String) field(responses, "title");
            NgScriptEngine.Input known = threads.get(key(board, id));
            Integer count = known == null ? null : (Integer) known.options.get("resCount");
            // Do not confuse locally downloaded responses with the board's total count.
            NgScriptEngine.Input thread = NgMetadata.thread(rawTitle, board, id, count, System.currentTimeMillis());
            List<NgScriptEngine.Input> inputs = new ArrayList<>();
            List<Integer> numbers = new ArrayList<>();
            for (Object response : responses) {
                Map<String, Object> options = new LinkedHashMap<>(thread.options);
                options.put("target", "body");
                if (known != null) options.put("posterId", known.options.get("posterId"));
                options.put("threadTitle", thread.text);
                options.put("responseId", field(response, "h"));
                int number = ((Number) field(response, "o")).intValue();
                options.put("responseNumber", number);
                options.put("name", plain((String) field(response, "n")));
                options.put("mail", field(response, "j"));
                options.put("dateText", field(response, "q"));
                inputs.add(new NgScriptEngine.Input(plain((String) field(response, "c")), options));
                numbers.add(number);
            }
            lastBodies = inputs;
            if (!enabled) return;
            boolean[] matches = bodyEngine.evaluate(inputs);
            Set<Integer> matched = new HashSet<>();
            for (int i = 0; i < matches.length; i++) if (matches[i]) matched.add(numbers.get(i));
            responseMatches.put(adapter, matched);
        } catch (Exception e) { report(e); }
    }
    @SuppressWarnings("deprecation")
    private static String plain(String html) { return html == null ? "" : Html.fromHtml(html).toString(); }

    /** Combine with the native result; never clears stock NG or overrides NG-off mode. */
    public static int responseFlags(Object adapter, Object response, int original) {
        if (!enabled || bodyEngine.error() != null) return original;
        try {
            java.lang.reflect.Field ngEnabled = adapter.getClass().getDeclaredField("p");
            ngEnabled.setAccessible(true);
            if (!ngEnabled.getBoolean(adapter)) return original;
            Set<Integer> matches = responseMatches.get(adapter);
            if (matches != null && matches.contains(((Number) field(response, "o")).intValue())) return original | 16;
        } catch (Exception e) { report(e); }
        return original;
    }
    static String status() {
        return "取得済み：スレ " + lastTitles.size() + "件／レス " + lastBodies.size() + "件\n"
                + "スレルール：" + (titleEngine.error() == null ? "エラーなし" : titleEngine.error()) + "\n"
                + "レスルール：" + (bodyEngine.error() == null ? "エラーなし" : bodyEngine.error()) + "\n" + hookError;
    }
}
