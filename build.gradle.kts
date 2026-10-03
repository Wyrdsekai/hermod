// Root build: shared configuration only. Each module declares its own
// dependencies, which is the point of the split -- `hermod-core` having none
// is now a fact the build enforces, not a claim in a comment.
// The root carries the version too: without it the bundle filename came out
// as "hermod-unspecified.zip", since `version` at the root is not the one
// set on each subproject.
version = "0.2.0"

subprojects {
    apply(plugin = "java-library")

    group = "org.wyrdsekai"
    version = "0.2.0"

    repositories { mavenCentral() }

    extensions.configure<JavaPluginExtension> {
        toolchain { languageVersion = JavaLanguageVersion.of(21) }
        // Maven Central requires BOTH a sources jar and a javadoc jar, and
        // rejects the publication without them.
        withSourcesJar()
        withJavadocJar()
    }

    dependencies {
        add("testImplementation", platform("org.junit:junit-bom:5.10.2"))
        add("testImplementation", "org.junit.jupiter:junit-jupiter")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
        add("testImplementation", "org.assertj:assertj-core:3.25.3")
    }

    tasks.withType<Test>().configureEach { useJUnitPlatform() }

    // PUBLISH FROM THE PUBLIC TREE, NOT THIS ONE.
    //
    // What gets published should be built from the source people can actually
    // read. Publishing from the private tree would make the public repo a
    // description of the artifact rather than its origin, and any divergence
    // -- a file the export filters, or a real difference -- would ship in
    // silence. The POM's scm already points at github.com/Wyrdsekai/hermod, so
    // an artifact built here would describe a tree it did not come from.
    //
    // `scripts/export-oss.sh` is the marker: the export deliberately excludes
    // it, so its presence means "this is the private tree". Local publishing
    // stays allowed everywhere -- it is how you rehearse.
    tasks.matching {
        it.name.startsWith("publish") && !it.name.contains("MavenLocal")
    }.configureEach {
        doFirst {
            if (rootProject.file("scripts/export-oss.sh").exists()) {
                throw GradleException(
                    "Refusing to publish from the private tree. Run " +
                    "./scripts/export-oss.sh, then publish from ../hermod-oss " +
                    "so the artifact is built from the public source.")
            }
        }
    }
}

// ── Publishing to Maven Central ───────────────────────────────────────────
//
// There is no official Gradle plugin for the Central Publisher Portal, so
// rather than take a third-party build plugin we do what the Portal asks for
// directly: stage the signed artifacts into a local Maven-layout repository,
// zip it, and upload that bundle. Fewer moving parts, and nothing to audit
// beyond what is written here.
//
//   ./gradlew centralBundle          -> build/central/hermod-<version>.zip
//   then upload it with the token from the Portal (see CONTRIBUTING.md).

val centralStaging = layout.buildDirectory.dir("central-staging")

subprojects {
    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = "centralStaging"
                    url = uri(rootProject.layout.buildDirectory.dir("central-staging"))
                }
            }
        }
    }
}

// The staging directory is emptied before anything is staged into it, so a bundle
// carries only the version being released. Central is immutable: a bundle that also
// carries an already-published version is refused whole. (The first 0.2.0 bundle was
// built in a tree whose staging directory still held 0.1.0 from August, and had both.)
val cleanCentralStaging = tasks.register<Delete>("cleanCentralStaging") {
    group = "publishing"
    description = "Empty the Central staging directory, so a bundle carries only this version."
    delete(centralStaging)
}

subprojects {
    plugins.withId("maven-publish") {
        tasks.configureEach {
            if (name.endsWith("ToCentralStagingRepository")) {
                dependsOn(cleanCentralStaging)
            }
        }
    }
}

tasks.register<Zip>("centralBundle") {
    group = "publishing"
    description = "Stage signed artifacts and zip them into a Central Portal bundle."
    dependsOn(cleanCentralStaging)
    dependsOn(subprojects.mapNotNull { it.tasks.findByName("publishAllPublicationsToCentralStagingRepository") })
    from(centralStaging)
    // Prove it: a version directory other than this one in the staging tree fails the bundle.
    doFirst {
        val here = project.version.toString()
        val strays = centralStaging.get().asFile.walkTopDown()
            .filter { it.isDirectory && it.name.matches(Regex("\\d+\\.\\d+\\.\\d+.*")) && it.name != here }
            .map { it.relativeTo(centralStaging.get().asFile).path }.toList()
        if (strays.isNotEmpty()) {
            throw GradleException("staging holds other versions: $strays — the bundle must carry only $here")
        }
    }
    // The Portal rejects checksum files it did not ask for, and Gradle's
    // maven-metadata is not part of a release bundle.
    exclude("**/maven-metadata*")
    // Checksums OF the signature files. Gradle emits them; nothing consumes
    // them, and a bundle carrying files the Portal did not ask for is a
    // needless way to fail validation.
    exclude("**/*.asc.md5", "**/*.asc.sha1", "**/*.asc.sha256", "**/*.asc.sha512")
    archiveFileName = "hermod-${version}.zip"
    destinationDirectory = layout.buildDirectory.dir("central")
    doLast {
        logger.lifecycle("bundle: ${archiveFile.get().asFile}")
    }
}
