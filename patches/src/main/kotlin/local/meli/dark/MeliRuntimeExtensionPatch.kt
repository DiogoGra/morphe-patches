package local.meli.dark

import app.morphe.patcher.patch.bytecodePatch

/** Shared runtime only; each consumer adds its own hooks and behavior. */
val meliRuntimeExtensionPatch = bytecodePatch {
    extendWith("meli-settings.dex")
}
