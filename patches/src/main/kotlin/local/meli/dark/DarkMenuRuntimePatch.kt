package local.meli.dark

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

/** UI-only fixes for Flox menu sections whose server colors override the XML theme. */
val darkMenuRuntimePatch = bytecodePatch(
    name = "Dark Theme menu UI hooks",
    description = "Recolors native menu section labels and separators after server styling.",
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
        val section = mutableClassDefBy("Lcom/mercadolibre/android/ui_sections/bricks/builders/g;")
        val bindSection = section.methods.singleOrNull { method ->
            method.name == "bind" && method.parameterTypes.map(CharSequence::toString) == listOf(
                "Lcom/mercadolibre/android/flox/engine/Flox;",
                "Landroid/view/View;",
                "Lcom/mercadolibre/android/flox/engine/flox_models/FloxBrick;",
            )
        } ?: throw PatchException("Menu section renderer changed")
        bindSection.overrideColorCall("Landroid/widget/TextView;", "setTextColor", 0xffc7cbd2.toInt(), after = true)

        val separator = mutableClassDefBy("Lcom/mercadolibre/android/ui_sections/bricks/builders/separator/SeparatorRowBuilder;")
        val buildSeparator = separator.methods.singleOrNull { method ->
            method.name == "build" && method.parameterTypes.map(CharSequence::toString) ==
                listOf("Lcom/mercadolibre/android/flox/engine/Flox;")
        } ?: throw PatchException("Menu separator constructor changed")
        // Padding around the divider must match the adjacent menu rows, avoiding dark bands.
        buildSeparator.overrideColorCall("Landroid/view/View;", "setBackgroundColor", 0xff1b1e25.toInt())

        val bindSeparator = separator.methods.singleOrNull { method ->
            method.name == "bind" && method.parameterTypes.map(CharSequence::toString) == listOf(
                "Lcom/mercadolibre/android/flox/engine/Flox;",
                "Landroid/view/View;",
                "Lcom/mercadolibre/android/flox/engine/flox_models/FloxBrick;",
            )
        } ?: throw PatchException("Menu separator binder changed")
        bindSeparator.overrideColorCall("Landroid/view/View;", "setBackgroundColor", 0xff353a45.toInt())
    }
}

private fun app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.overrideColorCall(
    owner: String,
    name: String,
    color: Int,
    after: Boolean = false,
) {
    val calls = implementation!!.instructions.withIndex().filter { (_, instruction) ->
        val target = (instruction as? ReferenceInstruction)?.reference as? MethodReference
        target?.definingClass == owner && target.name == name &&
            target.parameterTypes.map(CharSequence::toString) == listOf("I")
    }
    if (calls.size != 1) throw PatchException("Expected one $owner.$name call, found ${calls.size}")
    val call = calls.single()
    val registers = call.value as? FiveRegisterInstruction ?: throw PatchException("$owner.$name format changed")
    if (registers.registerCount != 2) throw PatchException("$owner.$name argument count changed")
    val colorInstruction = "const v${registers.registerD}, ${"0x%08x".format(color)}"
    if (after) {
        if (registers.registerC > 15 || registers.registerD > 15) {
            throw PatchException("$owner.$name registers too high for a second UI setter")
        }
        addInstructions(
            call.index + 1,
            "$colorInstruction\ninvoke-virtual {v${registers.registerC}, v${registers.registerD}}, $owner->$name(I)V",
        )
    } else {
        addInstructions(call.index, colorInstruction)
    }
}
