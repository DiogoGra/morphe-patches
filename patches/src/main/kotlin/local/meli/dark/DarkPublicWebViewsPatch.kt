package local.meli.dark

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel

/** Styles the audited Categories and Home campaign pages, preserving media and page behavior. */
val darkPublicWebViewsPatch = bytecodePatch(
    name = "Dark Theme public WebViews",
    description = "Scoped CSS for Categories and the Home campaign's HTML surfaces, without media filters.",
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
        val client = mutableClassDefBy("Lcom/mercadolibre/android/mlwebkit/core/clients/b;")
        val onPageFinished = client.methods.singleOrNull { method ->
            method.name == "onPageFinished" &&
                method.parameterTypes.map(CharSequence::toString) ==
                listOf("Landroid/webkit/WebView;", "Ljava/lang/String;") &&
                method.returnType == "V"
        } ?: throw PatchException("MLWebKit page-finished callback changed")
        if (onPageFinished.implementation?.registerCount != 12) {
            throw PatchException("MLWebKit page-finished registers changed")
        }

        val categoriesCss = listOf(
            "html,body,#root-app{background:#111318!important;color:#f2f3f5!important}",
            ".vertical-tabs-wrapper__list{background:#1b1e25!important}",
            ".vertical-tab{background:#1b1e25!important;border-color:#353a45!important;color:#c7cbd2!important}",
            ".vertical-tab--selected{background:#232730!important}",
            ".vertical-tab__content-title{color:#c7cbd2!important}",
            ".vertical-tab--selected .vertical-tab__content-title{color:#6aa7ff!important}",
            ".see-more-quick-access__item-title{color:#f2f3f5!important}",
            ".andes-thumbnail--circle{background:#232730!important}",
        ).joinToString("")
        // The Home TakeOver is HTML: white card surfaces and two CSS gradients.
        // Never target img, video, canvas, SVG artwork, event handlers, links or coupon states.
        val campaignCss = listOf(
            "html,body,main,.home-pto,.home-pto .sbc-landing-layout{background-color:#111318!important;color:#f2f3f5!important}",
            ".home-pto .andes-landings-background,.home-pto .andes-landings-background::before{background-color:#111318!important;background-image:linear-gradient(#111318,#161a2b)!important}",
            ".home-pto .smart-coupon-lightning-scheduled{background-color:#1b1e25!important;color:#f2f3f5!important}",
            ".home-pto .andes-landings-typography,.home-pto .smart-coupon-lightning-scheduled__title,.home-pto .smart-coupon-lightning-scheduled__time-label,.home-pto .smart-coupon-lightning-scheduled__time-value{color:#f2f3f5!important}",
            ".home-pto .smart-coupon-lightning-scheduled__category{color:#c7cbd2!important}",
            ".home-pto .smart-coupon-lightning-scheduled__min-amount,.home-pto .smart-coupon-lightning-scheduled__conditions-text{color:#9ea4ae!important}",
            ".home-pto .smart-coupon-lightning-scheduled__divider::after{background-image:repeating-linear-gradient(#353a45 0px,#353a45 4px,transparent 0px,transparent 10px)!important}",
        ).joinToString("")
        val script = "(function(){if(location.hostname!=='www.mercadolibre.com.ar')return;" +
            "var css,id;if(location.pathname==='/categorias'){css='$categoriesCss';id='morphe-dark-categories'}" +
            "else if(location.pathname==='/hto/partial'){css='$campaignCss';id='morphe-dark-home-campaign'}else return;" +
            "if(document.getElementById(id))return;var s=document.createElement('style');s.id=id;" +
            "s.textContent=css;(document.head||document.documentElement).appendChild(s)})()"
        val smaliScript = script.replace("\\", "\\\\").replace("\"", "\\\"")

        val continuation = onPageFinished.getInstruction(0)
        onPageFinished.addInstructionsWithLabels(
            0,
            """
                if-eqz p1, :morphe_categories_done
                if-eqz p2, :morphe_categories_done
                const-string v0, "https://www.mercadolibre.com.ar/categorias"
                invoke-virtual {p2, v0}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z
                move-result v1
                if-nez v1, :morphe_public_css_apply
                const-string v0, "https://www.mercadolibre.com.ar/hto/partial"
                invoke-virtual {p2, v0}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z
                move-result v1
                if-eqz v1, :morphe_categories_done
                :morphe_public_css_apply
                const-string v0, "$smaliScript"
                const/4 v1, 0x0
                invoke-virtual {p1, v0, v1}, Landroid/webkit/WebView;->evaluateJavascript(Ljava/lang/String;Landroid/webkit/ValueCallback;)V
            """.trimIndent(),
            ExternalLabel("morphe_categories_done", continuation),
        )
    }
}
