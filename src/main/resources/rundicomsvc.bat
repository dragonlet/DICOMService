@echo off

@rem Get the directory that this script resides in
for %%F in (%0) do set dirname=%%~dpF

@rem set the current directory so that logs will go in the right place
@rem and the jks file is accessible
@rem first set the drive (equivalent to C: or whatever drive)
%dirname:~0,2%
@rem then set the directory
cd %dirname%

if exist log goto gotlog
    mkdir log
:gotlog

java -cp dicomsvc-@@VERSION@@-jar-with-dependencies.jar edu.umro.dicom.service.Root > log\%DATE:~10,4%_%DATE:~4,2%_%DATE:~7,2%_%TIME:~0,2%_%TIME:~3,2%_%TIME:~6,2%_%TIME:~9,2%.log 2>&1

