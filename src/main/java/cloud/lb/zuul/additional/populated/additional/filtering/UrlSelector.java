package cloud.lb.zuul.additional.populated.additional.filtering;

import java.util.ArrayList;
import java.util.List;

public class UrlSelector {
    String[] split;
    int distinct;
    List<String> list= new ArrayList<>();
    UrlSelector(String pattern)
    {
        StringBuilder bld= new StringBuilder();
        split=pattern.split("\\/");
        for(String s:split)
        {
            if(s.length()==0)
                continue;

            if(s.equals("*")||s.equals("**")) {

                list.add(bld.toString());
                bld.setLength(0);
                list.add(s);
                distinct++;

            }
            else
            {
                bld.append('/').append(s);

            }
       }
        if(bld.length()>0) {
            distinct++;
            list.add(bld.toString());
        }


    }
    boolean match(String url)
    {
        int start=0;
        boolean any=false;
        int match=0;
        boolean open=false;
        int lastDistinct=0;

        for(String s:list)
        {
            if(s.equals("*")) {
                int index=url.indexOf("/",start+1);
                start=index;
                open=true;
                continue;
            }
            if(s.equals("**"))
            {
                any=true;
                open =true;
                continue;
            }
            int index=url.indexOf(s,start);
            open=false;
            if(any)
            {

                if(index>0)
                {
                    match++;

                }
                else
                    return false;

            }
            else
            {
                if(index==start)
                {
                    match++;
                }
                else
                    return false;

            }
            start=index+s.length();
            lastDistinct=start;
        }
        if(open)
            match++;
        return (match>=distinct)&&(open||lastDistinct>=url.length());
    }
}
