plugins {
    java
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    // Paper API chỉ cần khi biên dịch, server đã có sẵn lúc chạy
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
        // Hiện chi tiết API deprecated (nếu có) để dễ sửa
        options.compilerArgs.add("-Xlint:deprecation")
    }

    processResources {
        filteringCharset = "UTF-8"
        // Chỉ thay ${version} trong plugin.yml, không đụng tới các yml khác
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }

    jar {
        archiveBaseName.set("UltimateWeapons")
    }
}
