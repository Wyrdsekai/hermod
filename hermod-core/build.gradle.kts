// The protocol. NO dependencies -- not "none that we noticed", none that the
// build will accept. A consumer gets this jar and nothing else, which is what
// makes it safe to vendor as a directory copy on platforms that cannot take a
// JVM dependency at all.
plugins {
    `maven-publish`
    signing
}

dependencies {
    // Deliberately empty. Anything the protocol needs belongs in the JDK or in
    // a transport module. If you find yourself adding a line here, the class
    // you are writing probably belongs in hermod-nats.
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "hermod-core"
            from(components["java"])
            pom {
                name = "hermod-core"
                description = "The hermod protocol: capability gossip, routing, admission, grants. No dependencies."
                url = "https://github.com/Wyrdsekai/hermod"
                developers {
                    developer {
                        id = "wyrdsekai"
                        name = "Wyrdsekai"
                        url = "https://github.com/Wyrdsekai"
                    }
                }
                scm {
                    url = "https://github.com/Wyrdsekai/hermod"
                    connection = "scm:git:https://github.com/Wyrdsekai/hermod.git"
                    developerConnection = "scm:git:ssh://git@github.com/Wyrdsekai/hermod.git"
                }
                licenses {
                    license {
                        name = "Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
            }
        }
    }
}

// The zero-dependency claim, checked. A published POM that quietly acquires a
// runtime dependency would end vendorability without anyone noticing, so fail
// the build instead of finding out from a consumer.
tasks.register("checkNoDependencies") {
    val runtime = configurations.named("runtimeClasspath")
    doLast {
        val found = runtime.get().resolvedConfiguration.resolvedArtifacts.map { it.moduleVersion.id }
        if (found.isNotEmpty()) {
            throw GradleException(
                "hermod-core must have NO runtime dependencies, but resolved: $found")
        }
    }
}
tasks.named("check") { dependsOn("checkNoDependencies") }

// Maven Central requires every artifact signed. Configured so it NEVER breaks a
// plain build: signing is required only when a publish is actually being run,
// and only when a key is configured. Someone who clones this and runs
// `./gradlew build` has no key and needs none.
signing {
    val signingKeyId: String? = findProperty("signing.keyId") as String?
    val inMemoryKey: String? = findProperty("signingKey") as String?   // for CI
    val inMemoryPass: String? = findProperty("signingPassword") as String?

    setRequired({ gradle.taskGraph.allTasks.any { it.name.startsWith("publish") } })

    if (inMemoryKey != null) {
        useInMemoryPgpKeys(inMemoryKey, inMemoryPass)
    } else if (signingKeyId == null) {
        // No keyring properties: fall back to the gpg agent, which keeps the
        // passphrase out of gradle.properties entirely.
        useGpgCmd()
    }
    sign(publishing.publications["maven"])
}
