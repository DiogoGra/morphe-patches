group = "local.meli.dark"
tasks.processResources {
    dependsOn(":runtime:buildDex")
    from(project(":runtime").layout.buildDirectory.file("dex/classes.dex")) { rename { "meli-settings.dex" } }
}

patches {
    about {
        name = "Diogo patches"
        description = "App patches compatible with Morphe"
        source = "https://github.com/DiogoGra/morphe-patches"
        author = "DiogoGra"
        contact = "https://github.com/DiogoGra/morphe-patches/issues"
        website = "https://github.com/DiogoGra/morphe-patches"
        license = "GPLv3"
    }
}
