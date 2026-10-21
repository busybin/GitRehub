// Shared build settings; regenerate rather than hand-edit.
// AGP 9 has built-in Kotlin: never apply org.jetbrains.kotlin.android; raise the bundled KGP here instead.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") version "9.4.0" apply false
    id("com.android.library") version "9.4.0" apply false
    id("com.android.test") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false // must match KGP above
    id("com.mikepenz.aboutlibraries.plugin") version "15.3.0" apply false
    // standalone compiler plugin; must match KGP
    id("org.jetbrains.kotlin.plugin.parcelize") version "2.4.21" apply false
    // kotlinx.serialization for the rewritten data layer (core/net) — no Gson at runtime.
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21" apply false
}
