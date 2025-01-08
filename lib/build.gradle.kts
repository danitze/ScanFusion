import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
}

val githubProperties = Properties()
githubProperties.load(FileInputStream(rootProject.file("github.properties")))

android {
    namespace = "com.danitze.scanfusionlib"
    compileSdk = 34

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = project.findProperty("GROUP")?.toString()
            artifactId = project.findProperty("POM_ARTIFACT_ID")?.toString()
            version = project.findProperty("VERSION_NAME")?.toString()
            artifact("$buildDir/outputs/aar/lib-release.aar")

            val pomName = project.findProperty("POM_NAME")?.toString()
            val pomDescription = project.findProperty("POM_DESCRIPTION")?.toString()
            val pomUrl = project.findProperty("POM_URL")?.toString()
            val licenseName = project.findProperty("POM_LICENSE_NAME")?.toString()
            val licenseUrl = project.findProperty("POM_LICENSE_URL")?.toString()
            val developerId = project.findProperty("POM_DEVELOPER_ID")?.toString()
            val developerName = project.findProperty("POM_DEVELOPER_NAME")?.toString()
            val developerUrl = project.findProperty("POM_DEVELOPER_URL")?.toString()

            pom {
                name.set(pomName)
                description.set(pomDescription)
                url.set(pomUrl)

                licenses {
                    license {
                        name.set(licenseName)
                        url.set(licenseUrl)
                    }
                }

                developers {
                    developer {
                        id.set(developerId)
                        name.set(developerName)
                        url.set(developerUrl)
                    }
                }

                issueManagement {
                    system.set("GitHub")
                    url.set("https://github.com/danitze/ScanFusion/issues")
                }
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/danitze/ScanFusion")

            credentials {
                username = githubProperties["gpr.usr"]?.toString() ?: System.getenv("GPR_USER")
                password = githubProperties["gpr.key"]?.toString() ?: System.getenv("GPR_API_KEY")
            }
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.play.services.mlkit.barcode.scanning)
    implementation(libs.google.zxing)
}