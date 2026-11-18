import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.File

plugins {
    kotlin("jvm") version "2.1.21"
    id("org.jetbrains.compose") version "1.8.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
}

group = "app.lostfilm"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
    implementation("org.json:json:20240303")
    implementation("com.github.kwhat:jnativehook:2.2.2")
    implementation("net.java.dev.jna:jna:5.14.0")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

compose.desktop {
    application {
        mainClass = "app.lostfilm.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "LostFilmDesktop"
            packageVersion = "1.0.0"
            description = "Unofficial LostFilm.TV desktop client"
            vendor = "LostFilm Desktop"
            modules("java.net.http", "jdk.crypto.ec")

            windows {
                iconFile.set(project.file("src/main/resources/icons/app.ico"))
            }
        }
    }
}

tasks.test {
    useJUnit()
}

val portableRoot = layout.buildDirectory.dir("portable")
val portableAppDir = portableRoot.map { it.dir("LostFilmDesktop") }

val portable by tasks.registering(Copy::class) {
    dependsOn("createDistributable")
    doFirst {
        delete(File(portableAppDir.get().asFile, "app"))
    }
    from(layout.buildDirectory.dir("compose/binaries/main/app/LostFilmDesktop"))
    from("tools/torrserver/TorrServer.exe") {
        into("torrserver")
    }
    into(portableAppDir)
    doLast {
        File(portableAppDir.get().asFile, "portable.marker").writeText("portable")
    }
}

val portableZip by tasks.registering(Zip::class) {
    dependsOn(portable)
    from(portableRoot)
    exclude("**/data/**")
    archiveFileName.set("LostFilmDesktop-portable-${project.version}.zip")
    destinationDirectory.set(layout.buildDirectory.dir("dist"))
}

