package local.meli.dark

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private val morpheMenuRuntimePatch = bytecodePatch {
    dependsOn(meliRuntimeExtensionPatch)
    execute {
        val bottom=mutableClassDefBy("Lcom/mercadolibre/navigation/activities/BottomBarActivity;").methods.single { it.name=="onResume" && it.parameterTypes.isEmpty() }
        val resumed=bottom.implementation!!.instructions.indexOfFirst {
            val ref=(it as? ReferenceInstruction)?.reference as? MethodReference
            ref?.name=="onResume" && it.opcode in listOf(Opcode.INVOKE_SUPER,Opcode.INVOKE_SUPER_RANGE)
        }
        if(resumed<0)throw PatchException("BottomBar resume changed")
        bottom.addInstructions(resumed+1,"invoke-static/range {p0 .. p0}, Llocal/meli/morphe/MenuUi;->watch(Landroid/app/Activity;)V")
    }
}

internal val morpheMenuSettingsPatch = resourcePatch {
    dependsOn(morpheMenuRuntimePatch)
    execute { installMorpheSettingsActivity() }
}
