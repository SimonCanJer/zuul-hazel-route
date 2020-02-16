package cloud.lb.zuul.additional.populated;

import cloud.lb.zuul.IHazelcastExporter;
import cloud.lb.zuul.IHzlNamePopulator;
import cloud.lb.zuul.additional.populated.additional.filtering.DispatchFilter;
import com.netflix.zuul.ZuulFilter;
import microhazle.building.api.CustomEndPoint;
import microhazle.building.api.IAServicePopulator;
import microhazle.building.api.IBuild;
import microhazle.building.api.IMounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.netflix.zuul.filters.ZuulProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.RequestEntity;
import org.springframework.web.client.RestTemplate;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

@Configuration
public class ConfigAdditionalDiscovery {
    static Logger logger;
    static {
        logger=LoggerFactory.getLogger("additionalDiscoveryConfig");
    }

 static class NameUpdater
    {
        @Autowired
        ZuulProperties zuulProps;
        @Autowired
        IHazelcastExporter exporter;
        void update()
        {
            Map<String, ZuulProperties.ZuulRoute> routes= zuulProps.getRoutes();
            IHzlNamePopulator populator= exporter.namePopulator();
            IHzlNamePopulator.Invalid []invalid=new IHzlNamePopulator.Invalid[1];
            for(String s:routes.keySet())
            {
                logger.info("query name "+s);
                populator.forName(s, invalid);
            }
        }

    }

    @Bean
    NameUpdater nameUpdater()
    {

        return new NameUpdater();

    }

   @Bean
   IHazelcastExporter hazelcastExporter()
   {
       return new HazelcastExporter();
   }
   @Bean
    ZuulFilter  dispatchingFilter()
   {
       return new DispatchFilter();

   }

    static class HazelcastExporter implements IHazelcastExporter
    {
        @Value("${services.federation.name}")
        String appName;
        IBuild mHzlBuilder= IBuild.INSTANCE;
        IMounter mounter;

        ReentrantLock locker=new ReentrantLock(true);
        HazelcastExporter()
        {


        }
        public void init(String appName)
        {
            if(mounter==null)
            {
                try {
                    locker.lock();
                    if(mounter==null) {
                        mounter = mHzlBuilder.forApplication(appName);
                        mounter.mountAndStart(null);

                    }
                }
                finally
                {
                    locker.unlock();
                }


            }
        }
        HazelcastNamePopulator populator;

        @Override
        public IMounter mounter() {

            init(appName);
            return mounter;
        }

        @Override
        public IHzlNamePopulator namePopulator() {
            init(appName);
            if(populator==null)
            {
                try {
                    locker.lock();
                    populator = new HazelcastNamePopulator(mounter);
                }
                finally
                {
                    locker.unlock();
                }

            }
            return populator;
        }
    }
    static class EndPointHolder
    {
        AtomicReference<List<CustomEndPoint>> refEndPoints = new AtomicReference<>();
        AtomicLong mCalls=new AtomicLong(0);
        String url(CustomEndPoint[] selected)
        {
            List<CustomEndPoint> urls= refEndPoints.get();
            int size;
            if((size=urls.size())==0)
                return null;
            int index= (int) (mCalls.getAndIncrement()%size);

            return (selected[0]=urls.get( (index))).getUrl();
        }
        void sink(List<CustomEndPoint> lst)
        {
            List<CustomEndPoint> copy= new CopyOnWriteArrayList(lst);
            logger.info("update for end point list\n"+ lst);
            refEndPoints.set(copy);
        }
        void remove(CustomEndPoint ep)
        {
            refEndPoints.get().remove(ep);

        }
    }
    static class HazelcastNamePopulator implements IHzlNamePopulator
    {

        private final IMounter mounter;
        IAServicePopulator populator;
        AtomicLong calls= new AtomicLong(0);
        ReentrantLock lock= new ReentrantLock();
        Map<String,EndPointHolder> mapNames=new HashMap<>();


        long time=-1;
        HazelcastNamePopulator(IMounter mounter)
        {
            this.mounter=mounter;
            populator = mounter.endPointPopulator();

        }


        @Override
        public void init(String appName) {

        }


        @Override
        public URL forName(String str,Invalid[] invalid) {
           if(!mapNames.containsKey(str))
           {
               try {
                   lock.lock();
                   if(!mapNames.containsKey(str))
                   {

                       EndPointHolder holder= new EndPointHolder();
                       ArrayList<CustomEndPoint> lst= new ArrayList<>();
                       populator.queryEndPoint(str,lst, holder::sink);
                       logger.info("discovered end point list for key "+ str );
                       holder.sink(lst);
                       mapNames.put(str,holder);
                   }
               }
               finally
               {
                   lock.unlock();
               }
           }
           EndPointHolder h=null;
           CustomEndPoint [] cep= new CustomEndPoint[1];
            String strUrl=null;
            try {
                strUrl=(h=mapNames.get(str)).url(cep);
                if(strUrl==null)
                    return null;

                return new URL(strUrl);
            } catch (MalformedURLException e) {
                logger.error("the url is malformed ",strUrl);
                return null;
            }
            finally{
                EndPointHolder hAncor=h;

                invalid[0]= new Invalid() {
                    @Override
                    public void invalidate() {
                        if(hAncor!=null)
                            if(cep!=null) {
                            logger.error("removing from cache compaligning for not connectable end point\n ",cep[0]);
                            hAncor.remove(cep[0]);
                            populator.complaignInvalid(cep[0]);

                        }
                    }
                };
            }
        }
    }

    @Bean
    ApplicationContextAware appContextAware()
    {
        return new ApplicationContextAware() {

            @Override
            public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
                IProperties props=applicationContext.getBean(IProperties.class);
                NameUpdater updater= applicationContext.getBean(NameUpdater.class);
                IHzlNamePopulator pop=applicationContext.getBean(IHazelcastExporter.class).namePopulator();
                updater.update();
                for(String s:props.populatedNames())
                    pop.forName(s,new IHzlNamePopulator.Invalid[1]);


            }
        };

    }
    static class Properties implements IProperties
    {
        @Value("${spring.application.name}")
        String appName;


        @Value("${naming.populated.lookup}")
        String services;
        @Override
        public String applicationName() {
            return appName;
        }

        @Override
        public Set<String> populatedNames() {
            Set<String>  res= new HashSet<>();
            String[] data= services.split("\\,");
            for(String s: data)
            {
                res.add(s);
            }
            return res;
        }
    }
    @Bean
    IProperties discoveryProperties()
    {
        return new Properties();
    }

}
