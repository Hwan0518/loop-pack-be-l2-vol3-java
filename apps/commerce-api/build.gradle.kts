import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

val benchmarkSourceSet = sourceSets.create("benchmark") {
    java.srcDir("src/benchmark/java")
    resources.srcDir("src/benchmark/resources")
    compileClasspath += sourceSets["main"].output
    runtimeClasspath += output + compileClasspath
}

configurations[benchmarkSourceSet.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[benchmarkSourceSet.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())
configurations[benchmarkSourceSet.compileOnlyConfigurationName].extendsFrom(configurations.testCompileOnly.get())
configurations[benchmarkSourceSet.annotationProcessorConfigurationName].extendsFrom(configurations.testAnnotationProcessor.get())

tasks.register<Test>("benchmarkTest") {
    description = "Runs benchmark-style performance tests excluded from the default test task."
    group = "verification"
    testClassesDirs = benchmarkSourceSet.output.classesDirs
    classpath = benchmarkSourceSet.runtimeClasspath
    maxParallelForks = 1
    useJUnitPlatform()
    systemProperty("user.timezone", "Asia/Seoul")
    systemProperty("spring.profiles.active", "test")
    jvmArgs("-Xshare:off")
    shouldRunAfter(tasks.test)
}

tasks.test {
    // SpringBoot/Testcontainers 통합 테스트가 같은 worker JVM에서 상태를 오염시키지 않도록
    // test class마다 새로운 JVM에서 실행한다.
    forkEvery = 1
}

dependencies {
    // add-ons
    implementation(project(":modules:jpa"))
    implementation(project(":modules:redis"))
    implementation(project(":modules:kafka"))
    implementation(project(":modules:event-schema"))
    implementation(project(":supports:jackson"))
    implementation(project(":supports:logging"))
    implementation(project(":supports:monitoring"))

    // web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${project.properties["springDocOpenApiVersion"]}")

    // retry
    implementation("org.springframework.retry:spring-retry")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // resilience4j
    implementation("io.github.resilience4j:resilience4j-spring-boot3")

    // cache
    implementation("com.github.ben-manes.caffeine:caffeine")

    // shedlock (대기열 스케줄러 분산 락)
    implementation("net.javacrumbs.shedlock:shedlock-spring:6.3.1")
    implementation("net.javacrumbs.shedlock:shedlock-provider-redis-spring:6.3.1")

    // querydsl
    annotationProcessor("com.querydsl:querydsl-apt::jakarta")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")
    annotationProcessor("jakarta.annotation:jakarta.annotation-api")

    // archunit
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")

    // test-fixtures
    testImplementation(testFixtures(project(":modules:jpa")))
    testImplementation(testFixtures(project(":modules:redis")))
    testImplementation(testFixtures(project(":modules:kafka")))
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.test)
    executionData(fileTree(layout.buildDirectory.asFile).include("jacoco/*.exec"))
    classDirectories.setFrom(
        files(
            sourceSets["main"].output.asFileTree.matching {
                exclude("**/Q*.class")
                exclude("**/*Application.class")
            }
        )
    )
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}
