/*
 * Checkout & Build 소스 파일 (모듈)
 * @author bruce_oh
 * @date 2021. 1. 13.
 */

node {

    // tool
    def javaHome
    def nodeHome
    def builderHome

    // properties
    def distDirectory

    // parameter
    def repoUserAuth
    def moduleList = []
    def selectModulesSet = [] as Set
    def jobActionList

    // variable
    def repoProjectPath
    def remoteBranch
    def buildType
    def buildCommand
    def checkoutDo = false
    def buildDo = false

    
    stage('preparation') {
        printlnHeadMessage "preparation"

        withFolderProperties {
            // println "==================== Setting Tools ===================="

            def isNeedBuilderHome = true

            try {
                def emptyHome = true

                try {
                    // println "${JDK_VERSION}"
                    javaHome = tool "${JDK_VERSION}"
                    emptyHome = false
                } catch (MissingPropertyException e) {
                    println "JDK_VERSION is Empty"
                }

                try {
                    // println "${NODE_VERSION}"
                    nodeHome = tool "${NODE_VERSION}"
                    emptyHome = false
                    isNeedBuilderHome = false
                } catch (MissingPropertyException e) {
                    println "NODE_VERSION is Empty"
                }

                if (emptyHome) {
                    throw new MissingPropertyException("NEED SET JDK_VERSION or NODE_VERSION", getClass())
                }

            } catch (MissingPropertyException e) {
                println "NEED SET JDK_VERSION or NODE_VERSION"
                throw e
            }

            try {
                if (isNeedBuilderHome) {
                    // println "${BUILDER_VERSION}"
                    builderHome = tool "${BUILDER_VERSION}"
                }
            } catch (MissingPropertyException e) {
                println "Error BUILDER_VERSION"
                throw e
            }


            // println "==================== Setting Properties ===================="

            try {
                // println "${DIST_DIRECTORY}"
                distDirectory = "${DIST_DIRECTORY}"
            } catch (MissingPropertyException e) {
                println "Error DIST_DIRECTORY"
                throw e
            }
        }


        // println "==================== Setting Parameters ===================="

        try {
            // println "REPO_USER_AUTH = $REPO_USER_AUTH"
            repoUserAuth = "$REPO_USER_AUTH"
        } catch (MissingPropertyException e) {
            println "Error REPO_USER_AUTH"
            throw e
        }

        try {
            // println "MODULES_SET = $MODULES_SET"
            def modulesSet = readYaml text: "$MODULES_SET"
            if(modulesSet != null && modulesSet.modules != null) {
                moduleList = modulesSet.modules 
            }
        } catch (MissingPropertyException e) {
            println "Error MODULES_SET"
            throw e
        }

        try {
            // println "SELECT_MODULES = $SELECT_MODULES"
            def list = "$SELECT_MODULES"
            selectModulesSet = list.tokenize('\\,') as Set
        } catch (MissingPropertyException e) {
            println "Error SELECT_MODULES"
            throw e
        }

        try {
            // println "JOB_ACTION = $JOB_ACTION"
            def list = "$JOB_ACTION"
            jobActionList = list.tokenize('\\,')
            for (int i = 0; i < jobActionList.size(); i++) {
                // println "JOB Action $i : " + jobActionList[i]
                jobActionList[i] = jobActionList[i].trim().toLowerCase()

                if('checkout'.equalsIgnoreCase(jobActionList[i])) {
                    checkoutDo = true
                } else if('build'.equalsIgnoreCase(jobActionList[i])) {
                    buildDo = true
                }
            }
        } catch (MissingPropertyException e) {
            println "Error JOB_ACTION"
            throw e
        }

    }

    for (String moduleItem in moduleList) {

        def moduleName = moduleItem.name

        stage(moduleName+" : checkout") {
            printlnHeadMessage moduleName+" : checkout" + (checkoutDo?"":" (Skip)")
            when(checkoutDo) {
                println "● checkout $moduleName Started"
                if (selectModulesSet.contains(moduleName)) {

                    def checkoutItem = moduleItem.checkout

                    println " --- Module(checkout) : $moduleName ---"
                    
                    repoProjectPath = checkoutItem.remote
                    remoteBranch = checkoutItem.branch

                    doCheckout(repoProjectPath, repoUserAuth, remoteBranch)
                } else {
                    println " --- Module(checkout) : $moduleName (Skip) ---"
                }
                println "● checkout $moduleName End"
            }
        }

        stage(moduleName+" : build") {
            printlnHeadMessage moduleName+" : build" + (buildDo?"":" (Skip)")
            when(buildDo) {
                println "● build $moduleName Started"
                if(selectModulesSet.contains(moduleName)) {

                    def buildItem = moduleItem.build

                    println " --- Module(build) : $moduleName ---"
                    
                    buildType = buildItem.type
                    buildCommand = buildItem.command

                    doBuild(javaHome, nodeHome, builderHome, buildType, buildCommand, distDirectory)
                } else {
                    println " --- Module(build) : $moduleName (Skip) ---"
                }
                println "● build $moduleName End"
            }
        }

    }

}

void doCheckout(repoProjectPath, repoUserAuth, remoteBranch) {
    git(
        url: "$repoProjectPath",
        credentialsId: "$repoUserAuth",
        branch : "$remoteBranch"
    )
}


void doBuild(javaHome, nodeHome, builderHome, buildType, buildCommand, distDirectory) {

    if('maven'.equalsIgnoreCase(buildType) || 'mvn'.equalsIgnoreCase(buildType)) {
        doMavenBuild(javaHome, builderHome, buildCommand, distDirectory)
    } else if('gradle'.equalsIgnoreCase(buildType)) {
        doGradleBuild(javaHome, builderHome, buildCommand, distDirectory)
    } else if('yarn'.equalsIgnoreCase(buildType)) {
        doYarnBuild(nodeHome, buildCommand, distDirectory)
    } else if('npm'.equalsIgnoreCase(buildType)) {
        doNpmBuild(nodeHome, buildCommand, distDirectory)
    } else {
        println "● No Matching BUILD_TYPE."
    }
}


void doMavenBuild(javaHome, builderHome, buildCommand, distDirectory) {
    withEnv(["PATH+JAVA_HOME=$javaHome/bin", "PATH+MAVEN_HOME=$builderHome/bin"]) {
        sh("java -version")
        sh("javac -version")
        
        sh("mvn -v")

        // "mvn -P dev -Dmaven.test.skip=true clean install"
        // "mvn -Dmaven.test.skip=true clean install"
        sh("${buildCommand}")

        sh("T_DIR=${JENKINS_HOME}/dist/${distDirectory}; [ ! -L \"\$T_DIR\" -a ! -d \"\$T_DIR\" ] && mkdir -p \$T_DIR || echo \"already existed directory (\$T_DIR)\" || exit 0")
        sh("rsync -arhz --delete ./target/ ${JENKINS_HOME}/dist/${distDirectory}")

        // // sh("rm -rf ${JENKINS_HOME}/dist/${distDirectory}; mkdir -p ${JENKINS_HOME}/dist/${distDirectory}")
        // // sh("cp -a ./target/. ${JENKINS_HOME}/dist/${distDirectory}/")
    }
}


void doGradleBuild(javaHome, builderHome, buildCommand, distDirectory) {
    withEnv(["PATH+JAVA_HOME=$javaHome/bin", "PATH+GRADLE_HOME=$builderHome/bin"]) {
        sh("java -version")
        sh("javac -version")

        sh("gradle -v")

        // "gradle clean build"
        sh("${buildCommand}")

        sh("T_DIR=${JENKINS_HOME}/dist/${distDirectory}; [ ! -L \"\$T_DIR\" -a ! -d \"\$T_DIR\" ] && mkdir -p \$T_DIR || echo \"already existed directory (\$T_DIR)\" || exit 0")
        sh("find ./build/libs/ -maxdepth 1 -type f -iregex '.*\\.\\(war\\|jar\\)' -exec cp '{}' ./build/ \\; || exit 0")
        // sh("cp -f ./build/libs/*.war ./build/ || exit 0")
        // sh("cp -f ./build/libs/*.jar ./build/ || exit 0")
        sh("rsync -arhz --delete ./build/ ${JENKINS_HOME}/dist/${distDirectory}")

        // // sh("rm -rf ${JENKINS_HOME}/dist/${distDirectory}; mkdir -p ${JENKINS_HOME}/dist/${distDirectory}")
        // // sh("cp -a ./build/. ${JENKINS_HOME}/dist/${distDirectory}/")
    }
}


void doYarnBuild(nodeHome, buildCommand, distDirectory) {

    withEnv(["PATH+NODEJS_HOME=$nodeHome/bin"]) {
        sh("node -v")

        sh("yarn -v")

        // "yarn --cwd /home/brucebatch/app/bruce-batch run clean; yarn --cwd /home/brucebatch/app/bruce-batch run build"
        // "yarn install"
        sh("${buildCommand}")

        sh("T_DIR=${JENKINS_HOME}/dist/${distDirectory}; [ ! -L \"\$T_DIR\" -a ! -d \"\$T_DIR\" ] && mkdir -p \$T_DIR || echo \"already existed directory (\$T_DIR)\" || exit 0")
        sh("rsync -arhz --delete . ${JENKINS_HOME}/dist/${distDirectory}")

        // // sh("rm -rf ${JENKINS_HOME}/dist/${distDirectory}; mkdir -p ${JENKINS_HOME}/dist/${distDirectory}")
        // // sh("cp -a . ${JENKINS_HOME}/dist/${distDirectory}/")
    }
}


void doNpmBuild(nodeHome, buildCommand, distDirectory) {
    withEnv(["PATH+NODEJS_HOME=$nodeHome/bin"]) {
        sh("node -v")
        
        sh("npm -v")

        // "npm run clean; npm run build"
        // "npm install"
        sh("${buildCommand}")

        sh("T_DIR=${JENKINS_HOME}/dist/${distDirectory}; [ ! -L \"\$T_DIR\" -a ! -d \"\$T_DIR\" ] && mkdir -p \$T_DIR || echo \"already existed directory (\$T_DIR)\" || exit 0")
        sh("rsync -arhz --delete . ${JENKINS_HOME}/dist/${distDirectory}")

        // // sh("rm -rf ${JENKINS_HOME}/dist/${distDirectory}; mkdir -p ${JENKINS_HOME}/dist/${distDirectory}")
        // // sh("cp -a . ${JENKINS_HOME}/dist/${distDirectory}/")
    }
}