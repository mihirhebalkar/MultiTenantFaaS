package simulations;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.*;

public class MultiTenantFaaS {
    static Map<Integer, Integer> cloudletTenantMap = new HashMap<>();
    static int tenantCount = 4;
    static int cloudletCount = 20;
    static int vmCount = 5;

    public static void main(String[] args) {
        runSimulation("RoundRobin");
        runSimulation("SJF");
        generateComparisonReport();
    }

    // Initialize and Run Simulation
    private static void runSimulation(String algo) {
        try {
            CloudSim.init(1, Calendar.getInstance(), false);

            Datacenter datacenter = createDatacenter("Datacenter_1");
            DatacenterBroker broker = new DatacenterBroker("Broker_" + algo);
            int brokerId = broker.getId();

            List<Vm> vms = createVMs(brokerId, vmCount);
            broker.submitVmList(vms);

            List<Cloudlet> cloudlets = createCloudlets(brokerId, cloudletCount, tenantCount);
            broker.submitCloudletList(cloudlets);

            if (algo.equals("SJF")) {
                cloudlets.sort(Comparator.comparingLong(Cloudlet::getCloudletLength));
            }

            CloudSim.startSimulation();
            List<Cloudlet> results = broker.getCloudletReceivedList();
            CloudSim.stopSimulation();

            generateCSV(results, algo);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Create Datacenter
    private static Datacenter createDatacenter(String name) throws Exception {
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            List<Pe> peList = new ArrayList<>();
            peList.add(new Pe(0, new PeProvisionerSimple(1000)));

            Host host = new Host(
                    i,
                    new RamProvisionerSimple(2048),
                    new BwProvisionerSimple(10000),
                    1000000,
                    peList,
                    new VmSchedulerTimeShared(peList)
            );

            hostList.add(host);
        }

        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                "x86", "Linux", "Xen", hostList,
                10.0, 3.0, 0.05, 0.001, 0.0
        );

        return new Datacenter(name, characteristics, new VmAllocationPolicySimple(hostList), new LinkedList<>(), 0);
    }

    // Create VMs
    private static List<Vm> createVMs(int brokerId, int count) {
        List<Vm> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Vm vm = new Vm(i, brokerId, 1000, 1, 512, 1000, 10000, "Xen", new CloudletSchedulerTimeShared());
            list.add(vm);
        }
        return list;
    }

    // Create Cloudlets and Assign Tenants
    private static List<Cloudlet> createCloudlets(int brokerId, int count, int tenants) {
        List<Cloudlet> list = new ArrayList<>();
        Random rand = new Random();
        UtilizationModel utilization = new UtilizationModelFull();

        for (int i = 0; i < count; i++) {
            Cloudlet c = new Cloudlet(i, 40000 + rand.nextInt(10000), 1, 300, 300, utilization, utilization, utilization);
            c.setUserId(brokerId);
            int tenantId = rand.nextInt(tenants);
            cloudletTenantMap.put(c.getCloudletId(), tenantId);
            list.add(c);
        }

        return list;
    }

    // Generate CSV Output
    private static void generateCSV(List<Cloudlet> cloudlets, String algo) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter("output/multitenant_" + algo + ".csv"));
            writer.write("CloudletID,Status,StartTime,FinishTime,ExecTime,TenantID\n");

            for (Cloudlet c : cloudlets) {
                writer.write(c.getCloudletId() + "," + c.getStatus() + "," +
                        c.getExecStartTime() + "," + c.getFinishTime() + "," +
                        c.getActualCPUTime() + "," + cloudletTenantMap.get(c.getCloudletId()) + "\n");
            }

            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Generate Comparison Text Report
    private static void generateComparisonReport() {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter("output/comparison_report.txt"));

            writer.write("Multi-Tenant FaaS Simulation Report\n");
            writer.write("====================================\n\n");

            Map<String, Map<Integer, Double>> summary = new HashMap<>();

            for (String algo : new String[]{"RoundRobin", "SJF"}) {
                Map<Integer, Double> tenantExecTimes = new HashMap<>();
                Scanner scanner = new Scanner(new java.io.File("output/multitenant_" + algo + ".csv"));
                scanner.nextLine(); // skip header

                while (scanner.hasNextLine()) {
                    String[] parts = scanner.nextLine().split(",");
                    int tenantId = Integer.parseInt(parts[5]);
                    double execTime = Double.parseDouble(parts[4]);

                    tenantExecTimes.put(tenantId, tenantExecTimes.getOrDefault(tenantId, 0.0) + execTime);
                }

                summary.put(algo, tenantExecTimes);
                scanner.close();
            }

            for (int tenantId = 0; tenantId < tenantCount; tenantId++) {
                writer.write("Tenant " + tenantId + ":\n");
                double rrTime = summary.get("RoundRobin").getOrDefault(tenantId, 0.0);
                double sjfTime = summary.get("SJF").getOrDefault(tenantId, 0.0);

                writer.write("  Round Robin Time: " + rrTime + "\n");
                writer.write("  SJF Time        : " + sjfTime + "\n");
                writer.write("  Winner          : " + (rrTime < sjfTime ? "Round Robin" : "SJF") + "\n\n");
            }

            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
