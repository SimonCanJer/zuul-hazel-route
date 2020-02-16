package cloud.lb.zuul;

import microhazle.building.api.IMounter;

public interface IHazelcastExporter {
    IMounter mounter();
    IHzlNamePopulator namePopulator();
}
