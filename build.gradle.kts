import net.minecraftforge.gradle.user.UserBaseExtension
import java.util.*

val kotlin_version = "1.5.0"

buildscript {
	
    repositories {
        mavenCentral()
        jcenter()
        maven {
            name = "forge"
            setUrl("https://maven.minecraftforge.net/")
        }
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version")
        classpath("net.minecraftforge.gradle.forge:ForgeGradle:3.+")
    }
}

apply {
	plugin("kotlin")
	plugin("java")
	plugin("java-library")
	plugin("net.minecraftforge.gradle.forge")
}

val config: Properties = file("build.properties").inputStream().let {
    val prop = Properties()
    prop.load(it)
    return@let prop
}

val mcVersion = config["minecraft.version"] as String
val forge_version = "$mcVersion-${config["forge.version"]}"
val shortVersion = mcVersion.substring(0, mcVersion.lastIndexOf("."))
val strippedVersion = shortVersion.replace(".", "") + "0"
val mappings_version = config["mappings.version"] as String

val lpVersion = config["lp.ver"] as String
val neiVersion = config["nei.ver"] as String
val mcmpVersion = config["mcmp.ver"] as String
val cofhcVersion = config["cofhc.ver"] as String
val cofhwVersion = config["cofhw.ver"] as String
val rfVersion = config["rf.ver"] as String
val tfVersion = config["tf.ver"] as String
val teVersion = config["te.ver"] as String
val tdVersion = config["td.ver"] as String
val rsVersion = config["rs.ver"] as String
val aeVersion = config["ae.ver"] as String

val modVersion = getVersionFromJava(file("src/main/java/com/tom/logisticsbridge/LBVersion.java"))
version = "$mcVersion-$modVersion"

minecraft {
    mappings = "snapshot"
    version = mappings_version
    runs {
        client {
            runDir = "run"

            property("forge.logging.markers", "SCAN,REGISTRIES,REGISTRYDUMP")
            property("forge.logging.console.level", "debug")
        }

        server {
            runDir = "run-server"

            property("forge.logging.markers", "SCAN,REGISTRIES,REGISTRYDUMP")
            property("forge.logging.console.level", "debug")
        }
    }
}


repositories {
    jcenter()
    maven {
        name = "JitPack.io"
        setUrl("https://jitpack.io")
    }
    maven {
        name = "forge"
        setUrl("https://files.minecraftforge.net/maven")
    }
    maven {
        name = "forge"
        setUrl("https://libraries.minecraft.net")
    } 
    flatDir { dirs("./lib") }
}

configurations {
    shade
    implementation.extendsFrom(shade)
}

dependencies {
    implementation(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = kotlin_version)
    implementation(group = "org.jetbrains.kotlin", name = "kotlin-stdlib-jdk8", version = kotlin_version)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.0")
    //
    minecraft(group = "net.minecraftforge", name = "forge", version = forge_version)
    //
    compileOnly(":logisticspipes-$lpVersion-api")
    compileOnly(":NotEnoughItems-$mcVersion-$neiVersion")
    implementation(":logisticspipes-$lpVersion-deobf")
    api fg.deobf(":MCMultiPart-$mcmpVersion")
    api fg.deobf(":ThermalDynamics-$mcVersion-$tdVersion")
    api fg.deobf(":refinedstorage-$rsVersion")
    api fg.deobf(":appliedenergistics2-$aeVersion")
    //
    runtimeOnly(":refinedstorage-$rsVersion")
    runtimeOnly(":MCMultiPart-$mcmpVersion")
    runtimeOnly(":ThermalExpansion-$mcVersion-$teVersion")
    runtimeOnly(":ThermalFoundation-$mcVersion-$tfVersion")
    runtimeOnly(":CoFHCore-$mcVersion-$cofhcVersion")
    runtimeOnly(":CoFHWorld-$mcVersion-$cofhwVersion")
    runtimeOnly(":RedstoneFlux-1.12-$rfVersion")
    runtimeOnly(":CodeChickenLib-$mcVersion")
    // https://mvnrepository.com/artifact/javassist/javassist
    shade(group = "javassist", name = "javassist", version = "3.12.1.GA")
}

tasks.withType(Wrapper) {
    version = 4.9
}

val processResources: ProcessResources by tasks
val sourceSets: SourceSetContainer = the<JavaPluginConvention>().sourceSets

processResources.apply {
    inputs.property("version", modVersion)
    inputs.property("mcversion", mcFullVersion)

    from(sourceSets["main"].resources.srcDirs) {
        include("mcmod.info")
        expand(mapOf("version" to modVersion,
            "mcversion" to mcFullVersion))
    }

    // copy everything else, thats not the mcmod.info
    from(sourceSets["main"].resources.srcDirs) {
        exclude("mcmod.info")
    }
    // access transformer
    rename("(.+_at.cfg)", "META-INF/$1")
}

val jar: Jar by tasks
jar.apply {
    configurations.shade.each { dep ->
        from(project.zipTree(dep)) {
            exclude("META-INF", "META-INF/**")
        }
    }
}

jar.finalizedBy("reobfJar")
