import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.0"
}

group = "net.confuscat.ccMageArena"
version = "0.1"

configurations {
    create("includeInJar") {
        this.isTransitive = false
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("--enable-preview")
}

val copyJar by tasks.register<Copy>("copyJar") {
    from("build/libs/")
    into("${System.getProperty("user.home")}\\BotWithUs\\scripts\\local\\")
    //into("J:\\xfer\\")
    include("*.jar")
}

tasks.named<Jar>("jar") {
    from({
        configurations["includeInJar"].map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    finalizedBy(copyJar)
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "20"
    targetCompatibility = "20"
    options.compilerArgs.add("--enable-preview")
}

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        setUrl("https://nexus.botwithus.net/repository/maven-snapshots/")
    }
}

dependencies {
    implementation("net.botwithus.rs3:botwithus-api:1.0.0-20241103.161409-2")
    implementation("net.botwithus.xapi.public:api:1.1.1-20250404.064306-2")
    "includeInJar"("net.botwithus.xapi.public:api:1.1.1-20250404.064306-2")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "20"
}