package dev.pratheeks.vmallocationsimulation;

import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyFirstFit;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException, CsvRequiredFieldEmptyException, CsvDataTypeMismatchException {
        new VmMigrationSimulation(new VmAllocationPolicyFirstFit(), 1000, 1L);
    }
}
