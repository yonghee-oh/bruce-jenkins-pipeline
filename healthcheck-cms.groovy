/*
 * 배포 전,후 L7 Health Check 설정
 * @author bruce_oh
 * @date 2020. 3. 27.
 */

node {

    // properties
    def allGroupNames
    def allServers
    def ownerName

    // params
    def healthCheckPath
    def healthCheck
    def remoteUserAuth
    def selectServers
    def serverGroupList = [:]
    def serverList = []


    stage('preparation') {
        printlnHeadMessage "preparation"

        // println "==================== Setting Properties ===================="
        
        withFolderProperties {
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
                // println "$OWNER_NAME"
                ownerName = "${OWNER_NAME}"
            } catch (MissingPropertyException e) {
                println "Missing Property - OWNER_NAME"
                ownerName = ''
            }
        }

        // println "==================== Setting Parameters ===================="
        
        try {
            // println "HEALTH_CHECK_PATH = $HEALTH_CHECK_PATH"
            healthCheckPath = "$HEALTH_CHECK_PATH"
        } catch (MissingPropertyException e) {
            println "Error HEALTH_CHECK_PATH"
            throw e
        }
        
        try {
            // println "HEALTH_CHECK = $HEALTH_CHECK"
            healthCheck = "$HEALTH_CHECK"
        } catch (MissingPropertyException e) {
            println "Error HEALTH_CHECK"
            throw e
        }

        try {
            // println "REMOTE_USER_AUTH = $REMOTE_USER_AUTH"
            remoteUserAuth = "$REMOTE_USER_AUTH"
        } catch (MissingPropertyException e) {
            println "Error REMOTE_USER_AUTH"
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

    }

    groupStage(
        serverGroupList: serverGroupList,
        serverList: serverList,
        stageArgs: [
            remoteUserAuth: remoteUserAuth,
            serverList: serverList,
            healthCheckPath: healthCheckPath,
            healthCheck: healthCheck,
            ownerName: ownerName
        ],
        this.&doSequentialStageGroup
    )
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

    stage(labelPrefix+"healthcheck switch-mode") {
        printlnHeadMessage labelPrefix+"healthcheck switch-mode"
        when(!skipStage) {
            println "● healthcheck switch-mode $args.stageArgs.healthCheck Started"
            doSwitchHealthCheckMode(args.stageArgs.remoteUserAuth, args.serverList, args.stageArgs.healthCheckPath, args.stageArgs.healthCheck, args.stageArgs.ownerName)
            println "● healthcheck switch-mode $args.stageArgs.healthCheck End"
        }
    }

    return skipStage
}