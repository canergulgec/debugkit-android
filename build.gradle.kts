// AGP 9.x auto-applies the Kotlin Android plugin, so it is intentionally NOT aliased
// here; adding it manually causes "Cannot add extension with name 'kotlin'".
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

// GROUP and VERSION live in gradle.properties so every published module shares them.
// JitPack overrides both at build time from the GitHub org/repo and the git tag.
allprojects {
    group = providers.gradleProperty("GROUP").get()
    version = providers.gradleProperty("VERSION").get()
}
