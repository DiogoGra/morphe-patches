package local.meli.dark

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * Only UI rendering paths. The checks deliberately fail on any unexpected app version or bytecode.
 */
val darkHomeRuntimePatch = bytecodePatch(
    name = "Dark Theme Home UI hooks",
    description = "Recolors the server-selected Home canvas and action-card text after they render.",
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
        // The welcome screen renders a server-selected background and rich text after inflation.
        val onboarding = mutableClassDefBy("Lcom/mercadolibre/home/activities/a;")
        val bindOnboarding = onboarding.methods.singleOrNull {
            it.name == "invoke" && it.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/Object;") &&
                it.returnType == "Ljava/lang/Object;"
        } ?: throw PatchException("Home onboarding renderer changed")
        if (bindOnboarding.implementation!!.registerCount != 18) throw PatchException("Home onboarding registers changed")
        val onboardingInstructions = bindOnboarding.implementation!!.instructions.toList()
        val canvasCalls = onboardingInstructions.withIndex().filter { (_, instruction) ->
            val method = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            method?.definingClass == "Landroid/view/View;" && method.name == "setBackgroundResource" &&
                method.parameterTypes.map(CharSequence::toString) == listOf("I")
        }
        val labelAdds = onboardingInstructions.withIndex().filter { (_, instruction) ->
            val method = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            val regs = instruction as? FiveRegisterInstruction
            method?.definingClass == "Landroid/view/ViewGroup;" && method.name == "addView" &&
                regs?.registerCount == 2 && regs.registerC == 1 && regs.registerD == 2
        }
        if (canvasCalls.size != 1 || labelAdds.size != 1) throw PatchException("Home onboarding paint targets changed")
        val canvas = canvasCalls.single()
        val canvasRegs = canvas.value as? FiveRegisterInstruction
        if (canvasRegs?.registerCount != 2 || canvasRegs.registerC != 2 || canvasRegs.registerD != 1) {
            throw PatchException("Home onboarding canvas registers changed")
        }
        // Apply in descending order so the original indices remain valid.
        bindOnboarding.replaceInstruction(canvas.index, "invoke-virtual {v2, v1}, Landroid/view/View;->setBackgroundColor(I)V")
        bindOnboarding.addInstructions(canvas.index, "const v1, 0xff111318")
        bindOnboarding.addInstructions(
            labelAdds.single().index,
            "const v3, 0xfff2f3f5\ninvoke-virtual {v2, v3}, Landroid/widget/TextView;->setTextColor(I)V",
        )

        // Rich text uses ForegroundColorSpan, which overrides TextView's base color.
        // Recolor only dark neutral spans in OnboardingActivity; links and colored accents survive.
        val spanStyle = mutableClassDefBy("Lcom/mercadolibre/home/newhome/model/components/dynamicaccess/e;")
        val paintSpan = spanStyle.methods.singleOrNull {
            it.name == "a" && it.parameterTypes.map(CharSequence::toString) ==
                listOf("Lcom/mercadolibre/home/newhome/model/components/dynamicaccess/ComponentStyle;", "I", "I")
        } ?: throw PatchException("Home onboarding rich-text renderer changed")
        if (paintSpan.implementation!!.registerCount != 10) throw PatchException("Home rich-text registers changed")
        val spanCalls = paintSpan.implementation!!.instructions.withIndex().filter { (_, instruction) ->
            val method = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            method?.definingClass == "Landroid/text/style/ForegroundColorSpan;" && method.name == "<init>" &&
                method.parameterTypes.map(CharSequence::toString) == listOf("I")
        }
        if (spanCalls.size != 1) throw PatchException("Home rich-text color span changed")
        val span = spanCalls.single()
        val spanRegs = span.value as? FiveRegisterInstruction
        if (spanRegs?.registerCount != 2 || spanRegs.registerC != 3 || spanRegs.registerD != 1) {
            throw PatchException("Home rich-text color registers changed")
        }
        paintSpan.addInstructionsWithLabels(
            span.index,
            """
                iget-object v4, p0, Lcom/mercadolibre/home/newhome/model/components/dynamicaccess/e;->i:Landroid/content/Context;
                instance-of v4, v4, Lcom/mercadolibre/home/activities/OnboardingActivity;
                if-eqz v4, :morphe_onboarding_span_done
                invoke-static {v1}, Landroid/graphics/Color;->red(I)I
                move-result v4
                invoke-static {v1}, Landroid/graphics/Color;->green(I)I
                move-result v5
                if-ne v4, v5, :morphe_onboarding_span_done
                invoke-static {v1}, Landroid/graphics/Color;->blue(I)I
                move-result v5
                if-ne v4, v5, :morphe_onboarding_span_done
                const/16 v5, 0xb0
                if-ge v4, v5, :morphe_onboarding_span_done
                const v1, 0xfff2f3f5
            """.trimIndent(),
            ExternalLabel("morphe_onboarding_span_done", paintSpan.getInstruction(span.index)),
        )

        val fragment = mutableClassDefBy("Lcom/mercadolibre/home/newhome/views/AbstractHomeFragment;")
        val bindHome = fragment.methods.singleOrNull {
            it.name == "x4" &&
                it.parameterTypes.map(CharSequence::toString) ==
                listOf("Lcom/mercadolibre/home/newhome/api/NewHomeResponse;")
        } ?: throw PatchException("Home response renderer changed")
        val homeCalls = bindHome.implementation!!.instructions.withIndex().filter { (_, instruction) ->
            val target = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            target?.definingClass == "Landroid/view/View;" &&
                target.name == "setBackgroundColor" &&
                target.parameterTypes.map(CharSequence::toString) == listOf("I")
        }
        if (homeCalls.size != 1) throw PatchException("Expected one Home canvas color setter, found ${homeCalls.size}")
        val homeCall = homeCalls.single()
        val registers = homeCall.value as? FiveRegisterInstruction
            ?: throw PatchException("Home canvas setter instruction format changed")
        if (registers.registerCount != 2 || registers.registerD != 0) {
            throw PatchException("Home canvas color register changed")
        }
        bindHome.addInstructions(homeCall.index, "const v0, 0xff111318")

        val actionHolder = mutableClassDefBy("Lcom/mercadolibre/home/newhome/views/viewholders/action/b;")
        val bindAction = actionHolder.methods.singleOrNull {
            it.name == "s" &&
                it.parameterTypes.map(CharSequence::toString) ==
                listOf(
                    "Lcom/mercadolibre/home/newhome/model/components/actioncard/ActionCardDto;",
                    "Ljava/lang/Integer;",
                    "Ljava/lang/Integer;",
                )
        } ?: throw PatchException("Home action-card renderer changed")
        if (bindAction.implementation!!.registerCount != 11) {
            throw PatchException("Home action-card registers changed")
        }
        val headerCalls = bindAction.implementation!!.instructions.withIndex().filter { (_, instruction) ->
            val target = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            target?.definingClass == "Lcom/mercadolibre/home/newhome/views/HeaderView;" &&
                target.name == "a" &&
                target.returnType == "V" &&
                target.parameterTypes.size == 4
        }
        if (headerCalls.size != 1) throw PatchException("Expected one action-card header binder, found ${headerCalls.size}")
        bindAction.addInstructions(
            headerCalls.single().index + 1,
            """
                iget-object v2, v5, Lcom/mercadolibre/home/newhome/views/HeaderView;->h:Lcom/mercadolibre/home/databinding/X;
                iget-object v5, v2, Lcom/mercadolibre/home/databinding/X;->e:Lcom/mercadolibre/android/andesui/textview/AndesTextView;
                const v4, 0xfff2f3f5
                invoke-virtual {v5, v4}, Landroid/widget/TextView;->setTextColor(I)V
                iget-object v5, v2, Lcom/mercadolibre/home/databinding/X;->d:Landroid/widget/TextView;
                invoke-virtual {v5, v4}, Landroid/widget/TextView;->setTextColor(I)V
            """.trimIndent(),
        )

        // Compose draws white wrappers and a white-to-mosaic transition between Home blocks.
        // Recolor all four paint loads, including the transition; image layers stay untouched.
        val itemDescriptor = "Lcom/mercadolibre/home/newhome/views/HomeComposeStaggeredGrid" +
            "\$content\$lambda\$20\$lambda\$19\$\$inlined\$itemsIndexed\$default\$4;"
        val itemClass = mutableClassDefBy(itemDescriptor)
        val drawItem = itemClass.methods.singleOrNull {
            it.name == "invoke" &&
                it.returnType == "V" &&
                it.parameterTypes.map(CharSequence::toString) ==
                listOf(
                    "Landroidx/compose/foundation/lazy/staggeredgrid/p;",
                    "I",
                    "Landroidx/compose/runtime/h;",
                    "I",
                )
        } ?: throw PatchException("Home Compose component wrapper changed")
        val whiteLoads = drawItem.implementation!!.instructions.withIndex().filter { (_, instruction) ->
            val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference
            field?.definingClass == "Landroidx/compose/ui/graphics/A;" &&
                field.name == "g" &&
                field.type == "J"
        }
        val registersExpected = listOf(8, 16, 11, 6)
        val registersFound = whiteLoads.map { (_, instruction) ->
            (instruction as? OneRegisterInstruction)?.registerA
        }
        if (registersFound != registersExpected) {
            throw PatchException("Home Compose wrapper color loads changed: $registersFound")
        }
        whiteLoads.asReversed().forEach { (index, instruction) ->
            val register = (instruction as OneRegisterInstruction).registerA
            val invoke = if (register <= 14) {
                "invoke-static {v$register, v${register + 1}}, Landroidx/compose/ui/graphics/C;->d(J)J"
            } else {
                "invoke-static/range {v$register .. v${register + 1}}, Landroidx/compose/ui/graphics/C;->d(J)J"
            }
            drawItem.addInstructions(
                index + 1,
                "const-wide v$register, 0xff111318L\n$invoke\nmove-result-wide v$register",
            )
        }

        // The banner's View canvas receives a remote gradient after XML inflation.
        // The pager retains its native yellow gradient; its transparent lower edge now sits on dark.
        val exhibitors = mutableClassDefBy("Lcom/mercadolibre/home/newhome/views/viewholders/exhibitors/l;")
        val paintExhibitors = exhibitors.methods.singleOrNull {
            it.name == "m" && it.parameterTypes.map(CharSequence::toString) ==
                listOf("Ljava/util/List;", "Landroid/graphics/drawable/Drawable;")
        } ?: throw PatchException("Home exhibitor canvas renderer changed")
        if (paintExhibitors.implementation!!.registerCount != 6) throw PatchException("Home exhibitor canvas registers changed")
        val exhibitorCanvasStyles = paintExhibitors.implementation!!.instructions.withIndex().filter { (_, instruction) ->
            val method = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            method?.definingClass == "Lcom/mercadolibre/home/newhome/views/util/b;" && method.name == "a" &&
                method.parameterTypes.map(CharSequence::toString) ==
                listOf("Landroid/view/View;", "Ljava/util/List;", "Landroid/graphics/drawable/Drawable;")
        }
        if (exhibitorCanvasStyles.size != 1 || (exhibitorCanvasStyles.single().value as? FiveRegisterInstruction)?.registerC != 1) {
            throw PatchException("Home exhibitor canvas paint changed")
        }
        paintExhibitors.addInstructions(
            exhibitorCanvasStyles.single().index + 1,
            "const v2, 0xff111318\ninvoke-virtual {v1, v2}, Landroid/view/View;->setBackgroundColor(I)V",
        )

        val newsHolder = mutableClassDefBy("Lcom/mercadolibre/home/newhome/views/viewholders/newsrow/c;")
        val paintNewsCanvas = newsHolder.methods.singleOrNull {
            it.name == "m" && it.parameterTypes.map(CharSequence::toString) ==
                listOf("Ljava/util/List;", "Landroid/graphics/drawable/Drawable;")
        } ?: throw PatchException("Home news-row canvas renderer changed")
        if (paintNewsCanvas.implementation!!.registerCount != 6) throw PatchException("Home news-row canvas registers changed")
        val canvasStyles = paintNewsCanvas.implementation!!.instructions.withIndex().filter { (_, instruction) ->
            val method = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            method?.definingClass == "Lcom/mercadolibre/home/newhome/views/util/b;" && method.name == "a" &&
                method.parameterTypes.map(CharSequence::toString) ==
                listOf("Landroid/view/View;", "Ljava/util/List;", "Landroid/graphics/drawable/Drawable;")
        }
        if (canvasStyles.size != 1 || (canvasStyles.single().value as? FiveRegisterInstruction)?.registerC != 1) {
            throw PatchException("Home news-row canvas paint call changed")
        }
        paintNewsCanvas.addInstructions(
            canvasStyles.single().index + 1,
            "const v2, 0xff111318\ninvoke-virtual {v1, v2}, Landroid/view/View;->setBackgroundColor(I)V",
        )
        val bindNews = newsHolder.methods.singleOrNull {
            it.name == "s" &&
                it.parameterTypes.map(CharSequence::toString) ==
                listOf("Lcom/mercadolibre/home/newhome/model/components/newsrow/NewsRowDto;", "Ljava/lang/Integer;")
        } ?: throw PatchException("Home news-row renderer changed")
        if (bindNews.implementation!!.registerCount != 11) {
            throw PatchException("Home news-row registers changed")
        }
        val richTextCalls = bindNews.implementation!!.instructions.withIndex().filter { (_, instruction) ->
            val target = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            target?.definingClass == "Lcom/mercadolibre/home/newhome/utils/p\$a;" &&
                target.name == "i" &&
                target.returnType == "Landroid/text/Spanned;"
        }
        if (richTextCalls.size != 1) throw PatchException("Expected one news-row rich-text parser")
        bindNews.addInstructions(
            richTextCalls.single().index,
            """
                const-string v5, "#000000"
                const-string v6, "#f2f3f5"
                invoke-virtual {v2, v5, v6}, Ljava/lang/String;->replace(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;
                move-result-object v2
                const-string v5, "#333333"
                invoke-virtual {v2, v5, v6}, Ljava/lang/String;->replace(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;
                move-result-object v2
            """.trimIndent(),
        )

        val tabItem = mutableClassDefBy("Lcom/mercadolibre/android/navigation_manager/tabbar/tab/TabItemCustomView;")
        val unselected = tabItem.methods.singleOrNull {
            it.name == "newSetUnSelectedStyle\$tabbar_mercadolibreRelease" &&
                it.parameterTypes.size == 4
        } ?: throw PatchException("Bottom-bar icon styler changed")
        if (unselected.implementation!!.registerCount != 6) {
            throw PatchException("Bottom-bar icon styler registers changed")
        }
        val clearFilters = unselected.implementation!!.instructions.withIndex().filter { (_, instruction) ->
            val target = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            target?.definingClass == "Landroid/widget/ImageView;" &&
                target.name == "clearColorFilter"
        }
        if (clearFilters.size != 1) throw PatchException("Expected one bottom-bar icon filter")
        unselected.addInstructions(
            clearFilters.single().index + 1,
            """
                const p1, 0xffc7cbd2
                invoke-virtual {p3, p1}, Landroid/widget/ImageView;->setColorFilter(I)V
                invoke-static {p1}, Landroid/content/res/ColorStateList;->valueOf(I)Landroid/content/res/ColorStateList;
                move-result-object p1
                invoke-virtual {p3, p1}, Landroid/widget/ImageView;->setImageTintList(Landroid/content/res/ColorStateList;)V
            """.trimIndent(),
        )

        val selected = tabItem.methods.singleOrNull {
            it.name == "newSetSelectedStyle\$tabbar_mercadolibreRelease" &&
                it.parameterTypes.size == 4
        } ?: throw PatchException("Bottom-bar selected icon styler changed")
        val selectedClearFilters = selected.implementation!!.instructions.withIndex().filter { (_, instruction) ->
            val target = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            target?.definingClass == "Landroid/widget/ImageView;" && target.name == "clearColorFilter"
        }
        if (selectedClearFilters.size != 1) throw PatchException("Expected one selected bottom-bar icon filter")
        selected.addInstructions(
            selectedClearFilters.single().index + 1,
            """
                const/4 p1, 0x0
                invoke-virtual {p3, p1}, Landroid/widget/ImageView;->setImageTintList(Landroid/content/res/ColorStateList;)V
            """.trimIndent(),
        )

        // Quick Access paints a server-supplied pale gradient over the Home canvas.
        // Keep its icon images and data, but let the dark canvas show through.
        val quickAccess = mutableClassDefBy("Lcom/mercadolibre/android/engagement_component/components/quickaccess/ui/x;")
        val drawQuickAccess = quickAccess.methods.singleOrNull {
            it.name == "d" && it.parameterTypes.firstOrNull()?.toString() == "Landroidx/compose/foundation/layout/p;"
        } ?: throw PatchException("Quick Access renderer changed")
        val gradientCalls = drawQuickAccess.implementation!!.instructions.withIndex().filter { (_, instruction) ->
            val target = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            target?.definingClass == "Lcom/mercadolibre/android/engagement_component/utils/compose/color/a\$a;" &&
                target.name == "a" && target.parameterTypes.map(CharSequence::toString) == listOf("Ljava/util/List;")
        }
        if (gradientCalls.size != 1) throw PatchException("Expected one Quick Access gradient")
        drawQuickAccess.addInstructions(gradientCalls.single().index, "const/4 v2, 0x0")

        val quickAccessComponents = mutableClassDefBy("Lcom/mercadolibre/android/engagement_component/components/quickaccess/ui/m;")
        val drawQuickAccessTitle = quickAccessComponents.methods.singleOrNull {
            it.name == "i" &&
                it.parameterTypes.firstOrNull()?.toString() == "Lcom/mercadolibre/android/engagement_component/components/quickaccess/component/i;"
        } ?: throw PatchException("Quick Access title renderer changed")
        val titleColors = drawQuickAccessTitle.implementation!!.instructions.withIndex().filter { (_, instruction) ->
            val target = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            target?.definingClass == "Lcom/google/android/gms/internal/mlkit_vision_barcode/D6;" &&
                target.name == "b" && target.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/String;")
        }
        if (titleColors.size != 1) throw PatchException("Expected one Quick Access title color")
        val titleResultIndex = titleColors.single().index + 1
        val titleResult = drawQuickAccessTitle.implementation!!.instructions[titleResultIndex] as? OneRegisterInstruction
            ?: throw PatchException("Quick Access title result changed")
        if (titleResult.registerA != 3) throw PatchException("Quick Access title color register changed")
        // D6.b accepts UiColor enum IDs only; an arbitrary hex string resolves to black.
        // Replace the resulting Compose color, leaving title content and layout intact.
        drawQuickAccessTitle.addInstructions(
            titleResultIndex + 1,
            """
                const-wide v3, 0xffc7cbd2L
                invoke-static {v3, v4}, Landroidx/compose/ui/graphics/C;->d(J)J
                move-result-wide v3
            """.trimIndent(),
        )

        // Home card borders come from SpecsDto as well as a default literal.
        // Map only light neutral stroke paint; retain colored accents and all border geometry.
        val cardStyle = mutableClassDefBy("Lcom/mercadolibre/home/newhome/utils/p\$a;")
        val strokeMethod = cardStyle.methods.singleOrNull {
            it.name == "a" && it.parameterTypes.map(CharSequence::toString) == listOf(
                "Lcom/google/android/material/card/MaterialCardView;", "Lcom/mercadolibre/home/newhome/model/SpecsDto;", "F",
            )
        } ?: throw PatchException("Home card stroke renderer changed")
        if (strokeMethod.implementation!!.registerCount != 6) throw PatchException("Home card stroke registers changed")
        val strokeCalls = strokeMethod.implementation!!.instructions.withIndex().filter { (_, instruction) ->
            val target = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            target?.definingClass == "Lcom/google/android/material/card/MaterialCardView;" &&
                target.name == "setStrokeColor" && target.parameterTypes.map(CharSequence::toString) == listOf("I")
        }
        val paintedStroke = strokeCalls.singleOrNull { (_, instruction) ->
            val regs = instruction as? FiveRegisterInstruction
            regs?.registerCount == 2 && regs.registerC == 3 && regs.registerD == 5
        }
        if (strokeCalls.size != 2 || paintedStroke == null) throw PatchException("Home card stroke paint changed")
        strokeMethod.addInstructionsWithLabels(
            paintedStroke.index,
            """
                invoke-static {p2}, Landroid/graphics/Color;->red(I)I
                move-result v0
                invoke-static {p2}, Landroid/graphics/Color;->green(I)I
                move-result v1
                if-ne v0, v1, :morphe_home_stroke_done
                invoke-static {p2}, Landroid/graphics/Color;->blue(I)I
                move-result v1
                if-ne v0, v1, :morphe_home_stroke_done
                const/16 v1, 0xb0
                if-lt v0, v1, :morphe_home_stroke_done
                const p2, 0xff353a45
            """.trimIndent(),
            ExternalLabel("morphe_home_stroke_done", strokeMethod.getInstruction(paintedStroke.index)),
        )

        // Polycard's native Compose container has its own color source.
        // Override the paint passed to the background modifier; its image layer
        // and click handler remain untouched.
        val polycardContainer = mutableClassDefBy("Lcom/mercadolibre/android/polycards/core/commons/c;")
        val drawPolycard = polycardContainer.methods.singleOrNull {
            it.name == "invoke" && it.parameterTypes.map(CharSequence::toString) ==
                listOf("Ljava/lang/Object;", "Ljava/lang/Object;", "Ljava/lang/Object;")
        } ?: throw PatchException("Polycard card renderer changed")
        val cardPaintCalls = drawPolycard.implementation!!.instructions.withIndex().filter { (_, instruction) ->
            val target = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            target?.definingClass == "Landroidx/compose/foundation/d;" &&
                target.name == "c" && target.parameterTypes.map(CharSequence::toString) == listOf(
                    "Landroidx/compose/ui/m;", "J", "Landroidx/compose/ui/graphics/r0;",
                )
        }
        if (cardPaintCalls.size != 1) throw PatchException("Expected one Polycard card paint")
        val cardRegisters = cardPaintCalls.single().value as? FiveRegisterInstruction
            ?: throw PatchException("Polycard card paint format changed")
        if (cardRegisters.registerCount != 4 || cardRegisters.registerD != 2 || cardRegisters.registerE != 3) {
            throw PatchException("Polycard card paint registers changed")
        }
        drawPolycard.addInstructions(
            cardPaintCalls.single().index,
            """
                const-wide v2, 0xff1b1e25L
                invoke-static {v2, v3}, Landroidx/compose/ui/graphics/C;->d(J)J
                move-result-wide v2
            """.trimIndent(),
        )

        // The card's own Gray900/Gray550 tokens still resolve to dark text.
        // Remap Polycard's dark text and medium solid-gray tokens; image/accent tokens stay as sent.
        val polycardColor = mutableClassDefBy("Lcom/mercadolibre/android/polycards/core/ui/styles/lookandfeel/color/a;")
        val resolvePolycardColor = polycardColor.methods.singleOrNull {
            it.name == "a" && it.returnType == "J" && it.parameterTypes.isEmpty()
        } ?: throw PatchException("Polycard color resolver changed")
        val darkTokenLoads = resolvePolycardColor.implementation!!.instructions.withIndex().mapNotNull { (index, instruction) ->
            val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference ?: return@mapNotNull null
            if (field.definingClass != "Lcom/mercadolibre/android/andesui/compose/components/theme/colors/f;" ||
                field.name !in setOf("p0", "f0", "d0", "m0", "n0")) return@mapNotNull null
            index to (field.name to instruction)
        }
        if (darkTokenLoads.map { it.second.first }.toSet() != setOf("p0", "f0", "d0", "m0", "n0") || darkTokenLoads.size != 5) {
            throw PatchException("Polycard dark text token loads changed")
        }
        darkTokenLoads.asReversed().forEach { (index, token) ->
            val register = (token.second as? OneRegisterInstruction)?.registerA
            if (register != 0) throw PatchException("Polycard text token result register changed")
            val color = when (token.first) {
                "d0" -> "0xffc7cbd2L"
                "m0", "n0" -> "0xffb8bec8L"
                else -> "0xfff2f3f5L"
            }
            resolvePolycardColor.addInstructions(
                index + 1,
                "const-wide v0, $color\n" +
                    "invoke-static {v0, v1}, Landroidx/compose/ui/graphics/C;->d(J)J\n" +
                    "move-result-wide v0",
            )
        }
    }
}
