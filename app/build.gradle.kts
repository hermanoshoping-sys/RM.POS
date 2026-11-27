plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.rmpos.x7m4qp"
    minSdk = 24
    targetSdk = 36
    versionCode = 8
    versionName = "8.0"

    val dotEnvValues: Map<String, String> = run {
      val envFile = rootProject.file(".env")
      if (!envFile.isFile) {
        emptyMap()
      } else {
        envFile.readLines()
          .map { it.trim() }
          .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
          .associate { line ->
            val idx = line.indexOf('=')
            val key = line.substring(0, idx).trim()
            val rawVal = line.substring(idx + 1).trim().removeSurrounding("\"").removeSurrounding("'")
            key to rawVal
          }
      }
    }
    fun resolveBuildEnv(name: String): String =
      (providers.environmentVariable(name).orNull ?: dotEnvValues[name] ?: "").trim()

    val escapedTileUrl = resolveBuildEnv("MAP_TILE_URL_TEMPLATE")
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
    val escapedTileAttribution = resolveBuildEnv("MAP_TILE_ATTRIBUTION")
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
    val escapedLicenseServerUrl = resolveBuildEnv("LICENSE_SERVER_URL")
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
    buildConfigField("String", "MAP_TILE_URL_TEMPLATE", "\"$escapedTileUrl\"")
    buildConfigField("String", "MAP_TILE_ATTRIBUTION", "\"$escapedTileAttribution\"")
    buildConfigField("String", "LICENSE_SERVER_URL", "\"$escapedLicenseServerUrl\"")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  val enableReleaseMinify =
    providers.gradleProperty("enableReleaseMinify").orNull.equals("true", ignoreCase = true)

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = enableReleaseMinify
      isShrinkResources = enableReleaseMinify
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  lint {
    checkReleaseBuilds = false
    abortOnError = false
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  packaging {
    jniLibs {
      useLegacyPackaging = false
    }
  }
  sourceSets {
    getByName("test").assets.srcDirs("$projectDir/schemas")
    getByName("androidTest").assets.srcDirs("$projectDir/schemas")
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      all {
        val relPath = ".m2/repository/org/robolectric/android-all-instrumented/16-robolectric-13921718-i7"
        val userHome = System.getProperty("user.home") ?: ""
        val candidateDirs = mutableListOf<File>()
        if (userHome.isNotBlank()) candidateDirs.add(file("$userHome/$relPath"))
        val homeEnv = System.getenv("HOME") ?: ""
        if (homeEnv.isNotBlank()) candidateDirs.add(file("$homeEnv/$relPath"))
        candidateDirs.add(file("/home/appuser/$relPath"))
        val foundDir = candidateDirs.firstOrNull { it.exists() }
        if (foundDir != null) {
          it.systemProperty("robolectric.offline", "true")
          it.systemProperty("robolectric.dependency.dir", foundDir.path)
        }
      }
    }
  }
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

abstract class VerifyReleaseLicenseServerUrlTask : org.gradle.api.DefaultTask() {
  @get:org.gradle.api.tasks.Input
  @get:org.gradle.api.tasks.Optional
  abstract val licenseServerUrl: org.gradle.api.provider.Property<String>

  @org.gradle.api.tasks.TaskAction
  fun verify() {
    val value = licenseServerUrl.orNull?.trim().orEmpty()
    if (value.isEmpty()) {
      throw org.gradle.api.GradleException(
        "Release build aborted: LICENSE_SERVER_URL is empty or unset. " +
          "Set the LICENSE_SERVER_URL environment variable before building release artifacts."
      )
    }
  }
}

val verifyReleaseLicenseServerUrl = tasks.register<VerifyReleaseLicenseServerUrlTask>(
  "verifyReleaseLicenseServerUrl"
) {
  group = "verification"
  description = "Fails release builds if LICENSE_SERVER_URL environment variable or .env entry is empty or unset."
  val dotEnvLicenseUrl = rootProject.file(".env").takeIf { it.isFile }?.readLines()
    ?.map { it.trim() }
    ?.firstOrNull { it.startsWith("LICENSE_SERVER_URL=") }
    ?.substringAfter("=")
    ?.trim()
    ?.removeSurrounding("\"")
    ?.removeSurrounding("'")
    ?: ""
  licenseServerUrl.set(
    providers.environmentVariable("LICENSE_SERVER_URL").orElse(dotEnvLicenseUrl)
  )
}

abstract class VerifyReleaseMapTileProviderTask : org.gradle.api.DefaultTask() {
  @get:org.gradle.api.tasks.Input
  @get:org.gradle.api.tasks.Optional
  abstract val mapTileUrlTemplate: org.gradle.api.provider.Property<String>

  @org.gradle.api.tasks.TaskAction
  fun verify() {
    val value = mapTileUrlTemplate.orNull?.trim().orEmpty()
    if (value.isEmpty()) {
      // Empty defaults at runtime to OpenFreeMap (MIT / commercial-friendly vector tiles via bundled MapLibre GL JS).
      return
    }
    val isPublicOsm = Regex(
      "^https://([a-c]\\.)?tile\\.openstreetmap\\.org(/|$)",
      RegexOption.IGNORE_CASE
    ).containsMatchIn(value)
    if (!value.startsWith("https://", ignoreCase = true) || isPublicOsm) {
      throw org.gradle.api.GradleException(
        "Release build aborted: MAP_TILE_URL_TEMPLATE must be a licensed HTTPS tile provider URL " +
          "(e.g. MapTiler/Mapbox/Stadia) or left empty for OpenFreeMap, and must never point to public OSM tile servers."
      )
    }
  }
}

val verifyReleaseMapTileProvider = tasks.register<VerifyReleaseMapTileProviderTask>(
  "verifyReleaseMapTileProvider"
) {
  group = "verification"
  description = "Fails release builds if MAP_TILE_URL_TEMPLATE is non-HTTPS or points to public OSM tile servers."
  mapTileUrlTemplate.set(providers.environmentVariable("MAP_TILE_URL_TEMPLATE"))
}

val enforceStrictLicenseUrlOnRelease =
  providers.environmentVariable("CI").orNull.equals("true", ignoreCase = true) ||
    providers.gradleProperty("strictLicenseServerCheck").orNull.equals("true", ignoreCase = true)

tasks.matching {
  it.name in setOf(
    "preReleaseBuild",
    "generateReleaseBuildConfig",
    "assembleRelease",
    "bundleRelease"
  )
}.configureEach {
  dependsOn(verifyReleaseMapTileProvider)
  if (enforceStrictLicenseUrlOnRelease) {
    dependsOn(verifyReleaseLicenseServerUrl)
  }
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.graphics.path)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.play.services.code.scanner)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.sqlcipher.android)
  implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.vico.compose)
  implementation(libs.vico.compose.m3)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  // implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.androidx.room.testing)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  // androidTestImplementation(platform(libs.androidx.compose.bom))
  // androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  // androidTestImplementation(libs.androidx.espresso.core)
  // androidTestImplementation(libs.androidx.junit)
  // androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}
