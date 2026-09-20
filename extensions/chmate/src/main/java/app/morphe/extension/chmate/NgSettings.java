package app.morphe.extension.chmate;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.util.List;

final class NgSettings {
    static void show(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(ProgrammableNg.PREFS, Context.MODE_PRIVATE);
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        Switch enabled = new Switch(activity);
        enabled.setText("プログラマブルNGを有効にする");
        enabled.setChecked(prefs.getBoolean("enabled", false));
        layout.addView(enabled);
        label(layout, "エッヂ専用・191 dev試作版\n(text, options) => true でNG。空欄は無効。\n"
                + "speedはレス/日、createdAtはDate、posterIdは記者ID。取得できない値はnullです。\n"
                + "レスではresponseId／responseNumberも使用できます。\n"
                + "同じ引数に同じ結果を返してください。複数条件は || や && でまとめます。\n"
                + "例：(text, {speed}) => speed !== null && speed > 10000");
        EditText title = editor(layout, "スレッドのNG関数", prefs.getString("title", ""));
        EditText body = editor(layout, "レス本文のNG関数", prefs.getString("body", ""));
        TextView status = label(layout, ProgrammableNg.status());
        Button test = new Button(activity);
        test.setText("取得済みデータでテスト／引数を確認");
        layout.addView(test);
        test.setOnClickListener(view -> {
            String result = preview("スレ", title.getText().toString(), ProgrammableNg.lastTitles)
                    + "\n\n" + preview("レス", body.getText().toString(), ProgrammableNg.lastBodies);
            status.setText(result);
        });
        label(layout, "先に板やスレを開くと、テストに実データを使用できます。\n"
                + "記者IDを使う場合はHaiagaruの記者ID表示をONにして板を再取得してください。\n"
                + "保存後は板／スレを再読み込みしてください。\n"
                + "スレNGはChMateのNG一覧へ、レスNGは通常のNGWord表示へ反映します。\n"
                + "この版では連鎖NG・透明NGへの自動連動は追加しません。");
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(layout);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("プログラマブルNG v0.1（191 dev）")
                .setView(scroll).setNegativeButton("閉じる", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            boolean saved = prefs.edit().putBoolean("enabled", enabled.isChecked())
                    .putString("title", title.getText().toString()).putString("body", body.getText().toString()).commit();
            if (!saved) { status.setText("保存に失敗しました。"); return; }
            ProgrammableNg.reload();
            status.setText("保存しました。板／スレを再読み込みしてください。\n" + ProgrammableNg.status());
        }));
        dialog.show();
    }
    private static String preview(String name, String source, List<NgScriptEngine.Input> inputs) {
        if (inputs.isEmpty()) return name + "：実データ未取得。先に板／スレを開いてください。";
        NgScriptEngine engine = new NgScriptEngine(source);
        boolean[] results = engine.evaluate(inputs);
        int count = 0;
        for (boolean value : results) if (value) count++;
        NgScriptEngine.Input sample = inputs.get(0);
        return name + "：" + count + "/" + results.length + "件がNG\n"
                + (engine.error() == null ? "" : "エラー：" + engine.error() + "\n")
                + "先頭のtext：" + sample.text.substring(0, Math.min(sample.text.length(), 200)) + "\n"
                + "先頭のoptions（createdAtはcreatedAtMsからDateに変換）：\n" + sample.options.toString();
    }
    private static TextView label(LinearLayout layout, String value) {
        TextView text = new TextView(layout.getContext());
        text.setText(value);
        text.setPadding(0, 10, 0, 10);
        layout.addView(text);
        return text;
    }
    private static EditText editor(LinearLayout layout, String name, String value) {
        label(layout, name);
        EditText input = new EditText(layout.getContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setTypeface(Typeface.MONOSPACE);
        input.setTextSize(13);
        input.setMinLines(3);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(NgScriptEngine.MAX_SOURCE)});
        input.setText(value);
        layout.addView(input, new LinearLayout.LayoutParams(-1, -2));
        return input;
    }
}
