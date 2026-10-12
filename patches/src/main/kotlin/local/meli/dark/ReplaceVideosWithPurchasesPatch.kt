package local.meli.dark

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val NAVIGATION_RUNTIME = "Llocal/meli/morphe/NavigationRuntime;"
private const val RESPONSE = "Lcom/mercadolibre/android/navigation_manager/tabbar/model/DynamicTabbarResponse;"

val replaceVideosWithPurchasesPatch = bytecodePatch(
    name = "Replace Videos with My purchases",
    description = "Replaces the Videos tab with the native My purchases tab and places it in the center. Keeps the cart tab and its badge.",
    default = false,
) {
    compatibleWith(Compatibility(
        name = "Mercado Libre",
        packageName = "com.mercadolibre",
        targets = listOf(AppTarget(version = "10.574.1")),
    ))
    dependsOn(morpheMenuSettingsPatch)
    execute {
        val converter = mutableClassDefBy("Lcom/mercadolibre/android/navigation_manager/tabbar/e;")
        val load = converter.methods.singleOrNull {
            it.name == "b" && it.parameterTypes.map(CharSequence::toString) == listOf("Landroid/content/Context;") && it.returnType == RESPONSE
        } ?: throw PatchException("Tab bar configuration loader changed")
        val build = converter.methods.singleOrNull {
            it.name == "d" && it.parameterTypes.map(CharSequence::toString) == listOf(RESPONSE) && it.returnType == "Ljava/util/Collection;"
        } ?: throw PatchException("Tab bar configuration converter changed")
        if(converter.methods.none { it.name == "e" && it.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/String;") && it.returnType == RESPONSE }) {
            throw PatchException("Native tab bar parser changed")
        }
        if (load.implementation?.registerCount != 5 || build.implementation?.registerCount != 34) {
            throw PatchException("Tab bar converter registers changed")
        }
        val model = classDefBy(RESPONSE)
        if(model.methods.none { it.name == "setDefinitions" && it.parameterTypes.map(CharSequence::toString) == listOf("Ljava/util/List;") }) {
            throw PatchException("Native tab definitions API changed")
        }
        val definitions = classDefBy("Lcom/mercadolibre/android/navigation_manager/tabbar/model/Definitions;")
        if(definitions.methods.none { it.name == "getTabId" && it.returnType == "Ljava/lang/String;" }) {
            throw PatchException("Native tab identity API changed")
        }
        if(definitions.methods.none { it.name == "setPresentationMode" && it.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/String;") }) {
            throw PatchException("Native tab presentation API changed")
        }
        val itemModel = classDefBy("Lcom/mercadolibre/android/navigation_manager/tabbar/TabBarItemConfiguration;")
        if(itemModel.methods.none { it.name == "setPresentationMode" && it.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/String;") }) {
            throw PatchException("Native tab item presentation API changed")
        }
        val returns = build.implementation!!.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_OBJECT }
        if(returns.size != 1 || (returns.single().value as OneRegisterInstruction).registerA != 2) {
            throw PatchException("Native tab item return changed")
        }
        // Replace the return so existing branch labels also enter the hook.
        build.replaceInstruction(returns.single().index,"move-object/from16 v0, p0")
        build.addInstructions(returns.single().index+1,"invoke-static {v2, v0}, $NAVIGATION_RUNTIME->finishItems(Ljava/util/Collection;Ljava/lang/Object;)V\nreturn-object v2")
        // Run after any server/cache load, before both view configuration and navigation
        // route registration. No view replacement, positional click override or JSON asset edit.
        load.addInstructions(0,"invoke-static/range {p0 .. p0}, $NAVIGATION_RUNTIME->rememberContext(Landroid/content/Context;)V")
        build.addInstructions(0,"invoke-static/range {p0 .. p0}, $NAVIGATION_RUNTIME->normalizeResponse(Ljava/lang/Object;)V")
    }
}
