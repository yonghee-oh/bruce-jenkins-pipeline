import jenkins.model.* 
import groovy.json.JsonSlurper

/*
 * 전체 Jenkins Job 목록 (Folder Properties 에서 참조)
 * ( "system-" 또는 "system_" 또는 "SYSTEM-" 또는 "SYSTEM_" 으로 시작하는 배치 Job은 제외)
 * BATCH_SERVER_URL 필수값
 * BATCH_SERVER_AUTH 필수값
 * @author bruce_oh
 * @date 2020. 4. 24.
 */

String jenkinsCredentialsId = ""
String userCredentials = ""

String batchServerUrl = BATCH_SERVER_URL
String auth = BATCH_SERVER_AUTH

if (auth.indexOf('[') > -1 && auth.lastIndexOf(']') > auth.indexOf('[') && auth.lastIndexOf(']') <= auth.size()) {
    jenkinsCredentialsId = auth.substring(auth.indexOf('[') + 1, auth.lastIndexOf(']')).trim()
}

def jenkinsCredentials = com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(
        com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl.class,
        Jenkins.instance,
        null,
        null
)

for (creds in jenkinsCredentials) {
    if(creds.id == jenkinsCredentialsId) {
        userCredentials = creds.username+":"+creds.password
        break
    }
}


try {
    String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userCredentials.getBytes()))

    def http_client = new URL(batchServerUrl).openConnection() as HttpURLConnection
    http_client.setRequestMethod('GET')
    http_client.setRequestProperty ("Authorization", basicAuth)
    http_client.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
    http_client.setRequestProperty("Content-Language", "UTF-8")
    http_client.setUseCaches(false)
    http_client.connect()

    def httpResponse = [:]    
    
    // Check if got HTTP 200
    if (http_client.responseCode == 200) {
        httpResponse = new JsonSlurper().parseText(http_client.inputStream.getText('UTF-8'))
    }
    
    def jobList = []
    httpResponse.jobs.each { job ->
        String jobName = job.name.trim().toLowerCase()
        if(jobName.indexOf('system-') < 0 && jobName.indexOf('system_') < 0) {
            jobList.add(job.name+" ("+job.url+")"+":selected")
        }
    }
    return jobList.sort()

} catch (Exception e) {
    throw e
}