import java.time.LocalDateTime

plugins {
    application
    java
    id("org.openjfx.javafxplugin") version "0.0.10"
    id("org.beryx.jlink") version "2.23.6"
    id("com.diffplug.spotless") version "5.14.1"
    id("io.wusa.semver-git-plugin") version "2.3.7"
}

// Used by gradle assemble & run tasks
val mainClassName = "net.rptools.tokentool.client.TokenTool"

semver {
    snapshotSuffix = "SNAPSHOT" // (default) appended if the commit is without a release tag
    dirtyMarker = "dirty" // (default) appended if the are uncommitted changes
    initialVersion = "2.0.0" // (default) initial version in semantic versioning
    tagType = io.wusa.TagType.LIGHTWEIGHT

    branches { // list of branch configurations
        branch {
            incrementer = "NO_VERSION_INCREMENTER" // NO_VERSION_INCREMENTER, PATCH_INCREMENTER, MINOR_INCREMENTER, MAJOR_INCREMENTER, CONVENTIONAL_COMMITS_INCREMENTER
            formatter = Transformer<Any, io.wusa.Info> { info: io.wusa.Info -> "${info.version.major}.${info.version.minor}.${info.version.patch}" }
            regex = ".+" // regex for the branch you want to configure, put this one last
        }
    }
}

project.version = semver.info

val vendor: String by project
val sentryDSN: String by project
val environment: String by project

// Custom properties
ext {
    val os = org.gradle.internal.os.OperatingSystem.current()

    if (semver.info.dirty || semver.info.version.toString().endsWith(semver.snapshotSuffix)) {
        set("sentryDSN", "")
        set("environment", "Development")
    } else {
        set("sentryDSN", sentryDSN)
        set("environment", "Production")
    }

    println("OS Detected: $os")
    println("Configuring for ${project.name}")
    println("version: ${semver.info}")
    println("vendor: $vendor")
    println("revision: ${semver.info.shortCommit}")
    println("revisionFull: ${semver.info.commit}")
    println("date: ${LocalDateTime.now()}")
}

application {
    mainModule.set("net.rptools.tokentool")
    mainClass.set("net.rptools.tokentool.client.TokenTool")
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
}

java {
    sourceCompatibility = JavaVersion.VERSION_16
    targetCompatibility = JavaVersion.VERSION_16
    modularity.inferModulePath.set(true)
}

javafx {
    version = "16"
    modules = listOf("javafx.base", "javafx.controls", "javafx.fxml", "javafx.swing", "javafx.graphics")
}

spotless {
    java {
        licenseHeaderFile("${projectDir}/spotless.license.java")
        targetExclude("**/module-info.java")
        //googleJavaFormat()
    }

    format("misc") {
        target("**/*.gradle", "**/.gitignore")
        // spotless has built-in rules for most basic formatting tasks
        trimTrailingWhitespace()
        // or spaces. Takes an integer argument if you don"t like 4
        indentWithSpaces(4)
    }
}


// In this section you declare where to find the dependencies of your project
repositories {
    mavenCentral()
}

dependencies {
    // Logging
    annotationProcessor(group = "org.apache.logging.log4j", name = "log4j-core", version = "2.14.1")
    implementation(group = "org.apache.logging.log4j", name = "log4j-api", version = "2.14.1")

    // Note: log4j-1.2-api(versions 2.12.1+ breaks logging)
    implementation(group = "org.apache.logging.log4j", name = "log4j-1.2-api", version = "2.14.1")

    // Bridges v1 to v2 for other code in other libs
    implementation(group = "org.slf4j", name = "slf4j-simple", version = "1.7.31")

    implementation(group = "io.sentry", name = "sentry", version = "4.1.0")
    implementation(group = "io.sentry", name = "sentry-log4j2", version = "4.1.0")
    implementation(group = "javax.servlet", name = "javax.servlet-api", version = "4.0.1")

    // For PDF image extraction
    implementation(group = "org.apache.pdfbox", name = "pdfbox", version = "2.0.24")

    // To decrypt password/secured PDFs
    implementation(group = "org.bouncycastle", name = "bcmail-jdk15on", version = "1.69")

    // For pdf image extraction, specifically for jpeg2000 (jpx) support.
    implementation(group = "com.github.jai-imageio", name = "jai-imageio-core", version = "1.4.0")
    implementation(group = "com.github.jai-imageio", name = "jai-imageio-jpeg2000", version = "1.4.0")
    implementation(group = "org.sejda.imageio", name = "webp-imageio", version = "0.1.6")

    // Image processing lib https://github.com/haraldk/TwelveMonkeys
    implementation(group = "com.twelvemonkeys.imageio", name = "imageio-core", version = "3.6.4")
    implementation(group = "com.twelvemonkeys.imageio", name = "imageio-jpeg", version = "3.6.4")
    implementation(group = "com.twelvemonkeys.imageio", name = "imageio-psd", version = "3.6.4")

    // Other public libs
    implementation(group = "commons-io", name = "commons-io", version = "2.11.0")
    implementation(group = "com.google.code.gson", name = "gson", version = "2.8.6")
}

val sharedManifest = the<JavaPluginConvention>().manifest {
    attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to semver.info,
            "Implementation-Vendor" to vendor,
            "Git-Commit" to semver.info.shortCommit,
            "Git-Commit-SHA" to semver.info.commit,
            "Built-By" to System.getProperty("user.name"),
            "Built-Date" to LocalDateTime.now(),
            "Built-JDK" to System.getProperty("java.version"),
            "Source-Compatibility" to project.java.sourceCompatibility,
            "Target-Compatibility" to project.java.targetCompatibility,
            "Main-Class" to mainClassName
    )
}

tasks.jar {
    manifest = project.the<JavaPluginConvention>().manifest {
        from(sharedManifest)
    }
}

tasks.register<Jar>("uberJar") {
    manifest = project.the<JavaPluginConvention>().manifest {
        from(sharedManifest)
    }

    archiveClassifier.set("uber")

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }.apply {
            exclude("META-INF/*.SF")
            exclude("META-INF/*.DSA")
            exclude("META-INF/*.RSA")
        }
    })
}

jlink {
    // We need to keep the semver down to just major.minor.patch (no -alpha.1 or -rc.1, etc) otherwise jpackage fails
    val appVersion = "${semver.info.version.major}.${semver.info.version.minor}.${semver.info.version.patch}"
    project.version = appVersion;

    options.set(listOf("--strip-debug", "--strip-native-commands", "--compress", "2", "--no-header-files", "--no-man-pages"))

    forceMerge("log4j-api", "gson")

    launcher {
        name = "TokenTool"
        jvmArgs = listOf("-Dfile.encoding=UTF-8")
    }

    jpackage {
        val os = org.gradle.internal.os.OperatingSystem.current()
        // not working :(
        // installerOutputDir = releaseDir
        // installerOutputDir = file("releases")
        // imageOutputDir = file("$buildDir/my-packaging-image")
        outputDir = "../releases"

        imageOptions = mutableListOf()
        imageName = "TokenTool"

        installerName = "TokenTool"
        installerOptions = mutableListOf(
                "--verbose",
                "--description", project.description,
                "--copyright", "Copyright 2000-2020 RPTools.net",
                "--license-file", "package/license/COPYING.AFFERO",
                "--app-version", appVersion,
                "--vendor", vendor
        )

        if (os.isWindows) {
            println("Setting Windows installer options")
            imageOptions.addAll(listOf("--icon", "package/windows/TokenTool.ico"))
            installerOptions.addAll(listOf(
                    "--win-dir-chooser",
                    "--win-per-user-install",
                    "--win-shortcut",
                    "--win-menu",
                    "--win-menu-group", "RPTools"
            ))
        }

        if (os.isMacOsX) {
            println("Setting MacOS installer options")
            imageOptions.addAll(listOf("--icon", "package/macosx/tokentool-icon.icns"))
        }

        if (os.isLinux) {
            println("Setting Linux installer options")
            imageOptions.addAll(listOf("--icon", "package/linux/tokentool.png"))
            installerOptions.addAll(listOf(
                    "--linux-menu-group", "RPTools",
                    "--linux-shortcut")
            )

            if (installerType == "deb") {
                installerOptions.addAll(listOf(
                        "--linux-deb-maintainer", "admin@rptools.net"
                ))
            }

            if (installerType == "rpm") {
                installerOptions.addAll(listOf(
                        "--linux-rpm-license-type", "AGPLv3"
                ))
            }
        }
    }
}

// Create the GenerateBuildProperties task
tasks.register<GenerateBuildProperties>("generateBuildProperties") {
    outputDir.set(project.layout.buildDirectory.dir("resources/main"))
}

// A task that generates various dynamic properties used at runtime
open class GenerateBuildProperties @javax.inject.Inject constructor(objects: ObjectFactory) : DefaultTask() {
    @OutputDirectory
    val outputDir: DirectoryProperty = objects.directoryProperty()

    @TaskAction
    fun compile() {
        val dir = outputDir.get().asFile
        val srcFile = File(dir, "build.properties")
        logger.quiet("output dir = $dir")

        srcFile.writeText("# Auto-Generated properties from Gradle compileJava step\n")
        srcFile.appendText("# ${LocalDateTime.now()}\n\n")
        srcFile.appendText("version=${project.semver.info}\n")
        srcFile.appendText("vendor=${project.ext["vendor"]}\n")
        srcFile.appendText("environment=${project.ext["environment"]}\n")
        srcFile.appendText("sentryDSN=${project.ext["sentryDSN"]}\n")
        srcFile.appendText("git-commit=${project.semver.info.shortCommit}\n")
        srcFile.appendText("git-commit-sha=${project.semver.info.commit}\n")
    }
}

tasks.getByName<Zip>("distZip").enabled = false
tasks.getByName<Tar>("distTar").enabled = false
tasks.named("compileJava") { dependsOn("generateBuildProperties") }