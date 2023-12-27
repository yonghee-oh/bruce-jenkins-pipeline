/*
 * 전체 과정 통합 배포 (All in one)
 * @author bruce_oh
 * @date 2021. 1. 12.
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
     * ---> (진행 여부 선택) ---> Batch OFF
     * ---> (진행 여부 선택) ---> HealthCheck(L7) OFF
     * ---> node STOP
     * ---> 경고 디렉토리 확인 ---> 최근 백업 파일 temp로 이동 ---> 백업
     * ---> 소스 동기화(업로드, 배포) ---> 최종 동기화 버전 저장
     * ---> node START
     * ---> (진행 여부 선택) ---> HealthCheck(L7) ON
     * ---> (진행 여부 선택) ---> Batch ON
     * 
     * 완료
     * 
     * 
     * (순서 구조)
     * 
     * checkout
     * build
     * 
     * batch off
     *
     * group 1~n (begin)
     *     health check off
     * 
     *     node stop
     * 
     *     source deploy
     * 
     *     node start
     * 
     *     health check on
     * group 1~n (end)
     * 
     * batch on
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

    // params (switch batch mode)
    def batchServerAuth
    def jenkinsBatchList

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
    def nodeAlias

    // variable
    def checkoutDo = false
    def buildDo = false
    def switchBatchDo = false
    def switchHealthCheckDo = false
    def nodeDo = false
    def deployDo = false
    def rsyncDeployDo = false
    def warDeployDo = false
    def jarDeployDo = false
    def appDocBase
    def jenkinsCredentialsId
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
            // println "NODE_ALIAS = $NODE_ALIAS"
            nodeAlias = "$NODE_ALIAS"
        } catch (MissingPropertyException e) {
            println "Error NODE_ALIAS"
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
        // server job action 에 따라 node 구분

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
                } else if('batch'.equalsIgnoreCase(jobActionList[i])) {	
                    switchBatchDo = true
                } else if('healthcheck'.equalsIgnoreCase(jobActionList[i])) {
                    switchHealthCheckDo = true
                } else if('node'.equalsIgnoreCase(jobActionList[i])) {
                    nodeDo = true
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
            // println "BATCH_SERVER_AUTH = $BATCH_SERVER_AUTH"
            batchServerAuth = "$BATCH_SERVER_AUTH"
            if (batchServerAuth.indexOf('[') > -1 && batchServerAuth.lastIndexOf(']') > batchServerAuth.indexOf('[') && batchServerAuth.lastIndexOf(']') <= batchServerAuth.size()) {
                jenkinsCredentialsId = batchServerAuth.substring(batchServerAuth.indexOf('[') + 1, batchServerAuth.lastIndexOf(']')).trim()
            }
        } catch (MissingPropertyException e) {
            println "Error BATCH_SERVER_AUTH"
            throw e
        }

        try {
            // println "JENKINS_BATCH_LIST = $JENKINS_BATCH_LIST"
            jenkinsBatchList = "$JENKINS_BATCH_LIST".tokenize('\\,')
            for (int i = 0; i < jenkinsBatchList.size(); i++) {
                // println "jenkinsBatchJob $i : " + jenkinsBatchJob[i]
                def jenkinsBatchJob = jenkinsBatchList[i].trim()
                jenkinsBatchList[i] = jenkinsBatchJob.substring(jenkinsBatchJob.indexOf('(') + 1, jenkinsBatchJob.lastIndexOf(')')).trim()
            }
        } catch (MissingPropertyException e) {
            println "Error JENKINS_BATCH_LIST"
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
            nodeDo = false
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



    // BATCH -- start

    stage('batch OFF') {
        printlnHeadMessage "batch OFF" + (switchBatchDo?"":" (Skip)")
        when(switchBatchDo) {
            println "● batch OFF Started"
            try {
                // PM을 고려한 최대 대기 시간 (6시간)
                timeout(time: 6, unit: 'HOURS') {
                    input '배치를 OFF 합니다. 계속 진행 하시겠습니까?'
                }
                doSwitchBatch(jenkinsCredentialsId, jenkinsBatchList, 'OFF')
            } catch(Exception e) {
                println "batch OFF (Aborted)"
                throw e
            }
            println "● batch OFF End"
        }
    }

    // BATCH -- end


    // L7 OFF -- node STOP
    // 
    // -- node START -- L7 ON
    groupStage(
        serverGroupList: serverGroupList,
        serverList: serverList,
        stageArgs: [
            remoteUserAuth: remoteUserAuth,
            serverList: serverList,
            healthCheckPath: healthCheckPath,
            healthCheckOn: healthCheckOn,
            healthCheckOff: healthCheckOff,
            switchHealthCheckDo: switchHealthCheckDo,
            waitSecForHealthCheckOff: waitSecForHealthCheckOff,
            nodeAlias: nodeAlias,
            nodeDo: nodeDo,
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

    // Batch -- start

    stage('batch ON') {
        printlnHeadMessage "batch ON" + (switchBatchDo?"":" (Skip)")
        when(switchBatchDo) {
            println "● batch ON Started"
            try {
                // PM을 고려한 최대 대기 시간 (6시간)
                timeout(time: 6, unit: 'HOURS') {
                    input '배치를 ON 합니다. 계속 진행 하시겠습니까?'
                }
                doSwitchBatch(jenkinsCredentialsId, jenkinsBatchList, 'ON')
            } catch(Exception e) {
                println "batch ON (Aborted)"
                throw e
            }
            println "● batch ON End"
        }
    }

    // Batch -- end

    
}

def getBatchAction(batchAction) {
    def action
    switch(batchAction.trim().toLowerCase()) {        
        case 'on': 
            action = 'enable'
            break
        default: 
            action = 'disable'
            break
    }
    return action
}

def getNodeAction(nodeAlias='node', nodeAction) {
    def action
    switch(nodeAction.trim().toLowerCase()) {        
        case 'start': 
            action = 'bash -ci ' + nodeAlias + 'boot || exit 0'
            break
        case 'restart': 
            action = 'bash -ci ' + nodeAlias + 'down || exit 0; bash -ci ' + nodeAlias + 'boot || exit 0;'
            break
        case 'stop': 
            action = 'bash -ci ' + nodeAlias + 'down || exit 0'
            break
        case 'status': 
            action = 'ps -ef | grep node'
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

void doSwitchBatch(credentialsId, jenkinsBatchList, action) {
    withCredentials([usernamePassword(credentialsId: "$credentialsId",
        usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
        String userInfo = USERNAME+":"+PASSWORD
        withEnv(["USER_INFO=$userInfo", "SWITCH_MODE=" + getBatchAction(action)]) {
            // sh 'echo USER_INFO is "$USER_INFO"'
            // sh 'echo SWITCH_MODE is "$SWITCH_MODE"'
            println "Success Getting API Key of Jenkins!!!"

            for (int i = 0; i < jenkinsBatchList.size(); i++) {
                def batchUrl = jenkinsBatchList[i]
                println "batchUrl $i = $batchUrl${SWITCH_MODE}"
                
                def result = sh(
                script: 
                """#!/bin/bash
                    curl -s -u ${USER_INFO} -I -X POST '$batchUrl${SWITCH_MODE}'
                """, returnStdout: true)
                // println "result ==== $result"

                // sleep 1
                println "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
            }

            println "Finished switching batch mode for Deploy!!!"
        }
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
            def owner = getDefaultString(ownerName, USERNAME)

            sshCommand remote: remote, command: "sudo mkdir -p $healthCheckPathDir", sudo: true
            sshCommand remote: remote, command: "sudo bash -c 'echo \"$healthCheck\" > $healthCheckPath'", sudo: true
            sshCommand remote: remote, command: "sudo chmod 775 $healthCheckPathDir", sudo: true
            sshCommand remote: remote, command: "sudo chown -R $owner:$owner $healthCheckPathDir", sudo: true
            
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
            def owner = getDefaultString(ownerName, USERNAME)

            sshCommand remote: remote, command: "sudo su -l $owner -c '$action'", sudo: true
            
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
            def owner = getDefaultString(ownerName, USERNAME)

            sshCommand remote: remote, command: "sudo su -l $owner -c 'T_DIR=${appPath}; [ ! -L \"\$T_DIR\" -a ! -d \"\$T_DIR\" ] && mkdir -p \$T_DIR || echo \"already existed directory (\$T_DIR)\" || exit 0'", sudo: true
            sshCommand remote: remote, command: "sudo su -l $owner -c 'T_DIR=~/backup/log; [ ! -L \"\$T_DIR\" -a ! -d \"\$T_DIR\" ] && mkdir -p \$T_DIR || echo \"already existed directory (\$T_DIR)\" || exit 0'", sudo: true
            sshCommand remote: remote, command: "sudo su -l $owner -c 'T_DIR=~/backup/recent/${appDocBase}; [ ! -L \"\$T_DIR\" -a ! -d \"\$T_DIR\" ] && mkdir -p \$T_DIR || echo \"already existed directory (\$T_DIR)\" || exit 0'", sudo: true
            sshCommand remote: remote, command: "sudo su -l $owner -c 'T_DIR=~/backup/recent/temp; [ ! -L \"\$T_DIR\" -a ! -d \"\$T_DIR\" ] && mkdir -p \$T_DIR || echo \"already existed directory (\$T_DIR)\" || exit 0'", sudo: true
            
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
            def owner = getDefaultString(ownerName, USERNAME)

            // def delCmd = "awk -v under='${validBackupTime}' -v rm_do='rm -f ~/backup/%s\n' '{n=split(\$0, arr, \".\"); if (arr[n] < under) { printf rm_do, \$0}}' <(ls -1 ~/backup | grep '${appDocBase}') | sh"
            def sudoDelCmd = "awk -v under='${validBackupTime}' -v rm_do='rm -f ~/backup/%s\n' '{n=split(\\\$0, arr, \\\".\\\"); if (arr[n] < under) { printf rm_do, \\\$0}}' <(ls -1 ~/backup | grep '${appDocBase}') | sh"

            // sshCommand remote: remote, command: "$delCmd"
            sshCommand remote: remote, command: "sudo su -l $owner -c \"$sudoDelCmd\"", sudo: true

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
            def owner = getDefaultString(ownerName, USERNAME)

            sshCommand remote: remote, command: "sudo su -l $owner -c 'rm -rf ~/backup/recent/temp/${appDocBase}'", sudo: true
            sshCommand remote: remote, command: "sudo su -l $owner -c 'mv ~/backup/recent/${appDocBase} ~/backup/recent/temp/'", sudo: true

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
            def owner = getDefaultString(ownerName, USERNAME)

            sshCommand remote: remote, command: "sudo su -l $owner -c 'cp -a ${appPath}/ ~/backup/recent/${appDocBase}'", sudo: true

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
            sshCommand remote: remote, command: "sudo chown ${remote.user}.${remote.user} ${appPath}", sudo: true
            sshCommand remote: remote, command: "sudo chown -R ${remote.user}.${remote.user} ${appPath}/", sudo: true
            sh("sshpass -p ${remote.password} ssh -o StrictHostKeyChecking=no ${remote.user}@${remote.host} pwd")
            sh("sshpass -p ${remote.password} rsync -arhz -e 'ssh -o StrictHostKeyChecking=no' --rsync-path='sudo rsync' --delete ${sourcePath}/ ${remote.user}@${remote.host}:${appPath}")
            //심볼릭 링크라 소유자 변경시, 자기 자신과 하위를 같이 변경해야한다.
            sshCommand remote: remote, command: "sudo chown $owner:$owner ${appPath}", sudo: true
            sshCommand remote: remote, command: "sudo chown -R $owner:$owner ${appPath}/", sudo: true

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
            def owner = getDefaultString(ownerName, USERNAME)

            // @war:통 배포
            println "******** war ===> Sync"

            sh("cp -f ${JENKINS_HOME}/dist/${distDirectory}/*.war ${JENKINS_HOME}/dist/${distDirectory}/${appDocBase}.war || exit 0")

            sshPut remote: remote, from: "${JENKINS_HOME}/dist/${distDirectory}/${appDocBase}.war", into: "."
            sshCommand remote: remote, command: "sudo su -l $owner -c 'rm -rf ${appPath}/*'", sudo: true
            sshCommand remote: remote, command: "sudo mv ${appDocBase}.war ${appPath}", sudo: true
            sshCommand remote: remote, command: "sudo chown $owner:$owner ${appPath}/${appDocBase}.war", sudo: true
            sshCommand remote: remote, command: "sudo su -l $owner -c 'cd ${appPath}; jar xf ${appDocBase}.war'", sudo: true

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
            def owner = getDefaultString(ownerName, USERNAME)

            // @jar:통 배포
            println "******** jar ===> Sync"

            sh("cp -f ${JENKINS_HOME}/dist/${distDirectory}/*.jar ${JENKINS_HOME}/dist/${distDirectory}/${appDocBase}.jar || exit 0")

            sshPut remote: remote, from: "${JENKINS_HOME}/dist/${distDirectory}/${appDocBase}.jar", into: "."
            sshCommand remote: remote, command: "sudo su -l $owner -c 'rm -rf ${appPath}/*'", sudo: true
            sshCommand remote: remote, command: "sudo mv ${appDocBase}.jar ${appPath}", sudo: true
            sshCommand remote: remote, command: "sudo chown $owner:$owner ${appPath}/${appDocBase}.jar", sudo: true
            // sshCommand remote: remote, command: "sudo su -l $owner -c 'cd ${appPath}; jar xf ${appDocBase}.jar'", sudo: true

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
            def owner = getDefaultString(ownerName, USERNAME)

            println "******** Rsync ===> Save Final Sync Version"

            // @Rsync:변경 부분 배포
            sshCommand remote: remote, command: """
            sudo su -l $owner -c 'cd ${appPath}; tar czf ${appDocBase}.tar.gz.${backupTime} *; mv ${appDocBase}.tar.gz.${backupTime} ~/backup/; echo ${backupTime}   ${appPath} to ${appDocBase}.tar.gz copied by _rsync_. >> ~/backup/log/deploy_backup_history.log'
            """.trim(), sudo: true
            // sshCommand remote: remote, command: """
            // sudo su -l $owner -c 'BACKUP_TIME=\$(date +%Y%m%d_%H%M); cd ${appPath}; tar czf ${appDocBase}.tar.gz.\$BACKUP_TIME *; mv ${appDocBase}.tar.gz.\$BACKUP_TIME ~/backup/; echo \$BACKUP_TIME   ${appPath} to ${appDocBase}.tar.gz copied by _rsync_. >> ~/backup/log/deploy_backup_history.log'
            // """.trim(), sudo: true

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
            def owner = getDefaultString(ownerName, USERNAME)

            println "******** war ===> Save Final Sync Version"

            // @war:통 배포
            sshCommand remote: remote, command: """
            sudo su -l $owner -c 'mv ${appPath}/${appDocBase}.war ~/backup/${appDocBase}.war.${backupTime}; echo ${backupTime}   ${appDocBase}.war copied by _war_. >> ~/backup/log/deploy_backup_history.log'
            """.trim(), sudo: true
            // sshCommand remote: remote, command: """
            // sudo su -l $owner -c 'BACKUP_TIME=\$(date +%Y%m%d_%H%M); mv ${appPath}/${appDocBase}.war ~/backup/${appDocBase}.war.\$BACKUP_TIME; echo \$BACKUP_TIME   ${appDocBase}.war copied by _war_. >> ~/backup/log/deploy_backup_history.log'
            // """.trim(), sudo: true

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
            def owner = getDefaultString(ownerName, USERNAME)

            println "******** jar ===> Save Final Sync Version"

            // @jar:통 배포
            sshCommand remote: remote, command: """
            sudo su -l $owner -c 'mv ${appPath}/${appDocBase}.jar ~/backup/${appDocBase}.jar.${backupTime}; echo ${backupTime}   ${appDocBase}.jar copied by _jar_. >> ~/backup/log/deploy_backup_history.log'
            """.trim(), sudo: true
            // sshCommand remote: remote, command: """
            // sudo su -l $owner -c 'BACKUP_TIME=\$(date +%Y%m%d_%H%M); mv ${appPath}/${appDocBase}.jar ~/backup/${appDocBase}.jar.\$BACKUP_TIME; echo \$BACKUP_TIME   ${appDocBase}.jar copied by _jar_. >> ~/backup/log/deploy_backup_history.log'
            // """.trim(), sudo: true

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

    stage(labelPrefix+"node STOP") {
        printlnHeadMessage labelPrefix+"node STOP" + (args.stageArgs.nodeDo?"":" (Skip)")
        when(args.stageArgs.nodeDo && !skipStage) {
            println "● node STOP Started"
            def ndAction = getNodeAction(args.stageArgs.nodeAlias, 'STOP')
            doServerAction(args.stageArgs.remoteUserAuth, args.serverList, ndAction, args.stageArgs.ownerName)
            sleep 3
            println "● node STOP End"
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

    stage(labelPrefix+"node START") {
        printlnHeadMessage labelPrefix+"node START" + (args.stageArgs.nodeDo?"":" (Skip)")
        when(args.stageArgs.nodeDo && !skipStage) {
            println "● node START Started"
            def ndAction = getNodeAction(args.stageArgs.nodeAlias, 'START')
            doServerAction(args.stageArgs.remoteUserAuth, args.serverList, ndAction, args.stageArgs.ownerName)
            sleep 8
            println "● node START End"
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

    // L7 -- end

    return skipStage
}
