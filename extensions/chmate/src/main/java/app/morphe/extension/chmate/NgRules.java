package app.morphe.extension.chmate;

import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/** Named, independently scoped rules. URL matching is exact, never a substring. */
public final class NgRules {
    public static final int MAX_RULES = 32;
    public static final class Rule {
        public final String id, name, target, boards, source;
        public final boolean enabled, allBoards;
        final NgScriptEngine engine;
        public Rule(String id, String name, String target, boolean enabled, boolean allBoards, String boards, String source) {
            if (id == null || id.isEmpty() || name == null || name.trim().isEmpty()) throw new IllegalArgumentException("ルール名を入力してください。");
            if (!"title".equals(target) && !"body".equals(target)) throw new IllegalArgumentException("判定対象が不正です。");
            if (source == null || source.length() > NgScriptEngine.MAX_SOURCE) throw new IllegalArgumentException("関数が長すぎます。");
            if (!allBoards && (boards == null || boards.trim().isEmpty())) throw new IllegalArgumentException("対象板URLを1行に1つ入力してください。");
            if (!allBoards) for (String board : boards.trim().split("\\s+")) if (boardKey(board) == null) throw new IllegalArgumentException("板URLが不正です：" + board);
            this.id=id; this.name=name.trim(); this.target=target; this.enabled=enabled; this.allBoards=allBoards;
            this.boards=boards == null ? "" : boards.trim(); this.source=source; this.engine=new NgScriptEngine(source);
        }
        public boolean accepts(NgScriptEngine.Input input) {
            if (!enabled || !target.equals(input.options.get("target"))) return false;
            String key = boardKey((String) input.options.get("boardUrl"));
            if (key == null) return false;
            if (allBoards) return true;
            for (String board : boards.split("\\s+")) if (key.equals(boardKey(board))) return true;
            return false;
        }
    }
    public final List<Rule> rules;
    public volatile String batchError = "";
    public NgRules(List<Rule> rules) {
        if (rules.size() > MAX_RULES) throw new IllegalArgumentException("ルールは32件までです。");
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Rule rule : rules) if (!ids.add(rule.id)) throw new IllegalArgumentException("ルールIDが重複しています。");
        this.rules = Collections.unmodifiableList(new ArrayList<>(rules));
    }
    public static String boardKey(String value) {
        try {
            URI uri = new URI(value);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) return null;
            String path = uri.getPath();
            if (path == null || !path.matches("/[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*/?")) return null;
            int port = uri.getPort();
            return uri.getHost().toLowerCase(Locale.ROOT) + (port == -1 || port == 80 || port == 443 ? "" : ":" + port)
                    + path.replaceAll("/+$", "");
        } catch (Exception e) { return null; }
    }
    /** Any matching enabled rule hides an item. Failed rules contribute no matches. */
    public boolean[] evaluate(List<NgScriptEngine.Input> inputs) {
        boolean[] combined = new boolean[inputs.size()];
        long deadline = System.nanoTime() + 500_000_000L;
        batchError = "";
        for (Rule rule : rules) {
            if (!rule.enabled) continue;
            List<NgScriptEngine.Input> selected = new ArrayList<>();
            List<Integer> indices = new ArrayList<>();
            for (int i=0; i<inputs.size(); i++) if (rule.accepts(inputs.get(i))) { selected.add(inputs.get(i)); indices.add(i); }
            if (selected.isEmpty()) continue;
            if (System.nanoTime() > deadline) {
                batchError = "ルール全体の実行予算を超えたため、この判定ではNGにしません。";
                return new boolean[inputs.size()];
            }
            boolean[] values = rule.engine.evaluate(selected);
            for (int i=0; i<values.length; i++) combined[indices.get(i)] |= values[i];
        }
        return combined;
    }
    public String encode() {
        Properties p = new Properties(); p.setProperty("version", "2"); p.setProperty("count", ""+rules.size());
        for (int i=0; i<rules.size(); i++) {
            Rule r=rules.get(i); String k=i+".";
            p.setProperty(k+"id",r.id); p.setProperty(k+"name",r.name); p.setProperty(k+"target",r.target);
            p.setProperty(k+"enabled",""+r.enabled); p.setProperty(k+"allBoards",""+r.allBoards);
            p.setProperty(k+"boards",r.boards); p.setProperty(k+"source",r.source);
        }
        try { StringWriter w=new StringWriter(); p.store(w,null); return w.toString(); }
        catch (java.io.IOException e) { throw new IllegalArgumentException(e); }
    }
    public static NgRules decode(String text) {
        try {
            Properties p=new Properties(); p.load(new StringReader(text));
            if (!"2".equals(p.getProperty("version"))) throw new IllegalArgumentException("保存形式が未対応です。");
            int count=Integer.parseInt(p.getProperty("count"));
            if (count<0 || count>MAX_RULES) throw new IllegalArgumentException("ルール件数が不正です。");
            List<Rule> list=new ArrayList<>();
            for(int i=0;i<count;i++) { String k=i+"."; list.add(new Rule(p.getProperty(k+"id"),p.getProperty(k+"name"),p.getProperty(k+"target"),
                Boolean.parseBoolean(p.getProperty(k+"enabled")),Boolean.parseBoolean(p.getProperty(k+"allBoards")),p.getProperty(k+"boards"),p.getProperty(k+"source"))); }
            return new NgRules(list);
        } catch (Exception e) { throw new IllegalArgumentException("ルール設定を読めません："+e.getMessage(),e); }
    }
    public static NgRules legacy(String title, String body) {
        List<Rule> list=new ArrayList<>();
        if (title != null && !title.trim().isEmpty()) list.add(new Rule("legacy-title","以前のスレNG","title",true,false,"https://bbs.eddibb.cc/liveedge/",title));
        if (body != null && !body.trim().isEmpty()) list.add(new Rule("legacy-body","以前のレスNG","body",true,false,"https://bbs.eddibb.cc/liveedge/",body));
        return new NgRules(list);
    }
}
