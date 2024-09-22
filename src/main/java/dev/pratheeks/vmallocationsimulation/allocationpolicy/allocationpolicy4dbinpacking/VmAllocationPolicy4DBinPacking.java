package dev.pratheeks.vmallocationsimulation.allocationpolicy.allocationpolicy4dbinpacking;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyAbstract;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.vms.Vm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class VmAllocationPolicy4DBinPacking extends VmAllocationPolicyAbstract implements VmAllocationPolicy {

    private int initialAllocationLastHostIndex = 0;
    private Map<Long, Long> currentOptimumAllocation = null; // Current optimal allocation VM_ID -> HOST_ID

    /**
     * Recalculate the packing of VMs on a single host
     */
    public void recalculatePacking(Host sourceHost) {
        List<Host> allSimHostList = getHostList();
        List<SimpleHost> possibleDestinationHosts = new ArrayList<>();
        List<SimpleVm> allVms = new ArrayList<>();
        for (Host host : allSimHostList) {
            if (host.getId() != sourceHost.getId()) {
                final var simpleHost = new SimpleHost(host.getId(), host.getRam().getCapacity(), host.getPesNumber(),
                        host.getBw().getCapacity(), host.getPowerModel().getPower(1.0));
                for (Vm vm : host.getVmList()) {
                    final var simpleVm = new SimpleVm(vm.getId(), vm.getRam().getCapacity(), vm.getPesNumber(),
                            vm.getBw().getCapacity(), 0.0
                            // TODO: Add VM power utilization when computing the allocation
                            // vm.getPowerModel().getPower()
                    );
                    simpleHost.allocateVm(simpleVm);
                    allVms.add(simpleVm);
                }
                possibleDestinationHosts.add(simpleHost);
            }
        }

        // Add the VMs from the source host
        for (Vm vm : sourceHost.getVmList()) {
            final var simpleVm = new SimpleVm(vm.getId(), vm.getRam().getCapacity(), vm.getPesNumber(),
                    vm.getBw().getCapacity(), 0.0
                    // TODO: Add VM power utilization when computing the allocation
                    // vm.getPowerModel().getPower()
            );
            allVms.add(simpleVm);
        }

        final var optimalBinPacking = new OptimalBinPacking(possibleDestinationHosts, allVms);
        currentOptimumAllocation = optimalBinPacking.findOptimalAllocation();
        optimalBinPacking.printAllocation();
    }

    @Override
    protected Optional<Host> defaultFindHostForVm(Vm vm) {
        // Optimum calculation not done yet, do the initial allocation using FirstFit
        if (currentOptimumAllocation == null) {
            return initialAllocationFindHostForVm(vm);
        }

        // Optimum calculation done, return the host from the current allocation
        long allocatedHostId = currentOptimumAllocation.getOrDefault(vm.getId(), -1L);
        if (allocatedHostId != -1) {
            return getHostList().stream().filter(h -> h.getId() == allocatedHostId).findFirst();
        }
        // VM not found in the current allocation
        return Optional.empty();
    }


    private Optional<Host> initialAllocationFindHostForVm(final Vm vm) {
        final List<Host> hostList = getHostList();
        /* The for loop just defines the maximum number of Hosts to try.
         * When a suitable Host is found, the method returns immediately. */
        final int maxTries = hostList.size();
        for (int i = 0; i < maxTries; i++) {
            final Host host = hostList.get(initialAllocationLastHostIndex);
            if (host.isSuitableForVm(vm)) {
                return Optional.of(host);
            }

            /* If it gets here, the previous Host doesn't have capacity to place the VM.
             * Then, moves to the next Host.*/
            incLastHostIndex();
        }

        return Optional.empty();
    }

    private void incLastHostIndex() {
        initialAllocationLastHostIndex = ++initialAllocationLastHostIndex % getHostList().size();
    }
}
