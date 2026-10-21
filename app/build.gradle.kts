plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.fitnesslink.fit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fitnesslink.fit"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Barcode scanning + CameraX
    implementation(libs.mlkit.barcode)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.camerax.video)

    // Media
    implementation(libs.coil.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.exoplayer.hls)

    // Sign-in
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    // Billing
    implementation(libs.billing.ktx)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.messaging.ktx)

    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
}

// FTNSSLNK-540: block new light-only colours in UI code. Files under ui/ (except
// ui/theme) may not use the deprecated Color.kt neutrals, Color.White/Black or raw
// Color(0x…) literals unless listed in config/hardcoded-colors-allowlist.txt. The
// allowlist only shrinks: each dark-mode migration story removes its files.
// Regenerate after a migration with: ./gradlew checkHardcodedColors -PupdateColorAllowlist
val hardcodedColorPattern = Regex(
    """(?<![\w.])(BackgroundColor|TextPrimaryColor|TextSecondaryColor|MediumGray|BlendGray|ProgressBG|DisabledButton|CalendarLine|ImagePlaceholder|MenuColor|InfoColor|HighlightState|White|Black)\b|Color\.(White|Black)\b|Color\(0x"""
)
val uiSourceDir = file("src/main/java/com/fitnesslink/fit/ui")
val colorAllowlistFile = rootProject.file("config/hardcoded-colors-allowlist.txt")
val updateColorAllowlist = project.hasProperty("updateColorAllowlist")

tasks.register("checkHardcodedColors") {
    group = "verification"
    description = "Fails on light-only colours in UI files that are not on the allowlist."
    doLast {
        val root = uiSourceDir.parentFile.parentFile.parentFile.parentFile // .../java
        val offenders = uiSourceDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && !it.path.contains("/ui/theme/") }
            .filter { f -> f.readLines().any { line -> !line.trimStart().startsWith("import ") && hardcodedColorPattern.containsMatchIn(line) } }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .sorted()
            .toList()
        if (updateColorAllowlist) {
            colorAllowlistFile.parentFile.mkdirs()
            colorAllowlistFile.writeText(
                "# FTNSSLNK-540: UI files still using light-only colours. Only remove lines.\n" +
                    offenders.joinToString("\n", postfix = "\n")
            )
            println("Wrote ${offenders.size} files to ${colorAllowlistFile.name}")
            return@doLast
        }
        val allowed = if (colorAllowlistFile.exists())
            colorAllowlistFile.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
        else emptySet()
        val violations = offenders.filterNot { it in allowed }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Light-only colours found in ${violations.size} file(s) not on the allowlist. " +
                    "Use FLTheme.colors (see ui/theme/README.md):\n" + violations.joinToString("\n") { "  $it" }
            )
        }
        val cleaned = allowed - offenders.toSet()
        if (cleaned.isNotEmpty()) {
            println("These allowlisted files are now clean; remove them from ${colorAllowlistFile.name}:\n" + cleaned.joinToString("\n") { "  $it" })
        }
    }
}
tasks.named("check") { dependsOn("checkHardcodedColors") }
