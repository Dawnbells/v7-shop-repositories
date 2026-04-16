plugins {
    id("java")
    id("org.springframework.boot") version "3.3.0"
    id("io.spring.dependency-management") version "1.1.5"
}

group = "cn.v7soft"
version = "0.0.1-SNAPSHOT"

repositories {
    maven { url=uri("https://maven.aliyun.com/repository/public/") }
    maven { url=uri("https://maven.aliyun.com/repository/central") }
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    /** annotation */
    implementation("org.jetbrains:annotations:13.0")
    /** dev */
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    /** web */
    implementation("org.springframework.boot:spring-boot-starter-web")
    /** validate */
    implementation("jakarta.validation:jakarta.validation-api")
    implementation("org.hibernate.validator:hibernate-validator:7.0.1.Final")
    /** config */
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    /** Database: mysql, redis, jpa */
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("com.mysql:mysql-connector-j")
    /** lombok */
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    /** api doc */
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.0.2")
    implementation("com.github.xiaoymin:knife4j-openapi3-jakarta-spring-boot-starter:4.3.0")
    /** hutool */
    implementation("cn.hutool:hutool-all:5.8.25")
    /**  sa-token */
    implementation("cn.dev33:sa-token-spring-boot3-starter:1.37.0")
    implementation("cn.dev33:sa-token-redis-jackson:1.37.0")
    implementation("cn.dev33:sa-token-jwt:1.37.0")
    /** forest */
//    implementation("com.dtflys.forest:forest-spring-boot3-starter:1.5.33")
    /** validate */
    implementation("jakarta.validation:jakarta.validation-api")
}

tasks.test {
    useJUnitPlatform()
}