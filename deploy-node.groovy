/*
 * 빌드한 소스를 서버에 배포 (Deploy)
 * @author bruce_oh
 * @date 2020. 4. 8.
 */

node {

    // properties
    def allGroupNames
    def allServers
    def distDirectory
    def keepBackupDays = 30
    def ownerName
    
    // params
    def remoteUserAuth
    def selectServers
    def serverGroupList = [:]
    def serverList = []
    def appPath
    def syncType

    // variable
    def rsyncDeployDo = false
    def warDeployDo = false
    def jarDeployDo = false
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
                // println "${DIST_DIRECTORY}"
                distDirectory = "${DIST_DIRECTORY}"
            } catch (MissingPropertyException e) {
                println "Error DIST_DIRECTORY"
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

    }


    groupStage(
        serverGroupList: serverGroupList,
        serverList: serverList,
        stageArgs: [
            remoteUserAuth: remoteUserAuth,
            distDirectory: distDirectory,
            appPath: appPath,
            appDocBase: appDocBase,
            keepBackupDays: keepBackupDays,
            ownerName: ownerName,
            rsyncDeployDo: rsyncDeployDo,
            warDeployDo: warDeployDo,
            jarDeployDo: jarDeployDo
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

    stage(labelPrefix+"move recent(backup)") {
        printlnHeadMessage labelPrefix+"move recent(backup)"
        when(!skipStage) {
            println "● move recent(backup) Started"
            doMoveRecentBackup(args.stageArgs.remoteUserAuth, args.serverList, args.stageArgs.appDocBase, args.stageArgs.ownerName)
            println "● move recent(backup) End"
        }
    }

    stage(labelPrefix+"backup") {
        printlnHeadMessage labelPrefix+"backup"
        when(!skipStage) {
            println "● backup Started"
            doBackup(args.stageArgs.remoteUserAuth, args.serverList, args.stageArgs.appPath, args.stageArgs.appDocBase, args.stageArgs.ownerName)
            println "● backup End"
        }
    }

    stage(labelPrefix+"sync") {
        printlnHeadMessage labelPrefix+"sync"
        if(!(args.stageArgs.rsyncDeployDo || args.stageArgs.warDeployDo || args.stageArgs.jarDeployDo)) {
            println "● No Matched sync type... (NOT WORKING)"
        }
        when((args.stageArgs.rsyncDeployDo || args.stageArgs.warDeployDo || args.stageArgs.jarDeployDo) && !skipStage) {
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
        if(!(args.stageArgs.rsyncDeployDo || args.stageArgs.warDeployDo || args.stageArgs.jarDeployDo)) {
            println "● No Matched sync type... (NOT WORKING)"
        }
        when((args.stageArgs.rsyncDeployDo || args.stageArgs.warDeployDo || args.stageArgs.jarDeployDo) && !skipStage) {
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

    return skipStage
}
