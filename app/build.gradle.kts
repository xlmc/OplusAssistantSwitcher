import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    id("com.android.application")
}

// versionName / versionCode 默认值用于开发与 CI 测试构建；
// Release 由 release.yml 从 Git Tag 推导并显式传入，保证二者永不漂移。
val versionNameProp = (project.findProperty("versionName") as String?) ?: "0.1.0"
val versionCodeProp = (project.findProperty("versionCode") as String?)?.toInt() ?: 1

fun signingProperty(name: String): String? =
    providers.gradleProperty(name).orNull ?: providers.environmentVariable(name).orNull

val releaseStoreFile = signingProperty("releaseStoreFile")
val releaseStorePassword = signingProperty("releaseStorePassword")
val releaseKeyAlias = signingProperty("releaseKeyAlias")
val releaseKeyPassword = signingProperty("releaseKeyPassword")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.ouhuan.oplusassistant"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.ouhuan.oplusassistant"
        minSdk = 29
        targetSdk = 35
        versionCode = versionCodeProp
        versionName = versionNameProp
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes += "**"
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    applicationVariants.all {
        val baseName = "OplusAssistantSwitcher-v${versionName}"
        outputs.all {
            val output = this as BaseVariantOutputImpl
            output.outputFileName =
                if (name == "release") "$baseName.apk" else "$baseName-debug.apk"
        }
    }
}

dependencies {
    // 现代 Xposed Module API，运行时由 LSPosed 提供
    compileOnly("io.github.libxposed:api:102.0.0")
    // App 侧与 LSPosed 管理器通信（Remote Preferences 写入、框架信息）
    implementation("io.github.libxposed:service:102.0.0")

    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.room:room-runtime:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
}
