import de.undercouch.gradle.tasks.download.*
import org.jetbrains.kotlin.gradle.plugin.mpp.*
import org.jetbrains.kotlin.konan.target.*

plugins {
	kotlin("multiplatform")
	id("de.undercouch.download")
	`maven-publish`
}

val glfwVersion = "3.3.6"
val downloadsDir = buildDir.resolve("downloads")
val glfwWin64Dir = downloadsDir.resolve("glfw.bin.WIN64")
val glfwMacosDir = downloadsDir.resolve("glfw.bin.MACOS")
val glfwMacosArmDir = downloadsDir.resolve("glfw.bin.MACOS-ARM4")

val downloadWin64Binaries by tasks.registering(Download::class) {
	src("https://github.com/glfw/glfw/releases/download/$glfwVersion/glfw-$glfwVersion.bin.WIN64.zip")
	dest(downloadsDir.resolve("glfw.bin.WIN64.zip"))

	overwrite(false)
}

val downloadMacOSBinaries by tasks.registering(Download::class) {
	src("https://github.com/glfw/glfw/releases/download/$glfwVersion/glfw-$glfwVersion.bin.MACOS.zip")
	dest(downloadsDir.resolve("glfw.bin.MACOS.zip"))

	overwrite(false)
}

val unzipWin64Binaries by tasks.registering(Copy::class) {
	from(downloadWin64Binaries.map { zipTree(it.dest) }) {
		eachFile {
			relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
		}
		include("glfw-*/include/**", "glfw-*/lib-mingw-w64/**")

		includeEmptyDirs = false
	}
	into(glfwWin64Dir)
}

val unzipMacOSBinaries by tasks.registering(Copy::class) {
	from(downloadMacOSBinaries.map { zipTree(it.dest) }) {
		eachFile {
			relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
		}
		include("glfw-*/include/**", "glfw-*/lib-x86_64/**")

		includeEmptyDirs = false
	}
	into(glfwMacosDir)
}

val unzipMacOSArm64Binaries by tasks.registering(Copy::class) {
	from(downloadMacOSBinaries.map { zipTree(it.dest) }) {
		eachFile {
			relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
		}
		include("glfw-*/include/**", "glfw-*/lib-arm64/**")

		includeEmptyDirs = false
	}
	into(glfwMacosArmDir)
}

val useSingleTarget: Boolean by rootProject.extra
val lwjglVersion: String by rootProject.extra
val lwjglNatives: String by rootProject.extra

kotlin {
	jvm {
		compilations.all {
			kotlinOptions.jvmTarget = "1.8"
		}
	}

	evaluationDependsOn(":kgl-vulkan")
	val vulkanUnzipDocs = project(":kgl-vulkan").tasks.named<Copy>("unzipDocs")
	val vulkanHeaderDir = vulkanUnzipDocs.map { it.destinationDir.resolve("include") }

	if (!useSingleTarget || HostManager.hostIsLinux) linuxX64("linux")
	if (!useSingleTarget || HostManager.hostIsMac) macosX64("macos")
	if (!useSingleTarget || HostManager.hostIsMac) macosArm64("macosArm64")
	if (!useSingleTarget || HostManager.hostIsMingw) mingwX64("mingw")

	targets.withType<KotlinNativeTarget> {
		compilations.named("main") {
			cinterops.create("cglfw") {
				tasks.named(interopProcessingTaskName) {
					dependsOn(vulkanUnzipDocs)
					if (konanTarget == KonanTarget.MACOS_X64) {
						dependsOn(unzipMacOSBinaries)
					}
					if (konanTarget == KonanTarget.MACOS_ARM64) {
						dependsOn(unzipMacOSArm64Binaries)
					}
					if (konanTarget == KonanTarget.MINGW_X64) {
						dependsOn(unzipWin64Binaries)
					}
				}
				includeDirs(vulkanHeaderDir)
				if (konanTarget == KonanTarget.MACOS_X64) {
					includeDirs(unzipMacOSBinaries.map { it.destinationDir.resolve("include") })
				}
				if (konanTarget == KonanTarget.MACOS_ARM64) {
					includeDirs(unzipMacOSArm64Binaries.map { it.destinationDir.resolve("include") })
				}
				if (konanTarget == KonanTarget.MINGW_X64) {
					includeDirs(unzipWin64Binaries.map { it.destinationDir.resolve("include") })
				}
			}
		}
	}

	sourceSets {
		commonMain {
			dependencies {
				implementation(kotlin("stdlib-common"))
				api(project(":kgl-core"))
			}
		}

		commonTest {
			dependencies {
				implementation(kotlin("test-common"))
				implementation(kotlin("test-annotations-common"))
			}
		}

		named("jvmMain") {
			dependencies {
				api("org.lwjgl:lwjgl-glfw:$lwjglVersion")
			}
		}

		named("jvmTest") {
			dependencies {
				implementation(kotlin("test-junit"))
				implementation("org.lwjgl:lwjgl:$lwjglVersion:$lwjglNatives")
				implementation("org.lwjgl:lwjgl-glfw:$lwjglVersion:$lwjglNatives")
			}
		}

		targets.withType<KotlinNativeTarget> {
			named("${name}Main") {
				kotlin.srcDir("src/nativeMain/kotlin")
				resources.srcDir("src/nativeMain/resources")
			}

			named("${name}Test") {
				kotlin.srcDir("src/nativeTest/kotlin")
				resources.srcDir("src/nativeTest/resources")
				dependencies {
					implementation(project(":kgl-glfw-static"))
				}
			}
		}
	}
}
