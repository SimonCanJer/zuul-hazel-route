package cloud.lb.zuul;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.cloud.netflix.zuul.EnableZuulServer;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.UnknownFormatConversionException;

@SpringBootApplication
@EnableZuulProxy
//@EnableDiscoveryClient
public class ZuulRunner {

    static public void main(String args[])
    {
// This is name of federation of services, where we want to route
        System.getProperties().put("services.federation.name","services.federation");
       /* these are maps of rerawting rules. For instance zuul.route.map defines mapping of first element of
       URL path on element used in internal routing. Thus, /notes_service/** will be rerouted as /toDo/**
        */
        System.getProperties().put("zuul.route.map","notes_service=toDo,other_service=nothing");

        System.getProperties().put("zuul.route.map-prefix","x_service=add");
        SpringApplication.run(ZuulRunner.class, args);
    }
}
