plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

group = "org.connecttag.libs.kotlin"
version = "1.0.7"

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "org.connecttag.services.networkmonitor.android"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.ktx)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
}

afterEvaluate {
    publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["release"])
            groupId = "org.connecttag.libs.kotlin"
            artifactId = "network-monitor-android"
            version = "1.0.7"

            pom {
                name.set("ConnectTag Network Monitor Android")
                description.set("Reactive Android Network and Internet connectivity monitoring via Kotlin Flow and ConnectivityManager callbacks")
                url.set("https://github.com/connecttag/network-monitor-android")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("connecttagye")
                        name.set("Connect Tag")
                        email.set("connecttagye@gmail.com")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/connecttag/network-monitor-android.git")
                    developerConnection.set("scm:git:ssh://git@github.com:connecttag/network-monitor-android.git")
                    url.set("https://github.com/connecttag/network-monitor-android/tree/main")
                }
            }
        }
    }
}
}
