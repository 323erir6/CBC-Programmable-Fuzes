plugins {
    java
    id("net.neoforged.moddev") version "2.0.80"
}

version = property("mod_version")!!
group = "dev.cbcprogrammablefuzes"

base {
    archivesName.set("cbc-programmable-fuzes")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://maven.ryanhcode.dev/releases")
}

val targetInstanceMods = file("${System.getenv("APPDATA")}/ElyPrismLauncher/instances/6.9.3/minecraft/mods")

fun targetMod(name: String) = targetInstanceMods.resolve(name)

neoForge {
    version = property("neo_version") as String
    mods {
        create("cbc_programmable_fuzes") {
            sourceSet(sourceSets.main.get())
        }
    }
    runs {
        create("server") {
            server()
            gameDirectory = file("run-server")
            programArgument("--nogui")
        }
    }
}

dependencies {
    compileOnly(files(targetMod("create-1.21.1-6.0.10.jar")))
    compileOnly(files("../_deps_CreateBigCannons/createbigcannons-5.11.7+mc.1.21.1.jar"))
    compileOnly(files("../_deps_CreateBigCannons/ponder-neoforge-1.0.82+mc1.21.1.jar"))
    compileOnly(files(targetMod("cc-tweaked-1.21.1-forge-1.120.0.jar")))
    compileOnly(files(targetMod("sable-neoforge-1.21.1-2.0.5.jar")))
    compileOnly(files("../_tmp_selected_mods/CBCmoreshells-1.5.1.jar"))
    compileOnly("dev.ryanhcode.sable-companion:sable-companion-common-1.21.1:1.6.0")

    runtimeOnly(files(targetMod("create-1.21.1-6.0.10.jar")))
    runtimeOnly(files(targetMod("createbigcannons-5.11.7+mc.1.21.1.jar")))
    runtimeOnly(files(targetMod("ritchiesprojectilelib-2.1.2+mc.1.21.1-neoforge.jar")))
    runtimeOnly(files(targetMod("cc-tweaked-1.21.1-forge-1.120.0.jar")))
    runtimeOnly(files(targetMod("sable-neoforge-1.21.1-2.0.5.jar")))
    runtimeOnly(files(targetMod("neoperipheral-1.4ve.jar")))
    runtimeOnly(files(targetMod("cbc_compact_mount-1.2.2.jar")))
    runtimeOnly(files("../_tmp_selected_mods/CBCmoreshells-1.5.1.jar"))
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand("version" to project.version)
    }
}

sourceSets.test {
    compileClasspath += sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().compileClasspath
}
val optimizationRegressionTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("ua.ivan.cbcprogrammablefuzes.ProximityOptimizationTest")
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    })
}
tasks.check { dependsOn(optimizationRegressionTest) }
