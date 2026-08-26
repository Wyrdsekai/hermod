// The reference transport: capability gossip and task RPC over NATS.
plugins {
    `maven-publish`
    signing
}

dependencies {
    api(project(":hermod-core"))
    // API, not implementation: NatsDoors and NatsGossip take an
    // io.nats.client.Connection in their public constructors, so a consumer
    // cannot call them without this on its own compile classpath. The
    // single-module build could not tell the difference; the split makes the
    // real API surface visible.
    api("io.nats:jnats:2.20.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2")
    implementation("org.slf4j:slf4j-api:2.0.13")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.13")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "hermod-nats"
            from(components["java"])
            pom {
                name = "hermod-nats"
                description = "NATS transport for hermod: gossip and task RPC."
                url = "https://github.com/Wyrdsekai/hermod"
                developers {
                    developer {
                        id = "wyrdsekai"
                        name = "Wyrdsekai"
                        url = "https://github.com/Wyrdsekai"
                    }
                }
                licenses {
                    license {
                        name = "Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
                scm {
                    url = "https://github.com/Wyrdsekai/hermod"
                    connection = "scm:git:https://github.com/Wyrdsekai/hermod.git"
                    developerConnection = "scm:git:ssh://git@github.com/Wyrdsekai/hermod.git"
                }
            }
        }
    }
}

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
