import jenkins.model.*

/*
 * 전체 Jenkins User/Password 인증서 목록 (각 Scope에 저장된 인증서 중 User/Passowrd 타입)
 * jenkins-batch-server 로 만든 credentials ID가 있다면, 선택한 것으로 처리
 * @author bruce_oh
 * @date 2020. 4. 24.
 */

def jenkinsCredentials = com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(
    com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl.class,
    Jenkins.instance,
    null,
    null
)

def myCredentialsList = []

for (creds in jenkinsCredentials) {
    String item = "$creds.username($creds.description)[$creds.id]"
    if('jenkins-batch-server' == creds.id) {
        item = item + ":selected"
    }
    myCredentialsList.add(item)
}

return myCredentialsList