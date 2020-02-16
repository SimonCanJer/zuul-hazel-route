package cloud.lb.zuul.additional.populated;

import java.util.Set;

public interface IProperties {
    String applicationName();
    Set<String> populatedNames();
}
