
cd $( dirname $0 ) >/dev/null 2>&1

export log=$(date +/tmp/dicomsvc_%Y-%m-%d_%H-%M-%S.log)

echo Putting log entries in: ${log}

java -cp dicomsvc-@@VERSION@@-jar-with-dependencies.jar edu.umro.dicom.service.Root >${log} 2>&1

