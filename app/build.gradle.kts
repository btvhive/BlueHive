// build.gradle.kts

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.util.zip.ZipFile

// Fails the build when an image would be decoded too big to draw well.
//
// A folder with no density qualifier (plain drawable/) counts as mdpi, so on
// our 320 dpi boxes everything in it is decoded at 2x per side. v368 had all
// the 1080p art there: the home screen needed ~77 MB of textures per frame
// against Android 7's 48 MB cache and scrolled at 6 fps on the Fire TV 4K
// stick, and the sidebar came out 204x4320 -- past the GPU's 4096 limit --
// so it was never drawn at all. v369 moved the art to drawable-xhdpi/.
val checkDrawableSizes by tasks.registering {
    val resDir = layout.projectDirectory.dir("src/main/res")
    inputs.files(fileTree(resDir) { include("drawable*/**") })

    doLast {
        // Biggest raster allowed in a folder with no density qualifier.
        val maxUnqualifiedSidePx = 512
        // GL_MAX_TEXTURE_SIZE on the Fire TV sticks (PowerVR GE8300).
        val maxTextureSidePx = 4096
        // Density of every box we ship to (AFTMM, AFTSS, onn 4K).
        val deviceDpi = 320
        val densities = mapOf(
            "ldpi" to 120, "mdpi" to 160, "tvdpi" to 213, "hdpi" to 240,
            "xhdpi" to 320, "xxhdpi" to 480, "xxxhdpi" to 640,
        )

        // Width x height from the file header, or null when it is not a raster.
        fun imageSize(f: File): Pair<Int, Int>? {
            val b = f.inputStream().use { it.readNBytes(64 * 1024) }
            fun u8(i: Int) = b[i].toInt() and 0xFF
            fun be16(i: Int) = (u8(i) shl 8) or u8(i + 1)
            fun le16(i: Int) = u8(i) or (u8(i + 1) shl 8)
            fun le24(i: Int) = le16(i) or (u8(i + 2) shl 16)
            fun be32(i: Int) = (be16(i) shl 16) or be16(i + 2)
            return when {
                b.size >= 24 && u8(0) == 0x89 && String(b, 1, 3) == "PNG" -> be32(16) to be32(20)
                b.size >= 30 && String(b, 0, 4) == "RIFF" && String(b, 8, 4) == "WEBP" ->
                    when (String(b, 12, 4)) {
                        "VP8X" -> (le24(24) + 1) to (le24(27) + 1)
                        "VP8L" -> {
                            val v = le16(21) or (le16(23) shl 16)
                            ((v and 0x3FFF) + 1) to (((v shr 14) and 0x3FFF) + 1)
                        }
                        "VP8 " -> (le16(26) and 0x3FFF) to (le16(28) and 0x3FFF)
                        else -> null
                    }
                b.size >= 4 && u8(0) == 0xFF && u8(1) == 0xD8 -> {
                    var i = 2
                    var size: Pair<Int, Int>? = null
                    while (i + 9 < b.size && size == null) {
                        if (u8(i) != 0xFF) { i++; continue }
                        val marker = u8(i + 1)
                        if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                            size = be16(i + 7) to be16(i + 5)
                        } else {
                            i += 2 + be16(i + 2)
                        }
                    }
                    size
                }
                else -> null
            }
        }

        val problems = mutableListOf<String>()
        resDir.asFile.listFiles { d -> d.isDirectory && d.name.startsWith("drawable") }
            .orEmpty().sortedBy { it.name }.forEach { dir ->
                val qualifiers = dir.name.split("-").drop(1)
                val noScaling = "nodpi" in qualifiers || "anydpi" in qualifiers
                val folderDpi = qualifiers.firstNotNullOfOrNull { densities[it] }
                dir.listFiles().orEmpty().sortedBy { it.name }.forEach { f ->
                    val (w, h) = imageSize(f) ?: return@forEach
                    val name = "${dir.name}/${f.name} (${w}x$h)"
                    if (folderDpi == null && !noScaling && maxOf(w, h) > maxUnqualifiedSidePx) {
                        problems += "$name is in a folder with no density, so it is decoded at " +
                            "${w * deviceDpi / 160}x${h * deviceDpi / 160} on our TVs. " +
                            "Move it to the folder for its pixels per dp: drawable-xhdpi/ " +
                            "at 2 (1080p art), drawable-xxxhdpi/ at 4."
                    }
                    val scale = if (noScaling) 1.0 else deviceDpi.toDouble() / (folderDpi ?: 160)
                    val dw = (w * scale).toInt()
                    val dh = (h * scale).toInt()
                    if (maxOf(dw, dh) > maxTextureSidePx) {
                        problems += "$name decodes to ${dw}x$dh at $deviceDpi dpi, over the " +
                            "$maxTextureSidePx px GPU limit, so Android 7 will not draw it."
                    }
                }
            }
        if (problems.isNotEmpty()) {
            throw GradleException(
                "Drawable size check failed:\n" + problems.joinToString("\n") { "  - $it" }
            )
        }
    }
}

tasks.named("preBuild") {
    dependsOn(checkDrawableSizes)
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.androidx.baselineprofile)
}


// ── Version source of truth ──────────────────────────────────────────────
// versionCode/versionName come from app/version.properties (checked in), NOT
// from the build clock. This makes the version a deliberate, monotonic release
// act: bump version.properties, rebuild, ship. Both Gradle and the future #7
// upload tooling read this same file, so the APK's stamped versionCode and the
// server manifest's latest_version_code can never drift. Fail loud if it's
// missing or malformed — a silent fallback would reintroduce the very
// non-determinism we're removing.
val versionProps = Properties().apply {
    val f = file("version.properties")
    require(f.exists()) { "version.properties missing at ${f.absolutePath}" }
    f.inputStream().use { load(it) }
}
val appVersionCode = (versionProps.getProperty("versionCode")
    ?: error("versionCode missing from version.properties")).trim().toInt()
val appVersionName = (versionProps.getProperty("versionName")
    ?: error("versionName missing from version.properties")).trim()

// ── Release signing ──────────────────────────────────────────────────────
// Credentials live in keystore.properties at the project root — GITIGNORED,
// never committed. The keystore file itself lives OUTSIDE the repo entirely.
// If keystore.properties is absent the signingConfig stays unconfigured and
// assembleRelease fails at signing — exactly the behavior we want. A
// debug-signed APK on the update server would permanently weld the install
// base to a throwaway key.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.example.bluehive"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bluehive.tv"
        minSdk = 25
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Default for the ${netSecConfig} placeholder in AndroidManifest.xml.
        // MUST exist here: a placeholder with no value fails the manifest merge
        // for EVERY variant that does not set one, so debug and any future build
        // type would stop building the moment release started overriding it.
        //
        // The permissive file is the safe default because it is the one that can
        // only ever be too lenient on a LAN address. The release block overrides
        // it to the strict config as soon as all three backend URLs are https.
        manifestPlaceholders["netSecConfig"] = "network_security_config"

        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters.clear()
            abiFilters += listOf("armeabi-v7a")
        }
    }

    signingConfigs {
        create("release") {
            val storePath = keystoreProps.getProperty("RELEASE_STORE_FILE")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = keystoreProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = keystoreProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = keystoreProps.getProperty("RELEASE_KEY_PASSWORD")
            }
            // No fallback. If keystore.properties is missing, this config stays
            // unconfigured and assembleRelease fails at signing — exactly the
            // behavior we want. A debug-signed release APK must never exist.
        }
    }

    buildTypes {
        debug {
            val devBaseUrl = project.findProperty("BLUEHIVE_DEV_BASE_URL")?.toString()
                ?: "http://192.168.1.136:8000/"
            val devApiKey = project.findProperty("BLUEHIVE_DEV_API_KEY")?.toString()
                ?: ""
            val devPlatformUrl = project.findProperty("BLUEHIVE_DEV_PLATFORM_URL")?.toString()
                ?: "http://192.168.1.136:9000"
            val devUpdateUrl = project.findProperty("BLUEHIVE_DEV_UPDATE_URL")?.toString()
                ?: "http://192.168.1.136:8090"

            buildConfigField("String", "API_BASE_URL", "\"$devBaseUrl\"")
            buildConfigField("String", "API_KEY", "\"$devApiKey\"")
            buildConfigField("String", "PLATFORM_BASE_URL", "\"$devPlatformUrl\"")
            // Self-update manifest host (#7). Public, unauthenticated, identity-
            // independent — see bluehive-updates container. Swapping to
            // https://updates.<domain> at prod time is the ONLY change needed.
            buildConfigField("String", "UPDATE_BASE_URL", "\"$devUpdateUrl\"")

            isMinifyEnabled = false
            isShrinkResources = false

            ndk {
                //noinspection ChromeOsAbiSupport
                abiFilters.clear()
                abiFilters += listOf("armeabi-v7a", "x86_64")
            }
        }



        release {

            // PROD-first, DEV as the fallback. Nothing changes until a
            // BLUEHIVE_PROD_* property exists, so release builds keep pointing at
            // the LAN box exactly as before — but the cutover is then just adding
            //   BLUEHIVE_PROD_BASE_URL=https://api.<domain>/
            // to gradle.properties, with no edit to this file.
            //
            // https matters beyond the cert: the release network-security-config
            // sets cleartextTrafficPermitted=false, so an http:// prod URL would
            // be refused by the platform, not merely insecure. And HTTP/3 needs
            // TLS — see the h3 block in the backend Caddyfile.
            fun prop(name: String): String? =
                project.findProperty("BLUEHIVE_PROD_$name")?.toString()
                    ?: project.findProperty("BLUEHIVE_DEV_$name")?.toString()

            val prodBaseUrl     = prop("BASE_URL")     ?: "http://192.168.1.136:8000/"
            val prodApiKey      = prop("API_KEY")      ?: ""
            val prodPlatformUrl = prop("PLATFORM_URL") ?: "http://192.168.1.136:9000"
            val prodUpdateUrl   = prop("UPDATE_URL")   ?: "http://192.168.1.136:8090"

            buildConfigField("String", "API_BASE_URL", "\"$prodBaseUrl\"")
            buildConfigField("String", "API_KEY", "\"$prodApiKey\"")
            buildConfigField("String", "PLATFORM_BASE_URL", "\"$prodPlatformUrl\"")
            // Same dev IP for now — see comment in debug block. The prod cutover
            // is a one-line change here, nothing else in the app touches this.
            buildConfigField("String", "UPDATE_BASE_URL", "\"$prodUpdateUrl\"")

            // ── Network security config, DERIVED from the URLs above ──────────
            // Deliberately not a separate -P flag. A flag is a second thing to
            // remember, and the two would drift: someone sets the https URLs,
            // forgets the flag, and ships a public APK still carrying a standing
            // permission to speak plaintext to 192.168.1.136. Reading the scheme
            // means the security posture cannot disagree with the destination.
            //
            //   all three https  -> network_security_config_public  (NO cleartext
            //                       exceptions at all; the platform refuses any
            //                       http:// the app attempts)
            //   anything http    -> network_security_config         (keeps the
            //                       192.168.1.136 exception so LAN builds work)
            //
            // The strict config is what makes a missed https:// fail LOUDLY —
            // "Cleartext HTTP traffic to <host> not permitted", naming the host —
            // instead of silently shipping tokens over the open internet.
            val allHttps = listOf(prodBaseUrl, prodPlatformUrl, prodUpdateUrl)
                .all { it.startsWith("https://") }
            manifestPlaceholders["netSecConfig"] =
                if (allHttps) "network_security_config_public" else "network_security_config"
            println(
                "BlueHive release: netSecConfig=" +
                    manifestPlaceholders["netSecConfig"] +
                    (if (allHttps) " (public, cleartext BLOCKED)"
                     else " (LAN, cleartext allowed to 192.168.1.136)")
            )

            // ENABLED 2026-08-12. This was false, which quietly disabled the whole
            // proguard-rules.pro file below — including the -assumenosideeffects
            // block that strips android.util.Log. Every Log.d/i/w/e call, and the
            // strings they build, shipped in the release APK, and the diagnostics
            // "Send Logs" feature then uploaded that output to the backend.
            //
            // Minification also raises the cost of patching the APK. It does not
            // make it impossible — client-side checks are presentation, server-side
            // is enforcement — but there is no reason to ship readable class names.
            //
            // IF A RELEASE BUILD MISBEHAVES AND DEBUG DOES NOT, LOOK HERE FIRST.
            // R8 breaks reflection-based code SILENTLY: Gson fields come back null
            // rather than throwing, and a renamed AIDL interface simply fails to
            // bind. Keep rules for both are in proguard-rules.pro — io.companion.host
            // is a published contract and must never be obfuscated.
            isMinifyEnabled = true

            // Left OFF deliberately. Resource shrinking removes drawables R8 cannot
            // see referenced, breaking anything resolved by name at runtime
            // (getIdentifier, painterResource behind a variable). The APK is ~198MB
            // and nearly all of that is cronet's embedded native libraries, which
            // resource shrinking does not touch — so it buys little here while adding
            // a second variable to any regression. Revisit once minification has been
            // running cleanly for a while.
            isShrinkResources = false

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            signingConfig = signingConfigs.getByName("release")

            // armeabi-v7a ONLY for shipped builds.
            //
            // Every ARM Android TV device runs v7a, including 64-bit ones, so this
            // is the universal choice — while x86_64 only serves emulators and a
            // handful of x86 boxes. It cost nothing until cronet-embedded arrived;
            // that ships a full Chromium network stack per ABI, which took the
            // release APK from ~30MB to 204MB. Dropping the unused ABI gives most
            // of that back. Re-add x86_64 only if you actually ship to x86.
            ndk {
                abiFilters.clear()
                abiFilters += listOf("armeabi-v7a")
            }
        }





    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
        aidl = true   // ← AGP 8+ disables AIDL by default; the host contract needs it
    }
}

kotlin {
    jvmToolchain(17)

    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// ── Workaround: AGP 9.3.1 + Kotlin 2.3.0 + minification ───────────────────────
//
// Turning on isMinifyEnabled activates AGP's `produceReleaseComposeMapping` task,
// which improves Compose frame names in R8 stack traces. That task resolves
// org.jetbrains.kotlin:compose-group-mapping at AGP's OWN built-in Kotlin version
// (2.2.10, via android.builtInKotlin=true in gradle.properties) rather than the
// project's — and that artifact was never published at 2.2.10.
//
// Verified 2026-08-12 by fetching the POMs directly:
//     2.2.10  Maven Central 404   Google maven 404
//     2.3.0   Maven Central 200   <- the version this project actually uses
//
// So the build failed with "Could not find org.jetbrains.kotlin:compose-group-
// mapping:2.2.10" the moment minification was switched on. Known-bad interaction
// with Kotlin 2.3 — see JetBrains KT-83266.
//
// Pinning the request to the project's Kotlin version resolves it. Scoped to that
// single configuration so no other dependency resolution is affected, and read
// from the version catalog so it follows any future Kotlin bump automatically.
configurations.matching { it.name.contains("composeMappingProducer") }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin" && requested.name == "compose-group-mapping") {
            useVersion(libs.versions.kotlin.get())
        }
    }
}

// ── Release APK through a TV's own bytecode verifier ─────────────────────────
//
// v371-v375 compiled, installed and launched fine, then crashed the moment a TV
// show opened or a movie source was pressed: R8 mis-allocated registers in the
// huge TVShowsDetailsScreenContent and ART rejected the whole class with a
// VerifyError. Nothing in the build or the install reports that. So right after
// assembleRelease, the APK is pushed to a connected TV and run through its own
// dex2oat in verify mode (verify-on-device.sh, ~20 s). Any rejected method fails
// the build and renames the APK, so it cannot be installed by mistake.
//
// No TV connected: a warning, not a failure (publish-release.ps1 runs the same
// check and refuses to publish). -PverifyDevice=<serial> picks the TV (default
// the onn box; it needs Android 10+), -PskipDeviceVerify skips it.
val verifyReleaseOnDevice by tasks.registering {
    val apk = layout.buildDirectory.file("outputs/apk/release/app-release.apk")
    val script = layout.projectDirectory.file("verify-on-device.sh")
    val scratch = layout.buildDirectory.dir("tmp/verifyReleaseOnDevice")
    val adbExe = androidComponents.sdkComponents.adb
    // project., because inside this block a bare hasProperty is the TASK's own,
    // which never sees -P flags: -PskipDeviceVerify was ignored until 2026-10-25
    // and the check ran on the TV anyway.
    val wanted = (project.findProperty("verifyDevice") as String?) ?: "192.168.1.74:5555"
    val skip = project.hasProperty("skipDeviceVerify")

    doLast {
        if (skip) {
            logger.warn("verifyReleaseOnDevice: SKIPPED (-PskipDeviceVerify) - this APK was not checked for VerifyErrors")
            return@doLast
        }
        val adbPath = adbExe.get().asFile.absolutePath
        fun adb(vararg args: String): String {
            val p = ProcessBuilder(adbPath, *args).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            return out
        }

        val devices = adb("devices").lines()
            .mapNotNull { Regex("""^(\S+)\s+device$""").find(it.trim())?.groupValues?.get(1) }
        val dev = if (wanted in devices) wanted else devices.firstOrNull()
        if (dev == null) {
            logger.warn("verifyReleaseOnDevice: no TV connected over adb - this APK was NOT checked " +
                "for VerifyErrors. publish-release.ps1 checks it again before publishing.")
            return@doLast
        }

        // LF only: the TV's sh chokes on the CRLF a Windows checkout may give it.
        val sh = scratch.get().file("bh-verify.sh").asFile
        sh.parentFile.mkdirs()
        sh.writeText(script.asFile.readText().replace("\r\n", "\n"))

        val apkFile = apk.get().asFile
        logger.lifecycle("verifyReleaseOnDevice: checking every class on $dev (~20 s)")
        adb("-s", dev, "push", apkFile.absolutePath, "/data/local/tmp/bh-verify.apk")
        adb("-s", dev, "push", sh.absolutePath, "/data/local/tmp/bh-verify.sh")
        val out = adb("-s", dev, "shell", "sh", "/data/local/tmp/bh-verify.sh")

        val m = Regex("""RESULT rc=(\d+) classes=(\d+) failed=(\d+)""").find(out)
        if (m == null) {
            logger.warn("verifyReleaseOnDevice: $dev gave no usable result - this APK was NOT checked:\n$out")
            return@doLast
        }
        val classes = m.groupValues[2].toInt()
        val failed = m.groupValues[3].toInt()
        if (failed > 0) {
            val rejected = File(apkFile.parentFile, "app-release.REJECTED-by-verifier.apk")
            rejected.delete()
            apkFile.renameTo(rejected)
            throw GradleException(
                "Android's verifier on $dev REJECTS $failed method(s) - this APK would crash on every TV " +
                    "that reaches them. Renamed it to ${rejected.name} so it cannot be installed.\n" +
                    out.lines().filter { it.isNotBlank() && !it.startsWith("RESULT") }.joinToString("\n") { "  $it" }
            )
        }
        if (classes < 1000) {
            logger.warn("verifyReleaseOnDevice: $dev only reported $classes classes - could not confirm " +
                "this APK verifies (use the onn box, 192.168.1.74:5555)")
            return@doLast
        }
        logger.lifecycle("verifyReleaseOnDevice: $classes classes, 0 failures on $dev")
    }
}

// ── Details screens: no giant functions ──────────────────────────────────────
//
// R8 has miscompiled TVShowsDetailsScreenContent twice (2026-09-21 and
// v371-v375): one composable of 11,297 dex code units and 287 registers, and
// what R8 got wrong both times was register allocation across a method that
// size. The redesign split it (docs/details-redesign-architecture.md); this
// keeps it split. After every release build the dex is measured, and any
// method from the details screens over 2,500 code units or 100 registers fails
// the build. The rest of the app is reported, not enforced.
//
// Until Stage 10 the functions being split had ratchet ceilings of their own;
// the sizes after each stage are in docs/details-redesign-baseline.md. The
// budget keeps every details method far under ART's 10,000-unit huge-method
// limit, above which ART never compiles a method and runs it interpreted.
//
// The Favorites and Home redesign (docs/favorites-home-redesign-architecture.md)
// brings the ratchet back for the app's last two giants, FavoritesScreen and
// HomeScreenComposeContent: they may not grow, and each ceiling drops after
// every stage until the function is under the budget and leaves the list. The
// ceilings sit about 1% (code units) and 2.5% (registers) above the size
// measured at the last stage, for the reason below. Since Stage F1 the budget
// also holds for everything else written in favorites/.
//
// R8 re-decides inlining across the whole app on every build, so a function
// can grow a little with its source unchanged (Stage 1: +42 units when five
// moved helpers were inlined back). Code moved out of a composable comes back
// in when it lands in a non-restartable function with one caller, such as a
// remember... function or a plain helper. Put moved effects in a restartable
// child composable, called where they were, as Stage 10 did.
//
// Each method is matched to the class it was WRITTEN in through R8's mapping
// file. R8 relocates static methods freely (FavoritesScreen has shipped inside
// Rhino's org.mozilla.javascript.Kit, and in v405 inside kotlin.reflect.KClasses
// next to details code), so the class it lands in proves nothing.
val checkMethodSizes by tasks.registering {
    val apk = layout.buildDirectory.file("outputs/apk/release/app-release.apk")
    val mapping = layout.buildDirectory.file("outputs/mapping/release/mapping.txt")
    val scratch = layout.buildDirectory.dir("tmp/checkMethodSizes")
    val sdkDir = androidComponents.sdkComponents.sdkDirectory

    doLast {
        val maxUnits = 2_500
        val maxRegisters = 100
        // App methods at least this big carry the same R8 risk: reported, not enforced.
        val warnUnits = 5_000
        // Being split by the Favorites and Home redesign (units to registers).
        // At Stage 0 (v405): FavoritesScreen 5,660 / 209, HomeScreenComposeContent
        // 5,030 / 118. FavoritesScreen shrank to 4,977 / 141 at F1 (v406) and
        // 3,837 / 95 at F2 (v408); since F3 (v409) it is under the budget and
        // off this list.
        val shrinking = mapOf(
            // HomeScreenComposeContent until H1 moved it into home/ as HomeScreen;
            // 4,153 / 99 at H1 (v410), 3,012 / 67 at H2 (v412).
            "HomeScreenKt.HomeScreen" to (3_040 to 69),
        )

        val apkFile = apk.get().asFile
        if (!apkFile.exists()) {
            logger.warn("checkMethodSizes: no release APK at $apkFile - not checked")
            return@doLast
        }
        val tools = File(sdkDir.get().asFile, "build-tools").listFiles().orEmpty()
            .filter { File(it, "dexdump.exe").exists() || File(it, "dexdump").exists() }
            .maxByOrNull { dir -> dir.name.split('.').joinToString("") { it.padStart(5, '0') } }
            ?: throw GradleException("checkMethodSizes: no dexdump under ${sdkDir.get().asFile}/build-tools")
        val dexdump = File(tools, if (File(tools, "dexdump.exe").exists()) "dexdump.exe" else "dexdump")

        // "residual.Class.method" -> the class it was written in. A member line
        // whose original name differs from the residual name is an inlined
        // frame, not the method itself, and is skipped.
        val owner = HashMap<String, String>()
        val mapFile = mapping.get().asFile
        if (mapFile.exists()) {
            var headerOrig = ""
            var headerResidual = ""
            mapFile.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.isEmpty() || line[0] == '#') return@forEach
                    if (line[0] != ' ') {
                        val arrow = line.indexOf(" -> ")
                        if (arrow > 0 && line.endsWith(":")) {
                            headerOrig = line.substring(0, arrow)
                            headerResidual = line.substring(arrow + 4, line.length - 1)
                        }
                        return@forEach
                    }
                    val arrow = line.lastIndexOf(" -> ")
                    val paren = line.indexOf('(')
                    if (arrow < 0 || paren < 0 || paren > arrow) return@forEach
                    val residualName = line.substring(arrow + 4)
                    val qualified = line.substring(line.lastIndexOf(' ', paren) + 1, paren)
                    val dot = qualified.lastIndexOf('.')
                    if ((if (dot >= 0) qualified.substring(dot + 1) else qualified) != residualName) return@forEach
                    owner.getOrPut("$headerResidual.$residualName") {
                        if (dot >= 0) qualified.substring(0, dot) else headerOrig
                    }
                }
            }
        } else {
            logger.warn("checkMethodSizes: no R8 mapping file - methods are matched by the class they land in")
        }

        fun inDetails(origin: String): Boolean {
            val simple = origin.substringAfterLast('.')
            return origin.startsWith("com.example.bluehive.details.") ||
                simple.startsWith("TVShowsDetailsScreenCompose") ||
                simple.startsWith("MoviesDetailsScreenCompose")
        }
        fun inBudget(origin: String): Boolean =
            inDetails(origin) || origin.startsWith("com.example.bluehive.favorites.") ||
                origin.startsWith("com.example.bluehive.home.")

        data class Method(val key: String, val origin: String, val units: Int, val registers: Int)
        val methods = ArrayList<Method>()
        val dexDir = scratch.get().asFile.apply { deleteRecursively(); mkdirs() }
        ZipFile(apkFile).use { zip ->
            zip.entries().asSequence().filter { Regex("""classes\d*\.dex""").matches(it.name) }.forEach { entry ->
                File(dexDir, entry.name).outputStream().use { out -> zip.getInputStream(entry).use { it.copyTo(out) } }
            }
        }
        dexDir.listFiles().orEmpty().filter { it.extension == "dex" }.sortedBy { it.name }.forEach { dex ->
            val p = ProcessBuilder(dexdump.absolutePath, dex.absolutePath)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            var cls = ""
            var name = ""
            var registers = -1
            p.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { raw ->
                    val t = raw.trimStart()
                    when {
                        t.startsWith("Class descriptor") ->
                            cls = t.substringAfter("'L").substringBefore(";'").replace('/', '.')
                        t.startsWith("name ") -> {
                            name = t.substringAfter("'").substringBeforeLast("'")
                            registers = -1
                        }
                        t.startsWith("registers ") -> registers = t.substringAfter(':').trim().toIntOrNull() ?: -1
                        t.startsWith("insns size ") && registers >= 0 -> {
                            val units = t.substringAfter(':').trim().substringBefore(' ').toIntOrNull() ?: 0
                            val origin = owner["$cls.$name"] ?: cls
                            if (units >= 1_000 || inBudget(origin)) {
                                methods += Method("${origin.substringAfterLast('.')}.$name", origin, units, registers)
                            }
                        }
                    }
                }
            }
            p.waitFor()
        }

        if (methods.none { inDetails(it.origin) }) throw GradleException("checkMethodSizes: found no details-screen methods - the dex parse is broken")
        val budgeted = methods.filter { inBudget(it.origin) && it.key !in shrinking }
        val problems = mutableListOf<String>()
        for (m in budgeted) {
            if (m.units > maxUnits || m.registers > maxRegisters) {
                problems += "${m.key} is ${m.units} units / ${m.registers} registers " +
                    "(budget $maxUnits / $maxRegisters). Split it -- see the note above checkMethodSizes."
            }
        }
        val split = methods.filter { it.key in shrinking }
        for (m in split) {
            val (units, registers) = shrinking.getValue(m.key)
            if (m.units > units || m.registers > registers) {
                problems += "${m.key} grew to ${m.units} units / ${m.registers} registers " +
                    "(ceiling $units / $registers). It is being split: put new code in a new composable."
            }
        }
        val gone = shrinking.keys - split.map { it.key }.toSet()
        if (gone.isNotEmpty()) {
            logger.lifecycle("checkMethodSizes: no longer in the APK, take off the shrinking list: ${gone.joinToString()}")
        }
        logger.lifecycle("checkMethodSizes: largest methods in details/ and favorites/ (budget $maxUnits units / $maxRegisters registers):")
        budgeted.sortedByDescending { it.units }.take(5).forEach { m ->
            logger.lifecycle("  %6d units %4d regs  %s".format(m.units, m.registers, m.key))
        }
        if (split.isNotEmpty()) {
            logger.lifecycle("checkMethodSizes: being split (docs/favorites-home-redesign-architecture.md):")
            split.sortedByDescending { it.units }.forEach { m ->
                val (units, registers) = shrinking.getValue(m.key)
                logger.lifecycle("  %6d units %4d regs  %s  [ceiling %d / %d]".format(m.units, m.registers, m.key, units, registers))
            }
        }
        val giants = methods.filter {
            !inBudget(it.origin) && it.key !in shrinking &&
                it.origin.startsWith("com.example.bluehive.") && it.units >= warnUnits
        }
        if (giants.isNotEmpty()) {
            logger.warn("checkMethodSizes: other app methods over $warnUnits units (same R8 risk, not enforced):")
            giants.sortedByDescending { it.units }.forEach {
                logger.warn("  %6d units %4d regs  %s".format(it.units, it.registers, it.key))
            }
        }
        if (problems.isNotEmpty()) {
            throw GradleException("Method size check failed:\n" + problems.joinToString("\n") { "  - $it" })
        }
    }
}

// The cheap local check first, then the one that needs a TV.
verifyReleaseOnDevice.configure { mustRunAfter(checkMethodSizes) }

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(checkMethodSizes, verifyReleaseOnDevice)
}

// ── Details and Favorites screens: who may use whom ──────────────────────────
//
// The details redesign (docs/details-redesign-architecture.md §5) splits the
// two screens into details/moviesdetails, details/tvshowdetails and
// details/shared. The walls are the point: in v371-v375 the movie screen
// called into the TV file, so when ART rejected the TV class, movie playback
// died with it. Kotlin's `internal` covers the whole app module and cannot
// enforce this, so it is checked on every build:
//   - moviesdetails never uses tvshowdetails, and the other way round;
//   - details/shared uses neither screen;
//   - code outside details/ reaches a screen only through its Activity.
// The Favorites and Home redesign (docs/favorites-home-redesign-architecture.md
// §8) walls in favorites/ the same way, from Stage F1:
//   - code outside favorites/ reaches it only through FavoritesScreenActivity
//     and openFavoritesScreen;
//   - favorites/ uses no details code but the two Activities and the launch
//     types DetailsExtra and putDetailsExtras, and nothing in home/.
// and home/ from Stage H1: outside it, only HomeScreenCompose.kt (its
// Activity) uses it, and only HomeScreen. Like all code outside details/ and
// favorites/, home/ reaches those only through their entry points.
// Whole-line comments are skipped. Anything else that names a forbidden
// package fails, including a trailing comment -- reword the comment.
val checkDetailsBoundaries by tasks.registering {
    val javaDir = layout.projectDirectory.dir("src/main/java")
    inputs.files(fileTree(javaDir) { include("**/*.kt") })

    doLast {
        val appDir = javaDir.dir("com/example/bluehive").asFile
        val entryPoints = setOf(
            "com.example.bluehive.details.moviesdetails.MoviesDetailsScreenCompose",
            "com.example.bluehive.details.tvshowdetails.TVShowsDetailsScreenCompose",
        )
        val screenRef = Regex("""com\.example\.bluehive\.details\.(moviesdetails|tvshowdetails)((?:\.[A-Za-z_][A-Za-z0-9_]*)*)(\.\*)?""")
        val favoritesEntryPoints = setOf(
            "com.example.bluehive.favorites.FavoritesScreenActivity",
            "com.example.bluehive.favorites.openFavoritesScreen",
        )
        val favoritesLaunchTypes = setOf(
            "com.example.bluehive.details.shared.navigation.DetailsExtra",
            "com.example.bluehive.details.shared.navigation.putDetailsExtras",
        )
        val favoritesRef = Regex("""com\.example\.bluehive\.favorites((?:\.[A-Za-z_][A-Za-z0-9_]*)*)(\.\*)?""")
        val sharedRef = Regex("""com\.example\.bluehive\.details\.shared((?:\.[A-Za-z_][A-Za-z0-9_]*)*)(\.\*)?""")
        val homeRef = Regex("""com\.example\.bluehive\.home\b""")
        val homeFullRef = Regex("""com\.example\.bluehive\.home\b((?:\.[A-Za-z_][A-Za-z0-9_]*)*)(\.\*)?""")
        val homeEntry = "com.example.bluehive.home.HomeScreen"
        fun through(m: MatchResult, allowed: Set<String>) =
            m.groupValues[2].isEmpty() && allowed.any { m.value == it || m.value.startsWith("$it.") }
        val problems = mutableListOf<String>()
        appDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val rel = file.relativeTo(appDir).invariantSeparatorsPath
            val area = when {
                rel.startsWith("details/moviesdetails/") -> "moviesdetails"
                rel.startsWith("details/tvshowdetails/") -> "tvshowdetails"
                rel.startsWith("details/") -> "shared"
                rel.startsWith("favorites/") -> "favorites"
                rel.startsWith("home/") -> "home"
                else -> "outside"
            }
            file.readLines().forEachIndexed { index, raw ->
                val line = raw.trim()
                if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) return@forEachIndexed
                screenRef.findAll(raw).forEach { m ->
                    val screen = m.groupValues[1]
                    val allowed = when (area) {
                        "moviesdetails", "tvshowdetails" -> screen == area
                        "shared" -> false
                        else -> m.groupValues[3].isEmpty() &&
                            entryPoints.any { m.value == it || m.value.startsWith("$it.") }
                    }
                    if (!allowed) problems += "$rel:${index + 1}: $area code must not use ${m.value}"
                }
                if (area == "favorites") {
                    sharedRef.findAll(raw).filterNot { through(it, favoritesLaunchTypes) }.forEach { m ->
                        problems += "$rel:${index + 1}: favorites code must not use ${m.value}"
                    }
                    homeRef.findAll(raw).forEach { m ->
                        problems += "$rel:${index + 1}: favorites code must not use ${m.value}"
                    }
                } else {
                    favoritesRef.findAll(raw).filterNot { through(it, favoritesEntryPoints) }.forEach { m ->
                        problems += "$rel:${index + 1}: $area code must not use ${m.value}"
                    }
                }
                if (area != "home" && area != "favorites") {
                    homeFullRef.findAll(raw).filterNot {
                        rel == "HomeScreenCompose.kt" && it.groupValues[2].isEmpty() && it.value == homeEntry
                    }.forEach { m ->
                        problems += "$rel:${index + 1}: $area code must not use ${m.value} (only HomeScreenCompose, and only HomeScreen)"
                    }
                }
            }
        }
        if (problems.isNotEmpty()) {
            throw GradleException("Boundary check failed:\n" + problems.joinToString("\n") { "  - $it" })
        }
    }
}

tasks.named("preBuild") {
    dependsOn(checkDetailsBoundaries)
}

dependencies {

    // ═══════════════════════════════════════════════════════════
    // BASELINE PROFILE
    // ═══════════════════════════════════════════════════════════
    implementation(libs.androidx.profileinstaller)        // applies the profile at install/first-run
    baselineProfile(project(":baselineprofile"))          // consumes the generated profile

    implementation("androidx.media3:media3-datasource-okhttp:1.9.0")

    implementation("com.github.exjunk:ThanosEffect:1.0.2")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation(libs.androidx.animation.core)
    implementation(libs.androidx.ui.unit)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.ui.test)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.foundation.layout)
    implementation(libs.androidx.runtime)
    implementation(libs.foundation)
    implementation(libs.foundation.layout)
    implementation(libs.androidx.compose.foundation.foundation)
    implementation(libs.androidx.compose.foundation)

    // ═══════════════════════════════════════════════════════════
    // JETPACK COMPOSE
    // ═══════════════════════════════════════════════════════════
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.12.2")

    implementation("androidx.tv:tv-foundation:1.0.0-alpha10")
    implementation("androidx.tv:tv-material:1.0.0-alpha10")

    // coil-compose removed from here — it lives in the IMAGE LOADING block below
    // with the rest of the Coil artifacts so all versions stay in sync.

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")



    // ═══════════════════════════════════════════════════════════
    // MEDIA3 (ExoPlayer)
    // ═══════════════════════════════════════════════════════════
    val media3Version = "1.9.0"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation(libs.media3.exoplayer.hls)

    implementation("androidx.media3:media3-datasource-okhttp:${media3Version}")
    implementation(libs.okhttp.urlconnection)

    // ── HTTP/3 (QUIC) data source ────────────────────────────────────────────
    // OkHttp cannot speak HTTP/3 at all. Cronet (Chrome's network stack) can,
    // and that is the whole point here: CUJO reads the hostname from the TLS
    // ClientHello on TCP, while QUIC runs over UDP/443 which consumer filters
    // overwhelmingly do not inspect. MEASURED: GeckoView reached vidfast.vc
    // where curl failed 0/12 from the same box, and the CDNs advertise
    // `alt-svc: h3=":443"` — so the browser has a road the player doesn't.
    //
    // cronet-embedded, not play-services-cronet: it adds ~10MB but works even
    // where Play Services Cronet is missing or stale, which on a cheap TV box
    // is not a safe assumption. ExoPlayerConfig falls back to OkHttp if the
    // engine fails to build, so this can only add a path, never remove one.
    implementation("androidx.media3:media3-datasource-cronet:${media3Version}")
    implementation("org.chromium.net:cronet-embedded:119.6045.31")


    // ═══════════════════════════════════════════════════════════
    // YOUTUBE EXTRACTION
    // ═══════════════════════════════════════════════════════════
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.25.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")

    // ═══════════════════════════════════════════════════════════
    // IMAGE LOADING — all Coil artifacts from the same source
    // ═══════════════════════════════════════════════════════════
    // Previously coil-compose was ALSO declared in the Compose section
    // above as "io.coil-kt:coil-compose:2.7.0". Having two declarations
    // of the same artifact at potentially different versions causes D8 to
    // generate SAM-bridge lambdas (ExternalSyntheticLambda) that are
    // incompatible at runtime, producing the ClassCastException in
    // BlueHiveApplication.onCreate(). Single source of truth here.
    implementation(libs.coil)
    implementation(libs.coil.video)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    // ═══════════════════════════════════════════════════════════
    // NETWORKING
    // ═══════════════════════════════════════════════════════════
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    // DNS-over-HTTPS for stream fetches — see ExoPlayerConfig.okHttpClient().
    implementation(libs.okhttp.dnsoverhttps)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)

    // ═══════════════════════════════════════════════════════════
    // LIFECYCLE & COROUTINES
    // ═══════════════════════════════════════════════════════════
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-process:2.10.0")

    // ═══════════════════════════════════════════════════════════
    // ANDROID CORE
    // ═══════════════════════════════════════════════════════════
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.leanback)

    // ═══════════════════════════════════════════════════════════
    // MISC
    // ═══════════════════════════════════════════════════════════
    implementation(libs.common)
    implementation(libs.app.update.ktx)
    implementation(libs.androidx.viewfinder.core)

    // ═══════════════════════════════════════════════════════════
    // TESTING
    // ═══════════════════════════════════════════════════════════
    testImplementation(libs.junit)
    // The real org.json for JVM tests. android.jar's copy is a stub that throws,
    // which would make every JSON-reading helper look like it returns null.
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
