/*
 * Checkout & Build 소스 파일
 * @author bruce_oh
 * @date 2020. 4. 7.
 */

node {

    // tool
    def javaHome
    def builderHome

    // properties
    def distDirectory

    // parameter
    def repoUserAuth
    def repoProjectPath
    def remoteBranch
    def buildType
    def buildCommand
    def jobActionList

    // variable
    def checkoutDo = false
    def buildDo = false

    
    stage('preparation') {
        printlnHeadMessage "preparation"

        withFolderProperties {
            // println "==================== Setting Tools ===================="

            try {   
                // println "${JDK_VERSION}"
                javaHome = tool "${JDK_VERSION}"
            } catch (MissingPropertyException e) {
                println "Error JDK_VERSION"
                throw e
            }

            try {
                // println "${BUILDER_VERSION}"
                builderHome = tool "${BUILDER_VERSION}"
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
            // println "REPO_PROJECT_PATH = $REPO_PROJECT_PATH"
            repoProjectPath = "$REPO_PROJECT_PATH"
        } catch (MissingPropertyException e) {
            println "Error REPO_PROJECT_PATH"
            throw e
        }

        try {
            // println "REMOTE_BRANCH = $REMOTE_BRANCH"
            remoteBranch = "$REMOTE_BRANCH"
        } catch (MissingPropertyException e) {
            println "Error REMOTE_BRANCH"
            throw e
        }

        try {
            // println "BUILD_TYPE = $BUILD_TYPE"
            buildType = "$BUILD_TYPE"
        } catch (MissingPropertyException e) {
            println "Error BUILD_TYPE"
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

        try {
            if (buildDo) {
                // println "BUILD_COMMAND = $BUILD_COMMAND"
                buildCommand = "$BUILD_COMMAND"
            }
        } catch (MissingPropertyException e) {
            println "Error BUILD_COMMAND"
            throw e
        }

    }
/*
 * Checkout & Build 소스 파일
 * @author bruce_oh
 * @date 2020. 4. 7.
 */

node {

    // tool
    def javaHome
    def mvnHome

    // properties
    def distDirectory

    // parameter
    def repoUserAuth
    def repoProjectPath
    def remoteBranch
    def jobActionList
    def mavenCommand

    // variable
    def checkoutDo = false
    def buildDo = false

    
    stage('preparation') {
        printlnHeadMessage "preparation"

        withFolderProperties {
            // println "==================== Setting Tools ===================="

            try {   
                // println "${JDK_VERSION}"
                javaHome = tool "${JDK_VERSION}"
            } catch (MissingPropertyException e) {
                println "Error JDK_VERSION"
                throw e
            }

            try {   
                // println "${MAVEN_VERSION}"
                mvnHome = tool "${MAVEN_VERSION}"
            } catch (MissingPropertyException e) {
                println "Error MAVEN_VERSION"
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
            // println "REPO_PROJECT_PATH = $REPO_PROJECT_PATH"
            repoProjectPath = "$REPO_PROJECT_PATH"
        } catch (MissingPropertyException e) {
            println "Error REPO_PROJECT_PATH"
            throw e
        }

        try {
            // println "REMOTE_BRANCH = $REMOTE_BRANCH"
            remoteBranch = "$REMOTE_BRANCH"
        } catch (MissingPropertyException e) {
            println "Error REMOTE_BRANCH"
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
        
        try {
            if (buildDo) {
                // println "MAVEN_COMMAND = $MAVEN_COMMAND"
                mavenCommand = "$MAVEN_COMMAND"
            }
        } catch (MissingPropertyException e) {
            println "Error MAVEN_COMMAND"
            throw e
        }

    }

    stage('checkout') {
        printlnHeadMessage "checkout" + (checkoutDo?"":" (Skip)")
        when(checkoutDo) {
            println "● checkout Started"
            doCheckout(repoProjectPath, repoUserAuth, remoteBranch)
            println "● checkout End"
        }
    }

    stage('build') {
        printlnHeadMessage "build" + (buildDo?"":" (Skip)")
        when(buildDo) {
            println "● build Started"
            doBuild(javaHome, mvnHome, distDirectory, mavenCommand)
            println "● build End"
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

void doBuild(javaHome, mvnHome, distDirectory, mavenCommand) {
    withEnv(["PATH+JAVA_HOME=$javaHome/bin", "PATH+MAVEN_HOME=$mvnHome/bin"]) {
        sh("java -version")
        sh("javac -version")

        sh("mvn -v")
        // "mvn -P dev -Dmaven.test.skip=true clean install"
        // "mvn -Dmaven.test.skip=true clean install"
        sh("${mavenCommand}")

        sh("T_DIR=${JENKINS_HOME}/dist/${distDirectory}; [ ! -L \"\$T_DIR\" -a ! -d \"\$T_DIR\" ] && mkdir -p \$T_DIR || echo \"already existed directory (\$T_DIR)\" || exit 0")
        sh("rsync -arhz --delete ./target/ ${JENKINS_HOME}/dist/${distDirectory}")

        // // sh("rm -rf ${JENKINS_HOME}/dist/${distDirectory}; mkdir -p ${JENKINS_HOME}/dist/${distDirectory}")
        // // sh("cp -a ./target/. ${JENKINS_HOME}/dist/${distDirectory}/")
    }
}

    stage('checkout') {
        printlnHeadMessage "checkout" + (checkoutDo?"":" (Skip)")
        when(checkoutDo) {
            println "● checkout Started"
            doCheckout(repoProjectPath, repoUserAuth, remoteBranch)
            println "● checkout End"
        }
    }

    stage('build') {
        printlnHeadMessage "build" + (buildDo?"":" (Skip)")
        when(buildDo) {
            println "● build Started"
            doBuild(javaHome, builderHome, buildType, buildCommand, distDirectory)
            println "● build End"
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

void doBuild(javaHome, builderHome, buildType, buildCommand, distDirectory) {

    def builderHomePath

    if('maven'.equalsIgnoreCase(buildType) || 'mvn'.equalsIgnoreCase(buildType)) {
        builderHomePath = "PATH+MAVEN_HOME=$builderHome/bin"
    } else if('gradle'.equalsIgnoreCase(buildType)) {
        builderHomePath = "PATH+GRADLE_HOME=$builderHome"
    }

    withEnv(["PATH+JAVA_HOME=$javaHome/bin", builderHomePath]) {
        sh("java -version")
        sh("javac -version")

        if('maven'.equalsIgnoreCase(buildType) || 'mvn'.equalsIgnoreCase(buildType)) {
            sh("mvn -v")
        } else if('gradle'.equalsIgnoreCase(buildType)) {
            sh("gradle -v")
        }

        // "mvn -P dev -Dmaven.test.skip=true clean install"
        // "mvn -Dmaven.test.skip=true clean install"
        sh("${buildCommand}")

        sh("T_DIR=${JENKINS_HOME}/dist/${distDirectory}; [ ! -L \"\$T_DIR\" -a ! -d \"\$T_DIR\" ] && mkdir -p \$T_DIR || echo \"already existed directory (\$T_DIR)\" || exit 0")
        sh("rsync -arhz --delete ./target/ ${JENKINS_HOME}/dist/${distDirectory}")

        // // sh("rm -rf ${JENKINS_HOME}/dist/${distDirectory}; mkdir -p ${JENKINS_HOME}/dist/${distDirectory}")
        // // sh("cp -a ./target/. ${JENKINS_HOME}/dist/${distDirectory}/")
    }
}