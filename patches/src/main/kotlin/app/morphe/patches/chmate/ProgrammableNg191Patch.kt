package app.morphe.patches.chmate

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

private const val NG = "Lapp/morphe/extension/chmate/ProgrammableNg;"
private const val THREAD_FRAGMENT = "Lo/r8lambdaGCnF6WpW_bFarRe7yCX2B6KzQ;"
private const val RESPONSE_ADAPTER = "Lo/m9ExternalSyntheticLambda1;"

/** Exact 191 dev hooks. Refuse to patch if the supplied APK differs. */
internal fun BytecodePatchContext.patchProgrammableNg191() {
    check(packageMetadata.versionName == "0.8.10.191 dev")
    val titleFilter = mutableClassDefBy(THREAD_FRAGMENT).methods.single {
        it.name == "c" && it.returnType == "Ljava/util/ArrayList;" &&
            it.parameters.map(CharSequence::toString) == listOf(
                "Ljava/util/ArrayList;", "Lo/mgExternalSyntheticLambda0;", "Ljava/util/ArrayList;"
            )
    }
    val titleReturns = titleFilter.implementation!!.instructions.mapIndexedNotNull { index, instruction ->
        if (instruction.opcode == Opcode.RETURN_OBJECT) index to (instruction as OneRegisterInstruction).registerA else null
    }
    check(titleReturns.size == 1) { "191 dev title filter return sites changed" }
    titleReturns.asReversed().forEach { (index, register) ->
        // Parameters are contiguous and dead at this return. /range also supports high registers.
        // Replace the labeled return itself: branches to that return must run the hook too.
        titleFilter.replaceInstruction(index, "move-object/16 p1, v$register")
        titleFilter.addInstructionsWithLabels(index + 1, """
            invoke-static/range {p0 .. p3}, $NG->filterThreads(Ljava/lang/Object;Ljava/util/ArrayList;Ljava/lang/Object;Ljava/util/ArrayList;)Ljava/util/ArrayList;
            move-result-object v$register
            return-object v$register
        """)
    }

    val adapter = mutableClassDefBy(RESPONSE_ADAPTER)
    val rebuild = adapter.methods.single {
        it.name == "c" && it.returnType == "V" && it.parameters.map(CharSequence::toString) == listOf("Z")
    }
    check(rebuild.implementation!!.instructions.any {
        val ref = (it as? ReferenceInstruction)?.reference as? FieldReference
        ref?.definingClass == RESPONSE_ADAPTER && ref.name == "K"
    }) { "191 dev response model anchor is missing" }
    rebuild.addInstructionsWithLabels(0, """
        invoke-static/range {p0 .. p0}, $NG->prepareResponses(Ljava/lang/Object;)V
    """)

    val responseFilter = adapter.methods.single {
        it.name == "SE_" && it.returnType == "I" && it.parameters.map(CharSequence::toString) == listOf(
            "Lo/processAdDisplayErrorPostbackForUserError;", "Z", "Landroid/util/SparseIntArray;"
        )
    }
    val responseReturns = responseFilter.implementation!!.instructions.mapIndexedNotNull { index, instruction ->
        if (instruction.opcode == Opcode.RETURN) index to (instruction as OneRegisterInstruction).registerA else null
    }
    check(responseReturns.size == 2) { "191 dev response return sites changed" }
    responseReturns.asReversed().forEach { (index, register) ->
        responseFilter.replaceInstruction(index, "move/16 p2, v$register")
        responseFilter.addInstructionsWithLabels(index + 1, """
            invoke-static/range {p0 .. p2}, $NG->responseFlags(Ljava/lang/Object;Ljava/lang/Object;I)I
            move-result v$register
            return v$register
        """)
    }
}
