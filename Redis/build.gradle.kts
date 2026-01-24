import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("org.asciidoctor.jvm.convert")
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

    // storage
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("com.mysql:mysql-connector-j")

    // redis
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.redisson:redisson-spring-boot-starter:${property("redissonVersion")}")

    // client
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
    implementation("io.github.openfeign:feign-hc5")
    implementation("io.github.openfeign:feign-micrometer")

    // logging
    implementation("io.micrometer:micrometer-tracing-bridge-brave")
    implementation("io.sentry:sentry-logback:${property("sentryVersion")}")

    // monitoring
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // api-docs
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    compileOnly("org.springframework.boot:spring-boot-starter-test")
    api("org.springframework.restdocs:spring-restdocs-mockmvc")
    api("org.springframework.restdocs:spring-restdocs-restassured")
    api("io.rest-assured:spring-mock-mvc")
}

java.sourceCompatibility = JavaVersion.valueOf("VERSION_${property("javaVersion")}")
tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "${project.property("javaVersion")}"
    }
}

tasks.test {
    useJUnitPlatform {
        excludeTags("develop", "restdocs")
    }
}

tasks.register<Test>("unitTest") {
    group = "verification"
    useJUnitPlatform {
        excludeTags("develop", "context", "restdocs")
    }
}

tasks.register<Test>("contextTest") {
    group = "verification"
    useJUnitPlatform {
        includeTags("context")
    }
}

tasks.register<Test>("restDocsTest") {
    group = "verification"
    useJUnitPlatform {
        includeTags("restdocs")
    }
}

tasks.register<Test>("developTest") {
    group = "verification"
    useJUnitPlatform {
        includeTags("develop")
    }
}

tasks.getByName("asciidoctor") {
    dependsOn("restDocsTest")
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}
