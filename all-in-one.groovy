/*
 * 전체 과정 통합 배포 (All in one)
 * @author bruce_oh
 * @date 2021. 1. 11.
 */

node {

    /*
     * 
     * (순서)
     * 
     * 실행
     * 
     * ---> 준비(설정 값 확인)
     * ---> checkout ---> build 
     * ---> (진행 여부 선택) ---> HealthCheck(L7) OFF
     * ---> nginx STOP ---> Tomcat STOP
     * ---> 경고 디렉토리 확인 ---> 최근 백업 파일 temp로 이동 ---> 백업
     * ---> 소스 동기화(업로드, 배포) ---> 최종 동기화 버전 저장
     * ---> Tomcat START ---> nginx START
     * ---> (진행 여부 선택) ---> HealthCheck(L7) ON
     * 
     * 완료
     * 
     * 
     * (순서 구조)
     * 
     * checkout
     * build
     * 
     * group 1~n (begin)
     *     health check off
     * 
     *     nginx stop
     *     tomcat stop
     * 
     *     source deploy
     * 
     *     tomcat start
     *     nginx start
     * 
     *     health check on
     * group 1~n (end)
     * 
     */

    // tool
    def javaHome
    def nodeHome
    def builderHome

    // properties
    def allGroupNames
    def allServers
    def distDirectory
    def waitSecForHealthCheckOff = 3
    def keepBackupDays = 30
    def ownerName
    
    // params (common)
    def remoteUserAuth
    def selectServers
    def serverGroupList = [:]
    def serverList = []
    def jobActionList

    // params (checkout & build)
    def repoUserAuth
    def repoProjectPath
    def remoteBranch
    def buildType
    def buildCommand

    // params (switch healthcheck mode)
    def healthCheckPath

    // params (deploy)
    def appPath
    def syncType

    // params (server)
    def nginxAlias
    def tomcatAlias

    // variable
    def checkoutDo = false
    def buildDo = false
    def switchHealthCheckDo = false
    def nginxDo = false
    def tomcatDo = false
    def deployDo = false
    def rsyncDeployDo = false
    def warDeployDo = false
    def jarDeployDo = false
    def appDocBase
    def healthCheckOff = "SERVER OFF"
    def healthCheckOn = "SERVER ON"


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
                // println "ALL_GROUP_NAMES = $ALL_GROUP_NAMES"
                // // "GROUP-1,GROUP-2,GROUP-3"
                allGroupNames = "${ALL_GROUP_NAMES}".tokenize('\\,')
            } catch (MissingPropertyException e) {
                println "Missing Property - ALL_GROUP_NAMES"
                allGroupNames = []
            }

            try {
                // println "ALL_SERVERS = $ALL_SERVERS"
                allServers = "${ALL_SERVERS}".tokenize('\\,')
            } catch (MissingPropertyException e) {
                println "Missing Property - ALL_SERVERS"
                allServers = []
            }

            try {
                // println "${DIST_DIRECTORY}"
                distDirectory = "${DIST_DIRECTORY}"
            } catch (MissingPropertyException e) {
                println "Error DIST_DIRECTORY"
                throw e
            }

            try {
                // println "${WAIT_SEC_FOR_HEALTHCHECK_OFF}"
                waitSecForHealthCheckOff = "${WAIT_SEC_FOR_HEALTHCHECK_OFF}"
            } catch (MissingPropertyException e) {
                println "Error WAIT_SEC_FOR_HEALTHCHECK_OFF"
                throw e
            }
            
            try {
                // println "$KEEP_BACKUP_DAYS"
                def keepingdays = "${KEEP_BACKUP_DAYS}" as Integer
                keepBackupDays = keepingdays
            } catch (NumberFormatException e) {
                println "KEEP_BACKUP_DAYS is not number, so to be set default $keepBackupDays"
            } catch (MissingPropertyException e) {
                println "KEEP_BACKUP_DAYS is Empty, so to be set default $keepBackupDays"
            } catch (Exception e) {
                println "KEEP_BACKUP_DAYS is to be set default $keepBackupDays"
            }

            try {
                // println "$OWNER_NAME"
                ownerName = "${OWNER_NAME}"
            } catch (MissingPropertyException e) {
                println "Missing Property - OWNER_NAME"
                ownerName = ''
            }

        }


        // println "==================== Setting Parameters ===================="

        try {
            // println "REMOTE_USER_AUTH = $REMOTE_USER_AUTH"
            remoteUserAuth = "$REMOTE_USER_AUTH"
        } catch (MissingPropertyException e) {
            println "Error REMOTE_USER_AUTH"
            throw e
        }

        try {
            // println "NGINX_ALIAS = $NGINX_ALIAS"
            nginxAlias = "$NGINX_ALIAS"
        } catch (MissingPropertyException e) {
            println "Error NGINX_ALIAS"
            throw e
        }

        try {
            // println "TOMCAT_ALIAS = $TOMCAT_ALIAS"
            tomcatAlias = "$TOMCAT_ALIAS"
        } catch (MissingPropertyException e) {
            println "Error TOMCAT_ALIAS"
            throw e
        }

        try {
            // println "SELECT_SERVERS = $SELECT_SERVERS"
            selectServers = "$SELECT_SERVERS"
        } catch (MissingPropertyException e) {
            println "Error SELECT_SERVERS"
            throw e
        }

        try {
            if (selectServers.toUpperCase().equals("LIST")) {
                serverList = "$SERVER_LIST".tokenize('\\,')
                for (int i = 0; i < serverList.size(); i++) {
                    // println "Server $i : $serverList[i]"
                    serverList[i] = serverList[i].trim()
                }
            } else if(allGroupNames.size() < 1 && selectServers.toUpperCase().equals("ALL")) {
                for (int i = 0; i < allServers.size(); i++) {
                    // println "Server $i : $allServers[i]"
                    serverList[i] = allServers[i].trim()
                }
            } else {
                serverGroupList = getServerGroupList(selectServers, allGroupNames)
            }

            if(serverList.size() < 1 && serverGroupList.size() < 1) {
                throw new MissingPropertyException("SERVER_LIST or SERVER_GROUP_LIST", getClass())
            }

        } catch (MissingPropertyException e) {
            println "Error SERVER_LIST or SERVER_GROUP_LIST"
            throw e
        }

        // server job action 파라미터에 추가 필요
        // server job action 에 따라 nginx, tomcat 구분

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
                } else if('healthcheck'.equalsIgnoreCase(jobActionList[i])) {
                    switchHealthCheckDo = true
                } else if('nginx'.equalsIgnoreCase(jobActionList[i])) {
                    nginxDo = true
                } else if('tomcat'.equalsIgnoreCase(jobActionList[i])) {
                    tomcatDo = true
                } else if('deploy'.equalsIgnoreCase(jobActionList[i])) {
                    deployDo = true
                }                
            }
        } catch (MissingPropertyException e) {
            println "Error JOB_ACTION"
            throw e
        }

        




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
            if (buildDo) {
                // println "BUILD_COMMAND = $BUILD_COMMAND"
                buildCommand = "$BUILD_COMMAND"    
            }
        } catch (MissingPropertyException e) {
            println "Error BUILD_COMMAND"
            throw e
        }



        try {
            // println "HEALTH_CHECK_PATH = $HEALTH_CHECK_PATH"
            healthCheckPath = "$HEALTH_CHECK_PATH"
        } catch (MissingPropertyException e) {
            println "Error HEALTH_CHECK_PATH"
            throw e
        }

        

        try {
            // println "APP_PATH = $APP_PATH"
            appPath = "$APP_PATH"
            def list = appPath.tokenize('\\/')
            appDocBase = list[list.size()-1]
        } catch (MissingPropertyException e) {
            println "Error APP_PATH"
            throw e
        }

        try {
            // println "SYNC_TYPE = $SYNC_TYPE"
            syncType = "$SYNC_TYPE"
            def type = syncType.tokenize('\\(')[0].trim().toLowerCase()
            // println "type = $type"
            if('rsync'.equalsIgnoreCase(type)) {
                rsyncDeployDo = true
            } else if('war'.equalsIgnoreCase(type)) {
                warDeployDo = true
            } else if('jar'.equalsIgnoreCase(type)) {
                jarDeployDo = true
            }
        } catch (MissingPropertyException e) {
            println "Error SYNC_TYPE"
            throw e
        }



        if(serverList.size() < 1 && serverGroupList.size() < 1) {
            switchHealthCheckDo = false
            nginxDo = false
            tomcatDo = false
            rsyncDeployDo = false
            warDeployDo = false
            jarDeployDo = false
            deployDo = false
        }

        if(!deployDo) {
            rsyncDeployDo = false
            warDeployDo = false
            jarDeployDo = false   
        }
    }


    // SOURCE -- start

    stage('checkout') {
        printlnHeadMessage "checkout" + (checkoutDo?"":" (Skip)")
        when(checkoutDo) {
            println "● checkout Started"
            doCheckout(repoProjectPath, repoUserAuth, remoteBranch)
            println "● checkout End"
            println "************************************************************"
            env.COMMIT_ID = sh (script: 'git log -1 --pretty=%h', returnStdout: true).trim()
            env.COMMIT_MSG = sh (script: 'git log -1 --pretty=%B', returnStdout: true).trim()
            env.COMMIT_AUTHOR = sh (script: 'git log -1 --pretty=%cn', returnStdout: true).trim()
            println "************************************************************"
        }
        
    }

    stage('build') {
        printlnHeadMessage "build" + (buildDo?"":" (Skip)")
        when(buildDo) {
            println "● build Started"
            doBuild(javaHome, nodeHome, builderHome, buildType, buildCommand, distDirectory)
            println "● build End"
        }
    }

    // SOURCE -- end



    // L7 OFF -- nginx STOP -- tomcat STOP -- 
    // 
    // -- tomcat START -- nginx START -- L7 ON
    groupStage(
        serverGroupList: serverGroupList,
        serverList: serverList,
        stageArgs: [
            remoteUserAuth: remoteUserAuth,
            serverList: serverList,
            checkoutDo: checkoutDo,
            healthCheckPath: healthCheckPath,
            healthCheckOn: healthCheckOn,
            healthCheckOff: healthCheckOff,
            switchHealthCheckDo: switchHealthCheckDo,
            waitSecForHealthCheckOff: waitSecForHealthCheckOff,
            nginxAlias: nginxAlias,
            nginxDo: nginxDo,
            tomcatAlias: tomcatAlias,
            tomcatDo: tomcatDo,
            distDirectory: distDirectory,
            appPath: appPath,
            appDocBase: appDocBase,
            keepBackupDays: keepBackupDays,
            ownerName: ownerName,
            rsyncDeployDo: rsyncDeployDo,
            warDeployDo: warDeployDo,
            jarDeployDo: jarDeployDo,
            deployDo: deployDo
        ],
        this.&doSequentialStageGroup
    )

    
}


def getNginxAction(nginxAlias='ng', nginxAction) {
    def action
    switch(nginxAction.trim().toLowerCase()) {        
        case 'start': 
            action = 'bash -ci ' + nginxAlias + 'start || exit 0'
            break
        case 'restart': 
            action = 'bash -ci ' + nginxAlias + 'reload || exit 0'
            break
        case 'stop': 
            action = 'bash -ci ' + nginxAlias + 'stop || exit 0'
            break
        case 'status': 
            action = 'bash -ci ' + nginxAlias + 'status || exit 0'
            break
        default: 
            action = 'whoami'
            println "The value is unknown"
            break
    }
    return action
}

def getTomcatAction(tomcatAlias='tomcat', tomcatAction) {
    def action
    switch(tomcatAction.trim().toLowerCase()) {        
        case 'start': 
            action = 'bash -ci ' + tomcatAlias + 'boot || exit 0'
            break
        case 'restart': 
            action = 'bash -ci ' + tomcatAlias + 'down || exit 0; bash -ci ' + tomcatAlias + 'boot || exit 0;'
            break
        case 'stop': 
            action = 'bash -ci ' + tomcatAlias + 'down || exit 0'
            break
        case 'status': 
            action = 'ps -ef | grep tomcat'
            break
        default: 
            action = 'whoami'
            println "The value is unknown"
            break
    }
    return action
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


void doSwitchHealthCheckMode(remoteUserAuth, serverList, healthCheckPath, healthCheck, ownerName='') {
    withCredentials([usernamePassword(credentialsId: "$remoteUserAuth",
        usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {

        for (int i = 0; i < serverList.size(); i++) {

            def serverInfo = serverList[i]
            println "s-i[$i] = $serverInfo"

            def healthCheckPathDir = healthCheckPath.substring(0, healthCheckPath.lastIndexOf('/'))

            def remote = getRemoteFromServerInfo(serverInfo, USERNAME, PASSWORD)

            println "call remote"
            // def owner = getDefaultString(ownerName, USERNAME)

            sshCommand remote: remote, command: "mkdir -p $healthCheckPathDir"
            sshCommand remote: remote, command: "bash -c 'echo \"$healthCheck\" > $healthCheckPath'"
            sshCommand remote: remote, command: "chmod 775 $healthCheckPathDir"
            // sshCommand remote: remote, command: "chown -R $owner:$owner $healthCheckPathDir"
            
            // sleep 1
            println "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
        }

        println "Finished All Switching Mode for L7 HealthCheck!!!"
    }
}

void doServerAction(remoteUserAuth, serverList, action, ownerName='') {
    withCredentials([usernamePassword(credentialsId: "$remoteUserAuth",
        usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {

        for (int i = 0; i < serverList.size(); i++) {

            def serverInfo = serverList[i]
            println "s-i[$i] = $serverInfo"

            def remote = getRemoteFromServerInfo(serverInfo, USERNAME, PASSWORD)

            println "call remote"
            // def owner = getDefaultString(ownerName, USERNAME)

            sshCommand remote: remote, command: "$action"
            
            // sleep 1
            println "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
        }

        println "Finished All Server Action!!!"
    }
}


void doCheckDirectory(remoteUserAuth, serverList, appPath, appDocBase, ownerName='') {
    withCredentials([usernamePassword(credentialsId: "$remoteUserAuth",
        usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {

        for (int i = 0; i < serverList.size(); i++) {

            def serverInfo = serverList[i]
            println "s-i[$i] = $serverInfo"

            def remote = getRemoteFromServerInfo(serverInfo, USERNAME, PASSWORD)

            println "call remote"
            // def owner = getDefaultString(ownerName, USERNAME)

            sshCommand remote: remote, command: "T_DIR=${appPath}; [ ! -L \"\$T_DIR\" -a ! -d \"\$T_DIR\" ] && mkdir -p \$T_DIR || echo \"already existed directory (\$T_DIR)\" || exit 0"
            sshCommand remote: remote, command: "T_DIR=~/backup/log; [ ! -L \"\$T_DIR\" -a ! -d \"\$T_DIR\" ] && mkdir -p \$T_DIR || echo \"already existed directory (\$T_DIR)\" || exit 0"
            sshCommand remote: remote, command: "T_DIR=~/backup/recent/${appDocBase}; [ ! -L \"\$T_DIR\" -a ! -d \"\$T_DIR\" ] && mkdir -p \$T_DIR || echo \"already existed directory (\$T_DIR)\" || exit 0"
            sshCommand remote: remote, command: "T_DIR=~/backup/recent/temp; [ ! -L \"\$T_DIR\" -a ! -d \"\$T_DIR\" ] && mkdir -p \$T_DIR || echo \"already existed directory (\$T_DIR)\" || exit 0"
            
            // sleep 1
            println "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
        }

    }
}

void doDeleteExpiredVersionBackup(remoteUserAuth, serverList, appDocBase, keepBackupDays, ownerName='') {
    withCredentials([usernamePassword(credentialsId: "$remoteUserAuth",
        usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {

        def validBackupTime = new Date().minus(keepBackupDays).format("yyyyMMdd_HHmm")

        for (int i = 0; i < serverList.size(); i++) {

            def serverInfo = serverList[i]
            println "s-i[$i] = $serverInfo"

            def remote = getRemoteFromServerInfo(serverInfo, USERNAME, PASSWORD)

            println "call remote"
            // def owner = getDefaultString(ownerName, USERNAME)

            def delCmd = "awk -v under='${validBackupTime}' -v rm_do='rm -f ~/backup/%s\n' '{n=split(\$0, arr, \".\"); if (arr[n] < under) { printf rm_do, \$0}}' <(ls -1 ~/backup | grep '${appDocBase}') | sh"

            sshCommand remote: remote, command: "$delCmd"
            // sshCommand remote: remote, command: "sudo su -l $owner -c \"$delCmd\"", sudo: true

            // sleep 1
            println "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
        }

    }
}

void doMoveRecentBackup(remoteUserAuth, serverList, appDocBase, ownerName='') {
    withCredentials([usernamePassword(credentialsId: "$remoteUserAuth",
        usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {

        for (int i = 0; i < serverList.size(); i++) {

            def serverInfo = serverList[i]
            println "s-i[$i] = $serverInfo"

            def remote = getRemoteFromServerInfo(serverInfo, USERNAME, PASSWORD)

            println "call remote"
            // def owner = getDefaultString(ownerName, USERNAME)

            sshCommand remote: remote, command: "rm -rf ~/backup/recent/temp/${appDocBase}"
            sshCommand remote: remote, command: "mv ~/backup/recent/${appDocBase} ~/backup/recent/temp/"

            // sleep 1
            println "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
        }

    }
}

void doBackup(remoteUserAuth, serverList, appPath, appDocBase, ownerName='') {
    withCredentials([usernamePassword(credentialsId: "$remoteUserAuth",
        usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {

        for (int i = 0; i < serverList.size(); i++) {

            def serverInfo = serverList[i]
            println "s-i[$i] = $serverInfo"

            def remote = getRemoteFromServerInfo(serverInfo, USERNAME, PASSWORD)

            println "call remote"
            // def owner = getDefaultString(ownerName, USERNAME)

            sshCommand remote: remote, command: "cp -a ${appPath}/ ~/backup/recent/${appDocBase}"

            // sleep 1
            println "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
        }

    }
}

void doSyncByRsync(remoteUserAuth, serverList, appPath, appDocBase, distDirectory, ownerName='') {
    withCredentials([usernamePassword(credentialsId: "$remoteUserAuth",
        usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {

        for (int i = 0; i < serverList.size(); i++) {

            def serverInfo = serverList[i]
            println "s-i[$i] = $serverInfo"

            def remote = getRemoteFromServerInfo(serverInfo, USERNAME, PASSWORD)

            println "call remote"
            def owner = getDefaultString(ownerName, USERNAME)

            // @Rsync:변경 부분 배포
            println "******** Rsync ===> Sync"


            def sourcePath = "${JENKINS_HOME}/dist/${distDirectory}"

            // rsync 하기 전에 maven 프로젝트인지 확인 필요 (maven-archiver 디렉토리가 반드시 존재)
            // find . -mindepth 1 -maxdepth 1 -type d -name 'maven-archiver' | head -1 | xargs -I '{}' basename '{}' || exit 0
            def returnStr = sh (script: "find ${sourcePath} -mindepth 1 -maxdepth 1 -type d -name 'maven-archiver' | head -1 | xargs -I '{}' basename '{}' || exit 0", returnStdout: true).trim()
            if ('maven-archiver'.equalsIgnoreCase(returnStr)) {
                
                // maven 프로젝트 중 build > finalName 으로 생성된 경우 디렉토리가 존재하면 그 디렉토리로 연동
                // find . -mindepth 1 -maxdepth 1 -type d | grep -Ev 'generated-|test-|maven-|classes' | head -1 | xargs -I '{}' basename '{}' || exit 0
                def returnDirName = sh (script: "find ${sourcePath} -mindepth 1 -maxdepth 1 -type d | grep -Ev 'generated-|test-|maven-|classes' | head -1 | xargs -I '{}' basename '{}' || exit 0", returnStdout: true).trim()
                if (returnDirName != null && returnDirName.length() > 0) {
                    sourcePath += "/${returnDirName}"
                }
            }

            //심볼릭 링크라 소유자 변경시, 자기 자신과 하위를 같이 변경해야한다.
            sshCommand remote: remote, command: "chown ${remote.user}.${remote.user} ${appPath}"
            sshCommand remote: remote, command: "chown -R ${remote.user}.${remote.user} ${appPath}/"
            sh("sshpass -p ${remote.password} ssh -o StrictHostKeyChecking=no ${remote.user}@${remote.host} pwd")
            sh("sshpass -p ${remote.password} rsync -arhz -e 'ssh -o StrictHostKeyChecking=no' --rsync-path='rsync' --delete ${sourcePath}/ ${remote.user}@${remote.host}:${appPath}")
            //심볼릭 링크라 소유자 변경시, 자기 자신과 하위를 같이 변경해야한다.
            sshCommand remote: remote, command: "chown $owner:$owner ${appPath}"
            sshCommand remote: remote, command: "chown -R $owner:$owner ${appPath}/"

            // sleep 1
            println "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
        }

    }
}

void doSyncByWar(remoteUserAuth, serverList, appPath, appDocBase, distDirectory, ownerName='') {
    withCredentials([usernamePassword(credentialsId: "$remoteUserAuth",
        usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {

        for (int i = 0; i < serverList.size(); i++) {

            def serverInfo = serverList[i]
            println "s-i[$i] = $serverInfo"

            def remote = getRemoteFromServerInfo(serverInfo, USERNAME, PASSWORD)

            println "call remote"
            // def owner = getDefaultString(ownerName, USERNAME)

            // @war:통 배포
            println "******** war ===> Sync"

            sh("cp -f ${JENKINS_HOME}/dist/${distDirectory}/*.war ${JENKINS_HOME}/dist/${distDirectory}/${appDocBase}.war || exit 0")

            sshPut remote: remote, from: "${JENKINS_HOME}/dist/${distDirectory}/${appDocBase}.war", into: "."
            sshCommand remote: remote, command: "rm -rf ${appPath}/*"
            sshCommand remote: remote, command: "mv ${appDocBase}.war ${appPath}"
            // sshCommand remote: remote, command: "chown $owner:$owner ${appPath}/${appDocBase}.war"
            sshCommand remote: remote, command: "cd ${appPath}; jar xf ${appDocBase}.war"

            // sleep 1
            println "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
        }

    }
}

void doSyncByJar(remoteUserAuth, serverList, appPath, appDocBase, distDirectory, ownerName='') {
    withCredentials([usernamePassword(credentialsId: "$remoteUserAuth",
        usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {

        for (int i = 0; i < serverList.size(); i++) {

            def serverInfo = serverList[i]
            println "s-i[$i] = $serverInfo"

            def remote = getRemoteFromServerInfo(serverInfo, USERNAME, PASSWORD)

            println "call remote"
            // def owner = getDefaultString(ownerName, USERNAME)

            // @jar:통 배포
            println "******** jar ===> Sync"

            sh("cp -f ${JENKINS_HOME}/dist/${distDirectory}/*.jar ${JENKINS_HOME}/dist/${distDirectory}/${appDocBase}.jar || exit 0")

            sshPut remote: remote, from: "${JENKINS_HOME}/dist/${distDirectory}/${appDocBase}.jar", into: "."
            sshCommand remote: remote, command: "rm -rf ${appPath}/*"
            sshCommand remote: remote, command: "mv ${appDocBase}.jar ${appPath}"
            // sshCommand remote: remote, command: "chown $owner:$owner ${appPath}/${appDocBase}.jar"
            // sshCommand remote: remote, command: "cd ${appPath}; jar xf ${appDocBase}.jar"

            // sleep 1
            println "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
        }

    }
}

void doSaveFinalSyncVersionByRsync(remoteUserAuth, serverList, appPath, appDocBase, ownerName='') {
    withCredentials([usernamePassword(credentialsId: "$remoteUserAuth",
        usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {

        def backupTime = new Date().format("yyyyMMdd_HHmm")

        for (int i = 0; i < serverList.size(); i++) {

            def serverInfo = serverList[i]
            println "s-i[$i] = $serverInfo"

            def remote = getRemoteFromServerInfo(serverInfo, USERNAME, PASSWORD)

            println "call remote"
            // def owner = getDefaultString(ownerName, USERNAME)

            println "******** Rsync ===> Save Final Sync Version"

            // @Rsync:변경 부분 배포
            sshCommand remote: remote, command: "cd ${appPath}; tar czf ${appDocBase}.tar.gz.${backupTime} *; mv ${appDocBase}.tar.gz.${backupTime} ~/backup/; echo ${backupTime}   ${appPath} to ${appDocBase}.tar.gz copied by _rsync_. >> ~/backup/log/deploy_backup_history.log"
            // sshCommand remote: remote, command: "BACKUP_TIME=\$(date +%Y%m%d_%H%M); cd ${appPath}; tar czf ${appDocBase}.tar.gz.\$BACKUP_TIME *; mv ${appDocBase}.tar.gz.\$BACKUP_TIME ~/backup/; echo \$BACKUP_TIME   ${appPath} to ${appDocBase}.tar.gz copied by _rsync_. >> ~/backup/log/deploy_backup_history.log"

            // sleep 1
            println "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
        }

    }
}

void doSaveFinalSyncVersionByWar(remoteUserAuth, serverList, appPath, appDocBase, ownerName='') {
    withCredentials([usernamePassword(credentialsId: "$remoteUserAuth",
        usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {

        def backupTime = new Date().format("yyyyMMdd_HHmm")

        for (int i = 0; i < serverList.size(); i++) {

            def serverInfo = serverList[i]
            println "s-i[$i] = $serverInfo"

            def remote = getRemoteFromServerInfo(serverInfo, USERNAME, PASSWORD)

            println "call remote"
            // def owner = getDefaultString(ownerName, USERNAME)

            println "******** war ===> Save Final Sync Version"

            // @war:통 배포
            sshCommand remote: remote, command: "mv ${appPath}/${appDocBase}.war ~/backup/${appDocBase}.war.${backupTime}; echo ${backupTime}   ${appDocBase}.war copied by _war_. >> ~/backup/log/deploy_backup_history.log"
            // sshCommand remote: remote, command: "BACKUP_TIME=\$(date +%Y%m%d_%H%M); mv ${appPath}/${appDocBase}.war ~/backup/${appDocBase}.war.\$BACKUP_TIME; echo \$BACKUP_TIME   ${appDocBase}.war copied by _war_. >> ~/backup/log/deploy_backup_history.log"

            // sleep 1
            println "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
        }

    }
}

void doSaveFinalSyncVersionByJar(remoteUserAuth, serverList, appPath, appDocBase, ownerName='') {
    withCredentials([usernamePassword(credentialsId: "$remoteUserAuth",
        usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {

        def backupTime = new Date().format("yyyyMMdd_HHmm")

        for (int i = 0; i < serverList.size(); i++) {

            def serverInfo = serverList[i]
            println "s-i[$i] = $serverInfo"

            def remote = getRemoteFromServerInfo(serverInfo, USERNAME, PASSWORD)

            println "call remote"
            // def owner = getDefaultString(ownerName, USERNAME)

            println "******** jar ===> Save Final Sync Version"

            // @jar:통 배포
            sshCommand remote: remote, command: "mv ${appPath}/${appDocBase}.jar ~/backup/${appDocBase}.jar.${backupTime}; echo ${backupTime}   ${appDocBase}.jar copied by _jar_. >> ~/backup/log/deploy_backup_history.log"
            // sshCommand remote: remote, command: "BACKUP_TIME=\$(date +%Y%m%d_%H%M); mv ${appPath}/${appDocBase}.jar ~/backup/${appDocBase}.jar.\$BACKUP_TIME; echo \$BACKUP_TIME   ${appDocBase}.jar copied by _jar_. >> ~/backup/log/deploy_backup_history.log"

            // sleep 1
            println "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
        }

    }
}

def doSequentialStageGroup(Map args) {
    // ----------------------------------------------------------------
    // 필수 : serverList
    // 선택 : groupName
    // 그외 : stageArgs (각 스테이지에서 필요한 매개변수 Map)
    args.groupName = args.groupName?:''
    String labelPrefix = args.groupName?args.groupName+' : ':''
    
    println "SERVER LIST : $args.serverList"

    // response for input(abort)
    boolean skipStage = false
    // ----------------------------------------------------------------


    // L7 -- start

    stage(labelPrefix+"healthcheck $args.stageArgs.healthCheckOff") {
        printlnHeadMessage labelPrefix+"healthcheck $args.stageArgs.healthCheckOff" + (args.stageArgs.switchHealthCheckDo?"":" (Skip)")
        try {
            if(args.stageArgs.switchHealthCheckDo && !skipStage) {
                // PM을 고려한 최대 대기 시간 (6시간)
                timeout(time: 6, unit: 'HOURS') {
                    input "$labelPrefix L7 HealthCheck를 $args.stageArgs.healthCheckOff 합니다. 계속 진행 하시겠습니까?"
                }
            }
        } catch(Exception e) {
            println "● healthcheck $args.stageArgs.healthCheckOff (Aborted)"
            skipStage = true
        }
        when(args.stageArgs.switchHealthCheckDo && !skipStage) {
            println "● healthcheck $args.stageArgs.healthCheckOff Started"
            doSwitchHealthCheckMode(args.stageArgs.remoteUserAuth, args.serverList, args.stageArgs.healthCheckPath, args.stageArgs.healthCheckOff, args.stageArgs.ownerName)
            
            println "● --------------------- sleep $args.stageArgs.waitSecForHealthCheckOff sec for Switch OFF Complete..."
            sleep args.stageArgs.waitSecForHealthCheckOff
            
            println "● healthcheck $args.stageArgs.healthCheckOff End"
        }
    }

    // L7 -- end

    // SERVER -- start

    stage(labelPrefix+"nginx STOP") {
        printlnHeadMessage labelPrefix+"nginx STOP" + (args.stageArgs.nginxDo?"":" (Skip)")
        when(args.stageArgs.nginxDo && !skipStage) {
            println "● nginx STOP Started"
            def ngAction = getNginxAction(args.stageArgs.nginxAlias, 'STOP')
            doServerAction(args.stageArgs.remoteUserAuth, args.serverList, ngAction, args.stageArgs.ownerName)
            println "● nginx STOP End"
        }
    }

    stage(labelPrefix+"tomcat STOP") {
        printlnHeadMessage labelPrefix+"tomcat STOP" + (args.stageArgs.tomcatDo?"":" (Skip)")
        when(args.stageArgs.tomcatDo && !skipStage) {
            println "● tomcat STOP Started"
            def tcAction = getTomcatAction(args.stageArgs.tomcatAlias, 'STOP')
            doServerAction(args.stageArgs.remoteUserAuth, args.serverList, tcAction, args.stageArgs.ownerName)
            sleep 3
            println "● tomcat STOP End"
        }
    }

    // SERVER -- end



    // DEPLOY -- start

    stage(labelPrefix+"check-directory") {
        printlnHeadMessage labelPrefix+"check-directory"
        when(args.stageArgs.deployDo && !skipStage) {
            println "● check-directory Started"
            doCheckDirectory(args.stageArgs.remoteUserAuth, args.serverList, args.stageArgs.appPath, args.stageArgs.appDocBase, args.stageArgs.ownerName)
            println "● check-directory End"
        }
    }

    stage(labelPrefix+"delete expired version(backup)") {
        printlnHeadMessage labelPrefix+"delete expired version(backup)"
        when(!skipStage) {
            println "● delete expired version(backup) Started"
            doDeleteExpiredVersionBackup(args.stageArgs.remoteUserAuth, args.serverList, args.stageArgs.appDocBase, args.stageArgs.keepBackupDays, args.stageArgs.ownerName)
            println "● delete expired version(backup) End"
        }
    }

    stage(labelPrefix+"move recent(backup)") {
        printlnHeadMessage labelPrefix+"move recent(backup)"
        when(args.stageArgs.deployDo && !skipStage) {
            println "● move recent(backup) Started"
            doMoveRecentBackup(args.stageArgs.remoteUserAuth, args.serverList, args.stageArgs.appDocBase, args.stageArgs.ownerName)
            println "● move recent(backup) End"
        }
    }

    stage(labelPrefix+"backup") {
        printlnHeadMessage labelPrefix+"backup"
        when(args.stageArgs.deployDo && !skipStage) {
            println "● backup Started"
            doBackup(args.stageArgs.remoteUserAuth, args.serverList, args.stageArgs.appPath, args.stageArgs.appDocBase, args.stageArgs.ownerName)
            println "● backup End"
        }
    }

    stage(labelPrefix+"sync") {
        printlnHeadMessage labelPrefix+"sync"
        if(args.stageArgs.deployDo && !(args.stageArgs.rsyncDeployDo || args.stageArgs.warDeployDo || args.stageArgs.jarDeployDo)) {
            println "● No Matched sync type... (NOT WORKING)"
        }
        when(args.stageArgs.deployDo && (args.stageArgs.rsyncDeployDo || args.stageArgs.warDeployDo || args.stageArgs.jarDeployDo) && !skipStage) {
            println "● sync Started"
            if (args.stageArgs.rsyncDeployDo) {
                doSyncByRsync(args.stageArgs.remoteUserAuth, args.serverList, args.stageArgs.appPath, args.stageArgs.appDocBase, args.stageArgs.distDirectory, args.stageArgs.ownerName)
            } else if (args.stageArgs.warDeployDo) {
                doSyncByWar(args.stageArgs.remoteUserAuth, args.serverList, args.stageArgs.appPath, args.stageArgs.appDocBase, args.stageArgs.distDirectory, args.stageArgs.ownerName)
            } else if (args.stageArgs.jarDeployDo) {
                doSyncByJar(args.stageArgs.remoteUserAuth, args.serverList, args.stageArgs.appPath, args.stageArgs.appDocBase, args.stageArgs.distDirectory, args.stageArgs.ownerName)
            } else {
                println "● No Matched sync type... (NOT WORKING)"
            }
            println "● sync End"
        }
    }
    
    stage(labelPrefix+"save final-sync-ver") {
        printlnHeadMessage labelPrefix+"save final-sync-ver"
        if(args.stageArgs.deployDo && !(args.stageArgs.rsyncDeployDo || args.stageArgs.warDeployDo || args.stageArgs.jarDeployDo)) {
            println "● No Matched sync type... (NOT WORKING)"
        }
        when(args.stageArgs.deployDo && (args.stageArgs.rsyncDeployDo || args.stageArgs.warDeployDo || args.stageArgs.jarDeployDo) && !skipStage) {
            println "● save final-sync-ver Started"
            if (args.stageArgs.rsyncDeployDo) {
                doSaveFinalSyncVersionByRsync(args.stageArgs.remoteUserAuth, args.serverList, args.stageArgs.appPath, args.stageArgs.appDocBase, args.stageArgs.ownerName)
            } else if (args.stageArgs.warDeployDo) {
                doSaveFinalSyncVersionByWar(args.stageArgs.remoteUserAuth, args.serverList, args.stageArgs.appPath, args.stageArgs.appDocBase, args.stageArgs.ownerName)
            } else if (args.stageArgs.jarDeployDo) {
                doSaveFinalSyncVersionByJar(args.stageArgs.remoteUserAuth, args.serverList, args.stageArgs.appPath, args.stageArgs.appDocBase, args.stageArgs.ownerName)
            } else {
                println "● No Matched sync type... (NOT WORKING)"
            }
            println "● save final-sync-ver End"
        }
    }

    // DEPLOY -- end



    // SERVER -- start

    stage(labelPrefix+"tomcat START") {
        printlnHeadMessage labelPrefix+"tomcat START" + (args.stageArgs.tomcatDo?"":" (Skip)")
        when(args.stageArgs.tomcatDo && !skipStage) {
            println "● tomcat START Started"
            def tcAction = getTomcatAction(args.stageArgs.tomcatAlias, 'START')
            doServerAction(args.stageArgs.remoteUserAuth, args.serverList, tcAction, args.stageArgs.ownerName)
            sleep 8
            println "● tomcat START End"
        }
    }

    stage(labelPrefix+"nginx START") {
        printlnHeadMessage labelPrefix+"nginx START" + (args.stageArgs.nginxDo?"":" (Skip)")
        when(args.stageArgs.nginxDo && !skipStage) {
            println "● nginx START Started"
            def ngAction = getNginxAction(args.stageArgs.nginxAlias, 'START')
            doServerAction(args.stageArgs.remoteUserAuth, args.serverList, ngAction, args.stageArgs.ownerName)
            println "● nginx START End"
        }
    }

    // SERVER -- end


    // L7 -- start

    stage(labelPrefix+"healthcheck $args.stageArgs.healthCheckOn") {
        printlnHeadMessage labelPrefix+"healthcheck $args.stageArgs.healthCheckOn" + (args.stageArgs.switchHealthCheckDo?"":" (Skip)")
        try {
            if(args.stageArgs.switchHealthCheckDo && !skipStage) {
                // PM을 고려한 최대 대기 시간 (6시간)
                timeout(time: 6, unit: 'HOURS') {
                    input "$labelPrefix L7 HealthCheck를 $args.stageArgs.healthCheckOn 합니다. 계속 진행 하시겠습니까?"
                }
            }
        } catch(Exception e) {
            println "● healthcheck $args.stageArgs.healthCheckOn (Aborted)"
            skipStage = true
        }
        when(args.stageArgs.switchHealthCheckDo && !skipStage) {
            println "● healthcheck $args.stageArgs.healthCheckOn Started"
            doSwitchHealthCheckMode(args.stageArgs.remoteUserAuth, args.serverList, args.stageArgs.healthCheckPath, args.stageArgs.healthCheckOn, args.stageArgs.ownerName)
            println "● healthcheck $args.stageArgs.healthCheckOn End"
        }
    }

    String slackSendMsg = "SUCCESSFUL: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]"
    if (args.stageArgs.checkoutDo) {
        slackSendMsg += "\n----------------------------------\n${env.COMMIT_AUTHOR} [${env.COMMIT_ID}]\n${env.COMMIT_MSG}"
    }
    slackSend (channel: '#배포알림', color: '#2AAD73', message: slackSendMsg)
    


    // L7 -- end

    return skipStage
}
