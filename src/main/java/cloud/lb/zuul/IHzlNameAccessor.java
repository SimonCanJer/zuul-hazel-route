package cloud.lb.zuul;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URL;
import java.util.Map;

public interface IHzlNameAccessor {
   public interface Invalid {
       void invalidate();
   }
   void init(String appName);
   URL forName(String str,Invalid[] invalid);
}
