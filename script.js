// Global variables to store the chart objects
let rrChart = null;
let sjfChart = null;
let taChart = null;
let comparisonChart = null;

// Define colors for tenants
const tenantColors = [
    'rgba(75, 192, 192, 0.7)',  // Teal
    'rgba(255, 99, 132, 0.7)',  // Red
    'rgba(54, 162, 235, 0.7)',  // Blue
    'rgba(255, 206, 86, 0.7)'   // Yellow
];

// Initialize when document is loaded
document.addEventListener('DOMContentLoaded', function() {
    // Set up tab switching
    const tabButtons = document.querySelectorAll('.tab-button');
    tabButtons.forEach(button => {
        button.addEventListener('click', function() {
            // Remove active class from all buttons and panes
            tabButtons.forEach(btn => btn.classList.remove('active'));
            document.querySelectorAll('.tab-pane').forEach(pane => pane.classList.remove('active'));
            
            // Add active class to current button and pane
            this.classList.add('active');
            document.getElementById(this.getAttribute('data-tab')).classList.add('active');
        });
    });
    
    // Load CSV data and display charts/tables
    loadData();
});

// Load CSV data from files
function loadData() {
    // Load Round Robin data
    Papa.parse('output/multitenant_RoundRobin.csv', {
        download: true,
        header: true,
        dynamicTyping: true,
        complete: function(results) {
            displayData('rr', results.data);
        }
    });
    
    // Load SJF data
    Papa.parse('output/multitenant_SJF.csv', {
        download: true,
        header: true,
        dynamicTyping: true,
        complete: function(results) {
            displayData('sjf', results.data);
        }
    });
    
    // Load Tenant Aware data
    Papa.parse('output/multitenant_TenantAware.csv', {
        download: true,
        header: true,
        dynamicTyping: true,
        complete: function(results) {
            displayData('ta', results.data);
            
            // Once all data is loaded, create comparison chart
            createComparisonChart();
        }
    });
}

// Display data for a specific algorithm
function displayData(prefix, data) {
    // Create chart
    createChart(prefix, data);
    
    // Populate table
    populateTable(prefix, data);
}

// Create chart for algorithm
function createChart(prefix, data) {
    const ctx = document.getElementById(`${prefix}-chart`).getContext('2d');
    
    // Group data by tenant
    const tenantData = {};
    const tenants = [];
    
    data.forEach(row => {
        const tenant = row.TenantID;
        if (!tenantData[tenant]) {
            tenantData[tenant] = [];
            tenants.push(tenant);
        }
        tenantData[tenant].push({
            x: row.CloudletID,
            y: parseFloat(row.ExecTime)
        });
    });
    
    // Sort tenants
    tenants.sort((a, b) => a - b);
    
    // Create datasets
    const datasets = tenants.map((tenant, index) => {
        return {
            label: `Tenant ${tenant}`,
            data: tenantData[tenant],
            backgroundColor: tenantColors[index % tenantColors.length],
            borderColor: tenantColors[index % tenantColors.length].replace('0.7', '1'),
            borderWidth: 1
        };
    });
    
    // Destroy existing chart if it exists
    if (window[`${prefix}Chart`]) {
        window[`${prefix}Chart`].destroy();
    }
    
    // Create new chart
    window[`${prefix}Chart`] = new Chart(ctx, {
        type: 'scatter',
        data: {
            datasets: datasets
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                title: {
                    display: true,
                    text: 'Cloudlet Execution Times by Tenant',
                    font: {
                        size: 16
                    }
                }
            },
            scales: {
                x: {
                    title: {
                        display: true,
                        text: 'Cloudlet ID'
                    }
                },
                y: {
                    title: {
                        display: true,
                        text: 'Execution Time (seconds)'
                    }
                }
            }
        }
    });
}

// Populate table with data
function populateTable(prefix, data) {
    const tableBody = document.querySelector(`#${prefix}-table tbody`);
    let html = '';
    
    // Add rows for each data point
    data.forEach(row => {
        html += `
            <tr>
                <td>${row.CloudletID}</td>
                <td>${row.Status}</td>
                <td>${parseFloat(row.StartTime).toFixed(2)}</td>
                <td>${parseFloat(row.FinishTime).toFixed(2)}</td>
                <td>${parseFloat(row.ExecTime).toFixed(2)}</td>
                <td>${row.TenantID}</td>
            </tr>
        `;
    });
    
    tableBody.innerHTML = html;
}

// Create comparison chart
function createComparisonChart() {
    // Wait until all data files have been loaded
    if (!window.rrChart || !window.sjfChart || !window.taChart) {
        setTimeout(createComparisonChart, 100);
        return;
    }
    
    const ctx = document.getElementById('comparison-chart').getContext('2d');
    
    // Calculate average execution time per tenant per algorithm
    const algorithms = [
        { name: 'Round Robin', prefix: 'rr' },
        { name: 'Shortest Job First', prefix: 'sjf' },
        { name: 'Tenant Aware', prefix: 'ta' }
    ];
    
    // Find all unique tenants
    const tenantSet = new Set();
    algorithms.forEach(algo => {
        const chartData = window[`${algo.prefix}Chart`].data.datasets;
        chartData.forEach(dataset => {
            const tenant = dataset.label.replace('Tenant ', '');
            tenantSet.add(tenant);
        });
    });
    
    // Sort tenants
    const tenants = Array.from(tenantSet).sort();
    
    // Calculate average execution time per tenant per algorithm
    const datasets = algorithms.map((algo, algoIndex) => {
        const data = [];
        
        tenants.forEach(tenant => {
            let totalTime = 0;
            let count = 0;
            
            const chartData = window[`${algo.prefix}Chart`].data.datasets;
            chartData.forEach(dataset => {
                if (dataset.label === `Tenant ${tenant}`) {
                    dataset.data.forEach(point => {
                        totalTime += point.y;
                        count++;
                    });
                }
            });
            
            data.push(count > 0 ? totalTime / count : 0);
        });
        
        return {
            label: algo.name,
            data: data,
            backgroundColor: `rgba(${75 + algoIndex * 80}, ${192 - algoIndex * 50}, ${192 - algoIndex * 30}, 0.7)`,
            borderColor: `rgba(${75 + algoIndex * 80}, ${192 - algoIndex * 50}, ${192 - algoIndex * 30}, 1)`,
            borderWidth: 1
        };
    });
    
    // Create comparison chart
    if (comparisonChart) {
        comparisonChart.destroy();
    }
    
    comparisonChart = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: tenants.map(tenant => `Tenant ${tenant}`),
            datasets: datasets
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                title: {
                    display: true,
                    text: 'Average Execution Time by Tenant and Algorithm',
                    font: {
                        size: 16
                    }
                }
            },
            scales: {
                y: {
                    beginAtZero: true,
                    title: {
                        display: true,
                        text: 'Average Execution Time (seconds)'
                    }
                }
            }
        }
    });
}