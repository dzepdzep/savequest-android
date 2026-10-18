plugins { id("com.android.library"); kotlin("android"); kotlin("kapt") }
android { namespace = "dev.savequest.storage"; compileSdk = 36; defaultConfig { minSdk = 26 }; compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 } }
kotlin { jvmToolchain(17); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }
dependencies { implementation("androidx.room:room-runtime:2.7.2"); implementation("androidx.room:room-ktx:2.7.2"); kapt("androidx.room:room-compiler:2.7.2") }

