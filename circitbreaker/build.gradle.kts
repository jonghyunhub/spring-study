// Kotlin 플러그인을 루트에서 선언하고 서브모듈에서는 apply만 하도록 중복 로드 경고 방지
plugins {
    kotlin("jvm") apply false
    kotlin("kapt") apply false
    kotlin("plugin.spring") apply false
    kotlin("plugin.jpa") apply false
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management") apply false
    id("org.jlleitschuh.gradle.ktlint") apply false
}
