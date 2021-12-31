plugins {
    id("com.github.johnrengelman.shadow") version "7.1.0"
    java
    `maven-publish`
}

repositories {
    maven("https://jcenter.bintray.com")
    maven("https://eldonexus.de/repository/maven-public/")
    maven("https://eldonexus.de/repository/maven-proxies/")
    mavenCentral()
}

dependencies {
    implementation("commons-io:commons-io:2.11.0")
    implementation("com.google.api-client:google-api-client:1.32.2")
    implementation("com.google.guava:guava:31.0.1-jre")
    implementation("com.fasterxml.jackson.core", "jackson-databind", "2.12.3")
    implementation("io.javalin", "javalin", "3.13.9")
    implementation("org.apache.commons","commons-lang3","3.12.0")
    implementation("net.dv8tion", "JDA", "5.0.0-alpha.3") {
        exclude(module = "opus-java")
    }
    implementation("com.zaxxer:HikariCP:5.0.0")
    implementation("de.chojo", "sql-util", "1.1.5")
    implementation("org.postgresql", "postgresql", "42.3.1")

    implementation("org.slf4j:slf4j-api:1.7.32")
    implementation("org.apache.logging.log4j:log4j-core:2.17.0")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.17.0")
    compileOnly("org.projectlombok", "lombok", "1.18.22")
    annotationProcessor("org.projectlombok", "lombok", "1.18.22")

}

group = "de.eldoria"
version = "1.0"
description = "UpdateButler"
java.sourceCompatibility = JavaVersion.VERSION_11

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}

tasks {
    shadowJar {
        mergeServiceFiles()
        manifest {
            attributes(mapOf("Main-Class" to "de.eldoria.updatebutler.UpdateButler"))
        }
    }

    build {
        dependsOn(shadowJar)
    }

    compileJava {
        options.encoding = "UTF-8"
    }

    javadoc {
        options.encoding = "UTF-8"
    }

    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}
