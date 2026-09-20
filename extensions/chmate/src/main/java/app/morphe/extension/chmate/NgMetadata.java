package app.morphe.extension.chmate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Contract shared by Android hooks and the JVM tests. Times are Unix milliseconds. */
public final class NgMetadata {
    private static final Pattern REPORTER = Pattern.compile("\\s+\\[([^\\[\\]\\s]+)★\\]\\s*$");
    public static NgScriptEngine.Input thread(String title, String boardUrl, long id, Integer count, long nowMs) {
        String text = title == null ? "" : title;
        String posterId = null;
        boolean edge = boardUrl != null && boardUrl.matches("https?://bbs\\.eddibb\\.cc(?::(?:80|443))?/liveedge/?");
        if (edge) {
            Matcher matcher = REPORTER.matcher(text);
            if (matcher.find()) {
                posterId = matcher.group(1);
                text = text.substring(0, matcher.start());
            }
        }
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("target", "title");
        options.put("boardUrl", boardUrl);
        options.put("threadId", id > 0 ? id : null);
        // This first port is scoped to Edge, whose keys are Unix seconds.
        Long created = edge && id > 0 && id <= 8_640_000_000_000L ? id * 1000 : null;
        options.put("createdAtMs", created);
        options.put("resCount", count != null && count >= 0 ? count : null);
        options.put("speed", created != null && nowMs > created && count != null && count >= 0
                ? count * 86400000.0 / (nowMs - created) : null);
        options.put("posterId", posterId);
        options.put("responseId", null);
        options.put("responseNumber", null);
        return new NgScriptEngine.Input(text, options);
    }
}
