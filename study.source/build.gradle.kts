plugins {
    idea
    java
    kotlin("jvm") version "2.1.0"
}

group = "study.ms2709"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
kotlin {
    jvmToolchain(21)
}

sourceSets {
    main {
        java.srcDir("src/main/kotlin")
    }
}
