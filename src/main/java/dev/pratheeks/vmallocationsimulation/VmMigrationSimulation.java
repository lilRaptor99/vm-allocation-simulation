package dev.pratheeks.vmallocationsimulation;

import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import dev.pratheeks.vmallocationsimulation.allocationpolicy.allocationpolicy4dbinpacking.VmAllocationPolicy4DBinPacking;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
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

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class VmMigrationSimulation {
    private final int HOSTS;
    private static final int HOST_PES = 64; // 64 core CPUs
    private static final long HOST_RAM = 128_000; // 128 GB
    private static final long HOST_STORAGE = 10_000_000; // 10 TB
    private static final long HOST_BW = 10_000; // 10 Gbps

    private final int VMS;
    private static final long VM_SIZE = 10_000; // 10 GB
    private static final long VM_BW = 1000; // 1 Gbps
    private static final long VM_RAM_MIN = 1000; // 1 GB
    private static final long VM_RAM_MAX = 32_000; // 32 GB
    private static final int VM_PES_MIN = 1;
    private static final int VM_PES_MAX = 16;

    private static final int CLOUDLET_LENGTH = 10_000_000;

    /**
     * Should perform at least 100 VM migrations (allocations) to properly assess the allocation algorithm
     */
    private static final int MINIMUM_ALLOCATIONS_PER_SIMULATION = 100; //

    private int totalNumberOfAllocations = 0;
    private int failedVmMigrations = 0;
    private int currentlyMigratingVMCount = 0;

    private final CloudSimPlus simulation;
    private final DatacenterSimple datacenter;
    private final DatacenterBrokerSimple broker;
    private final List<Host> hostList = new ArrayList<>();
    private final List<Vm> vmList = new ArrayList<>();
    private boolean migrationRequested = false;

    /**
     * Random seed decides the random VM configuration for the simulation run
     */
    private final Random random;

    private final List<CSVBean> csvLines= new ArrayList<>();

    public VmMigrationSimulation(VmAllocationPolicy vmAllocationAlgo, int hostCount, long randomSeed) throws IOException, CsvRequiredFieldEmptyException, CsvDataTypeMismatchException {
        String allocationAlgoName = vmAllocationAlgo.getClass().getSimpleName();
        HOSTS = hostCount;
        VMS = HOSTS * 3; // 3 times the host count
        random = new Random(randomSeed);

        System.out.printf("Starting simulation with %d Hosts with %d VMs for allocation algorithm: %s. Random seed: %d%n",
                HOSTS, VMS, allocationAlgoName, randomSeed);

        Log.setLevel(ch.qos.logback.classic.Level.WARN); // Limit log output
        simulation = new CloudSimPlus();
        datacenter = createDatacenter(vmAllocationAlgo);
        broker = new DatacenterBrokerSimple(simulation);

        createAndSubmitVms();
        createAndSubmitCloudlets();

        // Set up a clock listener to trigger the first migration after a certain time
        simulation.addOnClockTickListener(this::clockTickListener);
        // Event listener to set up VM migration after all VM migrations of a Host is finished
        datacenter.addOnVmMigrationFinishListener(this::vmMigrationFinishListener);

        simulation.start();

        // new CloudletsTableBuilder(broker.getCloudletFinishedList()).build();
        System.out.printf("Simulation finished with %d VM migrations. Of which %d migrations failed!%n", totalNumberOfAllocations, failedVmMigrations);

        // Writing the output to CSV
        String outputDir = "results/" + allocationAlgoName;
        Files.createDirectories(Paths.get(outputDir));
        String csvFileName = outputDir + "/" + allocationAlgoName + "_hosts_" + HOSTS + "_" + randomSeed + ".csv";
        Writer writer = new FileWriter(csvFileName);
        var mappingStrategy = new CustomColumnPositionStrategy<CSVBean>();
        mappingStrategy.setType(CSVBean.class);
        StatefulBeanToCsv<CSVBean> beanToCsv = new StatefulBeanToCsvBuilder<CSVBean>(writer)
                .withMappingStrategy(mappingStrategy)
                .withApplyQuotesToAll(false)
                .build();
        beanToCsv.write(csvLines);
        writer.close();
    }

    private DatacenterSimple createDatacenter(VmAllocationPolicy vmAllocationAlgo) {
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
        if(currentlyMigratingVMCount <= 2){
            migrateVmsInAHost(info);
        }
    }

    private void migrateVmsInAHost(EventInfo info){
        if (totalNumberOfAllocations < MINIMUM_ALLOCATIONS_PER_SIMULATION) {
            System.out.printf("\n##### Scheduling VM migration in a host... Current time: %f%n", info.getTime());
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

        List<Vm> vmsToMigrate = sourceHost.getVmList();
        long preProcessingTime = 0;

        VmAllocationPolicy vmAllocationPolicy = datacenter.getVmAllocationPolicy();

        if(vmAllocationPolicy.getClass().getSimpleName().equals("VmAllocationPolicy4DBinPacking")){
            long start = System.nanoTime();
            ((VmAllocationPolicy4DBinPacking)vmAllocationPolicy).recalculatePacking(sourceHost);
            long end = System.nanoTime();
            preProcessingTime = end-start;
        }

        double preProcessingTimePerVm = ((double) preProcessingTime / vmsToMigrate.size()) / 1_000_000.0;

        System.out.println("##### VMs to migrate: " + vmsToMigrate.size());
        System.out.println("##### Pre-processing time per VM: " + preProcessingTimePerVm + " ms");

        for (Vm vm : sourceHost.getVmList()) {
            // Allocating a host for the VM in source host
            long start = System.nanoTime();
            final var targetHost = vmAllocationPolicy.findHostForVm(vm).orElse(Host.NULL);
            long end = System.nanoTime();
            totalNumberOfAllocations++;

            double allocationTime = ((end-start) / 1_000_000.0) + preProcessingTimePerVm;

            if (Host.NULL.equals(targetHost)) {
                System.out.printf("!!!!! No suitable host found for VM %d%n", vm.getId());
                csvLines.add(new CSVBean(vm.getId(), allocationTime, false,
                        sourceHost.getId(), -1L, 0, false));
                continue;
            }
            // TODO: Get migration time and migration status from migration completion event
            csvLines.add(new CSVBean(vm.getId(), allocationTime, true,
                    sourceHost.getId(), targetHost.getId(), 0, true));
            System.out.printf(">>>> Migrating VM %d from Host %d to Host %d%n", vm.getId(), sourceHost.getId(), targetHost.getId());
            datacenter.requestVmMigration(vm, targetHost);
            currentlyMigratingVMCount++;
        }
    }
}
