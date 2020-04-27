import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.vcsLabeling
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.maven
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2019.2"

project {
    buildType(FeaturesBuild)
    buildType(Build)
    buildType(IntegrationTest)
    buildType(PerformanceTest)
    buildType(AggregatedTests)
    buildType(DeployDev)
    buildType(AcceptanceTestDev)
    buildType(DeployTest)
}

object FeaturesBuild : BuildType({
    name = "Build - Features"

    vcs {
        root(DslContext.settingsRoot)
        branchFilter = """
                    -:<default>
                    +:JIRA-*
                """.trimIndent()
    }

    triggers {
        vcs {
        }
    }

    steps {
        maven {
            goals = "clean package"
        }
    }

})

object Build : BuildType({
    templates(AbsoluteId("MavenBuild"))
    name = "Build - Master"

    vcs {
        root(DslContext.settingsRoot)
        branchFilter = "+:<default>"
    }

    steps {
        maven {
            goals = "clean package"
        }
        script {
            scriptContent = "echo ##teamcity[jacocoReport dataPath='target/jacoco.exec' includes='org.springframework.samples.petclinic.*']"
        }
    }

    features {
        vcsLabeling {
            vcsRootId = "__ALL__"
            labelingPattern = "build/%system.build.number%"
            branchFilter = ""
        }
    }
})

object IntegrationTest : BuildType({
    name = "Test - Integration"
    buildNumberPattern = "${Build.depParamRefs["system.build.number"]}"

    vcs {
        root(DslContext.settingsRoot)
        branchFilter = "+:<default>"
    }

    steps {
        maven {
            goals = "clean test-compile failsafe:integration-test"
        }
    }

    dependencies {
        snapshot(Build) {}
    }

})

object PerformanceTest : BuildType({
    name = "Test - Performance"
    buildNumberPattern = "${Build.depParamRefs["system.build.number"]}"

    vcs {
        root(DslContext.settingsRoot)
        branchFilter = "+:<default>"
    }

    steps {
        maven {
            goals = "clean test"
        }
    }

    dependencies {
        snapshot(Build) {}
    }

})

object AggregatedTests : BuildType({
    name = "Test - Aggregated"
    buildNumberPattern = "${Build.depParamRefs["system.build.number"]}"
    type = Type.COMPOSITE

    vcs {
        root(DslContext.settingsRoot)
        branchFilter = "+:<default>"
    }

    dependencies {
        snapshot(IntegrationTest) {}
        snapshot(PerformanceTest) {}
    }

})

object DeployDev : BuildType({
    name = "Deploy - Dev"
    buildNumberPattern = "${Build.depParamRefs["system.build.number"]}"
    type = Type.DEPLOYMENT

    vcs {
        root(DslContext.settingsRoot)
        branchFilter = "+:<default>"
    }

    steps {
        script {
            scriptContent = "dir"
        }
    }

    dependencies {
        artifacts(Build) {
            buildRule = sameChainOrLastFinished()
            artifactRules = "**/*.jar"
        }
        snapshot(AggregatedTests) {}
    }

    features {
        vcsLabeling {
            vcsRootId = "__ALL__"
            labelingPattern = "deploy/dev/%system.build.number%"
            branchFilter = ""
        }
    }

})

object AcceptanceTestDev : BuildType({
    name = "Acceptance Test - Dev"
    buildNumberPattern = "${Build.depParamRefs["system.build.number"]}"

    vcs {
        root(DslContext.settingsRoot)
        branchFilter = "+:<default>"
    }

    triggers {
        vcs {
            watchChangesInDependencies = true
        }
    }

    steps {
        maven {
            goals = "clean test"
        }
    }

    dependencies {
        snapshot(DeployDev) {}
    }

})

object DeployTest : BuildType({
    name = "Deploy - Test"
    buildNumberPattern = "${Build.depParamRefs["system.build.number"]}"
    type = Type.DEPLOYMENT

    vcs {
        root(DslContext.settingsRoot)
        branchFilter = "+:<default>"
    }

    steps {
        script {
            scriptContent = "dir"
        }
    }

    dependencies {
        artifacts(Build) {
            buildRule = sameChainOrLastFinished()
            artifactRules = "**/*.jar"
        }
        snapshot(AcceptanceTestDev) {}
    }

    features {
        vcsLabeling {
            vcsRootId = "__ALL__"
            labelingPattern = "deploy/test/%system.build.number%"
            branchFilter = ""
        }
    }

})
