#!/bin/bash

# --- REMOTE ANDROID BUILDS ---
#
# This script TURNS ON remote android builds for your project and make them be executed on a remote server instead of local machine
#
# BEFORE using this script you should completely prepare a remote host for building android projects
#
# You MUST explicitly call [ remote_build.off ] when you done working

ROOT_PROJECT_NAME=`pwd | grep -o '[^/]*$'`
LOCAL_USER=`whoami`

SERVER_SSH_ALIAS=`cat remote_android_config.properties | grep "server.ssh.alias=" | cut -d'=' -f2 | sed 's/\"//g'`
if [ -z $SERVER_SSH_ALIAS ]; then
  echo 'Error: mandatory parameter [server.ssh.alias] is not set'
  exit 1
fi
REMOTE_USER=`cat remote_android_config.properties | grep "server.ssh.user.name=" | cut -d'=' -f2 | sed 's/\"//g'`
if [ -z $REMOTE_USER ]; then
  echo 'Error: mandatory parameter [server.ssh.user.name] is not set'
  exit 1
fi


REMOTE_FOLDER=`cat remote_android_config.properties | grep "server.ssh.folder=" | cut -d'=' -f2 | sed 's/\"//g'`
if [ -z $REMOTE_FOLDER ]; then
  echo 'Error: mandatory parameter [server.ssh.folder] is not set'
  exit 1
fi

ssh -q -o BatchMode=yes -o ConnectTimeout=5 $SERVER_SSH_ALIAS exit
if [ $? != "0" ]; then
    echo "Error: no connection to the server"
    exit 1
fi

PREFIX="/mnt/sdc1"
REMOTE_FOLDER_PATH="$PREFIX/$REMOTE_USER/$REMOTE_FOLDER/$LOCAL_USER/$ROOT_PROJECT_NAME"
ssh $SERVER_SSH_ALIAS "mkdir -p $PREFIX/$REMOTE_USER/$REMOTE_FOLDER/$LOCAL_USER/$ROOT_PROJECT_NAME; echo sdk.dir=/home/$REMOTE_USER/Android/Sdk > $PREFIX/$REMOTE_USER/$REMOTE_FOLDER/$LOCAL_USER/$ROOT_PROJECT_NAME/local.properties"

mkdir -p ~/.gradle/init.d
rm ~/.gradle/init.d/mirakle.gradle 2>/dev/null
cat <<EOF >> ~/.gradle/init.d/mirakle.gradle

def projectToBuildRemotely = "$ROOT_PROJECT_NAME"

initscript {
	repositories {
   	 maven{
   		 url=uri(System.getProperty('user.home') + '/.m2/repository')
   	 }
   	 mavenCentral()
	}
	dependencies {
    	classpath 'io.github.adambl4:mirakle:1.6.1'
	}
}


apply plugin: Mirakle

rootProject {
    if (projectToBuildRemotely.equals(name)) {
        project.logger.lifecycle('Remote builds mode activated for this project. Going to start remote build now.\nPass \`-x mirakle\` to build locally.')
        mirakle {
            host '$SERVER_SSH_ALIAS'
            remoteFolder "$REMOTE_FOLDER_PATH"
			sshClient = "ssh" // test
            excludeCommon = ["*.DS_Store"]
            excludeCommon += ["*.hprof"]
            excludeCommon += [".idea"]
            excludeCommon += [".gradle"]
            // excludeCommon += ["**/.git/"] // fails for signal
            excludeCommon += ["**/.gitignore"]
            excludeCommon += ["**/local.properties"]
            excludeCommon += ["**/backup_*.gradle"]
            excludeCommon += ["remote_*.sh"]
            excludeCommon += ["remote_android_*.properties"]
            excludeCommon += ["remote_android_*.config"]
            excludeLocal += ["**/build/"]
            excludeLocal += ["*.keystore"]
            excludeLocal += ["*.apk"]
            excludeRemote += ["**/src/"]
            excludeRemote += ["**/build/.transforms/**"]
            excludeRemote += ["**/build/kotlin/**"]
            excludeRemote += ["**/build/intermediates/**"]
            excludeRemote += ["**/build/tmp/**"]
            rsyncToRemoteArgs += ["-avAXEWSlHh"]
            rsyncToRemoteArgs += ["--info=progress2"]
            // rsyncToRemoteArgs += ["-i"]
            rsyncToRemoteArgs += ["--compress-level=9"]
            rsyncFromRemoteArgs += ["-avAXEWSlHh"]
            rsyncFromRemoteArgs += ["--info=progress2"]
            // rsyncFromRemoteArgs += ["-i"]
            rsyncFromRemoteArgs += ["--compress-level=9"]
            fallback false
            downloadInParallel false
            downloadInterval 3000
            breakOnTasks = ["install", "package"]
        }
    } else {
        project.logger.lifecycle("Remote builds mode activated but for different project. Stop now.")
    }
}
EOF

echo "Remote build enabled"

