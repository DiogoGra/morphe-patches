package local.meli.dark

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/** Recolors the recommendations feed's own canvas without changing shared Andes colors. */
val darkRecommendationsPatch = bytecodePatch(
    name = "Dark Theme recommendations feed",
    description = "Uses a dark native background for the recommendations feed in its two render paths.",
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
        val feed = mutableClassDefBy(
            "Lcom/mercadolibre/android/recommendations_combo/recommendations/feed/ui/RecommendationsFeedView;",
        )
        listOf("s", "t").forEach { methodName ->
            val render = feed.methods.singleOrNull { it.name == methodName }
                ?: throw PatchException("Recommendations feed renderer $methodName changed")
            val setters = render.implementation!!.instructions.withIndex().filter { (_, instruction) ->
                val target = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                target?.name == "setBackgroundColor" &&
                    target.parameterTypes.map(CharSequence::toString) == listOf("I") &&
                    target.returnType == "V"
            }
            if (setters.size != 1) {
                throw PatchException("Expected one feed background setter in $methodName, found ${setters.size}")
            }
            val setter = setters.single()
            val registers = setter.value as? FiveRegisterInstruction
                ?: throw PatchException("Feed background setter format changed in $methodName")
            if (registers.registerCount != 2) {
                throw PatchException("Feed background setter arguments changed in $methodName")
            }
            render.addInstructions(setter.index, "const v${registers.registerD}, 0xff111318")
        }

        // The runtime Home trace selects FeedCardComponentView j -> feed_cards/core/e
        // -> PolycardListGeneric h.a/c/e, not the older FlashDealsFeedComponentView B/I.
        // t.s is the inner list brush; t.a paints the yellow outer promotional canvas.
        val listRenderer = mutableClassDefBy(
            "Lcom/mercadolibre/android/feed_cards/cards/polycardlistgeneric/h;",
        )
        val drawList = listRenderer.methods.singleOrNull {
            it.name == "e" && it.parameterTypes.map(CharSequence::toString) == listOf(
                "Ljava/util/List;", "Lcom/mercadolibre/android/feed_cards/cards/polycardlistgeneric/t;",
                "Ljava/util/Map;", "Lcom/mercadolibre/android/feed_cards/core/tracking/b;",
                "Lkotlin/jvm/functions/Function2;", "Landroidx/compose/runtime/h;", "I",
            )
        } ?: throw PatchException("Home generic Polycard list renderer changed")
        if (drawList.implementation!!.registerCount != 26) throw PatchException("Polycard list registers changed")
        val innerBrush = drawList.implementation!!.instructions.withIndex().singleOrNull { (_, instruction) ->
            val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference
            field?.definingClass == "Lcom/mercadolibre/android/feed_cards/cards/polycardlistgeneric/t;" &&
                field.name == "s" && field.type == "Landroidx/compose/ui/graphics/t;"
        } ?: throw PatchException("Polycard list inner brush changed")
        val brushRegisters = innerBrush.value as? TwoRegisterInstruction
        if (brushRegisters?.registerA != 7 || brushRegisters.registerB != 4) {
            throw PatchException("Polycard list brush registers changed")
        }
        // v0 and v13 are dead scratch here. Preserve the live Compose flags in v1;
        // restore the background modifier's original default flags in v5.
        drawList.addInstructions(innerBrush.index + 1, """
            move v13, v1
            const-wide v0, 0xff303743L
            invoke-static {v0, v1}, Landroidx/compose/ui/graphics/C;->d(J)J
            move-result-wide v0
            new-instance v7, Landroidx/compose/ui/graphics/s0;
            const/4 v5, 0x0
            invoke-direct {v7, v0, v1, v5}, Landroidx/compose/ui/graphics/s0;-><init>(JLkotlin/jvm/internal/DefaultConstructorMarker;)V
            move v1, v13
            const/4 v5, 0x6
        """.trimIndent())

        // White label backplates (for example sold quantity) need a dark background
        // when their gray text tokens are light. Keep colored shipping/discount labels.
        val labelBackground = mutableClassDefBy(
            "Lcom/mercadolibre/android/polycards/core/ui/commons/label/f;",
        ).methods.singleOrNull {
            it.name == "a" && it.parameterTypes.map(CharSequence::toString) == listOf(
                "Landroidx/compose/ui/m;", "Lcom/mercadolibre/android/polycards/core/ui/styles/lookandfeel/color/a;",
                "Lcom/mercadolibre/android/polycards/core/ui/styles/spacing/b;", "Landroidx/compose/runtime/d0;",
            )
        } ?: throw PatchException("Polycard label background renderer changed")
        if (labelBackground.implementation!!.registerCount != 11) throw PatchException("Polycard label registers changed")
        val paintLambda = labelBackground.implementation!!.instructions.withIndex().singleOrNull { (_, instruction) ->
            instruction.opcode == Opcode.NEW_INSTANCE &&
                (instruction as? ReferenceInstruction)?.reference.toString() == "Lcom/mercadolibre/c;"
        } ?: throw PatchException("Polycard label paint lambda changed")
        labelBackground.addInstructionsWithLabels(paintLambda.index, """
            invoke-virtual {p1}, Lcom/mercadolibre/android/polycards/core/ui/styles/lookandfeel/color/a;->a()J
            move-result-wide v0
            sget-wide v2, Landroidx/compose/ui/graphics/A;->g:J
            cmp-long v4, v0, v2
            if-nez v4, :original_label_paint
            new-instance p1, Lcom/mercadolibre/android/polycards/core/ui/styles/lookandfeel/color/a${'$'}i;
            const-string v0, "#232730"
            invoke-direct {p1, v0}, Lcom/mercadolibre/android/polycards/core/ui/styles/lookandfeel/color/a${'$'}i;-><init>(Ljava/lang/String;)V
        """.trimIndent(), ExternalLabel("original_label_paint", paintLambda.value))

        // Remote label styles can override the light text tokens with medium gray
        // (#757575 in the observed sold-quantity/old-price labels). Change resolved
        // foreground paint only; keep black text on yellow discount plates and accents.
        val labelText = mutableClassDefBy(
            "Lcom/mercadolibre/android/polycards/core/ui/commons/label/f;",
        ).methods.singleOrNull {
            it.name == "d" && it.parameterTypes.map(CharSequence::toString) == listOf(
                "Lcom/mercadolibre/android/polycards/core/domain/models/commons/style/PolycardStyleModel;",
                "Lcom/mercadolibre/android/polycards/core/ui/styles/lookandfeel/color/a;",
                "Lcom/mercadolibre/android/polycards/core/ui/styles/lookandfeel/font/size/PolycardFontSize;",
                "Lcom/mercadolibre/android/polycards/core/ui/styles/lookandfeel/font/weight/PolycardFontWeight;",
                "Lcom/mercadolibre/android/polycards/core/ui/styles/lookandfeel/font/style/PolycardFontStyle;",
                "Lcom/mercadolibre/android/polycards/core/ui/styles/spacing/b;",
            )
        } ?: throw PatchException("Polycard label text style renderer changed")
        if (labelText.implementation!!.registerCount != 47) throw PatchException("Polycard label text registers changed")
        val sizeCalls = labelText.implementation!!.instructions.withIndex().filter { (_, instruction) ->
            val method = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            method?.definingClass == "Lcom/mercadolibre/android/polycards/core/ui/styles/lookandfeel/font/size/b;" &&
                method.name == "c" && method.returnType == "I"
        }
        if (sizeCalls.size != 2) throw PatchException("Polycard label text paint position changed")
        val afterColor = sizeCalls[1]
        val labelInstructions = labelText.implementation!!.instructions.toList()
        if (labelInstructions[afterColor.index + 1].opcode != Opcode.MOVE_RESULT) {
            throw PatchException("Polycard label font-size result changed")
        }
        // v7/v8/v9 are dead after the resolved packed foreground is copied to v10/v11.
        // The original color branches jump directly to the size call. Insert after
        // that call/result so every branch executes this foreground remap.
        labelText.addInstructionsWithLabels(afterColor.index + 2, """
            invoke-static {v10, v11}, Landroidx/compose/ui/graphics/C;->j(J)I
            move-result v7
            ushr-int/lit8 v8, v7, 0x10
            and-int/lit16 v8, v8, 0xff
            ushr-int/lit8 v9, v7, 0x8
            and-int/lit16 v9, v9, 0xff
            if-ne v8, v9, :original_label_text_style
            and-int/lit16 v9, v7, 0xff
            if-ne v8, v9, :original_label_text_style
            const/16 v9, 0x20
            if-lt v8, v9, :original_label_text_style
            const/16 v9, 0xb0
            if-gt v8, v9, :original_label_text_style
            const-wide v10, 0xffb8bec8L
            invoke-static {v10, v11}, Landroidx/compose/ui/graphics/C;->d(J)J
            move-result-wide v10
        """.trimIndent(), ExternalLabel("original_label_text_style", labelInstructions[afterColor.index + 2]))

        // price_v2 uses demandcore Polylabel styles. The first color resolver in n.a
        // returns the foreground; the second resolves a background and stays original.
        // Both the text style and MoneyAmount color copy consume this foreground.
        val polylabelStyle = mutableClassDefBy(
            "Lcom/mercadolibre/android/demandcore/components/polylabel/ui/mappers/n;",
        ).methods.singleOrNull {
            it.name == "a" && it.parameterTypes.firstOrNull()?.toString() ==
                "Lcom/mercadolibre/android/demandcore/components/polylabel/domain/models/c;"
        } ?: throw PatchException("Polylabel text style mapper changed")
        if (polylabelStyle.implementation!!.registerCount != 29) throw PatchException("Polylabel style registers changed")
        val polylabelInstructions = polylabelStyle.implementation!!.instructions.toList()
        val colorResolvers = polylabelInstructions.withIndex().filter { (_, instruction) ->
            val method = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            method?.definingClass == "Lcom/mercadolibre/android/demandcore/components/core/ui/utils/a;" &&
                method.name == "a" && method.returnType == "J" &&
                method.parameterTypes.map(CharSequence::toString) == listOf("J", "Ljava/lang/String;")
        }
        if (colorResolvers.size != 2) throw PatchException("Polylabel foreground/background resolvers changed")
        val foreground = colorResolvers.first()
        if ((polylabelInstructions[foreground.index + 1] as? OneRegisterInstruction)?.registerA != 10) {
            throw PatchException("Polylabel foreground result changed")
        }
        // v5/v6/v7 are dead after resolving the foreground. The original code next
        // resets v1 from the style model and v6/v7 from the background's default.
        polylabelStyle.addInstructionsWithLabels(foreground.index + 2, """
            invoke-static {v10, v11}, Landroidx/compose/ui/graphics/C;->j(J)I
            move-result v5
            ushr-int/lit8 v6, v5, 0x10
            and-int/lit16 v6, v6, 0xff
            ushr-int/lit8 v7, v5, 0x8
            and-int/lit16 v7, v7, 0xff
            if-ne v6, v7, :original_polylabel_background
            and-int/lit16 v7, v5, 0xff
            if-ne v6, v7, :original_polylabel_background
            const/16 v1, 0x20
            if-lt v6, v1, :original_polylabel_background
            const/16 v1, 0xb0
            if-gt v6, v1, :original_polylabel_background
            const-wide v10, 0xffb8bec8L
            invoke-static {v10, v11}, Landroidx/compose/ui/graphics/C;->d(J)J
            move-result-wide v10
        """.trimIndent(), ExternalLabel("original_polylabel_background", polylabelInstructions[foreground.index + 2]))

        // Countdown numbers share the Polycard text palette, but their yellow/cream
        // backplates stay light. Override only foreground paint for those light plates.
        val timer = mutableClassDefBy("Lcom/mercadolibre/android/polycards/core/ui/commons/timer/f;")
        val drawTimer = timer.methods.singleOrNull { it.name == "e" }
            ?: throw PatchException("Polycard countdown renderer changed")
        if (drawTimer.implementation!!.registerCount != 85) throw PatchException("Polycard countdown registers changed")
        val timerInstructions = drawTimer.implementation!!.instructions.toList()
        val timerTextLoad = timerInstructions.withIndex().singleOrNull { (index, instruction) ->
            val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference
            field?.definingClass == "Lcom/mercadolibre/android/polycards/core/ui/commons/label/g;" &&
                field.name == "b" &&
                (timerInstructions.getOrNull(index + 2) as? OneRegisterInstruction)?.registerA == 31
        } ?: throw PatchException("Polycard countdown foreground paint changed")
        val createTextStyle = timerInstructions[timerTextLoad.index + 3]
        // v3 held the resolved color object and is replaced by the new TextStyle next.
        // Keep all timestamp, unit and animation parameters unchanged.
        drawTimer.addInstructionsWithLabels(timerTextLoad.index + 3, """
            invoke-static/range {v21 .. v22}, Landroidx/compose/ui/graphics/C;->j(J)I
            move-result v3
            ushr-int/lit8 v3, v3, 0x10
            and-int/lit16 v3, v3, 0xff
            add-int/lit16 v3, v3, -0xb0
            if-ltz v3, :original_timer_text_style
            invoke-static/range {v21 .. v22}, Landroidx/compose/ui/graphics/C;->j(J)I
            move-result v3
            ushr-int/lit8 v3, v3, 0x8
            and-int/lit16 v3, v3, 0xff
            add-int/lit16 v3, v3, -0xb0
            if-ltz v3, :original_timer_text_style
            const-wide v31, 0xff111318L
            invoke-static/range {v31 .. v32}, Landroidx/compose/ui/graphics/C;->d(J)J
            move-result-wide v31
        """.trimIndent(), ExternalLabel("original_timer_text_style", createTextStyle))
    }
}
