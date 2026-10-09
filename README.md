# Diogo patches

Patches for use with [Morphe](https://morphe.software/).

## Supported apps

| App | Package | Target version | Features |
| --- | --- | --- | --- |
| Mercado Libre | `com.mercadolibre` | `10.574.1` (`1816182806`) | Dark theme and in-app theme settings |

## Download

Download `patches-1.0.0.mpp` from the [latest release](https://github.com/DiogoGra/morphe-patches/releases/latest) and load it in Morphe Desktop. Use the original APKM for the supported app version and enable all six included patches.

## Mercado Libre - Theme

- Applies a dark theme to native backgrounds, cards, text fields, navigation and feed components.
- Preserves the brand yellow, product images and multimedia.
- Applies scoped CSS to the public Home campaign and Categories WebViews.
- Adds **Más → Morphe → Ajustes de Morphe → Tema oscuro** using the app's native menu row, font, settings icon and Andes switch.
- Saves the theme preference locally. Dark theme is enabled by default.
- Shows **Reinicio requerido** when the theme is changed. **Cancelar** postpones the change; **Reiniciar** reopens the app with the selected theme.
- Retains the original light rendering and preserves purchase, payment and authentication logic.

The bundle includes `Dark Theme (native)`, `Dark Theme Home UI hooks`, `Dark Theme menu UI hooks`, `Dark Theme public WebViews`, `Dark Theme recommendations feed` and `Dark Theme text field UI hooks`.

Tested with Morphe CLI **1.7.0** on an Android **API 35** emulator. Theme switching, Home, menu, Categories, Videos, search suggestions and the login text field were checked without signing in or submitting forms. Private account screens and the product detail page have not been visually verified. Server-side content variations may require further theme adjustments.

## Build

Requirements:

- JDK 17.
- Android SDK with `platforms/android-35/android.jar` and `cmdline-tools/latest/lib/r8.jar`.
- `ANDROID_SDK_ROOT` pointing to that SDK.

```sh
./gradlew buildAndroid --no-daemon
```

On Windows, use `gradlew.bat`. The bundle is generated at `patches/build/libs/patches-1.0.0.mpp`. Gradle 9.7.1 and the source dependencies under `vendor/` are pinned. Java runtime sources are compiled to DEX as part of the build. No manual APK edits are required. Gradle dependencies must be available online or in the local cache.

Applying the bundle with Morphe CLI 1.7.0 requires Java 21. Keep your signing key locally to update an existing patched installation with the same signature.

## License

[GPL-3.0](LICENSE). Bundled dependencies retain their license and notice files. See [NOTICE](NOTICE) and [VENDOR_COMMITS.txt](VENDOR_COMMITS.txt).

This is an independent patch bundle compatible with Morphe and is not affiliated with Mercado Libre or the Morphe project.
