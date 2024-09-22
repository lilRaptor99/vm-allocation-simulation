package dev.pratheeks.vmallocationsimulation.allocationpolicy.allocationpolicy4dbinpacking;

import lombok.Data;

@Data
public class SimpleVm {
    private long id;
    private long ram;
    private long pes;
    private long bw;
    private double power;

    public SimpleVm(long id, long ram, long pes, long bw, double power) {
        this.id = id;
        this.ram = ram;
        this.pes = pes;
        this.bw = bw;
        this.power = power;
    }
}
