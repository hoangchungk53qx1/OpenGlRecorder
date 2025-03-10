import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.EnumSet

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.dagger.hilt.android) apply false
//    alias(libs.plugins.gms) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.gradle.spotless) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kotlinx.kover) apply false
}

subprojects {
    tasks.withType<KotlinCompile> {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    tasks.withType<KotlinCompilationTask<*>> {
        compilerOptions {
            freeCompilerArgs.addAll(
                listOf(
                    "-opt-in=kotlin.RequiresOptIn",
                    "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                    "-opt-in=kotlinx.coroutines.FlowPreview",
                ),
            )
        }
    }
}

allprojects {
    // Apply the spotless plugin to all subprojects
    apply<com.diffplug.gradle.spotless.SpotlessPlugin>()
    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        val ktlintVersion = rootProject.libs.versions.ktlint.get()

        kotlin {
            target("**/*.kt")
            targetExclude("**/build/**/*.kt", "**/.gradle/**/*.kt")

            ktlint(ktlintVersion)

            trimTrailingWhitespace()
            indentWithSpaces()
            endWithNewline()
        }

        format("xml") {
            target("**/res/**/*.xml")
            targetExclude("**/build/**/*.xml", "**/.idea/**/*.xml", "**/.gradle/**/*.xml")

            trimTrailingWhitespace()
            indentWithSpaces()
            endWithNewline()
            lineEndings = com.diffplug.spotless.LineEnding.UNIX
        }

        kotlinGradle {
            target("**/*.gradle.kts", "*.gradle.kts")
            targetExclude("**/build/**/*.kts", "**/.gradle/**/*.kts")

            ktlint(ktlintVersion)

            trimTrailingWhitespace()
            indentWithSpaces()
            endWithNewline()
        }
    }

    // Configure the test task to run in parallel
    afterEvaluate {
        tasks.withType<Test> {
            maxParallelForks =
                (Runtime.getRuntime().availableProcessors() / 2)
                    .coerceAtLeast(1)
                    .also { println("Setting maxParallelForks to $it") }
            testLogging {
                showExceptions = true
                showCauses = true
                showStackTraces = true
                showStandardStreams = true
                events =
                    EnumSet.of(
                        TestLogEvent.PASSED,
                        TestLogEvent.FAILED,
                        TestLogEvent.SKIPPED,
                        TestLogEvent.STANDARD_OUT,
                        TestLogEvent.STANDARD_ERROR,
                    )
                exceptionFormat = TestExceptionFormat.FULL
            }
        }
    }

    // Apply the detekt plugin to all subprojects
    apply<io.gitlab.arturbosch.detekt.DetektPlugin>()
    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        source.from(files("src/"))
        config.from(files("${rootProject.projectDir}/config/detekt/detekt.yml"))
        allRules = true
        buildUponDefaultConfig = true
        parallel = true
        autoCorrect = true
    }
    afterEvaluate {
        dependencies {
            "detektPlugins"(rootProject.libs.compose.rules.detekt)
        }
    }
}
