# zuul-hazel-route
Protoptype/ example of  service publishing and request routing using zuul and hazelcast with no eurica
 and concrete IP addresses. 
  -The project uses a fragments of solutions  which have been created previously and demonstrates any possibilties
 and benefites of Hazelcast use for REST services discovery.
  -The project is based on base of Hazelcast based tools and API which are in the project https://github.com/SimonCanJer/microhazle.
  -The project configuration is also suppose usage of REST faced application, which provides ToDo notes service, uses
 microservice, which consumes and processes requests over Hazelcast queues and registers URL of controller of the REST 
 facade over Hazelcast. API of the mentioned tool is used. The project of the application, underlaying microservice and related add ins
 to Spring is here:https://github.com/SimonCanJer/spring-micro-hazel
 - Pom of this project refers to jars which are generated during mvn install ran on  https://github.com/SimonCanJer/microhazle directory.
 - project https://github.com/SimonCanJer/spring-micro-hazel is used for  request handling after routing(from POSTMAN for instance). This project is
 interrelated with https://github.com/SimonCanJer/spring-micro-hazel by means of the request mapping ("/toDo")
 - both of the projects (referred https://github.com/SimonCanJer/microhazle) and partner in demo(https://github.com/SimonCanJer/spring-micro-hazel) can be installed
 automatically by means of running either of  the install scripts
 :install.win.bat or install.lunux.sh (depending on OS of coarse). The scripts make all the installation work including
 clone of related repositories and sequenting call of maven installer. Finally it rans Facade application exposing
 REST controller on the top of Tomcat which is backed by microservice implementing the whole work behind the facade.
#The project structure 
 
