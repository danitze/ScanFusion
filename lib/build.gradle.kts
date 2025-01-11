import org.jreleaser.model.Active

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
    id("signing")
    alias(libs.plugins.jreleaser)
}

android {
    namespace = "com.danitze.scanfusionlib"
    compileSdk = 34
    version = project.properties["VERSION_NAME"].toString()

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
            withJavadocJar()
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

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = properties["GROUP"].toString()
            artifactId = properties["POM_ARTIFACT_ID"].toString()

            pom {
                name.set(project.properties["POM_NAME"].toString())
                description.set(project.properties["POM_DESCRIPTION"].toString())
                url.set("https://github.com/danitze/ScanFusion")
                issueManagement {
                    url.set("https://github.com/danitze/ScanFusion/issues")
                }

                scm {
                    url.set(project.properties["POM_SCM_URL"].toString())
                    connection.set(project.properties["POM_SCM_CONNECTION"].toString())
                    developerConnection.set(project.properties["POM_SCM_DEV_CONNECTION"].toString())
                }

                licenses {
                    license {
                        name.set(project.properties["POM_LICENSE_NAME"].toString())
                        url.set(project.properties["POM_LICENSE_URL"].toString())
                        distribution.set(project.properties["POM_LICENSE_DIST"].toString())
                    }
                }

                developers {
                    developer {
                        id.set(project.properties["POM_DEVELOPER_ID"].toString())
                        name.set(project.properties["POM_DEVELOPER_NAME"].toString())
                        email.set(project.properties["POM_DEVELOPER_EMAIL"].toString())
                        url.set(project.properties["POM_DEVELOPER_URL"].toString())
                    }
                }

                afterEvaluate {
                    from(components["release"])
                }
            }
        }
    }
    repositories {
        maven {
            setUrl(layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

jreleaser {
    project {
        inceptionYear = "2025"
        author("@danitze")
        //version = properties["VERSION_NAME"].toString()
    }
    gitRootSearch = true
    signing {
        active = Active.ALWAYS
        armored = true
        verify = true
    }
    release {
        github {
            skipTag = true
            sign = true
            branch = "master"
            branchPush = "master"
            overwrite = true
        }
    }
    deploy {
        maven {
            mavenCentral.create("sonatype") {
                active = Active.ALWAYS
                url = "https://central.sonatype.com/api/v1/publisher"
                stagingRepository(layout.buildDirectory.dir("staging-deploy").get().toString())
                setAuthorization("Basic")
                applyMavenCentralRules = false // Wait for fix: https://github.com/kordamp/pomchecker/issues/21
                sign = true
                checksums = true
                sourceJar = true
                javadocJar = true
                retryDelay = 60
            }
        }
    }
}

//apply(from = "../publish-package.gradle.kts")