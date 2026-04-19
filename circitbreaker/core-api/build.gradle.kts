import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("org.jlleitschuh.gradle.ktlint")
}

java.sourceCompatibility = JavaVersion.valueOf("VERSION_${property("javaVersion")}")

group = "${property("projectGroup")}"
version = "${property("applicationVersion")}"

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudDependenciesVersion")}")
        mavenBom("io.github.resilience4j:resilience4j-bom:${property("resilience4jVersion")}")
    }
}

dependencies {
    // common
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.ninja-squad:springmockk:${property("springMockkVersion")}")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    kapt("org.springframework.boot:spring-boot-configuration-processor")

    // web
    implementation("org.springframework.boot:spring-boot-starter-web")

    // aop - @CircuitBreaker 어노테이션이 프록시로 동작하려면 필수
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // circuit breaker
    implementation("io.github.resilience4j:resilience4j-spring-boot3")

    // client - stub-service 호출용
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
    implementation("io.github.openfeign:feign-hc5")
    implementation("io.github.openfeign:feign-micrometer")

    // monitoring - prometheus 메트릭 수집
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.micrometer:micrometer-tracing-bridge-brave")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "${project.property("javaVersion")}"
    }
}

tasks.test {
    useJUnitPlatform {
        excludeTags("develop")
    }
}

tasks.register<Test>("contextTest") {
    group = "verification"
    useJUnitPlatform {
        includeTags("context")
    }
}

tasks.register<Test>("developTest") {
    group = "verification"
    useJUnitPlatform {
        includeTags("develop")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}
