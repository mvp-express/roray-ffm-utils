plugins {
    java
    id("me.champeau.jmh") version "0.7.3"
}

group = "express.mvp"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":lib"))
    
    // JMH dependencies
    jmh("org.openjdk.jmh:jmh-core:1.37")
    jmhAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
    
    // Comparison libraries
    jmh("org.jctools:jctools-core:4.0.5") // For queue comparison
    jmh("org.agrona:agrona:1.21.2")       // For map comparison
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

jmh {
    duplicateClassesStrategy = org.gradle.api.file.DuplicatesStrategy.WARN
    fork = 1
    warmupIterations = 1
    iterations = 1
    
    // Enable FFM access
    jvmArgsAppend = listOf(
        "--enable-native-access=ALL-UNNAMED", 
        "--add-modules=jdk.incubator.vector"
    )
}
