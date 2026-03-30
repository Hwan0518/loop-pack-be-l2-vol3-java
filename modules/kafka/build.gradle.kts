plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    api(project(":modules:event-schema"))
    api("org.springframework.kafka:spring-kafka")

    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.testcontainers:kafka")

    testFixturesImplementation("org.testcontainers:kafka")
}
