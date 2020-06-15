import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    // Apply the java-library plugin to add support for Java Library
    `java-library`
    id("maven-publish")
}

group = "com.github.navikt"
version = (if (properties["version"] != null && properties["version"] != "unspecified") properties["version"] else "local-build")!!

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
repositories {
    // Use jcenter for resolving dependencies.
    // You can declare any Maven/Ivy/file repository here.
    jcenter()
}

dependencies {
    implementation("org.apache.kafka:kafka_2.12:2.5.0")
    implementation("com.bettercloud:vault-java-driver:5.1.0")

    // Use JUnit Jupiter API for testing.
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.1")

    // Use JUnit Jupiter Engine for testing.
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.6.1")
}

val test by tasks.getting(Test::class) {
    // Use junit platform for unit tests
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showExceptions = true
        showStackTraces = true
        showCauses = true
        exceptionFormat = TestExceptionFormat.FULL
        showStandardStreams = true
    }
}

tasks.withType<Wrapper> {
    gradleVersion = "6.4.1"
    distributionType = Wrapper.DistributionType.ALL
}

val sourcesJar by tasks.registering(Jar::class) {
    classifier = "sources"
    from(sourceSets.main.get().allSource)
}

val githubUser: String? by project
val githubPassword: String? by project

publishing {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/navikt/kafka-connect-vault-provider")
            credentials {
                username = githubUser
                password = githubPassword
            }
        }
    }
    publications {
        create<MavenPublication>("mavenJava") {

            pom {
                name.set("kafka-connect-vault-provider")
                description.set("Kafka Connect Vault Provider")
                url.set("https://github.com/navikt/kafka-connect-vault-provider")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                scm {
                    connection.set("scm:git:https://github.com/navikt/kafka-connect-vault-provider.git")
                    developerConnection.set("scm:git:https://github.com/navikt/kafka-connect-vault-provider.git")
                    url.set("https://github.com/navikt/kafka-connect-vault-provider")
                }
            }
            from(components["java"])
            artifact(sourcesJar.get())
        }
    }
}
