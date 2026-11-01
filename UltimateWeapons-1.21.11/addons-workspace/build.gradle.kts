// Cấu hình CHUNG cho mọi module addon. Module con không cần build.gradle.kts riêng.

// Chỉ các module thật sự là addon (có thư mục src/); project trung gian ":addons" bị bỏ qua
val addonProjects = subprojects.filter { it.projectDir.resolve("src").isDirectory }

configure(addonProjects) {
    apply(plugin = "java")

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "papermc"
        }
    }

    dependencies {
        // Paper API và jar plugin lõi chỉ cần khi biên dịch, server đã có sẵn lúc chạy
        "compileOnly"("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
        // Jar plugin lõi đã build: copy vào libs/UltimateWeapons.jar
        "compileOnly"(files(rootProject.file("libs/UltimateWeapons.jar")))
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    tasks.withType<ProcessResources>().configureEach {
        filteringCharset = "UTF-8"
    }

    // Tên jar = tên module, không kèm version (ví dụ hello_stick.jar)
    tasks.withType<Jar>().configureEach {
        archiveBaseName.set(project.name)
        archiveVersion.set("")
    }
}

// ./gradlew collectAddons: build mọi module rồi copy jar vào build/addons/
tasks.register<Copy>("collectAddons") {
    group = "build"
    description = "Build mọi addon và copy jar vào build/addons/"
    into(layout.buildDirectory.dir("addons"))
    addonProjects.forEach { addon ->
        from(addon.tasks.named("jar"))
    }
}
