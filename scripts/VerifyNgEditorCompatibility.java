import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import java.io.File;
import java.util.*;

/** Protect the stock 191 NG editor and reject the clobbered-p1 regression. */
public class VerifyNgEditorCompatibility {
 static Method load(String apk)throws Exception {
  var d=DexFileFactory.loadDexContainer(new File(apk),Opcodes.getDefault());
  for(String e:d.getDexEntryNames())for(ClassDef c:d.getEntry(e).getDexFile().getClasses())
   if(c.getType().equals("Lo/MaxFullscreenAdImplExternalSyntheticLambda4;"))for(Method m:c.getMethods())
    if(m.getName().equals("onViewCreated"))return m;
  throw new AssertionError("editor missing");
 }
 static String signature(Instruction i) {
  String s=i.getOpcode().name();
  if(i instanceof OneRegisterInstruction r)s+=" A"+r.getRegisterA();
  if(i instanceof TwoRegisterInstruction r)s+=" B"+r.getRegisterB();
  if(i instanceof ThreeRegisterInstruction r)s+=" C"+r.getRegisterC();
  if(i instanceof FiveRegisterInstruction r)s+=" regs"+r.getRegisterCount()+":"+r.getRegisterC()+":"+r.getRegisterD()+":"+r.getRegisterE()+":"+r.getRegisterF()+":"+r.getRegisterG();
  if(i instanceof RegisterRangeInstruction r)s+=" range"+r.getStartRegister()+":"+r.getRegisterCount();
  if(i instanceof ReferenceInstruction r)s+=" ref"+r.getReference();
  if(i instanceof WideLiteralInstruction r)s+=" literal"+r.getWideLiteral();
  return s;
 }
 public static void main(String[] args)throws Exception {
  Method original=load(args[0]),patched=load(args[1]);
  int registers=original.getImplementation().getRegisterCount();
  if(patched.getImplementation().getRegisterCount()!=registers)throw new AssertionError("registers changed");
  List<String> before=new ArrayList<>(),after=new ArrayList<>();int hooks=0;
  for(Instruction i:original.getImplementation().getInstructions())before.add(signature(i));
  for(Instruction i:patched.getImplementation().getInstructions()) {
   if(i instanceof ReferenceInstruction r && r.getReference() instanceof MethodReference m && m.getName().equals("addLegacyButton")) {
    if(!m.getParameterTypes().equals(List.of("Ljava/lang/Object;")))throw new AssertionError("unsafe View parameter");
    if(!(i instanceof FiveRegisterInstruction f) || f.getRegisterCount()!=1 || f.getRegisterC()!=registers-3)throw new AssertionError("must pass only p0");
    hooks++;
   } else if(i instanceof ReferenceInstruction r && r.getReference() instanceof MethodReference m
       && m.getDefiningClass().equals("Lapp/morphe/extension/chmate/Haiagaru;") && m.getName().equals("hideLegacyBanner")) {
    // Existing upstream ad hook is safe only at entry, before p1 is reused.
    if(!after.isEmpty() || !(i instanceof RegisterRangeInstruction f) || f.getStartRegister()!=registers-2 || f.getRegisterCount()!=1)
     throw new AssertionError("unsafe banner hook");
   } else after.add(signature(i));
  }
  if(hooks!=1 || !before.equals(after)) { for(int j=0;j<Math.min(before.size(),after.size());j++)if(!before.get(j).equals(after.get(j)))System.out.println(j+" original="+before.get(j)+" patched="+after.get(j)); throw new AssertionError("stock editor instructions changed: "+before.size()+"/"+after.size()); }
  System.out.println("PASS: stock NG editor instructions preserved; hook passes only fragment p0, never reused p1");
 }
}
