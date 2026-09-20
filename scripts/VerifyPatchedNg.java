import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import java.io.File;
import java.util.*;

public final class VerifyPatchedNg {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        var container = DexFileFactory.loadDexContainer(new File(args[0]), Opcodes.getDefault());
        String ng = "Lapp/morphe/extension/chmate/ProgrammableNg;";
        Map<String, Integer> hooks = new HashMap<>();
        boolean rhino = false, settings = false;
        for (String entry : container.getDexEntryNames()) for (ClassDef c : container.getEntry(entry).getDexFile().getClasses()) {
            rhino |= c.getType().equals("Lorg/mozilla/javascript/Interpreter;");
            settings |= c.getType().equals("Lapp/morphe/extension/chmate/NgSettings;");
            for (Method method : c.getMethods()) {
                if (method.getImplementation() == null) continue;
                List<Instruction> instructions = new ArrayList<>();
                List<Integer> addresses = new ArrayList<>();
                int address = 0;
                for (Instruction i : method.getImplementation().getInstructions()) {
                    instructions.add(i); addresses.add(address); address += i.getCodeUnits();
                    if (i instanceof ReferenceInstruction) {
                        var ref = ((ReferenceInstruction) i).getReference();
                        if (ref instanceof MethodReference && ((MethodReference) ref).getDefiningClass().equals(ng)) {
                            String name = ((MethodReference) ref).getName();
                            hooks.put(name, hooks.getOrDefault(name, 0) + 1);
                        }
                    }
                }
                boolean target = (c.getType().equals("Lo/r8lambdaGCnF6WpW_bFarRe7yCX2B6KzQ;") && method.getName().equals("c") && method.getReturnType().equals("Ljava/util/ArrayList;"))
                        || (c.getType().equals("Lo/m9ExternalSyntheticLambda1;") && method.getName().equals("SE_"));
                if (!target) continue;
                for (int n = 0; n < instructions.size(); n++) {
                    Opcode op = instructions.get(n).getOpcode();
                    if (op != Opcode.RETURN && op != Opcode.RETURN_OBJECT) continue;
                    check(n >= 3, "short return sequence");
                    Instruction invoke = instructions.get(n-2);
                    check(invoke.getOpcode() == Opcode.INVOKE_STATIC_RANGE, "NG hook missing before return");
                    check(((MethodReference)((ReferenceInstruction)invoke).getReference()).getDefiningClass().equals(ng), "wrong hook");
                    // Incoming branches must target the inserted move, never skip to return/move-result.
                    for (int j = 0; j < instructions.size(); j++) if (instructions.get(j) instanceof OffsetInstruction) {
                        int destination = addresses.get(j) + ((OffsetInstruction)instructions.get(j)).getCodeOffset();
                        check(destination < addresses.get(n-2) || destination > addresses.get(n), "branch bypasses NG hook in " + method);
                    }
                }
            }
        }
        check(rhino && settings, "missing engine/settings");
        check(hooks.getOrDefault("filterThreads", 0) == 1, "title hook count");
        check(hooks.getOrDefault("prepareResponses", 0) == 1, "response batch hook count");
        check(hooks.getOrDefault("responseFlags", 0) == 2, "response return hook count");
        check(hooks.getOrDefault("initialize", 0) == 1, "initialization hook count");
        check(hooks.getOrDefault("addSettingsButton", 0) == 1, "settings hook count");
        System.out.println("PASS: rebuilt APK contains Rhino, settings, initialization and all NG hooks; return branches cannot skip hooks");
    }
}
