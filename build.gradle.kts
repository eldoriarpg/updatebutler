plugins {
    id("com.github.johnrengelman.shadow") version "7.1.0"
    java
    `maven-publish`
}

repositories {
    mavenCentral()
    maven("https://m2.dv8tion.net/releases")
    maven("https://jcenter.bintray.com")
}

dependencies {
    implementation("commons-io:commons-io:2.7")
    implementation("com.google.api-client:google-api-client:1.23.0")
    implementation("com.google.guava:guava:31.0.1-jre")
    implementation("com.google.code.gson:gson:2.8.9")
    implementation("com.sparkjava:spark-core:2.9.3")
    implementation("org.apache.commons:commons-lang3:3.9")
    implementation("net.dv8tion", "JDA", "4.3.0_339") {
        exclude(module = "opus-java")
    }
    implementation("com.zaxxer:HikariCP:5.0.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:2.7.4")
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

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
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
