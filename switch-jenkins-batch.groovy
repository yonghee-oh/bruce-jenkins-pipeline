/*
 * 배포 전,후 Jenkins Batch 동작 설정 
 * @author bruce_oh
 * @date 2020. 3. 24.
 */

node {

    // params
    def batchServerAuth
    def jenkinsBatchList
    def batchAction

    // variable
    def jenkinsCredentialsId

    stage('preparation') {
        printlnHeadMessage "preparation"

        // println "==================== Setting Parameters ===================="


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
            // println "BATCH_ACTION = $BATCH_ACTION"
            // batchAction = "$BATCH_ACTION".toUpperCase().equals("ON") ? "enable" : "disable"
            batchAction = "$BATCH_ACTION"
        } catch (MissingPropertyException e) {
            println "Error BATCH_ACTION"
            throw e
        }
    }

    stage("batch switch-mode") {
        printlnHeadMessage "batch switch-mode"
        println "● batch switch-mode $batchAction Started"
        doSwitchBatch(jenkinsCredentialsId, jenkinsBatchList, batchAction)
        println "● batch switch-mode $batchAction End"
    }

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
