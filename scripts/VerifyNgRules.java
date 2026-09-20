import app.morphe.extension.chmate.*;
import java.util.*;
public class VerifyNgRules {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static NgRules.Rule rule(String id,boolean on,boolean all,String boards,String source){return new NgRules.Rule(id,"日本語 "+id,"title",on,all,boards,source);}
 public static void main(String[] args){
  String edge="https://bbs.eddibb.cc/liveedge/",other="https://example.org/news/";
  List<NgScriptEngine.Input> inputs=Arrays.asList(NgMetadata.thread("対象 [abc★]",edge,1700000000L,50,1700000100000L),NgMetadata.thread("対象",other,1700000000L,50,1700000100000L));
  NgRules.Rule a=rule("a",true,false,"http://bbs.eddibb.cc/liveedge","(text, {speed}) => speed > 0");
  NgRules.Rule b=rule("b",true,false,other,"text => text.includes('対象')");
  NgRules set=new NgRules(Arrays.asList(a,b));boolean[] r=set.evaluate(inputs);check(r[0]&&r[1],"multi board union");
  check(!a.accepts(inputs.get(1)),"board isolation");
  check(!a.accepts(NgMetadata.thread("x","https://bbs.eddibb.cc.evil.org/liveedge/",1,1,2000)),"host exact");
  check(!a.accepts(NgMetadata.thread("x","https://bbs.eddibb.cc/liveedge2/",1,1,2000)),"path exact");
  NgRules restored=NgRules.decode(set.encode());check(restored.rules.get(0).name.equals(a.name)&&restored.rules.get(1).source.equals(b.source),"roundtrip");
  check(!new NgRules(Arrays.asList(rule("off",false,true,"","text=>true"))).evaluate(inputs)[0],"toggle off");
  r=new NgRules(Arrays.asList(rule("bad",true,true,"","text=>{throw Error('bad')}"),b)).evaluate(inputs);check(!r[0]&&r[1],"error isolation");
  check(new NgRules(Arrays.asList(rule("all",true,true,"","text=>true"))).evaluate(inputs)[1],"all boards");
  NgRules legacy=NgRules.legacy("text=>true","text=>false");check(legacy.rules.size()==2&&!legacy.rules.get(0).allBoards&&!legacy.rules.get(0).accepts(inputs.get(1)),"migration edge only");
  check(inputs.get(1).options.get("speed").equals(43200.0)&&inputs.get(1).options.get("createdAtMs").equals(1700000000000L),"custom domain uses native timestamp");
  for (String board : Arrays.asList("https://bbs.jpnkn.com/test/", "https://jbbs.shitaraba.net/internet/12345/", "https://example.org/news/")) {
   NgScriptEngine.Input metadata=NgMetadata.thread("x",board,1700000000L,100,1700000100000L);
   check(metadata.options.get("createdAtMs").equals(1700000000000L) && metadata.options.get("speed").equals(86400.0),"no domain allowlist");
  }
  for(long id : new long[]{0,-1,9240000000L,Long.MAX_VALUE}) {
   NgScriptEngine.Input special=NgMetadata.thread("x",other,id,1,1700000100000L);
   check(special.options.get("createdAtMs")==null && special.options.get("speed")==null,"invalid/special key");
  }
  NgScriptEngine.Input known=NgMetadata.thread("x","https://greta.5ch.net/poverty/",1700000000L,100,1700000100000L);
  check(known.options.get("speed").equals(86400.0)&&known.options.get("posterId")==null,"5ch metadata");
  Map<String,Object> opts=new LinkedHashMap<>(inputs.get(0).options);opts.put("target","body");check(!a.accepts(new NgScriptEngine.Input("x",opts)),"target isolation");
  check(NgRules.boardKey("https://example.org/news/?x=y")==null,"reject query");
  System.out.println("PASS: names, toggles, exact board scope, all boards, OR rules, isolated errors, persistence, migration and nullable metadata");
 }
}
