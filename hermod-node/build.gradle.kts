// A compute-only mesh member: advertises, answers its door, and runs
// inference.chat against any OpenAI-compatible endpoint. An application, not a
// library -- nothing depends on it, so it is not published.
plugins { application }

dependencies {
    implementation(project(":hermod-core"))
    implementation(project(":hermod-nats"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("org.slf4j:slf4j-api:2.0.13")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.13")
}

application {
    mainClass = "org.wyrdsekai.hermod.node.HermodNodeMain"
    applicationName = "hermod-node"
}
