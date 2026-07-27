plugins {
    id("java")
}

group = "cn.v7soft"
version = "0.0.1-SNAPSHOT"

dependencies {
    implementation(project(":v7-soft-core"))
    implementation(project(":v7-shop-dao"))
    implementation(project(":v7-shop-common"))
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    // Spring Boot Mail
    implementation("org.springframework.boot:spring-boot-starter-mail")

    // Amazon S3 SDK
    implementation ("software.amazon.awssdk:s3:2.20.96")
    implementation ("software.amazon.awssdk:sesv2:2.20.96")
    // Thumbnailator for image resizing
    implementation ("net.coobird:thumbnailator:0.4.14")
    // WebP image support
//    implementation ("com.twelvemonkeys.imageio:imageio-webp:3.8.1")
//    implementation ("com.github.sejda-pdf:webp-imageio:0.1.2")
    implementation("com.aliyun:alidns20150109:3.4.5")
    implementation("com.aliyun:domain20180129:5.0.0")
    implementation("com.aliyun:green20220302:2.18.0")
    implementation("javax.xml.bind:jaxb-api:2.3.1")
    implementation("javax.activation:activation:1.1.1")
    implementation("org.glassfish.jaxb:jaxb-runtime:2.3.3")
    implementation("org.bouncycastle:bcpkix-jdk15on:1.70")
    implementation("com.aliyun.oss:aliyun-sdk-oss:3.17.4")
    implementation("org.pcap4j:pcap4j-core:1.8.1")
    /* imageIO */
    implementation("com.twelvemonkeys.imageio:imageio-core:3.10.1")
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.10.1")

    // Google Gemini AI SDK
    implementation("com.google.genai:google-genai:1.43.0")

    // Resilience4j - RateLimiter + Retry
    implementation("io.github.resilience4j:resilience4j-ratelimiter:2.2.0")
    implementation("io.github.resilience4j:resilience4j-retry:2.2.0")
}

tasks.test {
    useJUnitPlatform()
}
