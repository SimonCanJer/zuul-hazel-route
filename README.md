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
  - The project's main package is cloud.lb.zuul. The package exposes interfaces IHazelcastExporter, IHazelcastNameAccessor, which define main methods
  to create gateway and routing. ZuulRunner just defines necessary properties in order to connect to federation of services, which the gate is opened for, and
  additional mapping to intergrate Hazelcast discovery/population mechanism within Zuul infrastruture. Thus, application.properties file
  contains the meanful entry zuul.routes.notes_service.path=/notes_service/* , that is the pattern, whuch must be intercepted by installed filter, which is described
  bellow. Internal routing is defined by means of the property "zuul.route.map",  which maps entry of pattern from zuul.routes.notes_service.path on a new value. Thus, System.getProperties().put("zuul.route.map",
  "notes_service=toDo,other_service=nothing") means that entry notes_service will be replaced by toDo for internal routing
  - The main meanfull work is done inside the cloud.lb.zuul.additional.populated.ConfigAdditionalDiscovery, which exposes ZuulFilter bean
  cloud.lb.zuul.additional.populated.additional.filtering.DispathFilter, where forwarding is implemented and ApplicationContexAware listener with handler for
  context ready event. The handler initiates internals of micro-hazel tool, using its API,obtains list of available instamces of the
  alerted name to route and transmits a sink to be notified about additional registraionsof remote end points (names and their URL are regstered transparently)
  on JVM, providing the end point and extracted from internal Hazelcast collection, which is visible. The configuration eposes also
  IHazelcastExporter and IHzlNameAccessor interffaces, whih are used in the dispatch filer.
  The implementation of interfaces is leveraged by nested classes,which retrieve list of available
  URL per name and selects URL in accordance with the Round Robin principle. 
 - interceptions of request and routing are implemented is the DispatchFilter,whicg extends ZuulFilter.
 If incoming  matches a pattern which corresponds to a name, then  the implementation builds a new routing 
 path, where first element is removed or replaced by another element for internal routing. Path finaly added to a URL where the named end point exposed.
 -Forwarding done way of HttpUrlConnection, Zuul response is not sent automatically, request result is writtn to HttpServletResponse together with code.
 In a case when URL is not connectable, another URL will be selected for forwarding, and the not connectable URL
 will be removed from list and then from cross domain(federation) registration.
 - In order to test, start the application from the project https://github.com/SimonCanJer/spring-micro-hazel(both facade and DataService).
 It can be done automatically, when running installation script. Then start this application(it will be started automatically together with end point, when using
 one of installation scripts are mentioned above)
 -Run Postman (or another tool),
  type http://localhost:8050/notes_service/get_notes?for=me and send as GET(8050 is zull's port)
  you will see a JSON response
  type http://localhost:8050/notes_service/put_note?for=wife&&what=love me and send as PUT
  You should see resposne.
  
  

 
  
 
