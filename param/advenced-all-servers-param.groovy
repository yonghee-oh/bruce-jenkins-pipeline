import jenkins.model.*
import com.cloudbees.hudson.plugins.folder.*
import com.mig82.folders.*

/*
 * 서버 그룹 목록 파라미터 (Folder Properties 에서 참조)
 * JOB_NAME 필수값
 * @author bruce_oh
 * @date 2020. 4. 17.
 */

// Propertie Key
def myKey = 'ALL_GROUP_NAMES'
def mySubKey = 'ALL_SERVERS'
def myGroupNames = []
def myParam = []

Jenkins.instance.getAllItems().each{ item ->
    if(item.getName() == JOB_NAME) {
        item.getParent().getProperties().each { job ->
            if (job.getClass().getName() == "com.mig82.folders.properties.FolderProperties") {
                job.getProperties().each {
                    def pKey = it.getKey()
                    if (pKey != null && pKey.equals(myKey)) {
                        for(String prop : it.getValue().tokenize('\\,')) {
                            myGroupNames.add(prop.trim())
                        }
                    }
                }
            }
        }
    }
}


if (SELECT_SERVERS.equals("LIST")) {
    Jenkins.instance.getAllItems().each{ item ->
        if(item.getName() == JOB_NAME) {
            item.getParent().getProperties().each { job ->
                if (job.getClass().getName() == "com.mig82.folders.properties.FolderProperties") {
                    job.getProperties().each {
                        def pKey = it.getKey()
                        if (myGroupNames.size() > 0) {
                            for(String groupName : myGroupNames) {
                                if (pKey != null && pKey.equals(groupName)) {
                                    for(String prop : it.getValue().tokenize('\\,')) {
                                        myParam.add(prop)  
                                    }
                                }    
                            }
                        } else {
                            if (pKey != null && pKey.equals(mySubKey)) {
                                for(String prop : it.getValue().tokenize('\\,')) {
                                    myParam.add(prop)  
                                }
                            }    
                        }
                    }
                }
            }
        }
    }

    myParam = myParam.flatten().toSet() as List

    return myParam
} else {
    return [SELECT_SERVERS + ":selected"]
}