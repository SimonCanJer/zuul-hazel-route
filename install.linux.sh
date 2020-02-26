mypath= "$(pwd)"
sudo  mkdir -p "$1"
cd    "$1" || return $?
git    clone https://github.com/SimonCanJer/microhazle.git
cd     microhazle || return $?
mvn    install
echo   "microhazle installed into $1"
cd      "$1" || return $?

ECHO type we must install spring lib for microhazel and routing example
 if [ ${#2} -eq  0 ]
  then
    read  -r facade_dir
  else
    facade_dir=$2
  fi
mkdir  -p "$facade_dir"
cd     "$facade_dir" || return

echo   "**********************************************************************************"
echo    "***** installing spring library and routed application into %facade_dir%    *****"
echo    "**********************************************************************************"
git     clone https://github.com/SimonCanJer/spring-micro-hazel.git
cd      spring-micro-hazel || return $?
mvn     install
cd      "$mypath" || return $?
echo    "**********************************************************************************"
echo    "*****       running Maven to build and install the zuul application          *****"
echo    "**********************************************************************************"
mvn    install
echo  "********************** ***********************************************************"
echo  "**********     ZUUL ROUTING EXAMPLE HAS BEEN INSTALLED, LOUNCHING  ALL  **********"
echo  "********************** ***********************************************************"
cd    "$facade_dir" ||  return
cd     spring-micro-hazel\micro-hazel-spring-examples\target || return
exec   java -jar  micro-hazel-spring-examples-1.0.4-exec.jar  backend &
timeout 45
exec   java -jar  micro-hazel-spring-examples-1.0.4-exec.jar  facade &
timeout 45
cd $mypath
cd target
exec java -jar zuul-lb-1.0-SNAPSHOT-exec.jar &

