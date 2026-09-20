import app.morphe.extension.chmate.NgMetadata;
import app.morphe.extension.chmate.NgScriptEngine;
import java.util.*;

/** Plain JVM tests; execute with the Rhino runtime used by the extension. */
public final class VerifyNgScriptEngine {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        long id = 1789868590L;
        String board = "https://bbs.eddibb.cc/liveedge/";
        NgScriptEngine.Input fast = NgMetadata.thread("テスト [gieCrxgk★]", board, id, 439, (id + 1740) * 1000);
        NgScriptEngine.Input slow = NgMetadata.thread("テスト [otherID★]", board, id, 1, (id + 1740) * 1000);
        check(fast.text.equals("テスト"), "separate reporter suffix");
        check(fast.options.get("posterId").equals("gieCrxgk"), "reporter");
        check((Double) fast.options.get("speed") > 21000, "speed is responses/day");
        check(NgMetadata.thread("x", board, id, 2, id * 1000).options.get("speed") == null, "zero age");
        check(NgMetadata.thread("x", board, id, null, (id + 1) * 1000).options.get("speed") == null, "missing count");
        check(NgMetadata.thread("x [id★]", "https://other.example/board/", id, 2, (id+1)*1000).options.get("posterId") == null, "scope");
        NgScriptEngine engine = new NgScriptEngine("(text, {speed, posterId, createdAt}) => speed > 10000 && posterId === 'gieCrxgk' && createdAt.getTime() === 1789868590000");
        boolean[] first = engine.evaluate(Arrays.asList(fast, slow));
        check(engine.error() == null && first[0] && !first[1], "metadata in real Rhino: " + engine.error());
        boolean[] again = engine.evaluate(Arrays.asList(slow, fast));
        check(!again[0] && again[1], "cache includes metadata");
        check(new NgScriptEngine("text => text.includes('テスト')").evaluate(Arrays.asList(fast))[0], "old single argument");
        NgScriptEngine defaults = new NgScriptEngine("(text, { speed = null } = {}) => speed !== null");
        check(defaults.evaluate(Arrays.asList(fast))[0], "default destructuring: " + defaults.error());
        NgScriptEngine invalid = new NgScriptEngine("() => 1");
        check(!invalid.evaluate(Arrays.asList(fast))[0] && invalid.error() != null, "boolean only");
        NgScriptEngine failure = new NgScriptEngine("(t,o) => { if(o.posterId === 'otherID') throw new Error('bad'); return true; }");
        check(Arrays.equals(failure.evaluate(Arrays.asList(fast, slow)), new boolean[2]), "batch fail open");
        check(!failure.evaluate(Arrays.asList(fast))[0], "failed rule stays disabled");
        NgScriptEngine restricted = new NgScriptEngine("() => typeof java === 'undefined' && typeof Packages === 'undefined' && typeof document === 'undefined'");
        check(restricted.evaluate(Arrays.asList(fast))[0], "no Java/DOM bridge");
        NgScriptEngine loop = new NgScriptEngine("() => { while(true) {} }");
        long start = System.nanoTime();
        check(!loop.evaluate(Arrays.asList(fast))[0] && loop.error() != null, "loop stopped");
        check(System.nanoTime() - start < 2_000_000_000L, "bounded loop time");
        NgScriptEngine syntax = new NgScriptEngine("(text =>");
        check(!syntax.evaluate(Arrays.asList(fast))[0] && syntax.error() != null, "syntax error");
        System.out.println("PASS: native metadata, speed units, missing/zero age, ES6/default parameters, Date, cache, old rules, error isolation, instruction limits");
    }
}
