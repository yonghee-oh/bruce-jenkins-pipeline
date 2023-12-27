/*
 * NodeJS 서버 상태/시작/정지/재시작
 * @author bruce_oh
 * @date 2021. 1. 12.
 */

node {

    // properties
    def allGroupNames
    def allServers
    def ownerName
    
    // params
    def nodeAlias
    def middlewareList
    def remoteUserAuth
    def selectServers
    def serverGroupList = [:]
    def serverList = []
    def serverAction

    // variable
    def nodeDo = false


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

        try {
            // println "MIDDLEWARE_LIST = $MIDDLEWARE_LIST"
            def list = "$MIDDLEWARE_LIST"
            middlewareList = list.tokenize('\\,')
            for (int i = 0; i < middlewareList.size(); i++) {
                // println "Middleware $i : " + middlewareList[i]
                middlewareList[i] = middlewareList[i].trim().toLowerCase()

                if('node'.equalsIgnoreCase(middlewareList[i])) {
                    nodeDo = true
                }
            }
        } catch (MissingPropertyException e) {
            println "Error MIDDLEWARE_LIST"
            throw e
        }

        try {
            // println "SERVER_ACTION = $SERVER_ACTION"
            serverAction = "$SERVER_ACTION"
        } catch (MissingPropertyException e) {
            println "Error SERVER_ACTION"
            throw e
        }

    }

    groupStage(
        serverGroupList: serverGroupList,
        serverList: serverList,
        stageArgs: [
            remoteUserAuth: remoteUserAuth,
            nodeAlias: nodeAlias,
            nodeDo: nodeDo,
            serverAction: serverAction,
            ownerName: ownerName
        ],
        this.&doSequentialStageGroup
    )

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

    stage(labelPrefix+"node") {
        printlnHeadMessage labelPrefix+"node" + (args.stageArgs.nodeDo?"":" (Skip)")
        when(args.stageArgs.nodeDo && !skipStage) {
            println "● node $args.stageArgs.serverAction Started"
            def ndAction = getNodeAction(args.stageArgs.nodeAlias, args.stageArgs.serverAction)
            doServerAction(args.stageArgs.remoteUserAuth, args.serverList, ndAction, args.stageArgs.ownerName)
            println "● node $args.stageArgs.serverAction End"
        }
    }

    return skipStage
}