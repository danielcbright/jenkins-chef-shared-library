/* groovylint-disable LineLength */
def call () {
  echo "Check if version in metadata.rb is higher than what's currently on the master branch"
  script {
    cookbookName = sh (
      script: 'sed -e "s/^\'//" -e "s/\'$//" <<< `awk \'{for (I=1;I<=NF;I++) if ($I == "name") {print $(I+1)};}\' metadata.rb`',
      returnStdout: true
    ).trim()
    cookbookVersion = sh (
      script: 'sed -e "s/^\'//" -e "s/\'$//" <<< `awk \'{for (I=1;I<=NF;I++) if ($I == "version") {print $(I+1)};}\' metadata.rb`',
      returnStdout: true
    ).trim()
    sh 'git show origin/master:metadata.rb > metadata_master.rb'
    cookbookVersionMaster = sh (
      script: 'sed -e "s/^\'//" -e "s/\'$//" <<< `awk \'{for (I=1;I<=NF;I++) if ($I == "version") {print $(I+1)};}\' metadata_master.rb`',
      returnStdout: true
    ).trim()
  }
  if ( cookbookVersion > cookbookVersionMaster ) {
    echo "PASS: local cookbook version [$cookbookVersion] is higher than master branch version [$cookbookVersionMaster]"
  } else {
    error "FAIL: local cookbook version [$cookbookVersion] is NOT higher than master branch version [$cookbookVersionMaster]"
  }
}
