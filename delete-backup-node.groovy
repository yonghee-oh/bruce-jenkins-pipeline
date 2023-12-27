/*
 * 만료된(오래된) 백업 파일 제거 (Delete-Backup)
 * @author bruce_oh
 * @date 2022. 8. 23.
 */

node {

    // properties
    def allGroupNames
    def allServers
    def keepBackupDays = 30
    def ownerName
    
    // params
    def remoteUserAuth
    def selectServers
    def serverGroupList = [:]
    def serverList = []
    def appPath

    // variable
    def appDocBase


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
            // println "APP_PATH = $APP_PATH"
            appPath = "$APP_PATH"
            def list = appPath.tokenize('\\/')
            appDocBase = list[list.size()-1]
        } catch (MissingPropertyException e) {
            println "Error APP_PATH"
            throw e
        }

    }


    groupStage(
        serverGroupList: serverGroupList,
        serverList: serverList,
        stageArgs: [
            remoteUserAuth: remoteUserAuth,
            appPath: appPath,
            appDocBase: appDocBase,
            keepBackupDays: keepBackupDays,
            ownerName: ownerName
        ],
        this.&doSequentialStageGroup
    )

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

    stage(labelPrefix+"check-directory") {
        printlnHeadMessage labelPrefix+"check-directory"
        when(!skipStage) {
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

    return skipStage
}
