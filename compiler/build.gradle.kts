
import java.io.File
import org.gradle.api.tasks.bundling.Jar

apply { plugin("kotlin") }

jvmTarget = "1.6"

val compilerModules: Array<String> by rootProject.extra
val otherCompilerModules = compilerModules.filter { it != path }

val depDistProjects = listOf(
        ":kotlin-script-runtime",
        ":kotlin-stdlib",
        ":kotlin-reflect",
        ":kotlin-test:kotlin-test-jvm")

// TODO: it seems incomplete, find and add missing dependencies
val testDistProjects = listOf(
        "", // for root project
        ":prepare:mock-runtime-for-test",
        ":kotlin-compiler",
        ":kotlin-script-runtime",
        ":kotlin-stdlib",
        ":kotlin-stdlib-jre7",
        ":kotlin-stdlib-jre8",
        ":kotlin-stdlib-js",
        ":kotlin-reflect",
        ":kotlin-test:kotlin-test-jvm",
        ":kotlin-test:kotlin-test-junit",
        ":kotlin-test:kotlin-test-js",
        ":kotlin-daemon-client",
        ":kotlin-preloader",
        ":plugins:android-extensions-compiler",
        ":kotlin-ant",
        ":kotlin-annotations-jvm")

dependencies {
    depDistProjects.forEach {
        testCompile(projectDist(it))
    }
    testCompile(commonDep("junit:junit"))
    testCompileOnly(projectDist(":kotlin-test:kotlin-test-jvm"))
    testCompileOnly(projectDist(":kotlin-test:kotlin-test-junit"))
    testCompile(project(":compiler.tests-common"))
    testCompile(project(":compiler:tests-common-jvm6"))
    testCompile(project(":compiler:ir.ir2cfg"))
    testCompile(project(":compiler:ir.tree")) // used for deepCopyWithSymbols call that is removed by proguard from the compiler TODO: make it more straightforward
    otherCompilerModules.forEach {
        testCompileOnly(project(it))
    }
    testCompile(ideaSdkDeps("openapi", "idea", "util", "asm-all", "commons-httpclient-3.1-patched"))
    testRuntime(ideaSdkCoreDeps("*.jar"))
    testRuntime(ideaSdkDeps("*.jar"))
}

sourceSets {
    "main" {}
    "test" {
        projectDefault()
        // not yet ready
//        java.srcDir("tests-ir-jvm/tests")
    }
}

val jar: Jar by tasks
jar.apply {
    from(the<JavaPluginConvention>().sourceSets.getByName("main").output)
    from("../idea/src").apply {
        include("META-INF/extensions/common.xml",
                "META-INF/extensions/kotlin2jvm.xml",
                "META-INF/extensions/kotlin2js.xml")
    }
}

testsJar {}

projectTest {
    dependsOn(*testDistProjects.map { "$it:dist" }.toTypedArray())
    workingDir = rootDir
    systemProperty("kotlin.test.script.classpath", the<JavaPluginConvention>().sourceSets.getByName("test").output.classesDirs.joinToString(File.pathSeparator))
}

evaluationDependsOn(":compiler:tests-common-jvm6")

fun Project.codegenTest(taskName: String, jdk: String, body: Test.() -> Unit): Test = projectTest(taskName) {
    dependsOn(*testDistProjects.map { "$it:dist" }.toTypedArray())
    workingDir = rootDir
    environment("TEST_SERVER_CLASSES_DIRS", project(":compiler:tests-common-jvm6").the<JavaPluginConvention>().sourceSets.getByName("main").output.classesDirs.asPath)
    filter.includeTestsMatching("org.jetbrains.kotlin.codegen.CodegenJdkCommonTestSuite*")

    if (jdk == "JDK_9") {
        jvmArgs = listOf("--add-opens", "java.desktop/javax.swing=ALL-UNNAMED", "--add-opens", "java.base/java.io=ALL-UNNAMED")
    }
    body()
    doFirst {
        val jdkPath = project.property(jdk) ?: error("$jdk is not optional to run this test")
        executable = "$jdkPath/bin/java"
        println("Running test with $executable")
    }
}.also {
    task(taskName.replace(Regex("-[a-z]"), { it.value.takeLast(1).toUpperCase() })) {
        dependsOn(it)
        group = "verification"
    }
}

codegenTest("codegen-target6-jvm6-test", "JDK_18") {
    systemProperty("kotlin.test.default.jvm.target", "1.6")
    systemProperty("kotlin.test.java.compilation.target", "1.6")
    systemProperty("kotlin.test.box.in.separate.process.port", "5100")
}

codegenTest("codegen-target6-jvm9-test", "JDK_9") {
    systemProperty("kotlin.test.default.jvm.target", "1.6")
}

codegenTest("codegen-target8-jvm8-test", "JDK_18") {
    systemProperty("kotlin.test.default.jvm.target", "1.8")
}

codegenTest("codegen-target8-jvm9-test", "JDK_9") {
    systemProperty("kotlin.test.default.jvm.target", "1.8")
}

codegenTest("codegen-target9-jvm9-test", "JDK_9") {
    systemProperty("kotlin.test.default.jvm.target", "1.8")
    systemProperty("kotlin.test.substitute.bytecode.1.8.to.1.9", "true")
}
