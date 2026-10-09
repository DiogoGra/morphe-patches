package local.meli.dark

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.ResourcePatchContext
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList

private const val PACKAGE = "com.mercadolibre"
private const val VERSION = "10.574.1"
private const val VERSION_CODE = "1816182806"
private const val THEME = "MeliDarkNativeTheme"
private const val HOME_THEME = "MeliDarkHomeTheme"
private const val VPP_THEME = "MeliDarkVppTheme"

private const val BACKGROUND = "#111318"
private const val SURFACE = "#1b1e25"
private const val SURFACE_2 = "#232730"
private const val SURFACE_3 = "#2b303a"
private const val HEADER = "#161a2b"
private const val TEXT = "#f2f3f5"
private const val TEXT_SECONDARY = "#c7cbd2"
private const val MUTED = "#9ea4ae"
private const val BORDER = "#353a45"

private val compatibleApp = Compatibility(
    name = "Mercado Libre",
    packageName = PACKAGE,
    apkFileType = ApkFileType.APKM,
    appIconColor = 0xFFE600,
    targets = listOf(AppTarget(version = VERSION)),
)

// Activity-specific overlays keep each original theme parent and window behavior.
private val activityThemes = linkedMapOf(
    "com.mercadolibre.home.activities.HomeActivity" to ("" to HOME_THEME),
    "com.mercadolibre.android.search.activities.SearchActivity" to ("@style/Theme.Base" to THEME),
    "com.mercadolibre.android.autosuggest.ui.autosuggest.AutosuggestActivity" to ("@style/Theme.Base" to THEME),
    "com.mercadolibre.android.vpp.core.view.activities.VppActivity" to ("@style/vppActivityTheme" to VPP_THEME),
)

// Screen-owned colors complement the shared semantic Andes palette below.
private val colors = linkedMapOf(
    "home_new_background" to ("#eeeeee" to BACKGROUND),
    "home_onboarding_action_second_background_color" to ("@color/andes_white" to SURFACE_2),
    "home_onboarding_subtitle_text_color" to ("#b2333333" to TEXT_SECONDARY),
    "home_onboarding_terms_text_color" to ("#999999" to MUTED),
    "home_onboarding_text_color" to ("@color/andes_gray_800_solid" to TEXT),
    "home_onboarding_title_text_color" to ("@color/andes_gray_800_solid" to TEXT),
    "navigation_manager_tabbar_background_color" to ("@color/andes_bg_color_white" to SURFACE),
    "navigation_manager_tabbar_black_text_color" to ("@color/black" to TEXT),
    "navigation_manager_tabbar_not_selected_text_color" to ("@color/andes_gray_550_solid" to TEXT_SECONDARY),
    "ui_sections_menu_view_background_color" to ("#ffffff" to SURFACE),
    "ui_sections_menu_row_simple_color" to ("@color/andes_gray_800" to TEXT),
    "ui_sections_separator_background_color" to ("@color/andes_gray_040_solid" to BORDER),
    "search_cell_background_color" to ("@android:color/white" to SURFACE),
    "autosuggest_background" to ("@color/andes_gray_070_solid" to BACKGROUND),
    "autosuggest_hint" to ("@color/andes_gray_250_solid" to "#5a4a00"),
    "autosuggest_item_selection" to ("@color/andes_gray_100" to SURFACE_3),
    "autosuggest_text" to ("@color/andes_gray_550_solid" to TEXT),
    "autosuggest_toolbar" to ("@color/andes_white" to "@color/andes_color_yellow_500"),
    "search_cell_selection" to ("@color/andes_gray_100" to SURFACE_3),
    "search_cell_separator_gallery_background_color" to ("#cccccc" to BORDER),
    "search_cell_separator_list_background_color" to ("@color/andes_gray_070" to BORDER),
    "search_cell_text_view_dark_gray_color" to ("@color/andes_gray_800_solid" to TEXT),
    "search_cell_text_view_title_text_color" to ("@color/andes_gray_450_solid" to TEXT),
    "search_cell_text_view_light_gray_color" to ("@color/andes_gray_450" to MUTED),
    "search_cell_text_view_original_price_text_color" to ("@color/andes_gray_250" to MUTED),
    "search_divider_color" to ("@color/andes_gray_070_solid" to BORDER),
    "search_filters_divider_color" to ("@color/andes_gray_040" to BORDER),
    "search_filters_fragment_inner_background" to ("@color/andes_gray_040_solid" to BACKGROUND),
    "search_filters_fragment_title_text_color" to ("@color/andes_gray_900_solid" to TEXT),
    "search_filters_modal_title_background" to ("@color/andes_white" to SURFACE_2),
    "search_filters_modal_title_text" to ("@color/andes_gray_800_solid" to TEXT),
    "search_filters_row_background" to ("@color/andes_white" to SURFACE),
    "search_header_billboard_background" to ("@color/andes_gray_040_solid" to SURFACE),
    "search_header_billboard_border" to ("@color/andes_gray_450_solid" to BORDER),
    "search_header_product_title" to ("@color/andes_gray_800_solid" to TEXT),
    "search_header_product_subtitle" to ("@color/andes_gray_800_solid" to TEXT_SECONDARY),
    "search_appbar_label" to ("@color/andes_gray_450_solid" to TEXT_SECONDARY),
    "search_black_font" to ("@color/andes_gray_800" to TEXT),
    "search_prefix_suffix_color" to ("@color/andes_gray_450_solid" to TEXT_SECONDARY),
    "search_filters_collapsible_selected_value" to ("@color/andes_gray_450_solid" to TEXT_SECONDARY),
    "vpp_background_light_gray" to ("#f5f5f5" to SURFACE_2),
    "vpp_dot_default_color" to ("#d8d8d8" to MUTED),
    "vpp_table_gray" to ("@color/andes_gray_040_solid" to SURFACE_2),
    "vpp_table_highlight_gray" to ("#ebebeb" to SURFACE_3),
    "vpp_loading_dimmer" to ("#ccffffff" to "#cc111318"),
    // Login and identity screens stay functionally untouched. These are render-only colors.
    "login_background" to ("@color/andes_white" to BACKGROUND),
    "login_action_bar_icons" to ("@color/andes_gray_900" to TEXT),
    "login_components_black_color" to ("#cc000000" to TEXT),
    "login_components_login_tag_view_background" to ("#ffffff" to SURFACE),
    "login_components_login_tag_view_border" to ("#e7e7e7" to BORDER),
    "login_error_background" to ("#eeeeee" to SURFACE_2),
    "login_gray_dark" to ("@color/andes_gray_800_solid" to TEXT_SECONDARY),
    "login_hint_text" to ("#666666" to MUTED),
    "login_phone_title_color" to ("@color/andes_gray_900" to TEXT),
    "login_social_google_sign_in_stroke_border" to ("#e5e5e5" to BORDER),
)

private val styleItems = linkedMapOf(
    ("home_NewsRows" to "cardBackgroundColor") to ("@color/ui_meli_white" to SURFACE),
    ("navigation_manager_tabbar_divider" to "android:background") to ("@color/andes_gray_100_solid" to BORDER),
    ("AndesColorScheme" to "andesColorBackgroundPrimary") to ("?attr/andesColorGraySolid70" to BACKGROUND),
    ("AndesColorScheme" to "andesColorBackgroundSecondary") to ("?attr/andesColorGraySolid40" to SURFACE),
    ("AndesColorScheme" to "andesColorBackgroundTertiary") to ("?attr/andesColorWhite" to SURFACE_2),
    ("AndesColorScheme" to "andesColorBorderDisabled") to ("?attr/andesColorGray250" to BORDER),
    ("AndesColorScheme" to "andesColorBorderPrimary") to ("?attr/andesColorGray100" to BORDER),
    ("AndesColorScheme" to "andesColorFillDisabled") to ("?attr/andesColorGray100" to SURFACE_3),
    ("AndesColorScheme" to "andesColorFillPrimary") to ("?attr/andesColorWhite" to SURFACE),
    ("AndesColorScheme" to "andesColorFillSecondary") to ("?attr/andesColorGray40" to SURFACE_2),
    ("AndesColorScheme" to "andesColorFillTertiary") to ("?attr/andesColorGray100" to SURFACE_3),
    ("AndesColorScheme" to "andesColorIconDisabled") to ("?attr/andesColorGray250" to MUTED),
    ("AndesColorScheme" to "andesColorIconPrimary") to ("?attr/andesColorGray900" to TEXT),
    ("AndesColorScheme" to "andesColorIconSecondary") to ("?attr/andesColorGray550" to TEXT_SECONDARY),
    ("AndesColorScheme" to "andesColorInteractiveBorderDefault") to ("?attr/andesColorGray250" to BORDER),
    ("AndesColorScheme" to "andesColorInteractiveBorderHover") to ("?attr/andesColorGray550" to MUTED),
    ("AndesColorScheme" to "andesColorInteractiveIconActive") to ("?attr/andesColorGray900" to TEXT),
    ("AndesColorScheme" to "andesColorInteractiveIconDefault") to ("?attr/andesColorGray550" to TEXT_SECONDARY),
    ("AndesColorScheme" to "andesColorInteractiveIconHover") to ("?attr/andesColorGray800" to TEXT),
    ("AndesColorScheme" to "andesColorSurfaceActive") to ("?attr/andesColorGray70" to SURFACE_3),
    ("AndesColorScheme" to "andesColorSurfaceHover") to ("?attr/andesColorGray40" to SURFACE_2),
    ("AndesColorScheme" to "andesColorSurfacePrimary") to ("?attr/andesColorWhite" to SURFACE),
    ("AndesColorScheme" to "andesColorSurfaceSecondary") to ("?attr/andesColorGraySolid40" to SURFACE_2),
    ("AndesColorScheme" to "andesColorSurfaceTertiary") to ("?attr/andesColorGraySolid70" to SURFACE_3),
    ("AndesColorScheme" to "andesColorTextDisabled") to ("?attr/andesColorGray250" to MUTED),
    ("AndesColorScheme" to "andesColorTextPrimary") to ("?attr/andesColorGray900" to TEXT),
    ("AndesColorScheme" to "andesColorTextSecondary") to ("?attr/andesColorGray550" to TEXT_SECONDARY),
    ("Search.Carousel.CategoryNameText" to "android:textColor") to ("#cc000000" to TEXT),
    ("autosuggest_black_text" to "android:textColor") to ("@color/autosuggest_text" to "#302900"),
    ("Search.Carousel.OfficialStoreNameText" to "android:textColor") to ("#cc000000" to TEXT),
    ("Search.Carousel.OfficialStoreSubtitleText" to "android:textColor") to ("#848484" to TEXT_SECONDARY),
    ("Search.Header.OfficialStore.TextView.Title" to "android:textColor") to ("@color/andes_gray_800_solid" to TEXT),
    ("Search.Header.ResultsTitle.TextView.Title" to "android:textColor") to ("@color/andes_gray_550" to TEXT_SECONDARY),
    ("vppReviewsSelectedTab" to "android:textColor") to ("@color/andes_gray_800_solid" to TEXT),
    ("vppReviewsUnselectedTab" to "android:textColor") to ("@color/andes_gray_800_solid" to TEXT_SECONDARY),
    ("vppWrapper.vppComponentLiveTouchPointTitle" to "android:textColor") to ("@color/andes_gray_900" to TEXT),
)

// Search's generic search_background is also used in an add-to-cart view. Patch roots instead.
private val searchRootLayouts = linkedMapOf(
    "search_activity.xml" to 2,
    "search_carousel.xml" to 1,
    "search_component_items.xml" to 1,
    "search_component_zrp_disclaimer_intervention.xml" to 1,
    "search_fragment.xml" to 1,
    "search_header_compats_carousel.xml" to 1,
    "search_header_category.xml" to 1,
    "search_header_empty.xml" to 1,
    "search_header_cpg_carousel.xml" to 1,
    "search_line_carousel_content.xml" to 1,
)

private val themeItems = linkedMapOf(
    "android:windowBackground" to BACKGROUND,
    "android:colorBackground" to BACKGROUND,
    "android:textColorPrimary" to "@color/morphe_meli_dark_text_primary",
    "android:textColorSecondary" to "@color/morphe_meli_dark_text_secondary",
    "android:statusBarColor" to HEADER,
    "android:navigationBarColor" to BACKGROUND,
    "android:windowLightStatusBar" to "false",
    "android:windowLightNavigationBar" to "false",
    "android:enforceNavigationBarContrast" to "false",
    "colorSurface" to SURFACE,
    "colorOnSurface" to TEXT,
    "colorOnPrimary" to BACKGROUND,
    "andesColorBackgroundPrimary" to BACKGROUND,
    "andesColorBackgroundSecondary" to SURFACE,
    "andesColorBackgroundTertiary" to SURFACE_2,
    "andesColorSurfacePrimary" to SURFACE,
    "andesColorSurfaceSecondary" to SURFACE_2,
    "andesColorSurfaceTertiary" to SURFACE_3,
    "andesColorFillPrimary" to SURFACE,
    "andesColorFillSecondary" to SURFACE_2,
    "andesColorFillTertiary" to SURFACE_3,
    "andesColorTextPrimary" to TEXT,
    "andesColorTextSecondary" to TEXT_SECONDARY,
    "andesColorTextDisabled" to MUTED,
    "andesColorIconPrimary" to TEXT,
    "andesColorIconSecondary" to TEXT_SECONDARY,
    "andesColorBorderPrimary" to BORDER,
    "andesColorBorderDisabled" to BORDER,
    "andesColorInteractiveBorderDefault" to BORDER,
    "andesWindowLightStatusBar" to "false",
)

@Suppress("unused")
val darkThemePatch = resourcePatch(
    name = "Dark Theme (native)",
    description = "Dark native surfaces using Andes semantic colors; preserves brand colors, WebViews and business logic.",
    default = true,
) {
    compatibleWith(compatibleApp)
    dependsOn(darkHomeRuntimePatch)
    dependsOn(darkMenuRuntimePatch)
    dependsOn(darkPublicWebViewsPatch)
    dependsOn(darkRecommendationsPatch)
    dependsOn(darkTextfieldRuntimePatch)

    execute {
        val originalResources = snapshotLightResources()
        val metadata = packageMetadata
        if (metadata.packageName != PACKAGE || metadata.versionName != VERSION || metadata.versionCode != VERSION_CODE) {
            throw PatchException("Expected $PACKAGE $VERSION ($VERSION_CODE); found ${metadata.packageName} ${metadata.versionName} (${metadata.versionCode})")
        }

        document("res/values/colors.xml").use { doc ->
            colors.forEach { (name, values) -> doc.replaceResource("color", name, values.first, values.second) }
            // Material NavigationView resolves these attributes through TypedValue.resourceId.
            // Inline theme literals give resourceId=0 and crash its ColorStateList loader.
            linkedMapOf("morphe_meli_dark_text_primary" to TEXT, "morphe_meli_dark_text_secondary" to TEXT_SECONDARY).forEach { (name, value) ->
                if (doc.getElementsByTagName("color").elements().any { it.getAttribute("name") == name }) {
                    throw PatchException("Color $name already exists")
                }
                doc.documentElement.appendChild(doc.createElement("color").apply {
                    setAttribute("name", name)
                    textContent = value
                })
            }
        }

        document("res/values/styles.xml").use { doc ->
            styleItems.forEach { (key, values) -> doc.replaceStyleItem(key.first, key.second, values.first, values.second) }
            doc.addStyleItem("HomeNewCardViewStyle.ActionCard", "cardBackgroundColor", SURFACE)
            // Home tabs use custom TextViews; c0 reapplies these appearances on selection.
            // Their yellow brand background requires dark text, independently of the theme.
            doc.addStyleItem("HomesTabTextStyleSelected", "android:textColor", "#111318")
            doc.addStyleItem("HomesTabTextStyleUnselected", "android:textColor", "#302900")
            doc.addBaseThemeItems()
            doc.addDarkTheme()
        }
        // Android selects complete qualified styles; base-only edits do not reach API 29/35.
        listOf("values-v29", "values-v35").forEach { qualifier ->
            document("res/$qualifier/styles.xml").use { doc -> doc.addBaseThemeItems() }
        }

        document("AndroidManifest.xml").use { doc -> doc.setActivityThemes() }

        searchRootLayouts.forEach { (file, count) ->
            replaceLayoutAttribute("res/layout/$file", "android:background", "@color/search_background", BACKGROUND, count)
        }
        // The same layout also contains vpp_sticky_cart_actions_container; leave it unchanged.
        replaceLayoutAttributeForId(
            "res/layout/vpp_fragment.xml",
            "@id/vpp_fragment_container",
            "android:background",
            "@color/andes_white",
            BACKGROUND,
        )
        addLayoutAttributeForId("res/layout/home_new_news_row.xml", "@id/home_label_txt", "android:textColor", TEXT)
        addLayoutAttributeForId("res/layout/home_new_news_row.xml", "@id/home_disclaimer", "android:textColor", TEXT_SECONDARY)
        addLayoutAttributeForId("res/layout/home_new_news_row.xml", "@id/father_card_container", "android:background", BACKGROUND)
        addLayoutAttributeForId("res/layout/home_new_exhibitor_canvas.xml", "@id/home_new_exhibitor_item_container", "app:cardBackgroundColor", SURFACE)
        replaceLayoutAttributeForId("res/layout/home_new_exhibitor_canvas.xml", "@id/home_new_exhibitor_canvas", "android:background", "@color/white", SURFACE)
        replaceLayoutAttribute("res/drawable/home_new_exhibitor_rounded.xml", "android:color", "@color/andes_white", SURFACE, 1)
        replaceLayoutAttributeForId("res/layout/home_new_onboarding.xml", "@id/layout_onboarding", "android:background", "@color/white", BACKGROUND)
        replaceLayoutAttributeForId("res/layout/home_view_fragment_onboarding.xml", "@id/home_view_fragment_onboarding_container", "android:background", "@color/andes_white", BACKGROUND)
        replaceLayoutAttribute("res/layout/home_view_fragment_onboarding_step_register.xml", "android:background", "@color/andes_white", BACKGROUND, 1)
        replaceLayoutAttribute("res/layout/autosuggest_main.xml", "android:background", "@color/andes_white", BACKGROUND, 1)
        replaceLayoutAttribute("res/drawable/top_bar_kit_core_search_bg_rounded.xml", "android:color", "@android:color/white", SURFACE_2, 1)
        replaceLayoutAttribute("res/drawable/top_bar_kit_core_input_cursor.xml", "android:color", "#000000", TEXT, 1)
        replaceLayoutAttribute("res/layout/top_bar_kit_core_search_field.xml", "android:textColor", "@color/andes_text_color_primary", TEXT, 2)
        addLayoutAttributeForId("res/layout/top_bar_kit_core_search_field.xml", "@id/top_bar_kit_core_search_text", "android:textColorHint", MUTED)
        replaceLayoutAttribute("res/layout/top_bar_kit_core_search_field_editable.xml", "android:textColor", "@color/andes_text_color_primary", TEXT, 1)
        replaceLayoutAttribute("res/layout/top_bar_kit_core_search_field_editable.xml", "android:textColorHint", "@color/andes_text_color_secondary", MUTED, 1)
        // The suggestion binder assigns only local history/search glyphs to these two views.
        addLayoutAttributeForId("res/layout/autosuggest_item_component.xml", "@id/suggestion_start_icon", "android:tint", TEXT_SECONDARY)
        addLayoutAttributeForId("res/layout/autosuggest_item_component.xml", "@id/suggestion_end_icon", "android:tint", TEXT_SECONDARY)
        replaceLayoutAttributeForId("res/layout/login_activity_phone_login.xml", "@id/login_identification_footer_line", "android:background", "@color/andes_gray_100", BORDER)
        replaceLayoutAttributeForId("res/layout/login_social_divider_component.xml", "@id/login_identification_text_divider", "android:textColor", "@color/andes_text_color_primary", TEXT)
        replaceLayoutAttributeForId("res/layout/login_components_social_sign_in_button.xml", "@id/login_google_social_sign_in_label", "android:textColor", "@color/andes_text_color_secondary", TEXT_SECONDARY)
        replaceLayoutAttribute("res/layout/login_abstract_activity.xml", "android:background", "@color/andes_white", SURFACE, 1)
        addLayoutAttributeForId("res/layout/login_content_layout.xml", "@id/login_toolbar_actionbar", "app:titleTextColor", BACKGROUND)
        replaceLayoutAttribute("res/drawable/login_shape_bg_social_google_sign_in.xml", "android:color", "@color/andes_white", SURFACE_2, 1)
        replaceLayoutAttribute("res/drawable/andes_color_cursor.xml", "android:color", "#cc000000", TEXT, 1)
        // Input values inherit a light EditText appearance even when labels are semantic Andes.
        addLayoutAttributeForId("res/layout/andes_layout_textfield.xml", "@id/andes_textfield_edittext", "android:textColor", TEXT)
        listOf("autosuggest_shimmer_item_background.xml", "autosuggest_shimmer_card_background.xml").forEach { file ->
            replaceLayoutAttribute("res/drawable/$file", "android:color", "#e8e8e8", SURFACE_2, 1)
        }
        replaceLayoutAttributeForId(
            "res/layout/autosuggest_toolbar_view.xml",
            "@id/autosuggest_delete_button",
            "app:tint",
            "@color/autosuggest_text",
            "#302900",
        )
        replaceLayoutAttribute("res/layout/recommendation_feed_item_title.xml", "android:background", "@color/white", BACKGROUND, 1)
        replaceLayoutAttribute("res/layout/recommendation_feed_item_title.xml", "android:background", "@color/andes_gray_250", BORDER, 2)
        addLayoutAttributeForId("res/layout/recommendation_feed_item_title.xml", "@id/title", "android:textColor", TEXT)
        replaceLayoutAttribute("res/drawable/ui_components_toolbar_bg_toolbar_white_rounded.xml", "android:color", "@color/andes_white", SURFACE_2, 1)
        replaceLayoutAttribute("res/layout/ui_components_toolbar_search.xml", "android:textColor", "@color/andes_gray_900", TEXT_SECONDARY, 1)
        addLayoutAttributeForId("res/layout/ui_components_toolbar_search.xml", "@id/ui_components_toolbar_title_toolbar", "android:textColorHint", MUTED)
        replaceLayoutAttribute("res/drawable/ui_components_toolbar_ic_toolbar_search.xml", "android:strokeColor", "#aaaaaa", MUTED, 2)
        replaceLayoutAttribute("res/drawable/ui_components_toolbar_search_by_image_camera.xml", "android:fillColor", "#000000", TEXT_SECONDARY, 2)
        replaceLayoutAttribute("res/drawable/navigation_manager_tabbar_default_tab_selector.xml", "android:drawable", "@color/andes_bg_color_white", SURFACE, 1)
        replaceLayoutAttribute("res/drawable/navigation_manager_tabbar_default_tab_ripple.xml", "android:drawable", "@color/andes_bg_color_white", SURFACE, 1)
        replaceLayoutAttribute("res/layout/navigation_manager_tabbar_top_divider.xml", "android:background", "@color/andes_gray_100_solid", BORDER, 1)
        replaceLayoutAttribute("res/drawable/ui_sections_sc_orders_secondary_action_background.xml", "android:drawable", "@color/ui_sections_sc_orders_white", SURFACE, 1)
        replaceLayoutAttribute("res/layout/ui_sections_menu_container_flox.xml", "android:background", "@color/andes_white", SURFACE, 1)
        replaceLayoutAttribute("res/layout/ui_sections_separator_row.xml", "android:background", "@color/ui_sections_sc_orders_white", SURFACE, 1)
        replaceLayoutAttribute("res/layout/ui_sections_single_selection_row.xml", "android:background", "@color/ui_sections_sc_orders_white", SURFACE, 1)
        addLayoutAttributeForId("res/layout/ui_sections_single_selection_row.xml", "@id/sc_orders_single_selection_title", "android:textColor", TEXT_SECONDARY)
        listOf(
            "ui_sections_menu_row_default.xml" to "@id/drawer_navigation_default_icon",
            "ui_sections_menu_row_badge.xml" to "@id/drawer_navigation_badge_icon",
            "ui_sections_menu_row_broadcast_badge.xml" to "@id/drawer_navigation_badge_icon",
            "ui_sections_menu_row_tag.xml" to "@id/drawer_navigation_tag_icon",
        ).forEach { (layout, iconId) ->
            addLayoutAttributeForId("res/layout/$layout", iconId, "app:tint", TEXT_SECONDARY)
        }
        installSelectableResources(originalResources)
    }
}

private fun NodeList.elements(): List<Element> = (0 until length).mapNotNull { item(it) as? Element }

private fun Document.unique(tag: String, name: String): Element {
    val found = getElementsByTagName(tag).elements().filter { it.getAttribute("name") == name }
    if (found.size != 1) throw PatchException("Expected one <$tag name=\"$name\">, found ${found.size}")
    return found.single()
}

private fun Document.replaceResource(tag: String, name: String, old: String, new: String) {
    val element = unique(tag, name)
    val value = element.textContent.trim()
    if (value != old) throw PatchException("$tag/$name changed: expected $old, got $value")
    element.textContent = new
}

private fun Document.replaceStyleItem(styleName: String, itemName: String, old: String, new: String) {
    val style = unique("style", styleName)
    val found = style.childNodes.elements().filter { it.tagName == "item" && it.getAttribute("name") == itemName }
    if (found.size != 1) throw PatchException("Expected $styleName/$itemName once, found ${found.size}")
    val item = found.single()
    val value = item.textContent.trim()
    if (value != old) throw PatchException("$styleName/$itemName changed: expected $old, got $value")
    item.textContent = new
}

private fun Document.addStyleItem(styleName: String, itemName: String, value: String) {
    val style = unique("style", styleName)
    if (style.childNodes.elements().any { it.tagName == "item" && it.getAttribute("name") == itemName }) {
        throw PatchException("$styleName already defines $itemName")
    }
    style.appendChild(createElement("item").apply {
        setAttribute("name", itemName)
        textContent = value
    })
}

private fun Document.addDarkTheme() {
    // Preserve each Activity's original theme inheritance and startup/window behavior.
    listOf(
        THEME to "@style/Theme.Base",
        HOME_THEME to "@style/StartupInitializerSplashTheme.Application",
        VPP_THEME to "@style/vppActivityTheme",
    ).forEach { (name, parent) ->
        if (getElementsByTagName("style").elements().any { it.getAttribute("name") == name }) {
            throw PatchException("Style $name already exists")
        }
        val style = createElement("style").apply {
            setAttribute("name", name)
            setAttribute("parent", parent)
        }
        themeItems.forEach { (itemName, value) ->
            style.appendChild(createElement("item").apply {
                setAttribute("name", itemName)
                textContent = value
            })
        }
        documentElement.appendChild(style)
    }
}

private fun Document.addBaseThemeItems() {
    val style = unique("style", "Theme.MLTheme")
    linkedMapOf(
        "android:windowBackground" to BACKGROUND,
        "android:colorBackground" to BACKGROUND,
        "android:textColorPrimary" to "@color/morphe_meli_dark_text_primary",
        "android:textColorSecondary" to "@color/morphe_meli_dark_text_secondary",
        "android:navigationBarColor" to BACKGROUND,
        "android:windowLightNavigationBar" to "false",
        "android:enforceNavigationBarContrast" to "false",
        "android:forceDarkAllowed" to "false",
        "colorSurface" to SURFACE,
        "colorOnSurface" to TEXT,
    ).forEach { (name, value) ->
        val existing = style.childNodes.elements().filter { it.tagName == "item" && it.getAttribute("name") == name }
        if (name == "android:forceDarkAllowed" && existing.size == 1 && existing.single().textContent.trim() == "false") {
            // Preserve the existing opt-out in the API 29 and API 35 original themes.
            return@forEach
        }
        if (existing.isNotEmpty()) {
            throw PatchException("Theme.MLTheme already defines $name")
        }
        style.appendChild(createElement("item").apply {
            setAttribute("name", name)
            textContent = value
        })
    }
}

private fun Document.setActivityThemes() {
    val activities = getElementsByTagName("activity").elements()
    activityThemes.forEach { (name, themes) ->
        val found = activities.filter { it.getAttribute("android:name") == name }
        if (found.size != 1) throw PatchException("Expected activity $name once, found ${found.size}")
        val activity = found.single()
        val current = activity.getAttribute("android:theme")
        if (current != themes.first) throw PatchException("Theme of $name changed: expected '${themes.first}', got '$current'")
        activity.setAttribute("android:theme", "@style/${themes.second}")
    }
}

private fun ResourcePatchContext.replaceLayoutAttribute(path: String, attribute: String, old: String, new: String, count: Int) {
    document(path).use { doc ->
        val found = doc.getElementsByTagName("*").elements().filter { it.getAttribute(attribute) == old }
        if (found.size != count) throw PatchException("$path: expected $count $attribute=$old, found ${found.size}")
        found.forEach { it.setAttribute(attribute, new) }
    }
}

private fun ResourcePatchContext.replaceLayoutAttributeForId(
    path: String,
    id: String,
    attribute: String,
    old: String,
    new: String,
) {
    document(path).use { doc ->
        val found = doc.getElementsByTagName("*").elements().filter { it.getAttribute("android:id") == id }
        if (found.size != 1) throw PatchException("$path: expected one element $id, found ${found.size}")
        val element = found.single()
        val value = element.getAttribute(attribute)
        if (value != old) throw PatchException("$path: $id expected $attribute=$old, found $value")
        element.setAttribute(attribute, new)
    }
}

private fun ResourcePatchContext.addLayoutAttributeForId(
    path: String,
    id: String,
    attribute: String,
    value: String,
) {
    document(path).use { doc ->
        val found = doc.getElementsByTagName("*").elements().filter { it.getAttribute("android:id") == id }
        if (found.size != 1) throw PatchException("$path: expected one element $id, found ${found.size}")
        val element = found.single()
        if (element.hasAttribute(attribute)) throw PatchException("$path: $id already defines $attribute")
        element.setAttribute(attribute, value)
    }
}
