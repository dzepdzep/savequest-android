plugins { id("com.android.application"); kotlin("android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "dev.savequest.android"; compileSdk = 36
    defaultConfig { applicationId = if (providers.gradleProperty("auditInstall").orNull == "true") "dev.savequest.android.audit" else "dev.savequest.android"; minSdk = 26; targetSdk = 36; versionCode = 10; versionName = "0.1.9-alpha"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(17); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(project(":core")); implementation(project(":games:silver")); implementation(project(":storage"))
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.compose.material3:material3"); implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.room:room-runtime:2.7.2"); implementation("androidx.room:room-ktx:2.7.2")
    implementation("androidx.work:work-runtime-ktx:2.10.3")
    androidTestImplementation("androidx.test.ext:junit:1.3.0"); androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.08.00")); androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

