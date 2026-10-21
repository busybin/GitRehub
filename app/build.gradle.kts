plugins {
    id("com.android.application")
    // AGP 9: Kotlin is built in — do not apply kotlin-android (it errors out)
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.parcelize")
    // core/net serializes with kotlinx.serialization; the compiler plugin generates serializers.
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.mikepenz.aboutlibraries.plugin")
}

fun loadConfig(): HashMap<String, String> {
    val configs: HashMap<String, String> = HashMap()
    // No third-party OAuth credentials are bundled. To use device-code login, create your own OAuth
    // App and put its credentials in app/secrets.properties (gitignored): GITHUB_CLIENT_ID /
    // GITHUB_SECRET. Token login alone needs nothing configured.
    configs["GITHUB_CLIENT_ID"] = ""
    configs["GITHUB_SECRET"] = ""
    val inputFile = rootProject.file("app/secrets.properties")
    if (inputFile.exists()) {
        println("Loading credentials from app/secrets.properties...")
        inputFile.forEachLine {
            val data = it.split("=")
            if (data.size >= 2) configs[data[0].trim()] = data[1].trim()
        }
    } else {
        println("No app/secrets.properties — Token login only (no OAuth app configured).")
    }
    return configs
}

val config = loadConfig()

// Local smoke run against a mock API (never set in a published build): point every request at the
// given base URL and allow cleartext HTTP so the release build can be exercised on an emulator.
val mockApi = (project.findProperty("gitrehub.mockApi") as String?)?.takeIf { it.isNotBlank() }

// F-Droid re-signs the APK, so the in-app self-updater cannot install anything there. The F-Droid
// recipe passes -Pgitrehub.store=fdroid, which compiles the updater out; other builds keep it.
val storeFdroid = (project.findProperty("gitrehub.store") as String?) == "fdroid"

// The release key is not in the repo. F-Droid builds from source without it and expects an unsigned
// APK, so only attach the signing config when the key is actually present.
val releaseKeystore = rootProject.file("app/keys_release.jks")

android {
    namespace = "io.github.busybin.gitrehub"
    // SDK versions come from the baseline.* keys in gradle.properties; do not hardcode.
    compileSdk = (rootProject.findProperty("baseline.compileSdk") as String).toInt()
    defaultConfig {
        applicationId = "io.github.busybin.gitrehub"
        minSdk = (rootProject.findProperty("baseline.minSdk") as String).toInt()
        // Baseline pins targetSdk to 36 while compileSdk tracks 37.
        targetSdk = (rootProject.findProperty("baseline.targetSdk") as String).toInt()
        versionCode = 3
        // Plain number, no "V" prefix, so F-Droid / IzzyOnDroid can compare it directly; the release
        // tag equals the versionCode.
        versionName = "3"
        buildConfigField("String", "GITHUB_APP_ID", "\"io.github.busybin.gitrehub\"")
        // OAuth callback; your own GitHub OAuth App must whitelist it. Only needed for the
        // device-code login flow (see README) — plain token login needs no OAuth app at all.
        buildConfigField("String", "OAUTH_REDIRECT_URL", "\"${config["OAUTH_REDIRECT_URL"] ?: "gitrehub://login"}\"")
        buildConfigField("String", "GITHUB_CLIENT_ID", "\"${config["GITHUB_CLIENT_ID"]}\"")
        buildConfigField("String", "GITHUB_SECRET", "\"${config["GITHUB_SECRET"]}\"")
        buildConfigField("String", "REST_URL", "\"${mockApi ?: "https://api.github.com/"}\"")
        // False in F-Droid builds: updates are managed by the store, not by an APK we ship.
        buildConfigField("boolean", "SELF_UPDATE", (!storeFdroid).toString())
        manifestPlaceholders["usesCleartextTraffic"] = if (mockApi != null) "true" else "false"
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        // The repo ships its own debug keystore on purpose (stable, shared signature across
        // machines); keep using it so `adb install -r` works against existing installs.
        getByName("debug") {
            storeFile = rootProject.file("app/keys_debug.jks")
        }
        // Release: app/keys_release.jks is NOT in the repo (.gitignore) — provide your own, or set
        // PASSWORD / KEY_ALIAS in app/secrets.properties before building a release.
        create("release") {
            val keystore = releaseKeystore
            if (keystore.exists()) {
                storeFile = keystore
                storePassword = config["PASSWORD"]
                keyPassword = config["PASSWORD"]
                keyAlias = config["KEY_ALIAS"]
            } else {
                println("WARNING: app/keys_release.jks not found — release builds will be unsigned.")
            }
        }
    }
    buildTypes {
        release {
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(
                // AGP 9: getDefaultProguardFile only accepts the optimize variant.
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            // Unsigned when the key is absent (F-Droid); never fall back to the debug key.
            if (releaseKeystore.exists()) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
            // The package keeps a .debug suffix (coexists with release, separate data), but the
            // display name and version drop "debug".
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
        // viewBinding was removed with the classic UI: no layout XML left to bind.
    }

    lint {
        htmlReport = true
        xmlReport = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    // The theme XML now uses framework themes only: appcompat / material / fragment / preference
    // and core-splashscreen were only there for the classic View UI and are gone.

    // core/net JSON (replaces Gson for the rewritten data layer)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")

    // okhttp3
    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.9.3"))
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.3")

    // about lib (the About screen uses the Compose variant only)
    implementation("com.mikepenz:aboutlibraries-core:15.3.0")
    implementation("com.mikepenz:aboutlibraries-compose-m3:15.3.0")

    // HtmlSpanner + htmlcleaner (still used by the read-view HTML → Spannable pipeline)
    implementation("com.github.NightWhistler:HtmlSpanner:0.4")
    implementation("net.sourceforge.htmlcleaner:htmlcleaner:2.2")

    // commonmark
    implementation("com.atlassian.commonmark:commonmark:0.17.0")
    implementation("com.atlassian.commonmark:commonmark-ext-autolink:0.17.0")
    implementation("com.atlassian.commonmark:commonmark-ext-gfm-strikethrough:0.17.0")
    implementation("com.atlassian.commonmark:commonmark-ext-gfm-tables:0.17.0")
    implementation("com.atlassian.commonmark:commonmark-ext-ins:0.17.0")
    implementation("com.atlassian.commonmark:commonmark-ext-yaml-front-matter:0.17.0")

    // device name (used by AppHelper / DeviceNameGetter)
    implementation("com.jaredrummler:android-device-names:2.1.0")

    // mmkv
    // 16KB page size: 1.2.12's prebuilt .so is only 0x1000 (4KB) aligned and cannot load on
    // 16KB-page devices (Android 15+); 1.3.17's 64-bit libs are 0x4000 aligned, with all four ABIs
    // still present (2.x dropped armeabi-v7a / x86).
    implementation("com.tencent:mmkv:1.3.17")

    // androidx javax annotation
    implementation("androidx.annotation:annotation:1.4.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:4.4.0")
    testImplementation("org.assertj:assertj-core:3.22.0")
    // The offline-cache tests need a real HTTP server (store online, read offline); the interceptor
    // only depends on OkHttp, so they run on the JVM.
    testImplementation("com.squareup.okhttp3:mockwebserver:4.9.3")
    androidTestImplementation("org.mockito:mockito-core:4.4.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test:rules:1.4.0")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.4.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")

    // leak detection
//    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.8.1")

    // Modern UI layer (Compose).
    // The BOM must match the Compose version actually resolved at runtime: an older BOM pinned
    // compile-time 1.7.5 while aboutlibraries/coil lifted the runtime to 1.12.1, and a signature
    // change (FlowRow) caused NoSuchMethodError. BOM 2026.09.00 = 1.12.1 + material3 1.4.0.
    // Before changing it, compare with: ./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep compose
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.13.1")
    // image loading for Compose; coil-gif keeps animated GIFs in READMEs animating
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

