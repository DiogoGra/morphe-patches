package local.meli.dark

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation

private const val RUNTIME = "Llocal/meli/morphe/ThemeRuntime;"
internal object MorpheSettingsResources
private fun signature(method: MethodReference) = method.definingClass + "->" + method.name +
    "(" + method.parameterTypes.joinToString("") + ")" + method.returnType

/** Unnamed dependency: original paint bodies are retained for a true light-mode dispatch. */
val morpheSettingsRuntimePatch = bytecodePatch {
    dependsOn(morpheMenuSettingsPatch)
    val originalPaintMethods = linkedMapOf<String, ImmutableMethod>()
    execute {
        originalPaintMethods.clear()
        val signatures = MorpheSettingsResources::class.java.getResourceAsStream("/morphe-paint-methods.txt")!!.bufferedReader().readLines().filter { it.isNotBlank() }
        if(signatures.size != 40) throw PatchException("Expected 40 UI paint methods")
        signatures.forEach { expected ->
            val owner = expected.substringBefore("->")
            val original = classDefBy(owner).methods.singleOrNull { signature(it) == expected }
                ?: throw PatchException("Original paint method missing: $expected")
            originalPaintMethods[expected] = ImmutableMethod.of(original)
        }
        val app = mutableClassDefBy("Lcom/mercadolibre/MainApplication;").methods.single { it.name == "onCreate" && it.parameterTypes.isEmpty() }
        val superCreate = app.implementation!!.instructions.indexOfFirst {
            val ref = (it as? ReferenceInstruction)?.reference as? MethodReference
            ref?.name == "onCreate" && it.opcode in listOf(Opcode.INVOKE_SUPER,Opcode.INVOKE_SUPER_RANGE)
        }
        if(superCreate < 0) throw PatchException("Application startup changed")
        app.addInstructions(superCreate+1,"invoke-static/range {p0 .. p0}, $RUNTIME->initialize(Landroid/app/Application;)V")
        val base = mutableClassDefBy("Lcom/mercadolibre/android/commons/core/AbstractActivity;").methods.single { it.name == "attachBaseContext" }
        val superAttach = base.implementation!!.instructions.indexOfFirst {
            val ref = (it as? ReferenceInstruction)?.reference as? MethodReference
            ref?.definingClass == "Landroidx/appcompat/app/AppCompatActivity;" && ref.name == "attachBaseContext"
        }
        if(superAttach < 0) throw PatchException("ML context attachment changed")
        base.addInstructions(superAttach,"invoke-static {p1}, $RUNTIME->wrap(Landroid/content/Context;)Landroid/content/Context;\nmove-result-object p1")
        // R8 removed AppCompatDelegate.setDefaultNightMode in this APK. The original local
        // night-mode field remains, read by context attachment and later configuration logic.
        val delegate = mutableClassDefBy("Landroidx/appcompat/app/k;")
        if(delegate.fields.none { it.name=="T" && it.type=="I" })throw PatchException("AppCompat night-mode field changed")
        val attach = delegate.methods.single { it.name=="d" && it.parameterTypes.map(CharSequence::toString)==listOf("Landroid/content/Context;") && it.returnType=="Landroid/content/Context;" }
        if(attach.implementation!!.registerCount!=10)throw PatchException("AppCompat attachment registers changed")
        attach.addInstructions(0,"invoke-static {}, $RUNTIME->nightMode()I\nmove-result v0\niput v0, p0, Landroidx/appcompat/app/k;->T:I")
    }
    finalize {
        originalPaintMethods.forEach { (key, original) ->
            val owner = mutableClassDefBy(original.definingClass)
            val dark = owner.methods.single { signature(it) == key }
            val darkBody = MutableMethod(ImmutableMethod.of(dark)).apply { setName(original.name+"\$morpheDark") }
            val lightBody = MutableMethod(original).apply { setName(original.name+"\$morpheLight") }
            val static = AccessFlags.STATIC.isSet(original.accessFlags)
            val count = original.parameterTypes.sumOf { if(it.toString() in listOf("J","D")) 2 else 1 } + if(static) 0 else 1
            val invoke = when { static -> "invoke-static/range"; AccessFlags.PRIVATE.isSet(original.accessFlags) -> "invoke-direct/range"; else -> "invoke-virtual/range" }
            val args = if(count == 0) "{}" else "{p0 .. p${count-1}}"
            val descriptor = "("+original.parameterTypes.joinToString("")+")"+original.returnType
            val result = when(original.returnType) {
                "V" -> "return-void"
                "J","D" -> "move-result-wide v0\nreturn-wide v0"
                else -> if(original.returnType.startsWith("L") || original.returnType.startsWith("[")) "move-result-object v0\nreturn-object v0" else "move-result v0\nreturn v0"
            }
            val wrapper = MutableMethod(ImmutableMethod(original.definingClass,original.name,original.parameters,original.returnType,
                original.accessFlags,original.annotations,original.hiddenApiRestrictions,
                ImmutableMethodImplementation(count+2,emptyList(),emptyList(),emptyList())))
            wrapper.addInstructions(0,"""
                invoke-static {}, $RUNTIME->isDark()Z
                move-result v0
                if-eqz v0, :morphe_light
                $invoke $args, ${original.definingClass}->${darkBody.name}$descriptor
                $result
                :morphe_light
                $invoke $args, ${original.definingClass}->${lightBody.name}$descriptor
                $result
            """.trimIndent())
            owner.methods.remove(dark)
            owner.methods.addAll(listOf(wrapper,darkBody,lightBody))
        }
    }
}
