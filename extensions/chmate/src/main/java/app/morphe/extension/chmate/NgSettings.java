package app.morphe.extension.chmate;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class NgSettings {
    static void show(Activity activity) {
        SharedPreferences prefs=activity.getSharedPreferences(ProgrammableNg.PREFS, Context.MODE_PRIVATE);
        final List<NgRules.Rule> draft;
        try { draft=new ArrayList<>(ProgrammableNg.readRules(prefs).rules); }
        catch (IllegalArgumentException e) { new AlertDialog.Builder(activity).setMessage(e.getMessage()).setPositiveButton("閉じる",null).show(); return; }
        LinearLayout layout=column(activity);
        Switch enabled=new Switch(activity); enabled.setText("プログラマブルNGを有効にする");
        enabled.setChecked(prefs.getBoolean("enabled",false)); layout.addView(enabled);
        label(layout,"名前付きルールを32件まで登録できます。どれか1つに一致するとNGになります。\n各ルールでスレ／レス、対象板を指定してください。");
        LinearLayout rows=column(activity); layout.addView(rows);
        TextView status=label(layout,ProgrammableNg.status());
        Runnable[] render=new Runnable[1];
        render[0]=() -> {
            rows.removeAllViews();
            for(int i=0;i<draft.size();i++) {
                final int index=i; NgRules.Rule rule=draft.get(i);
                Switch toggle=new Switch(activity); toggle.setText(rule.name + (rule.target.equals("title") ? "［スレ］" : "［レス］"));
                toggle.setChecked(rule.enabled); rows.addView(toggle);
                toggle.setOnCheckedChangeListener((button,on) -> {
                    NgRules.Rule r=draft.get(index);
                    draft.set(index,new NgRules.Rule(r.id,r.name,r.target,on,r.allBoards,r.boards,r.source));
                });
                label(rows,rule.allBoards ? "対象：すべての板" : "対象："+rule.boards);
                Button edit=new Button(activity); edit.setText("編集・テスト"); rows.addView(edit);
                edit.setOnClickListener(v -> edit(activity,draft,index,render[0]));
                Button delete=new Button(activity); delete.setText("削除"); rows.addView(delete);
                delete.setOnClickListener(v -> new AlertDialog.Builder(activity).setMessage("「"+draft.get(index).name+"」を削除しますか？")
                    .setNegativeButton("戻る",null).setPositiveButton("削除",(d,w)->{draft.remove(index);render[0].run();}).show());
            }
        };
        render[0].run();
        Button add=new Button(activity); add.setText("ルールを追加"); layout.addView(add);
        add.setOnClickListener(v->{if(draft.size()>=NgRules.MAX_RULES) status.setText("32件まで登録できます。"); else edit(activity,draft,-1,render[0]);});
        label(layout,"編集後、この画面の「保存」で反映します。保存後は板／スレを再読み込みしてください。\n記者IDはエッヂ専用です。記者ID表示をONにして板を再取得してください。\n取得できない引数はnullです。板の種類によっては未対応の項目があります。");
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("プログラマブルNG v0.2（191 dev）")
            .setView(scroll(activity,layout)).setNegativeButton("閉じる",null).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            boolean saved=prefs.edit().putBoolean("enabled",enabled.isChecked()).putString("rules.v2",new NgRules(draft).encode()).commit();
            if(!saved){status.setText("保存に失敗しました。");return;}
            ProgrammableNg.reload();status.setText("保存しました。板／スレを再読み込みしてください。\n"+ProgrammableNg.status());
        }));dialog.show();
    }
    private static void edit(Activity activity,List<NgRules.Rule> draft,int index,Runnable render) {
        NgRules.Rule old=index<0 ? null : draft.get(index);
        LinearLayout layout=column(activity);
        EditText name=editor(layout,"ルール名",old==null?"":old.name,1);
        Spinner target=new Spinner(activity);
        target.setAdapter(new ArrayAdapter<String>(activity,android.R.layout.simple_spinner_dropdown_item,new String[]{"スレタイ","レス本文"}));
        target.setSelection(old!=null && old.target.equals("body") ? 1 : 0);layout.addView(target);
        Switch enabled=new Switch(activity);enabled.setText("このルールを有効にする");enabled.setChecked(old==null || old.enabled);layout.addView(enabled);
        Switch all=new Switch(activity);all.setText("すべての板を対象にする");all.setChecked(old!=null && old.allBoards);layout.addView(all);
        EditText boards=editor(layout,"対象板URL（1行に1つ。スレURLではなく板URL）",old==null?"https://bbs.eddibb.cc/liveedge/":old.boards,2);
        boards.setEnabled(!all.isChecked());all.setOnCheckedChangeListener((b,on)->boards.setEnabled(!on));
        label(layout,"http/httpsと末尾の / は同じ板として扱います。ホスト名・板のパスは完全一致です。\n(text, options) => true でNG。speedはレス/日、createdAtはDate。取得不能はnull。\n例：(text, {speed}) => speed !== null && speed > 10000");
        EditText source=editor(layout,"JavaScript関数",old==null?"(text, options) => false":old.source,5);
        TextView status=label(layout,"");
        java.util.function.Supplier<NgRules.Rule> read=()->new NgRules.Rule(old==null?UUID.randomUUID().toString():old.id,
            name.getText().toString(),target.getSelectedItemPosition()==0?"title":"body",enabled.isChecked(),all.isChecked(),boards.getText().toString(),source.getText().toString());
        Button test=new Button(activity);test.setText("対象板の実データでテスト／引数確認");layout.addView(test);
        test.setOnClickListener(v->{try{NgRules.Rule rule=read.get();List<NgScriptEngine.Input> inputs=rule.target.equals("title")?ProgrammableNg.lastTitles:ProgrammableNg.lastBodies;
            List<NgScriptEngine.Input> selected=new ArrayList<>();
            NgRules.Rule preview=new NgRules.Rule(rule.id,rule.name,rule.target,true,rule.allBoards,rule.boards,rule.source);
            for(NgScriptEngine.Input input:inputs)if(preview.accepts(input))selected.add(input);
            if(selected.isEmpty()){status.setText("対象板の実データがありません。対象の板／スレを開いてから再度試してください。");return;}
            boolean[] values=preview.engine.evaluate(selected);int count=0;for(boolean value:values)if(value)count++;
            status.setText("単体テスト（ON/OFFに関係なく実行）："+count+"/"+values.length+"件がNG\n"
                +(preview.engine.error()==null?"":"エラー："+preview.engine.error()+"\n")+"先頭のoptions：\n"+selected.get(0).options);
        }catch(IllegalArgumentException e){status.setText(e.getMessage());}});
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(old==null?"ルールを追加":"ルールを編集").setView(scroll(activity,layout))
            .setNegativeButton("キャンセル",null).setPositiveButton("一覧に反映",null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{
            NgRules.Rule rule=read.get();if(index<0)draft.add(rule);else draft.set(index,rule);render.run();dialog.dismiss();
        }catch(IllegalArgumentException e){status.setText(e.getMessage());}}));dialog.show();
    }
    private static LinearLayout column(Activity a){LinearLayout l=new LinearLayout(a);l.setOrientation(LinearLayout.VERTICAL);int p=(int)(12*a.getResources().getDisplayMetrics().density);l.setPadding(p,p,p,p);return l;}
    private static ScrollView scroll(Activity a,LinearLayout l){ScrollView s=new ScrollView(a);s.addView(l);return s;}
    private static TextView label(LinearLayout l,String value){TextView t=new TextView(l.getContext());t.setText(value);t.setPadding(0,10,0,10);l.addView(t);return t;}
    private static EditText editor(LinearLayout l,String name,String value,int lines){label(l,name);EditText e=new EditText(l.getContext());
        e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setTypeface(Typeface.MONOSPACE);e.setTextSize(13);e.setMinLines(lines);e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(NgScriptEngine.MAX_SOURCE)});
        e.setText(value);l.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
}
