package dev.pratheeks.vmallocationsimulation;

import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import dev.pratheeks.vmallocationsimulation.allocationpolicy.allocationpolicy4dbinpacking.VmAllocationPolicy4DBinPacking;
import org.cloudsimplus.allocationpolicies.*;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException, CsvRequiredFieldEmptyException, CsvDataTypeMismatchException {

        String[] allocationPolicies = new String[]{"BestFit", "FirstFit", "Simple", "RoundRobin", "4DBinPacking"};
        int[] hostConfigs = new int[]{5, 10, 20, 40, 80, 100, 200, 500, 1000};
        long[] randomSeedConfigs = new long[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

        for (String allocationPolicy : allocationPolicies) {
            for (int hostCount : hostConfigs) {
                if(allocationPolicy.equals("4DBinPacking") && hostCount > 50) {
                    continue;
                }
                for (long randomSeed : randomSeedConfigs) {
                    VmAllocationPolicy vmAllocationPolicy = getVmAllocationPolicy(allocationPolicy);
                    // Run the simulation
                    new VmMigrationSimulation(vmAllocationPolicy, hostCount, randomSeed);
                }
            }
        }
    }

    private static VmAllocationPolicy getVmAllocationPolicy(String allocationPolicy) {
        return switch (allocationPolicy) {
            case "4DBinPacking" -> new VmAllocationPolicy4DBinPacking();
            case "BestFit" -> new VmAllocationPolicyBestFit();
            case "FirstFit" -> new VmAllocationPolicyFirstFit();
            case "Simple" -> new VmAllocationPolicySimple();
            // case "Random" -> new VmAllocationPolicyRandom();
            case "RoundRobin" -> new VmAllocationPolicyRoundRobin();
            default -> throw new IllegalArgumentException("Invalid allocation policy: " + allocationPolicy);
        };
    }
}
