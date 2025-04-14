package simulations;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MultiTenantFaaS - A simulation of multi-tenant serverless computing environments
 * using CloudSim framework. This simulation compares different scheduling algorithms
 * (Round Robin and Shortest Job First) in a FaaS scenario with multiple tenants.
 */
public class MultiTenantFaaS {
    private static final Logger LOGGER = Logger.getLogger(MultiTenantFaaS.class.getName());
    private static final DecimalFormat DF = new DecimalFormat("#.##");
    
    // Simulation configuration parameters
    private static final Map<Integer, Integer> cloudletTenantMap = new HashMap<>();
    private static final int TENANT_COUNT = 4;
    private static final int CLOUDLET_COUNT = 20;
    private static final int VM_COUNT = 5;
    private static final int HOST_COUNT = 3;
    private static final int HOST_MIPS = 1000;
    private static final int HOST_RAM = 2048;
    private static final int HOST_STORAGE = 1000000;
    private static final int HOST_BW = 10000;
    private static final int VM_MIPS = 1000;
    private static final int VM_PES = 1;
    private static final int VM_RAM = 512;
    private static final int VM_BW = 1000;
    private static final int VM_SIZE = 10000;
    private static final int CLOUDLET_LENGTH_BASE = 40000;
    private static final int CLOUDLET_LENGTH_RANDOM = 10000;
    private static final int CLOUDLET_PES = 1;
    private static final int CLOUDLET_FILESIZE = 300;
    private static final int CLOUDLET_OUTPUT_SIZE = 300;
    private static final String OUTPUT_DIR = "output";
    private static final String[] ALGORITHMS = {"RoundRobin", "SJF", "TenantAware"};

    /**
     * Main method to run the simulation
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        // Create output directory if it doesn't exist
        createOutputDirectory();
        
        // Run simulations for each algorithm
        for (String algorithm : ALGORITHMS) {
            runSimulation(algorithm);
        }
        
        // Generate comparison report
        generateComparisonReport();
        
        LOGGER.info("Simulation completed successfully. Results are available in the 'output' directory.");
    }

    /**
     * Create output directory if it doesn't exist
     */
    private static void createOutputDirectory() {
        try {
            Files.createDirectories(Paths.get(OUTPUT_DIR));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to create output directory", e);
        }
    }

    /**
     * Initialize and run simulation for a specific algorithm
     * @param algorithm The scheduling algorithm to use
     */
    private static void runSimulation(String algorithm) {
        try {
            LOGGER.info("Starting simulation with " + algorithm + " algorithm");
            
            // Initialize the CloudSim library
            CloudSim.init(1, Calendar.getInstance(), false);

            // Create datacenter
            Datacenter datacenter = createDatacenter("Datacenter_1");
            
            // Create broker
            DatacenterBroker broker = new DatacenterBroker("Broker_" + algorithm);
            int brokerId = broker.getId();

            // Create VMs
            List<Vm> vms = createVMs(brokerId, VM_COUNT);
            broker.submitVmList(vms);

            // Create cloudlets and assign to tenants
            List<Cloudlet> cloudlets = createCloudlets(brokerId, CLOUDLET_COUNT, TENANT_COUNT);
            
            // Apply scheduling algorithm
            switch (algorithm) {
                case "SJF":
                    cloudlets.sort(Comparator.comparingLong(Cloudlet::getCloudletLength));
                    break;
                case "TenantAware":
                    // Group cloudlets by tenant and allocate fairly
                    Map<Integer, List<Cloudlet>> tenantCloudlets = new HashMap<>();
                    for (Cloudlet c : cloudlets) {
                        int tenantId = cloudletTenantMap.get(c.getCloudletId());
                        tenantCloudlets.computeIfAbsent(tenantId, k -> new ArrayList<>()).add(c);
                    }
                    
                    List<Cloudlet> sortedCloudlets = new ArrayList<>();
                    int maxCloudletsPerTenant = 0;
                    for (List<Cloudlet> tcl : tenantCloudlets.values()) {
                        maxCloudletsPerTenant = Math.max(maxCloudletsPerTenant, tcl.size());
                    }
                    
                    for (int i = 0; i < maxCloudletsPerTenant; i++) {
                        for (int t = 0; t < TENANT_COUNT; t++) {
                            List<Cloudlet> tcl = tenantCloudlets.getOrDefault(t, Collections.emptyList());
                            if (i < tcl.size()) {
                                sortedCloudlets.add(tcl.get(i));
                            }
                        }
                    }
                    cloudlets = sortedCloudlets;
                    break;
                default: // RoundRobin - default CloudSim behavior
                    break;
            }
            
            broker.submitCloudletList(cloudlets);

            // Start the simulation
            CloudSim.startSimulation();
            
            // Retrieve results
            List<Cloudlet> results = broker.getCloudletReceivedList();
            
            // Stop the simulation
            CloudSim.stopSimulation();

            // Generate output
            generateCSV(results, algorithm);
            
            LOGGER.info("Simulation for " + algorithm + " completed");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error running simulation with " + algorithm, e);
        }
    }

    /**
     * Create datacenter with hosts
     * @param name Datacenter name
     * @return Created datacenter
     * @throws Exception If creation fails
     */
    private static Datacenter createDatacenter(String name) throws Exception {
        List<Host> hostList = new ArrayList<>();
        
        for (int i = 0; i < HOST_COUNT; i++) {
            List<Pe> peList = new ArrayList<>();
            peList.add(new Pe(0, new PeProvisionerSimple(HOST_MIPS)));

            Host host = new Host(
                    i,
                    new RamProvisionerSimple(HOST_RAM),
                    new BwProvisionerSimple(HOST_BW),
                    HOST_STORAGE,
                    peList,
                    new VmSchedulerTimeShared(peList)
            );

            hostList.add(host);
        }

        // Datacenter characteristics
        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                "x86",                 // architecture
                "Linux",               // OS
                "Xen",                 // VMM
                hostList,              // list of hosts
                10.0,                  // time zone
                3.0,                   // cost per sec
                0.05,                  // cost per mem
                0.001,                 // cost per storage
                0.0                    // cost per bw
        );

        return new Datacenter(name, characteristics, new VmAllocationPolicySimple(hostList), new LinkedList<>(), 0);
    }

    /**
     * Create VMs to run the cloudlets
     * @param brokerId ID of the broker
     * @param count Number of VMs to create
     * @return List of created VMs
     */
    private static List<Vm> createVMs(int brokerId, int count) {
        List<Vm> vmList = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            Vm vm = new Vm(
                    i,                          // VM ID
                    brokerId,                   // User ID
                    VM_MIPS,                    // MIPS
                    VM_PES,                     // Number of CPUs
                    VM_RAM,                     // RAM
                    VM_BW,                      // Bandwidth
                    VM_SIZE,                    // Size
                    "Xen",                      // VMM
                    new CloudletSchedulerTimeShared() // Scheduler
            );
            
            vmList.add(vm);
        }
        
        return vmList;
    }

    /**
     * Create cloudlets and assign them to tenants
     * @param brokerId ID of the broker
     * @param count Number of cloudlets to create
     * @param tenants Number of tenants
     * @return List of created cloudlets
     */
    private static List<Cloudlet> createCloudlets(int brokerId, int count, int tenants) {
        List<Cloudlet> cloudletList = new ArrayList<>();
        Random rand = new Random(42); // Deterministic random for reproducibility
        UtilizationModel utilizationModel = new UtilizationModelFull();

        for (int i = 0; i < count; i++) {
            // Create cloudlet with random length
            int length = CLOUDLET_LENGTH_BASE + rand.nextInt(CLOUDLET_LENGTH_RANDOM);
            Cloudlet cloudlet = new Cloudlet(
                    i,                          // ID
                    length,                     // Length
                    CLOUDLET_PES,               // Number of PEs
                    CLOUDLET_FILESIZE,          // File size
                    CLOUDLET_OUTPUT_SIZE,       // Output size
                    utilizationModel,           // Utilization model for CPU
                    utilizationModel,           // Utilization model for RAM
                    utilizationModel            // Utilization model for BW
            );
            
            cloudlet.setUserId(brokerId);
            
            // Assign cloudlet to a tenant
            int tenantId = rand.nextInt(tenants);
            cloudletTenantMap.put(cloudlet.getCloudletId(), tenantId);
            
            cloudletList.add(cloudlet);
        }

        return cloudletList;
    }

    /**
     * Generate CSV output file with simulation results
     * @param cloudlets List of completed cloudlets
     * @param algorithm Algorithm used in the simulation
     */
    private static void generateCSV(List<Cloudlet> cloudlets, String algorithm) {
        String fileName = OUTPUT_DIR + File.separator + "multitenant_" + algorithm + ".csv";
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            // Write header
            writer.write("CloudletID,Status,VMId,StartTime,FinishTime,ExecTime,TenantID\n");

            // Write data for each cloudlet
            for (Cloudlet cloudlet : cloudlets) {
                writer.write(
                        cloudlet.getCloudletId() + "," +
                        cloudlet.getCloudletStatusString() + "," +
                        cloudlet.getVmId() + "," +
                        DF.format(cloudlet.getExecStartTime()) + "," +
                        DF.format(cloudlet.getFinishTime()) + "," +
                        DF.format(cloudlet.getActualCPUTime()) + "," +
                        cloudletTenantMap.get(cloudlet.getCloudletId()) + "\n"
                );
            }
            
            LOGGER.info("Generated CSV file: " + fileName);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error generating CSV file", e);
        }
    }

    /**
     * Generate a comparison report for all algorithms
     */
    private static void generateComparisonReport() {
        String fileName = OUTPUT_DIR + File.separator + "comparison_report.txt";
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            writer.write("Multi-Tenant FaaS Simulation Comparison Report\n");
            writer.write("============================================\n\n");
            writer.write("Simulation Parameters:\n");
            writer.write("  Tenants: " + TENANT_COUNT + "\n");
            writer.write("  Cloudlets: " + CLOUDLET_COUNT + "\n");
            writer.write("  VMs: " + VM_COUNT + "\n");
            writer.write("  Hosts: " + HOST_COUNT + "\n\n");

            // Collect data for each algorithm and tenant
            Map<String, Map<Integer, List<Double>>> algoTenantData = new HashMap<>();
            Map<String, Double> algoTotalTime = new HashMap<>();
            
            for (String algorithm : ALGORITHMS) {
                String csvFile = OUTPUT_DIR + File.separator + "multitenant_" + algorithm + ".csv";
                Map<Integer, List<Double>> tenantData = new HashMap<>();
                double totalTime = 0;
                
                try (Scanner scanner = new Scanner(new File(csvFile))) {
                    scanner.nextLine(); // Skip header
                    
                    while (scanner.hasNextLine()) {
                        String[] parts = scanner.nextLine().split(",");
                        int tenantId = Integer.parseInt(parts[6]);
                        double execTime = Double.parseDouble(parts[5]);
                        double waitTime = Double.parseDouble(parts[4]) - Double.parseDouble(parts[3]) - execTime;
                        
                        // Store both execution time and wait time
                        List<Double> times = tenantData.computeIfAbsent(tenantId, k -> new ArrayList<>());
                        times.add(execTime);
                        times.add(waitTime);
                        
                        totalTime += execTime;
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error reading CSV data for " + algorithm, e);
                    continue;
                }
                
                algoTenantData.put(algorithm, tenantData);
                algoTotalTime.put(algorithm, totalTime);
            }
            
            // Write overall performance summary
            writer.write("Overall Performance Summary:\n");
            writer.write("---------------------------\n");
            
            // Sort algorithms by total execution time
            List<Map.Entry<String, Double>> sortedAlgos = new ArrayList<>(algoTotalTime.entrySet());
            sortedAlgos.sort(Map.Entry.comparingByValue());
            
            for (Map.Entry<String, Double> entry : sortedAlgos) {
                writer.write(String.format("  %s: Total Execution Time = %s\n", 
                        entry.getKey(), DF.format(entry.getValue())));
            }
            writer.write("\nBest Overall Algorithm: " + sortedAlgos.get(0).getKey() + "\n\n");
            
            // Write per-tenant analysis
            writer.write("Per-Tenant Analysis:\n");
            writer.write("------------------\n");
            
            for (int tenantId = 0; tenantId < TENANT_COUNT; tenantId++) {
                writer.write("Tenant " + tenantId + ":\n");
                
                // Calculate metrics for each algorithm for this tenant
                Map<String, Double> avgExecTimes = new HashMap<>();
                Map<String, Double> avgWaitTimes = new HashMap<>();
                Map<String, Double> totalExecTimes = new HashMap<>();
                
                for (String algorithm : ALGORITHMS) {
                    Map<Integer, List<Double>> tenantData = algoTenantData.get(algorithm);
                    if (tenantData == null || !tenantData.containsKey(tenantId)) {
                        continue;
                    }
                    
                    List<Double> times = tenantData.get(tenantId);
                    double totalExec = 0;
                    double totalWait = 0;
                    int count = times.size() / 2;
                    
                    for (int i = 0; i < times.size(); i += 2) {
                        totalExec += times.get(i);
                        totalWait += times.get(i + 1);
                    }
                    
                    avgExecTimes.put(algorithm, totalExec / count);
                    avgWaitTimes.put(algorithm, totalWait / count);
                    totalExecTimes.put(algorithm, totalExec);
                }
                
                // Print metrics
                for (String algorithm : ALGORITHMS) {
                    if (!avgExecTimes.containsKey(algorithm)) {
                        continue;
                    }
                    
                    writer.write(String.format("  %s:\n", algorithm));
                    writer.write(String.format("    Avg Execution Time: %s\n", DF.format(avgExecTimes.get(algorithm))));
                    writer.write(String.format("    Avg Wait Time: %s\n", DF.format(avgWaitTimes.get(algorithm))));
                    writer.write(String.format("    Total Execution Time: %s\n", DF.format(totalExecTimes.get(algorithm))));
                }
                
                // Determine best algorithm for this tenant
                String bestAlgo = null;
                double bestScore = Double.MAX_VALUE;
                
                for (Map.Entry<String, Double> entry : totalExecTimes.entrySet()) {
                    if (entry.getValue() < bestScore) {
                        bestScore = entry.getValue();
                        bestAlgo = entry.getKey();
                    }
                }
                
                writer.write("  Best Algorithm for Tenant " + tenantId + ": " + bestAlgo + "\n\n");
            }
            
            // Add conclusion section
            writer.write("Conclusion:\n");
            writer.write("----------\n");
            writer.write("Based on the simulation results, we can observe how different scheduling\n");
            writer.write("algorithms affect the performance experienced by different tenants in a\n");
            writer.write("multi-tenant FaaS environment. The choice of optimal algorithm depends on\n");
            writer.write("specific tenant workloads and organizational priorities.\n\n");
            
            LOGGER.info("Generated comparison report: " + fileName);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error generating comparison report", e);
        }
    }
}