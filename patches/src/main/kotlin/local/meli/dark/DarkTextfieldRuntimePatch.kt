package local.meli.dark

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.Opcode

/** Recolors only the paint values of Andes text fields and the login social label. */
val darkTextfieldRuntimePatch = bytecodePatch(
    name = "Dark Theme text field UI hooks",
    description = "Dark text field fills, borders, labels and values without changing input behavior.",
    default = true,
) {
    compatibleWith(
        Compatibility(
            name = "Mercado Libre",
            packageName = "com.mercadolibre",
            targets = listOf(AppTarget(version = "10.574.1")),
        ),
    )

    dependsOn(morpheSettingsRuntimePatch)

    execute {
        val base = "Lcom/mercadolibre/android/andesui/textfield/state/"
        val idle = mutableClassDefBy("${base}c;")
        val error = mutableClassDefBy("${base}b;")
        val disabled = mutableClassDefBy("${base}a;")
        val readonly = mutableClassDefBy("${base}d;")

        idle.colorLoadsInBackground("andes_bg_color_white", 5, listOf(5, 5), 0xff232730.toInt())
        idle.colorLoadsInBackground("andes_gray_250", 4, listOf(4, 0), 0xff353a45.toInt())
        error.colorLoadsInBackground("andes_bg_color_white", 5, listOf(5, 0), 0xff232730.toInt())
        disabled.colorLoadsInBackground("andes_gray_250", 2, listOf(0), 0xff353a45.toInt())

        idle.colorProvider("d", "andes_text_color_secondary", "#c7cbd2")
        idle.colorProvider("f", "andes_text_color_primary", "#f2f3f5")
        idle.colorProvider("h", "andes_text_color_secondary", "#c7cbd2")
        error.colorProvider("h", "andes_text_color_secondary", "#c7cbd2")
        listOf("d", "f", "h").forEach { disabled.colorProvider(it, "andes_gray_250", "#9ea4ae") }
        readonly.colorProvider("f", "andes_text_color_secondary", "#c7cbd2")
        readonly.colorProvider("h", "andes_text_color_primary", "#f2f3f5")

        // This binder applies a server-selected label color after the component is constructed.
        val social = mutableClassDefBy("Lcom/mercadolibre/android/login/F;")
        val binder = social.methods.singleOrNull {
            it.name == "invoke" && it.parameterTypes.map(CharSequence::toString) ==
                listOf("Ljava/lang/Object;") && it.returnType == "Ljava/lang/Object;"
        } ?: throw PatchException("Login social button binder changed")
        if (binder.implementation!!.registerCount != 21) throw PatchException("Login social binder registers changed")
        val socialInstructions = binder.implementation!!.instructions.toList()
        val labelColorLoads = socialInstructions.withIndex().filter { (_, instruction) ->
            val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference
            field?.definingClass == "Lcom/mercadolibre/android/login_components/model/a;" &&
                field.name == "b" && field.type == "I"
        }
        if (labelColorLoads.size != 1) throw PatchException("Login social label color source changed")
        val labelLoad = labelColorLoads.single()
        val colorSetter = (socialInstructions.getOrNull(labelLoad.index + 1) as? ReferenceInstruction)?.reference as? MethodReference
        val colorRegs = socialInstructions.getOrNull(labelLoad.index + 1) as? FiveRegisterInstruction
        if (colorSetter?.definingClass != "Landroid/widget/TextView;" || colorSetter.name != "setTextColor" ||
            colorRegs?.registerCount != 2 || colorRegs.registerC != 4 || colorRegs.registerD != 6
        ) throw PatchException("Login social label setter changed")
        binder.addInstructions(labelLoad.index + 1, "const v6, 0xffc7cbd2")

        // The login ActionBar installs a raw, black Andes arrow icon after inflating its toolbar.
        val loginBase = mutableClassDefBy("Lcom/mercadolibre/android/login/activities/AbstractLoginActivity;")
        val setupToolbar = loginBase.methods.singleOrNull {
            it.name == "f4" && it.parameterTypes.map(CharSequence::toString) ==
                listOf("Landroid/view/View;", "I", "Landroid/view/ViewGroup\$LayoutParams;")
        } ?: throw PatchException("Login toolbar setup changed")
        if (setupToolbar.implementation!!.registerCount != 9) throw PatchException("Login toolbar registers changed")
        val arrowCalls = setupToolbar.implementation!!.instructions.withIndex().filter { (_, instruction) ->
            val method = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            method?.definingClass == "Landroidx/appcompat/app/a;" && method.name == "y" &&
                method.parameterTypes.map(CharSequence::toString) == listOf("I")
        }
        if (arrowCalls.size != 1) throw PatchException("Login back arrow setup changed")
        val arrowIndex = arrowCalls.single().index
        val nextInstruction = setupToolbar.getInstruction(arrowIndex + 1)
        val nextCall = (nextInstruction as? ReferenceInstruction)?.reference as? MethodReference
        if (nextCall?.definingClass != "Landroidx/appcompat/app/AppCompatActivity;" ||
            nextCall.name != "getSupportActionBar"
        ) throw PatchException("Login toolbar continuation changed")
        setupToolbar.addInstructionsWithLabels(
            arrowIndex + 1,
            """
                iget-object v0, p0, Lcom/mercadolibre/android/login/activities/AbstractLoginActivity;->r:Landroidx/appcompat/widget/Toolbar;
                invoke-virtual {v0}, Landroidx/appcompat/widget/Toolbar;->getNavigationIcon()Landroid/graphics/drawable/Drawable;
                move-result-object v0
                if-eqz v0, :morphe_login_arrow_done
                const v1, 0xfff2f3f5
                invoke-virtual {v0, v1}, Landroid/graphics/drawable/Drawable;->setTint(I)V
            """.trimIndent(),
            ExternalLabel("morphe_login_arrow_done", nextInstruction),
        )
    }
}

private fun app.morphe.patcher.util.proxy.mutableTypes.MutableClass.colorLoadsInBackground(
    fieldName: String,
    loadRegister: Int,
    resultRegisters: List<Int>,
    color: Int,
) {
    val method = methods.singleOrNull {
        it.name == "a" && it.parameterTypes.map(CharSequence::toString) == listOf("Landroid/content/Context;") &&
            it.returnType == "Landroid/graphics/drawable/Drawable;"
    } ?: throw PatchException("Andes text field background method changed")
    val instructions = method.implementation!!.instructions.toList()
    val loads = instructions.withIndex().filter { (_, instruction) ->
        val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference
        field?.definingClass == "Lcom/mercadolibre/android/andesui/c;" && field.name == fieldName
    }
    if (loads.size != resultRegisters.size) throw PatchException("Andes $fieldName load count changed: ${loads.size}")
    loads.withIndex().reversed().forEach { (position, indexed) ->
        val (index, instruction) = indexed
        val resultRegister = resultRegisters[position]
        if ((instruction as? OneRegisterInstruction)?.registerA != loadRegister) {
            throw PatchException("Andes $fieldName register changed")
        }
        val resolver = (instructions.getOrNull(index + 2) as? ReferenceInstruction)?.reference as? MethodReference
        val result = instructions.getOrNull(index + 3) as? OneRegisterInstruction
        if (resolver?.definingClass != "Landroidx/core/content/a;" || resolver.name != "c" ||
            result?.registerA != resultRegister || instructions[index + 3].opcode != Opcode.MOVE_RESULT
        ) throw PatchException("Andes $fieldName color resolution changed at $index: ${resolver?.definingClass}.${resolver?.name}, result=${result?.registerA}, opcode=${instructions[index + 3].opcode}, expected=$resultRegister")
        method.addInstructions(index + 4, "const v$resultRegister, ${"0x%08x".format(color)}")
    }
}

private fun app.morphe.patcher.util.proxy.mutableTypes.MutableClass.colorProvider(
    name: String,
    expectedField: String,
    color: String,
) {
    val method = methods.singleOrNull {
        it.name == name && it.parameterTypes.isEmpty() &&
            it.returnType == "Lcom/mercadolibre/android/andesui/color/a;"
    } ?: throw PatchException("Andes text field $name provider changed")
    val instructions = method.implementation!!.instructions.toList()
    val field = (instructions.firstOrNull() as? ReferenceInstruction)?.reference as? FieldReference
    val maker = (instructions.getOrNull(1) as? ReferenceInstruction)?.reference as? MethodReference
    if (method.implementation!!.registerCount != 2 || instructions.size != 4 ||
        field?.definingClass != "Lcom/mercadolibre/android/andesui/c;" || field.name != expectedField ||
        maker?.definingClass != "Lcom/google/android/gms/internal/mlkit_vision_barcode/J5;" || maker.name != "a" ||
        instructions[2].opcode != Opcode.MOVE_RESULT_OBJECT || instructions[3].opcode != Opcode.RETURN_OBJECT
    ) throw PatchException("Andes text field $name color provider changed")
    method.replaceInstructions(
        0,
        """
            new-instance v0, Lcom/mercadolibre/android/andesui/color/a;
            const-string v1, "$color"
            invoke-direct {v0, v1}, Lcom/mercadolibre/android/andesui/color/a;-><init>(Ljava/lang/String;)V
        """.trimIndent(),
    )
}
