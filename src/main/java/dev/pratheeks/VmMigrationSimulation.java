package dev.pratheeks;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyFirstFit;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.listeners.DatacenterVmMigrationEventInfo;
import org.cloudsimplus.listeners.EventInfo;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.util.Log;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.*;

public class VmMigrationSimulation {
    private static final int HOSTS = 10;
    private static final int HOST_PES = 64; // 64 core CPUs
    private static final long HOST_RAM = 128_000; // 128 GB
    private static final long HOST_STORAGE = 10_000_000; // 10 TB
    private static final long HOST_BW = 10_000; // 10 Gbps

    private static final int VMS = 30;
    private static final long VM_SIZE = 10_000; // 10 GB
    private static final long VM_BW = 1000; // 1 Gbps
    private static final long VM_RAM_MIN = 1000; // 1 GB
    private static final long VM_RAM_MAX = 32_000; // 32 GB
    private static final int VM_PES_MIN = 1;
    private static final int VM_PES_MAX = 16;

    private static final int CLOUDLET_LENGTH = 10_000_000;

    private static final int MINIMUM_ALLOCATIONS_PER_SIMULATION = 100; // Should perform at least 100 VM migrations (allocations)
    // to properly assess the allocation algorithm
    private int totalNumberOfAllocations = 0;
    private int failedVmMigrations = 0;
    private int currentlyMigratingVMCount = 0;

    private final CloudSimPlus simulation;
    private final DatacenterSimple datacenter;
    private final DatacenterBrokerSimple broker;
    private final List<Host> hostList = new ArrayList<>();
    private final List<Vm> vmList = new ArrayList<>();
    private boolean migrationRequested = false;

    private final Random random = new Random(1); // Change the random seed to get different VM configurations

    private final VmAllocationPolicy vmAllocationAlgo = new VmAllocationPolicyFirstFit();

    public static void main(String[] args) {
        new VmMigrationSimulation();
    }

    public VmMigrationSimulation() {
        // MigrationNumber VM_ID Allocation_time From_Host To_Host Success
        Log.setLevel(ch.qos.logback.classic.Level.WARN); // Limit log output
        simulation = new CloudSimPlus();
        datacenter = createDatacenter();
        broker = new DatacenterBrokerSimple(simulation);

        createAndSubmitVms();
        createAndSubmitCloudlets();

        // Set up a clock listener to trigger the first migration after a certain time
        simulation.addOnClockTickListener(this::clockTickListener);
        // Event listener to set up VM migration after all VM migrations of a Host is finished
        datacenter.addOnVmMigrationFinishListener(this::vmMigrationFinishListener);

        simulation.start();

//        new CloudletsTableBuilder(broker.getCloudletFinishedList()).build();
        System.out.printf("Simulation finished with %d VM migrations. Of which %d migrations failed!%n", totalNumberOfAllocations, failedVmMigrations);
    }

    private DatacenterSimple createDatacenter() {
        for (int i = 0; i < HOSTS; i++) {
            hostList.add(createHost());
        }
        return new DatacenterSimple(simulation, hostList, vmAllocationAlgo);
    }

    private Host createHost() {
        List<Pe> peList = new ArrayList<>(HOST_PES);
        for (int i = 0; i < HOST_PES; i++) {
            peList.add(new PeSimple(1000)); // 1000 MIPS per PE
        }
        return new HostSimple(HOST_RAM, HOST_BW, HOST_STORAGE, peList)
                .setVmScheduler(new VmSchedulerTimeShared());
    }

    private void createAndSubmitVms() {
        for (int i = 0; i < VMS; i++) {
            vmList.add(createVm());
        }
        broker.submitVmList(vmList);
    }

    private Vm createVm() {
        // Generate random VM resource requirements based on the host's capacity (10%-40%)
        int pes = VM_PES_MIN + random.nextInt(VM_PES_MAX - VM_PES_MIN + 1);
        long ram = VM_RAM_MIN + (long) (random.nextDouble() * (VM_RAM_MAX - VM_RAM_MIN));

        Vm vm = new VmSimple(1000, pes) // 1000 MIPS per PE
                .setRam(ram)
                .setSize(VM_SIZE)
                .setBw(VM_BW)
                .setCloudletScheduler(new CloudletSchedulerTimeShared());
        return vm;
    }

    private void createAndSubmitCloudlets() {
        for (Vm vm : vmList) {
            var cloudlet = new CloudletSimple(CLOUDLET_LENGTH, vm.getPesNumber())
                    .setUtilizationModel(new UtilizationModelFull());
            broker.bindCloudletToVm(cloudlet, vm);
            broker.submitCloudlet(cloudlet);
        }
    }

    /**
     * Print initial Vm allocation and request migration of VMs in a single randomly selected host
     */
    private void clockTickListener(EventInfo info) {
        if (migrationRequested) {
            return;
        }
        // Printing VM initial allocation
        System.out.println("#### INITIAL ALLOCATION ####");
        for (Vm vm : vmList) {
            System.out.printf("VM %d\t:: Host %d \t:: %d core,\t %d GB,\t %d Mbps%n", vm.getId(), vm.getHost().getId(),
                    vm.getPesNumber(), vm.getRam().getCapacity(), vm.getBw().getCapacity());
        }
        migrateVmsInAHost(info);
        migrationRequested = true;
    }
    /**
     * Migrate VMs in hosts until the required number of migrations for a simulation is reached.
     * VMs will be migrated when one host is finished migrating all its VMs
     */
    private void vmMigrationFinishListener(DatacenterVmMigrationEventInfo info){
        System.out.printf("##### VM migration finish listener: Current time: %f, \t VM: %d, \t Successful: %b%n",
                info.getTime(), info.getVm().getId(), info.isMigrationSuccessful());
        if(!info.isMigrationSuccessful()){
            failedVmMigrations++;
        }
        currentlyMigratingVMCount--;
        if(currentlyMigratingVMCount <= 5){
            migrateVmsInAHost(info);
        }
    }

    private void migrateVmsInAHost(EventInfo info){
        if (totalNumberOfAllocations < MINIMUM_ALLOCATIONS_PER_SIMULATION) {
            System.out.printf("\n\n##### Scheduling VM migration in a host... Current time: %f%n", info.getTime());
            Host sourceHost = selectRandomHostWithVms();
            if (sourceHost == null) {
                System.out.println("!!!!! ERROR: No VMs to migrate.");
                return;
            }
            migrateAllVmsFromHost(sourceHost);
        }
    }

    private Host selectRandomHostWithVms() {
        List<Host> hostsWithVms = new ArrayList<>();
        for (Host host : hostList) {
            if (!host.getVmList().isEmpty()) {
                hostsWithVms.add(host);
            }
        }
        if (!hostsWithVms.isEmpty()) {
            return hostsWithVms.get(random.nextInt(hostsWithVms.size()));
        }
        return null;
    }

    private void migrateAllVmsFromHost(Host sourceHost) {
        System.out.printf("#>>> Migration command received to migrate VMs in Host %d <<<%n", sourceHost.getId());
        for (Vm vm : sourceHost.getVmList()) {
            // Allocating a host for the VM in source host
            final var targetHost = datacenter.getVmAllocationPolicy().findHostForVm(vm).orElse(Host.NULL);
            totalNumberOfAllocations++;
            if (Host.NULL.equals(targetHost)) {
                System.out.printf("!!!!! No suitable host found for VM %d%n", vm.getId());
                continue;
            }
            System.out.printf(">>>> Migrating VM %d from Host %d to Host %d%n", vm.getId(), sourceHost.getId(), targetHost.getId());
            datacenter.requestVmMigration(vm, targetHost);
            currentlyMigratingVMCount ++;
        }
    }
}
