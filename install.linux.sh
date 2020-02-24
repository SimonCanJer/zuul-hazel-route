 mypath="$pwd"

sudo mkdir $1
cd     $1
exec git clone https://github.com/SimonCanJer/microhazle.git
cd microhazle
exec  mvn install
echo microhazle installed into $1
cd $1

ECHO type we must install spring lib for microhazel and routing example
 if [[${#2} -eq 0]]
then
read facade_dir
else
facade_dir=$2
mkdir $facade_dir
cd      $facade_dir

echo    **********************************************************************************
echo    *****       installing spring library and routed application into %facade_dir%     *****
echo    **********************************************************************************
exec   git clone https://github.com/SimonCanJer/spring-micro-hazel.git
cd    spring-micro-hazel
exec  mvn install
cd   $mypath
echo    **********************************************************************************
echo    *****       running Maven to build and install the zuul application               *****
echo    **********************************************************************************
exec  mvn install
echo  ********************** ***********************************************************
echo  **********     ZUUL ROUTING EXAMPLE HAS BEEN INSTALLED, LOUNCHING  ALL **********
echo  ********************** ***********************************************************
cd $facade_dir
cd spring-micro-hazel\micro-hazel-spring-examples\target
exec  java -jar  micro-hazel-spring-examples-1.0.4-exec.jar  backend &
timeout 45
exec start  java -jar  micro-hazel-spring-examples-1.0.4-exec.jar  facade &
timeout 45
cd $mypath
cd target
exec java -jar zuul-lb-1.0-SNAPSHOT-exec.jar &

