package app.morphe.extension.chmate;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Evaluates local user rules; no Android objects or Java bridges enter the JS scope. */
public final class NgScriptEngine {
    public static final int MAX_SOURCE = 32768;
    private final String source;
    private volatile String error;
    private final Map<Input, Boolean> cache = new LinkedHashMap<Input, Boolean>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Input, Boolean> entry) {
            return size() > 4096;
        }
    };

    public static final class Input {
        public final String text;
        public final Map<String, Object> options;
        public Input(String text, Map<String, Object> options) {
            this.text = text == null ? "" : text;
            this.options = new LinkedHashMap<>(options);
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Input)) return false;
            Input input = (Input) other;
            return text.equals(input.text) && options.equals(input.options);
        }
        @Override public int hashCode() { return 31 * text.hashCode() + options.hashCode(); }
    }

    private static final class Limit extends Error {
        Limit() { super("実行上限を超えたためルールを停止しました。"); }
    }
    private static final class BudgetFactory extends ContextFactory {
        long deadline;
        int instructions;
        @Override protected Context makeContext() {
            Context cx = super.makeContext();
            cx.setLanguageVersion(Context.VERSION_ES6);
            cx.setOptimizationLevel(-1); // Android cannot load generated JVM class files.
            cx.setMaximumInterpreterStackDepth(128);
            cx.setInstructionObserverThreshold(1000);
            cx.setClassShutter(className -> false);
            return cx;
        }
        @Override protected void observeInstructionCount(Context cx, int count) {
            instructions += count;
            if (instructions > 2_000_000 || System.nanoTime() > deadline
                    || Thread.currentThread().isInterrupted()) throw new Limit();
        }
    }

    public NgScriptEngine(String source) {
        this.source = source == null ? "" : source;
        if (this.source.length() > MAX_SOURCE) error = "関数は32768文字以内にしてください。";
    }
    public String error() { return error; }

    /** Atomic batch: an invalid/timed-out rule contributes no NG results. */
    public synchronized boolean[] evaluate(List<Input> inputs) {
        boolean[] results = new boolean[inputs.size()];
        if (source.trim().isEmpty() || error != null) return results;
        if (inputs.size() > 6000) {
            error = "一度に判定できる上限（6000件）を超えました。";
            return results;
        }
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            Boolean value = cache.get(inputs.get(i));
            if (value == null) missing.add(i); else results[i] = value;
        }
        if (missing.isEmpty()) return results;
        try {
            BudgetFactory factory = new BudgetFactory();
            factory.call(cx -> {
                ScriptableObject scope = cx.initSafeStandardObjects();
                // No Packages, java, Android Context, DOM, network or file handles.
                factory.deadline = System.nanoTime() + 250_000_000L;
                Object value = cx.evaluateString(scope, "\"use strict\"; (" + source + "\n)", "NG rule", 1, null);
                if (!(value instanceof Function)) throw new IllegalArgumentException("関数全体を入力してください。");
                Function predicate = (Function) value;
                for (int index : missing) {
                    if (System.nanoTime() > factory.deadline) throw new Limit();
                    Input input = inputs.get(index);
                    if (input.text.length() > 131072) throw new IllegalArgumentException("判定対象の本文が長すぎます。");
                    Scriptable options = cx.newObject(scope);
                    for (Map.Entry<String, Object> entry : input.options.entrySet()) {
                        Object item = entry.getValue();
                        if (item != null && !(item instanceof String) && !(item instanceof Number)
                                && !(item instanceof Boolean)) throw new IllegalArgumentException("未対応の引数型です。");
                        ScriptableObject.putProperty(options, entry.getKey(), item);
                    }
                    Object created = input.options.get("createdAtMs");
                    ScriptableObject.putProperty(options, "createdAt", created == null ? null
                            : cx.newObject(scope, "Date", new Object[]{created}));
                    Object result = predicate.call(cx, scope, scope, new Object[]{input.text, options});
                    if (!(result instanceof Boolean)) throw new IllegalArgumentException("戻り値はtrueかfalseにしてください。");
                    results[index] = (Boolean) result;
                }
                return null;
            });
            for (int index : missing) cache.put(inputs.get(index), results[index]);
            return results;
        } catch (Limit | StackOverflowError exception) {
            error = "実行時間・命令数・再帰の上限を超えました。保存し直すと再試行します。";
        } catch (LinkageError exception) {
            error = "この端末でJavaScriptエンジンを初期化できません：" + exception.getClass().getSimpleName();
        } catch (RuntimeException exception) {
            error = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        }
        cache.clear();
        return new boolean[inputs.size()];
    }
}
