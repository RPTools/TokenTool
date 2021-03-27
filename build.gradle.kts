import java.time.LocalDateTime

plugins {
    application
    id("org.openjfx.javafxplugin") version "0.0.9"
    id("org.beryx.jlink") version "2.23.3"
    id("com.diffplug.spotless") version "5.10.1"
    id("io.wusa.semver-git-plugin") version "2.3.7"
}

// Apply the java plugin to add support for Java
apply(plugin = "base")
apply(plugin = "application")
apply(plugin = "java")
//apply(plugin = "com.diffplug.spotless")

// Definitions
//defaultTasks "clean", "build"

// Used by gradle assemble & run tasks
val mainClassName = "net.rptools.tokentool/net.rptools.tokentool.client.TokenTool"

semver {
    snapshotSuffix = "SNAPSHOT" // (default) appended if the commit is without a release tag
    dirtyMarker = "dirty" // (default) appended if the are uncommitted changes
    initialVersion = "2.2.0" // (default) initial version in semantic versioning

    branches { // list of branch configurations
        branch {
            regex = ".+" // regex for the branch you want to configure, put this one last
            incrementer = "PATCH_INCREMENTER" // NO_VERSION_INCREMENTER, PATCH_INCREMENTER, MINOR_INCREMENTER, MAJOR_INCREMENTER, CONVENTIONAL_COMMITS_INCREMENTER
            formatter = Transformer<Any, io.wusa.Info> { info: io.wusa.Info -> "${info.version.major}.${info.version.minor}.${info.version.patch}" }
        }
    }
}

version = semver.info

val vendor: String by project
val sentry_dsn: String by project

// Custom properties
ext {
    val versionInfo = semver.info
    val revision = semver.info.shortCommit
    val revisionFull = semver.info.commit

//    val vendor = project.property("vendor")

    if (semver.info.dirty) {
        set("sentryDSN", "")
        set("environment", "Development")
    } else {
        set("sentryDSN", sentry_dsn)
        set("environment", "Production")
    }

    // Unable to use non-semver tagging because of .msi restrictions
    var releaseDir = file("/releases/")

    // vendor, tagVersion, msiVersion, and DSNs defaults are set in gradle.properties
//    println("OS Detected: " + osdetector.os)
    println("Configuring for " + project.name)
    println("version: $versionInfo")
    println("vendor: $vendor")
    println("revision: $revision")
    println("revisionFull: $revisionFull")
    println("date: ${LocalDateTime.now()}")

}

application {
    mainClass.set("net.rptools.tokentool/net.rptools.tokentool.client.TokenTool")
    applicationDefaultJvmArgs = listOf("-Dsentry.environment=${project.rootProject.ext["environment"]}", "-Dsentry.dsn=${project.rootProject.ext["sentryDSN"]}", "-Dfile.encoding=UTF-8")
}

java {
    sourceCompatibility = JavaVersion.VERSION_15
    targetCompatibility = JavaVersion.VERSION_15
    modularity.inferModulePath.set(true)
}

javafx {
    version = "15.0.1"
    modules("javafx.base", "javafx.controls", "javafx.fxml", "javafx.swing", "javafx.graphics")
}

// Default parameters for gradle run command
//run {
//    args = listOf("-v=" + tagVersion, "-vendor=" + vendor)
//    applicationDefaultJvmArgs = listOf("-Dsentry.environment=Development", "-Dfile.encoding=UTF-8")
//
//    if (System.getProperty("exec.args") != null) {
//        args System . getProperty ("exec.args").split()
//    }
//}

//spotless {
//    java {
//        licenseHeaderFile "spotless.license.java"
//        googleJavaFormat()
//    }
//
//    format "misc", {
//    target "**/*.gradle", "**/.gitignore"
//
//    // spotless has built-in rules for most basic formatting tasks
//    trimTrailingWhitespace()
//    // or spaces. Takes an integer argument if you don"t like 4
//    indentWithSpaces(4)
//}
//}


// In this section you declare where to find the dependencies of your project
repositories {
    mavenCentral()
}

dependencies {
    // Logging
    annotationProcessor(group = "org.apache.logging.log4j", name = "log4j-core", version = "2.13.1")
    implementation(group = "org.apache.logging.log4j", name = "log4j-api", version = "2.13.1")

    // Note: log4j-1.2-api(versions 2.12.1+ breaks logging)
    implementation(group = "org.apache.logging.log4j", name = "log4j-1.2-api", version = "2.12.0")

    // Bridges v1 to v2 for other code in other libs
    implementation(group = "org.slf4j", name = "slf4j-simple", version = "1.7.30")
    // implementation(group = "commons-logging", name = "commons-logging", version = "1.2")

    implementation(group = "io.sentry", name = "sentry", version = "4.1.0")
    implementation(group = "io.sentry", name = "sentry-log4j2", version = "4.1.0")
    implementation(group = "javax.servlet", name = "javax.servlet-api", version = "4.0.1")

    // For PDF image extraction
    implementation(group = "org.apache.pdfbox", name = "pdfbox", version = "2.0.19")

    // To decrypt password/secured PDFs
    implementation(group = "org.bouncycastle", name = "bcmail-jdk15on", version = "1.64")

    // For pdf image extraction, specifically for jpeg2000 (jpx) support.
    implementation(group = "com.github.jai-imageio", name = "jai-imageio-core", version = "1.4.0")
    implementation(group = "com.github.jai-imageio", name = "jai-imageio-jpeg2000", version = "1.3.0")
    implementation(group = "org.sejda.imageio", name = "webp-imageio", version = "0.1.6")

    // Image processing lib https://github.com/haraldk/TwelveMonkeys
    implementation(group = "com.twelvemonkeys.imageio", name = "imageio-core", version = "3.5")
    implementation(group = "com.twelvemonkeys.imageio", name = "imageio-jpeg", version = "3.5")
    implementation(group = "com.twelvemonkeys.imageio", name = "imageio-psd", version = "3.5")

    // Other public libs
    implementation(group = "commons-io", name = "commons-io", version = "2.6")
    implementation(group = "org.reflections", name = "reflections", version = "0.9.12")
    implementation(group = "com.google.code.gson", name = "gson", version = "2.8.6")
}

tasks {
    withType<Jar> {
        manifest.attributes.apply {
            put("Implementation-Title", project.name)
            put("Implementation-Version", semver.info)
            put("Implementation-Vendor", "$vendor")
            put("Git-Commit", semver.info.shortCommit)
            put("Git-Commit-SHA", semver.info.commit)
            put("Built-By", System.getProperty("user.name"))
            put("Built-Date", LocalDateTime.now())
            put("Built-JDK", System.getProperty("java.version"))
            put("Source-Compatibility", project.java.sourceCompatibility)
            put("Target-Compatibility", project.java.targetCompatibility)
            put("Main-Class", mainClassName)
        }
    }

//        withType<Copy> {
//        "configSentryRelease" {
//            from("build-resources/sentry.properties.template")
//            into("src/main/resources/")
//            rename("sentry.properties.template", "sentry.properties")
//            val tokens = listOf(
//                    AppVersion = "${project.rootProject.ext["tagVersion"]}",
//                    Environment = "${project.rootProject.ext["environment"]}",
//                    SentryDSN = "${project.rootProject.ext["sentryDSN"]}"
//            )
//            expand(tokens)
//            inputs.properties(tokens)
//        }
//    }
}

//
//jlink {
//    options = listOf("--strip-debug", "--strip-native-commands", "--compress", "2", "--no-header-files", "--no-man-pages")
//
//    forceMerge("log4j-api(", "gson"))
//
//    launcher {
//        name = "TokenTool"
//        args = listOf("-v=" + tagVersion, "-vendor=" + vendor)
//        jvmArgs = listOf("-Dfile.encoding=UTF-8")
//    }
//
//    jpackage {
//        // not working :(
//        // installerOutputDir = releaseDir
//        // installerOutputDir = file("releases")
//        // imageOutputDir = file("$buildDir/my-packaging-image")
//        outputDir = "../releases"
//
//        imageOptions = listOf()
//        imageName = "TokenTool"
//
//        installerName = "TokenTool"
//        installerOptions = listOf(
//                "--verbose",
//                "--description", project.description,
//                "--copyright", "Copyright 2000-2020 RPTools.net",
//                "--license-file", "package/license/COPYING.AFFERO",
//                "--app-version", tagVersion,
//                "--vendor", vendor
//        )
//
//        if (osdetector.os.is("windows")) {
//            println "Setting Windows installer options"
//            imageOptions += listOf("--icon", "package/windows/TokenTool.ico")
//            installerOptions += listOf(
//                    "--win-dir-chooser",
//                    "--win-per-user-install",
//                    "--win-shortcut",
//                    "--win-menu",
//                    "--win-menu-group", "RPTools"
//            )
//        }
//
//        if (osdetector.os.is("osx")) {
//            println "Setting MacOS installer options"
//            imageOptions += listOf("--icon", "package/macosx/tokentool-icon.icns")
//            installerOptions += listOf(
//
//            )
//        }
//
//        if (osdetector.os.is("unix")) {
//            println "Setting Linux installer options"
//            imageOptions += listOf("--icon", "package/linux/tokentool.png")
//            installerOptions += listOf(
//                    "--linux-menu-group", "RPTools",
//                    "--linux-shortcut"
//            )
//
//            // if (installerType == "deb") {
//            //     installerOptions += listOf(
//            //             "--linux-deb-maintainer", "office@walczak.it"
//            //     )
//            // }
//
//            // if (installerType == "rpm") {
//            //     installerOptions += listOf(
//            //             "--linux-rpm-license-type", "GPLv3"
//            //     )
//            // }
//        }
//    }
//}

// Configure current release tag in Sentry.io properties
//processResources.dependsOn configSentryRelease

tasks.getByName<Zip>("distZip").enabled = false
tasks.getByName<Tar>("distTar").enabled = false