set mypath=%cd%
echo %mypath%
echo "installing micro-hazel project > directory "+ %1
mkdir %1
cd %1
call git clone https://github.com/SimonCanJer/microhazle.git
cd microhazle
call mvn install
echo microhazle installed into %1
cd %mypath%
echo off
ECHO type we must install spring lib for microhazel and routing example
set /p facade_dir=directory to install?
mkdir  %facade_dir%
cd    %facade_dir%
echo on
echo    **********************************************************************************
echo    *****       installing spring library and routed application into %facade_dir%     *****
echo    **********************************************************************************
call  git clone https://github.com/SimonCanJer/spring-micro-hazel.git
cd    spring-micro-hazel
call  mvn install
cd %mypath%
echo    **********************************************************************************
echo    *****       running Maven to build and install the zuul application               *****
echo    **********************************************************************************
call mvn install
echo  ********************** ***********************************************************
echo  **********     ZUUL ROUTING EXAMPLE HAS BEEN INSTALLED, LOUNCHING  ALL **********
echo  ********************** ***********************************************************
cd %facade_dir%
cd spring-micro-hazel\micro-hazel-spring-examples\target
start  java -jar  micro-hazel-spring-examples-1.0.4-exec.jar  backend
timeout 45
start  java -jar  micro-hazel-spring-examples-1.0.4-exec.jar  facade
timeout 45
cd %mypath%
cd target
java -jar zuul-lb-1.0-SNAPSHOT-exec.jar

