plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.plugin.serialization)
}

group = "jezdibolt"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

dependencies {

    // --- Exposed ORM ---
    implementation("org.jetbrains.exposed:exposed-core:0.61.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.61.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.61.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.61.0")

    // --- Databases ---
    implementation("com.h2database:h2:2.2.224") // pro dev
    testImplementation("org.postgresql:postgresql:42.7.3")
    implementation("org.postgresql:postgresql:42.7.3")
    implementation("org.flywaydb:flyway-core:10.11.0")
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")


    // --- Utility ---
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    implementation("org.apache.commons:commons-csv:1.11.0")
    implementation("org.mindrot:jbcrypt:0.4")

    implementation("io.ktor:ktor-server-cors-jvm:2.3.11")

    // --- Ktor ---
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.swagger)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.netty)
    implementation(libs.logback.classic)
    implementation(libs.ktor.server.config.yaml)

    implementation("io.ktor:ktor-server-websockets-jvm:2.3.11")


    // --- Tests ---
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
}
