/*
 * toggle feature 상태/ON/OFF
 * @author bruce_oh
 * @date 2023. 2. 20.
 */

node {

    // properties
    def allGroupNames
    def allServers
    def ownerName
    
    // params
    def remoteUserAuth
    def selectServers
    def serverGroupList = [:]
    def serverList = []
    def serverPortList = []
    def toggleApiPath
    def toggleFeatureList = []
    def toggleAction
    

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

        try {
            // println "SERVER_PORTS = $SERVER_PORTS"
            def ports = "$SERVER_PORTS"
            for (String port : ports.split(',')) {
                serverPortList.add(port.trim())
            }
        } catch (MissingPropertyException e) {
            println "Error SERVER_PORTS"
            throw e
        }

        try {
            // println "TOGGLE_API_PATH = $TOGGLE_API_PATH"
            toggleApiPath = "$TOGGLE_API_PATH"
        } catch (MissingPropertyException e) {
            println "Error TOGGLE_API_PATH"
            throw e
        }

        try {
            // println "TOGGLE_FEATUERS = $TOGGLE_FEATUERS"
            def list = "$TOGGLE_FEATUERS"
            toggleFeatureList = list.tokenize('\\,')
            for (int i = 0; i < toggleFeatureList.size(); i++) {
                println "toggle feature $i : " + toggleFeatureList[i]
                toggleFeatureList[i] = toggleFeatureList[i].trim()
            }
        } catch (MissingPropertyException e) {
            println "Error TOGGLE_FEATUERS"
            throw e
        }

        try {
            // println "TOGGLE_ACTION = $TOGGLE_ACTION"
            toggleAction = "$TOGGLE_ACTION"
        } catch (MissingPropertyException e) {
            println "Error TOGGLE_ACTION"
            throw e
        }

    }

    groupStage(
        serverGroupList: serverGroupList,
        serverList: serverList,
        stageArgs: [
            remoteUserAuth: remoteUserAuth,
            serverPortList: serverPortList,
            toggleApiPath: toggleApiPath,
            toggleFeatureList: toggleFeatureList,
            toggleAction: toggleAction,
            ownerName: ownerName
        ],
        this.&doSequentialStageGroup
    )

}

void doToggleActionWithHttpie(remoteUserAuth, serverList, serverPortList, action='status', path='', featureName='', ownerName='') {
    withCredentials([usernamePassword(credentialsId: "$remoteUserAuth",
        usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {

        for (int i = 0; i < serverList.size(); i++) {

            def serverInfo = serverList[i]
            println "s-i[$i] = $serverInfo"

            def remote = getRemoteFromServerInfo(serverInfo, USERNAME, PASSWORD)

            println "call remote"
            // def owner = getDefaultString(ownerName, USERNAME)

            def apiPath = ("/".equalsIgnoreCase(path.getAt(0))) ? path.substring(1) : path

            def cmdList = []

            for (String apiPort in serverPortList) {
                def baseUrl = "$remote.host:$apiPort/$apiPath/$featureName"

                def cmd
                switch(action.trim().toLowerCase()) {
                    case 'status': 
                        // GET method
                        cmd = "http GET $baseUrl || exit 0"
                        break
                    case 'on':
                        // POST method
                        cmd = "http POST $baseUrl name=$featureName enabled=true --ignore-stdin || exit 0"
                        break
                    case 'off':
                        // POST method
                        cmd = "http POST $baseUrl name=$featureName enabled=false --ignore-stdin || exit 0"
                        break
                    default:
                        cmd = "echo '$baseUrl action invalid!!!'"
                        println "The value is unknown"
                        break
                }

                cmdList.add(cmd)
            }

            for (String cmd in cmdList) {
                sh(cmd)
            }
            
            // sleep 1
            println "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
        }

        println "Finished All Toggle Action!!!"
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
    println "TOGGLE FEATURE LIST : $args.stageArgs.toggleFeatureList"

    // response for input(abort)
    boolean skipStage = false
    // ----------------------------------------------------------------

    for (String featureName in args.stageArgs.toggleFeatureList) {
        String stageName = labelPrefix+featureName
        stage(stageName) {
            printlnHeadMessage stageName
            println "● $featureName ($args.stageArgs.toggleAction) Started"
            doToggleActionWithHttpie(args.stageArgs.remoteUserAuth, args.serverList, args.stageArgs.serverPortList, args.stageArgs.toggleAction, args.stageArgs.toggleApiPath, featureName, args.stageArgs.ownerName)
            println "● $featureName ($args.stageArgs.toggleAction) End"
        }
    }

    return skipStage
}