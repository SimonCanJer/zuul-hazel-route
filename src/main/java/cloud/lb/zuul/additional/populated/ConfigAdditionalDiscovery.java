package cloud.lb.zuul.additional.populated;

import cloud.lb.zuul.IHazelcastExporter;
import cloud.lb.zuul.IHzlNameAccessor;
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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

@Configuration
public class ConfigAdditionalDiscovery {
    static Logger logger;

    static {
        logger = LoggerFactory.getLogger("additionalDiscoveryConfig");
    }


    static class NameUpdater {
        @Autowired
        ZuulProperties zuulProps;
        @Autowired
        IHazelcastExporter exporter;

        void update() {
            Map<String, ZuulProperties.ZuulRoute> routes = zuulProps.getRoutes();
            IHzlNameAccessor populator = exporter.namePopulator();
            IHzlNameAccessor.Invalid[] invalid = new IHzlNameAccessor.Invalid[1];
            for (String s : routes.keySet()) {
                logger.info("query name " + s);
                populator.forName(s, invalid);
            }
        }

    }

    @Bean
    NameUpdater nameUpdater() {
        return new NameUpdater();
    }

    @Bean
    IHazelcastExporter hazelcastExporter() {
        return new HazelcastExporter();
    }

    @Bean
    ZuulFilter dispatchingFilter() {
        return new DispatchFilter();

    }

    /**
     * The class implementats  the IHazelcastExporter interface and
     * initiates  underlaying staff, incluidng activation of deep laying Hazelcast
     * based instances, creates and returns implementation of the IHzlNamePopulator
     * interface
     */
    static class HazelcastExporter implements IHazelcastExporter {
        @Value("${services.federation.name}")
        String appName;
        IBuild mHzlBuilder = IBuild.INSTANCE;
        IMounter mounter;

        ReentrantLock locker = new ReentrantLock(true);

        HazelcastExporter() {
        }

        public void init(String appName) {
            if (mounter == null) {
                try {
                    locker.lock();
                    if (mounter == null) {
                        mounter = mHzlBuilder.forApplication(appName);
                        mounter.mountAndStart(null);
                    }
                } finally {
                    locker.unlock();
                }
            }
        }

        HazelcastNamePAccessor populator;

        @Override
        public IMounter mounter() {

            init(appName);
            return mounter;
        }

        @Override
        public IHzlNameAccessor namePopulator() {
            init(appName);
            if (populator == null) {
                try {
                    locker.lock();
                    populator = new HazelcastNamePAccessor(mounter);
                } finally {
                    locker.unlock();
                }

            }
            return populator;
        }
    }

    /**
     * Just container of  CustomEndPoint
     * @see CustomEndPoint
     * Contains list of the CustomEndPoint classes, which are
     * injected over a method, which is exposed also
     * as sink to notify about list of urls changed
     */
    static class EndPointHolder {
        AtomicReference<List<CustomEndPoint>> refEndPoints = new AtomicReference<>();
        AtomicLong mCalls = new AtomicLong(0);
        String url(CustomEndPoint[] selected) {
            List<CustomEndPoint> urls = refEndPoints.get();
            int size;
            if ((size = urls.size()) == 0)
                return null;
            int index = (int) (mCalls.getAndIncrement() % size);
            return (selected[0] = urls.get((index))).getUrl();
        }

        /**
         * this method
         *
         * @param lst
         */
        void sink(List<CustomEndPoint> lst) {
            List<CustomEndPoint> copy = new CopyOnWriteArrayList(lst);
            logger.info("update for end point list\n" + lst);
            refEndPoints.set(copy);
        }

        void remove(CustomEndPoint ep) {
            refEndPoints.get().remove(ep);

        }
    }

    /**
     * The class implements the IHzlNamePopulator interface and
     * provides URL for a name. It just uses and instance
     * of
     * @see IAServicePopulator interface to get list of available URLS accociated with the
     * name. The main work executed by the function
     * @see #forName(String, Invalid[]), which just takes list of URLs and selects
     * a URL to return by means of Round Robin princile
     */
    static class HazelcastNamePAccessor implements IHzlNameAccessor {

        private final IMounter mounter;
        IAServicePopulator populator;
        AtomicLong calls = new AtomicLong(0);
        ReentrantLock lock = new ReentrantLock();
        Map<String, EndPointHolder> mapNames = new HashMap<>();
        long time = -1;

        HazelcastNamePAccessor(IMounter mounter) {
            this.mounter = mounter;
            populator = mounter.endPointPopulator();

        }
       @Override
        public void init(String appName) {

        }

        /**
         *
         * @param str name
         * @param invalid callback- invalidator of an URL, which is used to notify when it is not working
         * @return requested URL
         * The method gets list of endpoints for a name and transmits a sink for future
         * notifications about list od available instances has been changed.
         * The method exposes also a handler to react on notification about
         * not working URL
         *
         */
        @Override
        public URL forName(String str, Invalid[] invalid) {
            if (!mapNames.containsKey(str)) {
                try {
                    lock.lock();
                    if (!mapNames.containsKey(str)) {
                        EndPointHolder holder = new EndPointHolder();
                        ArrayList<CustomEndPoint> lst = new ArrayList<>();
                        populator.queryEndPoint(str, lst, holder::sink);
                        logger.info("discovered end point list for key " + str);
                        holder.sink(lst);
                        mapNames.put(str, holder);
                    }
                } finally {
                    lock.unlock();
                }
            }
            EndPointHolder h = null;
            CustomEndPoint[] cep = new CustomEndPoint[1];
            String strUrl = null;
            try {
                strUrl = (h = mapNames.get(str)).url(cep);
                if (strUrl == null)
                    return null;
                return new URL(strUrl);
            } catch (MalformedURLException e) {
                logger.error("the url is malformed ", strUrl);
                return null;
            } finally {
                EndPointHolder hAncor = h;/// Holder where list is taken from remmbered
                invalid[0] = new Invalid() {
                    @Override
                    public void invalidate() {
                        if (hAncor != null)
                            if (cep != null) {
                                logger.error("removing from cache complain for not connectable end point\n ", cep[0]);
                                hAncor.remove(cep[0]);// remove bad CustomEndPoint from holder
                                populator.complaignInvalid(cep[0]);// complain for invalid end point

                            }
                    }
                };
            }
        }
    }

    @Bean
    ApplicationContextAware appContextAware() {
        return new ApplicationContextAware() {

            @Override
            public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
                IProperties props = applicationContext.getBean(IProperties.class);
                NameUpdater updater = applicationContext.getBean(NameUpdater.class);
                IHzlNameAccessor pop = applicationContext.getBean(IHazelcastExporter.class).namePopulator();
                updater.update();
                for (String s : props.populatedNames())
                    pop.forName(s, new IHzlNameAccessor.Invalid[1]);


            }
        };

    }

    static class Properties implements IProperties {
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
            Set<String> res = new HashSet<>();
            String[] data = services.split("\\,");
            for (String s : data) {
                res.add(s);
            }
            return res;
        }
    }

    @Bean
    IProperties discoveryProperties() {
        return new Properties();
    }

}
