plugins { java }
val sdkRoot = providers.environmentVariable("ANDROID_SDK_ROOT").orElse(providers.environmentVariable("ANDROID_HOME")).orNull
    ?: throw GradleException("Set ANDROID_SDK_ROOT to an SDK containing platforms/android-35 and cmdline-tools/latest.")
val androidJar = file("$sdkRoot/platforms/android-35/android.jar")
dependencies { compileOnly(files(androidJar)) }
java { sourceCompatibility = JavaVersion.VERSION_1_8; targetCompatibility = JavaVersion.VERSION_1_8 }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
val buildDex by tasks.registering(JavaExec::class) {
    dependsOn(tasks.jar)
    classpath = files("$sdkRoot/cmdline-tools/latest/lib/r8.jar")
    mainClass.set("com.android.tools.r8.D8")
    val dexDir = layout.buildDirectory.dir("dex")
    inputs.file(tasks.jar.flatMap { it.archiveFile })
    inputs.file(androidJar)
    outputs.dir(dexDir)
    doFirst {
        dexDir.get().asFile.mkdirs()
        args("--release", "--min-api", "23", "--lib", androidJar.absolutePath, "--output", dexDir.get().asFile.absolutePath,
            tasks.jar.get().archiveFile.get().asFile.absolutePath)
    }
}
