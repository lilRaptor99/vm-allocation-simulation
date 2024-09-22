package dev.pratheeks.vmallocationsimulation.allocationpolicy.allocationpolicy4dbinpacking;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SimpleHost {
    private long id;
    private long ram;
    private long pes;
    private long bw;
    private double power;
    private long used_ram;
    private long used_pes;
    private long used_bw;
    private double used_power;

    private List<Long> allocatedVmIds = new ArrayList<>();

    public SimpleHost(long id, long ram, long pes, long bw, double power) {
        this.id = id;
        this.ram = ram;
        this.pes = pes;
        this.bw = bw;
        this.power = power;
    }

    public long getRemainingRam() {
        return ram - used_ram;
    }

    public long getRemainingPes() {
        return pes - used_pes;
    }

    public long getRemainingBw() {
        return bw - used_bw;
    }

    public double getRemainingPower() {
        return power - used_power;
    }

    public void allocateVm(SimpleVm vm) {
        allocatedVmIds.add(vm.getId());
        used_ram += vm.getRam();
        used_pes += vm.getPes();
        used_bw += vm.getBw();
        used_power += vm.getPower();
    }

    public void deallocateVm(SimpleVm vm) {
        allocatedVmIds.remove(vm.getId());
        used_ram -= vm.getRam();
        used_pes -= vm.getPes();
        used_bw -= vm.getBw();
        used_power -= vm.getPower();
    }

    public boolean canFitVm(SimpleVm vm) {
        return getRemainingRam() >= vm.getRam()
                && getRemainingPes() >= vm.getPes()
                && getRemainingBw() >= vm.getBw()
                && getRemainingPower() >= vm.getPower();
    }

    @Override
    public String toString() {
        return "SimpleHost{" +
                "id=" + id +
                ", ram=" + ram +
                ", pes=" + pes +
                ", bw=" + bw +
                ", power=" + power +
                ", used_ram=" + used_ram +
                ", used_pes=" + used_pes +
                ", used_bw=" + used_bw +
                ", used_power=" + used_power +
                ", allocatedVmIds=[ " + allocatedVmIds.stream().map(id -> id.toString() + " ").reduce("", String::concat) +
                "] }";
    }
}
