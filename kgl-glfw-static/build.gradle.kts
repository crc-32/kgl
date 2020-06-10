import org.jetbrains.kotlin.konan.target.KonanTarget

plugins {
	kotlin("multiplatform")
	`maven-publish`
}

evaluationDependsOn(":kgl-glfw")
val unzipWin64Binaries by project(":kgl-glfw").tasks.getting(Copy::class)
val unzipWin32Binaries by project(":kgl-glfw").tasks.getting(Copy::class)
val unzipMacOSBinaries by project(":kgl-glfw").tasks.getting(Copy::class)

kotlin {
	val staticLibs = mapOf(
			KonanTarget.MINGW_X64 to unzipWin64Binaries.destinationDir.resolve("lib-mingw-w64/libglfw3.a"),
			KonanTarget.MINGW_X86 to unzipWin32Binaries.destinationDir.resolve("lib-mingw-w64/libglfw3.a"),
			KonanTarget.LINUX_X64 to file("/usr/local/lib/libglfw3.a"),
			KonanTarget.MACOS_X64 to unzipMacOSBinaries.destinationDir.resolve("lib-macos/libglfw3.a")
	)

	configure(listOf(mingwX64(), mingwX86(), linuxX64(), macosX64())) {
		compilations {
			"main" {
				when (konanTarget) {
					KonanTarget.MINGW_X64 -> compileKotlinTask.dependsOn(unzipWin64Binaries)
					KonanTarget.MINGW_X86 -> compileKotlinTask.dependsOn(unzipWin32Binaries)
					KonanTarget.MACOS_X64 -> compileKotlinTask.dependsOn(unzipMacOSBinaries)
					else -> {}
				}
				val staticLib = staticLibs.getValue(konanTarget)
				kotlinOptions {
					freeCompilerArgs = listOf("-include-binary", staticLib.absolutePath)
				}
			}
		}
	}

	sourceSets {
		commonMain {
			dependencies {
				implementation(kotlin("stdlib-common"))
			}
		}
		commonTest {
			dependencies {
				implementation(kotlin("test-common"))
				implementation(kotlin("test-annotations-common"))
			}
		}
	}
}
