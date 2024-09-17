package dev.pratheeks.vmallocationsimulation;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvBindByPosition;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class CSVBean implements Serializable {
    @CsvBindByName(column = "vmId", required = true)
    @CsvBindByPosition(position = 0)
    private long vmId;
    @CsvBindByName(column = "allocationTime", required = true)
    @CsvBindByPosition(position = 1)
    private double allocationTime;
    @CsvBindByName(column = "allocated", required = true)
    @CsvBindByPosition(position = 2)
    private boolean allocated;
    @CsvBindByName(column = "fromHost", required = true)
    @CsvBindByPosition(position = 3)
    private long fromHost;
    @CsvBindByName(column = "toHost", required = true)
    @CsvBindByPosition(position = 4)
    private long toHost;
    @CsvBindByName(column = "migrationTime", required = true)
    @CsvBindByPosition(position = 5)
    private double migrationTime;
    @CsvBindByName(column = "migrationSuccess", required = true)
    @CsvBindByPosition(position = 6)
    private boolean migrationSuccess;
}
