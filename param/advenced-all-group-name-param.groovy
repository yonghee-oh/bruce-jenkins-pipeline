import jenkins.model.*
import com.cloudbees.hudson.plugins.folder.*
import com.mig82.folders.*

/*
 * 전체 그룹 이름 파라미터 (Folder Properties 에서 참조)
 * JOB_NAME 필수값
 * @author bruce_oh
 * @date 2020. 4. 17.
 */

// Propertie Key
def myKey = 'ALL_GROUP_NAMES'
def myParam = []

Jenkins.instance.getAllItems().each{ item ->
    if(item.getName() == JOB_NAME) {
        item.getParent().getProperties().each { job ->
            if (job.getClass().getName() == "com.mig82.folders.properties.FolderProperties") {
                job.getProperties().each {
                    def pKey = it.getKey()
                    if (pKey != null && pKey.equals(myKey)) {
                        def list = it.getValue().tokenize('\\,')
                        for(String prop : list) {
                            myParam.add(prop.trim())
                        }
                    }
                }
            }
        }
    }
}

myParam.add("LIST")
myParam.add("ALL")

myParam[0] = myParam.get(0) + ":selected"

return myParam
