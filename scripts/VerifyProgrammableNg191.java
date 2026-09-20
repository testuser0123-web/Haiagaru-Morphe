import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import java.io.File;
import java.util.*;

/** Checks exact hooks against the original APK, without distributing app code. */
public final class VerifyProgrammableNg191 {
    private static final Map<String, ClassDef> classes = new HashMap<>();
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static Method method(String owner, String name, String result, String... params) {
        List<Method> matches = new ArrayList<>();
        for (Method m : classes.get(owner).getMethods()) {
            List<String> types = new ArrayList<>();
            for (CharSequence p : m.getParameterTypes()) types.add(p.toString());
            if (m.getName().equals(name) && m.getReturnType().equals(result) && types.equals(Arrays.asList(params))) matches.add(m);
        }
        check(matches.size() == 1, "ambiguous/missing hook " + owner + name);
        return matches.get(0);
    }
    private static void field(String owner, String name, String type, boolean publicField) {
        for (Field f : classes.get(owner).getFields()) if (f.getName().equals(name)) {
            check(f.getType().equals(type), "wrong field type " + owner + name);
            check(!publicField || (f.getAccessFlags() & 1) != 0, "not public " + owner + name);
            return;
        }
        throw new AssertionError("missing field " + owner + name);
    }
    private static int returns(Method m, Opcode opcode) {
        int count = 0;
        for (Instruction i : m.getImplementation().getInstructions()) if (i.getOpcode() == opcode) count++;
        return count;
    }
    public static void main(String[] args) throws Exception {
        var container = DexFileFactory.loadDexContainer(new File(args[0]), Opcodes.getDefault());
        for (String entry : container.getDexEntryNames()) for (ClassDef c : container.getEntry(entry).getDexFile().getClasses()) classes.put(c.getType(), c);
        String fragment = "Lo/r8lambdaGCnF6WpW_bFarRe7yCX2B6KzQ;", adapter = "Lo/m9ExternalSyntheticLambda1;";
        String response = "Lo/processAdDisplayErrorPostbackForUserError;", url = "Ljp/syoboi/a2chMate/client/BBSUrlInfo;";
        Method title = method(fragment, "c", "Ljava/util/ArrayList;", "Ljava/util/ArrayList;", "Lo/mgExternalSyntheticLambda0;", "Ljava/util/ArrayList;");
        check(returns(title, Opcode.RETURN_OBJECT) == 1, "title returns changed");
        Method body = method(adapter, "SE_", "I", response, "Z", "Landroid/util/SparseIntArray;");
        check(returns(body, Opcode.RETURN) == 2, "body returns changed");
        Method rebuild = method(adapter, "c", "V", "Z");
        boolean found = false;
        for (Instruction i : rebuild.getImplementation().getInstructions()) if (i instanceof ReferenceInstruction) {
            var ref = ((ReferenceInstruction) i).getReference();
            if (ref instanceof FieldReference && ((FieldReference) ref).getDefiningClass().equals(adapter) && ((FieldReference) ref).getName().equals("K")) found = true;
        }
        check(found, "response rebuild anchor missing");
        field(fragment, "e", url, true);
        field(adapter, "K", "Lo/processAdapterInitializationPostback;", true);
        field(adapter, "M", "Lo/maExternalSyntheticLambda1;", true);
        field(adapter, "p", "Z", false);
        field("Lo/maExternalSyntheticLambda1;", "f", url, true);
        field(url, "e", "Ljava/lang/Long;", true);
        method(url, "i", "Ljava/lang/String;");
        field("Lo/MaxAdViewImplExternalSyntheticLambda2;", "a", "Lo/MaxAdViewImplExternalSyntheticLambda1;", true);
        field("Lo/MaxAdViewImplExternalSyntheticLambda2;", "d", "I", true);
        field("Lo/MaxAdViewImplExternalSyntheticLambda1;", "c", "Ljava/lang/String;", true);
        field("Lo/MaxAdViewImplExternalSyntheticLambda1;", "e", "J", true);
        field("Lo/processAdapterInitializationPostback;", "title", "Ljava/lang/String;", true);
        for (String name : Arrays.asList("c", "h", "n", "j", "q")) field(response, name, "Ljava/lang/String;", true);
        field(response, "o", "I", true);
        System.out.println("PASS: 191 dev title filter, response rebuild, response flags, 17 reflected fields, board URL getter");
    }
}
