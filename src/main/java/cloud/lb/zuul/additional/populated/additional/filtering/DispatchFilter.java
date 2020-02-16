package cloud.lb.zuul.additional.populated.additional.filtering;

import cloud.lb.zuul.IHazelcastExporter;
import cloud.lb.zuul.IHzlNamePopulator;
import com.google.gson.Gson;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.exception.ZuulException;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.netflix.zuul.filters.ZuulProperties;
import org.springframework.cloud.netflix.zuul.filters.support.FilterConstants;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class DispatchFilter extends ZuulFilter {
    Logger logger = LoggerFactory.getLogger("dispathFilter");
    @Autowired
    IHazelcastExporter hazelAccess;

    @Autowired
    ZuulProperties zuulProps;
    @Override
    public String filterType() {

        return "route";
    }
    @Value("${gateway.additional.route.naming}")
    String additional;
    HashMap<String,String> mapNaming;
    Gson gson= new Gson();
    @Value("${zuul.route.map}")
    String mapRouteExp;

    @Value("${zuul.route.map-prefix}")
    String mapPrefixExp;
    Map<String,String> mapPrefixes= new HashMap<>();
    Map<String,String> mapRoutes= new HashMap<>();
    public DispatchFilter()
    {

    }
    @PostConstruct
    void init()
    {
        mapNaming=gson.fromJson(additional,HashMap.class);
        decodeMap(mapRouteExp,mapRoutes);
        decodeMap(mapPrefixExp,mapPrefixes);
        logger.trace("found the routes \n "+ mapRoutes);
    }
    private static void decodeMap(String s,Map<String, String> map)
    {
        StringTokenizer parser= new StringTokenizer(s,",");
        while(parser.hasMoreTokens())
        {
            String[] entry= parser.nextToken().split("\\=");
            entry[0]=entry[0].trim();
            entry[1]=entry[1].trim();
            if(entry.length==2)
            {
                if(entry[0].length()>0 && entry[1].length()>0)
                {
                    map.put(entry[0],entry[1]);
                    continue;
                }
            }
            throw new UnknownFormatConversionException("s");


        }
    }

    @Override
    public int filterOrder() {
        return FilterConstants.PRE_DECORATION_FILTER_ORDER-2;
    }

    @Override
    public boolean shouldFilter() {
        RequestContext ctx=RequestContext.getCurrentContext();


        String name= null;
        name = selectName(ctx);
        return name!=null;
    }

    private String selectName(RequestContext ctx) {
        String name;
        String s=ctx.getRequest().getContextPath();
        name=mapNaming.get(s);
        if(name==null){
            Set<Map.Entry<String,ZuulProperties.ZuulRoute>> zes= zuulProps.getRoutes().entrySet();
            for(Map.Entry<String,ZuulProperties.ZuulRoute> ze:zes)
            {
               String path= ze.getValue().getPath();
               UrlSelector urlSelector= new UrlSelector(path);
               String url= ctx.getRequest().getRequestURI();
               if(urlSelector.match(url))
               {
                   name=ze.getKey();
               }
            }
        }
        return name;
    }

    @Override
    public Object run() throws ZuulException {
        String name= selectName(RequestContext.getCurrentContext());
        URL dest=null;
        IHzlNamePopulator.Invalid[] invalidator = new IHzlNamePopulator.Invalid[1] ;
        if(name==null)
        {
            dest=null;
        }
        else
        {
            dest= hazelAccess.namePopulator().forName(name,invalidator);

        }
        if(dest==null)
        {
            logger.warn("no destination found for name "+name);
            handleNoDest();

        }
        else
        {

            RequestContext ctx= RequestContext.getCurrentContext();
            while(true) {
                    ConnectException ce=null;
                    try {
                        route(name, dest, ctx);
                    }
                    catch(Exception e)
                    {
                        if(e instanceof ConnectException) {
                            ce = (ConnectException) e;
                            logger.error("the exposed end point is not connectable \n"+dest.toString()+"\n invalidate and complain");
                            invalidator[0].invalidate();
                        }
                        else
                        {

                            try {
                                RequestContext.getCurrentContext().getResponse().sendError(HttpStatus.SC_SERVICE_UNAVAILABLE,"Exception in router");
                            } catch (IOException e1) {
                                e1.printStackTrace();
                            }
                        }
                    }
                    if(ce==null)
                    {
                        break;
                    }
                    dest=hazelAccess.namePopulator().forName(name,invalidator);
                    if(dest==null)
                    {
                        logger.error(" no working instances ready to process for service name "+name);
                        handleNoDest();
                        break;
                    }
                }

                return null;
            }


        return null;
    }

    private void handleNoDest() {
        try {

            RequestContext.getCurrentContext().getResponse().sendError(HttpServletResponse.SC_NOT_FOUND,"No Such service");
        } catch (IOException e) {
            e.printStackTrace();
        }
        //setResponseStatusCode(HttpStatus.SC_NOT_FOUND);
        RequestContext.getCurrentContext().setSendZuulResponse(false);
    }

    private void route(String name, URL dest, RequestContext ctx) throws ConnectException {
        String s= dest.toString();
        StringTokenizer st= new StringTokenizer(ctx.getRequest().getServletPath(),"/");
        StringBuilder sb= new StringBuilder();
        int count=0;
        while(st.hasMoreTokens()) {
            String s1;
            if(count>0) {
                sb.append('/').append(s1 = st.nextToken());

            }

            else {
                if(mapRoutes.containsKey(name)) {
                    sb.append("/").append(mapRoutes.get(name));
                }
                if(mapPrefixes.containsKey(name)) {
                    sb.append("/").append(mapPrefixes.get(name));
                }


                st.nextToken();
            }
            count++;
        }

        String strRouted =UriComponentsBuilder.fromHttpUrl(s).path(sb.toString()).build().toUriString();


        ctx.setSendZuulResponse(false);
        forwardRequest(strRouted, ctx.getRequest(), ctx.getResponse());
    }

    private void forwardRequest(String strUrl,HttpServletRequest req, HttpServletResponse resp) throws ConnectException {
        String method=req.getMethod();
        final boolean hasoutbody = (method.equals("POST")||method.equals(org.springframework.http.HttpMethod.PUT)||method.equals(org.springframework.http.HttpMethod.PUT));

        try {
            final URL url = new URL(strUrl  // no trailing slash
                    + (req.getQueryString() != null ? "?" + req.getQueryString() : ""));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);

            final Enumeration<String> headers = req.getHeaderNames();
            while (headers.hasMoreElements()) {
                final String header = headers.nextElement();
                final Enumeration<String> values = req.getHeaders(header);
                while (values.hasMoreElements()) {
                    final String value = values.nextElement();
                    conn.addRequestProperty(header, value);
                }
            }

            //conn.setFollowRedirects(false);  // throws AccessDenied exception
            conn.setUseCaches(false);
            conn.setDoInput(true);
            conn.setDoOutput(hasoutbody);
            conn.connect();
            final byte[] buffer = new byte[16384];
            if (hasoutbody) {
                OutputStream os=conn.getOutputStream();
                while(true) {
                    final int read = req.getInputStream().read(buffer);
                    if (read <= 0) break;
                    os.write(buffer, 0, read);
                }

            }
            int code=200;

            resp.setStatus(code=conn.getResponseCode());
            for (int i = 0; ; ++i) {
                final String header = conn.getHeaderFieldKey(i);
                if (header == null)
                    break;
                final String value = conn.getHeaderField(i);
                resp.setHeader(header, value);
            }

            while (true) {
                final int read = conn.getInputStream().read(buffer);
                if (read <= 0) break;
                resp.getOutputStream().write(buffer, 0, read);
            }
        } catch (Exception e) {
            e.printStackTrace();
            if(e instanceof ConnectException)
            {
               throw (ConnectException)e;
            }
            try {
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"unspecified server error");
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            // pass
        }

    }
}
