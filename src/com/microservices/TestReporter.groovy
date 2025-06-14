package com.microservices

class TestReporter implements Serializable {
    def steps
    
    TestReporter(steps) {
        this.steps = steps
    }
    
    def publishJUnitResults(String pattern) {
        steps.echo "📊 Publishing JUnit test results: ${pattern}"
        
        steps.junit(
            testResults: pattern,
            allowEmptyResults: true,
            healthScaleFactor: 1.0,
            keepLongStdio: true
        )
    }
    
    def publishHTMLReport(String reportDir, String reportFiles, String reportName) {
        steps.echo "📄 Publishing HTML report: ${reportName}"
        
        steps.publishHTML([
            allowMissing: true,
            alwaysLinkToLastBuild: true,
            keepAll: true,
            reportDir: reportDir,
            reportFiles: reportFiles,
            reportName: reportName,
            reportTitles: reportName
        ])
    }
    
    def archiveTestArtifacts(String pattern) {
        steps.echo "📦 Archiving test artifacts: ${pattern}"
        
        steps.archiveArtifacts(
            artifacts: pattern,
            fingerprint: true,
            allowEmptyArchive: true
        )
    }
    
    def generateTestSummaryHTML(Map testResults, String buildNumber, String branchName) {
        def totalTests = 0
        def passedTests = 0
        def failedTests = 0
        def skippedTests = 0
        
        testResults.each { service, results ->
            totalTests += (results.total ?: 0)
            passedTests += (results.passed ?: 0)
            failedTests += (results.failed ?: 0)
            skippedTests += (results.skipped ?: 0)
        }
        
        def passRate = totalTests > 0 ? ((passedTests / totalTests) * 100).round(2) : 0
        
        return """
<!DOCTYPE html>
<html>
<head>
    <title>Test Summary Report - Build ${buildNumber}</title>
    <style>
        body { 
            font-family: Arial, sans-serif; 
            margin: 20px;
            background-color: #f5f5f5;
        }
        .container {
            max-width: 1200px;
            margin: 0 auto;
            background-color: white;
            padding: 20px;
            border-radius: 8px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }
        h1 { 
            color: #1976d2;
            border-bottom: 2px solid #1976d2;
            padding-bottom: 10px;
        }
        h2 { 
            color: #333;
            margin-top: 30px;
        }
        .summary-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 20px;
            margin: 20px 0;
        }
        .summary-card {
            background-color: #f8f9fa;
            padding: 20px;
            border-radius: 8px;
            text-align: center;
            border: 1px solid #e0e0e0;
        }
        .summary-card h3 {
            margin: 0 0 10px 0;
            color: #666;
            font-size: 14px;
            text-transform: uppercase;
        }
        .summary-card .value {
            font-size: 36px;
            font-weight: bold;
            margin: 10px 0;
        }
        .passed { color: #4caf50; }
        .failed { color: #f44336; }
        .skipped { color: #ff9800; }
        .info { color: #2196f3; }
        
        table {
            width: 100%;
            border-collapse: collapse;
            margin: 20px 0;
        }
        th, td {
            border: 1px solid #ddd;
            padding: 12px;
            text-align: left;
        }
        th {
            background-color: #f2f2f2;
            font-weight: bold;
        }
        tr:nth-child(even) {
            background-color: #f9f9f9;
        }
        
        .progress-bar {
            width: 100%;
            height: 30px;
            background-color: #e0e0e0;
            border-radius: 15px;
            overflow: hidden;
            margin: 20px 0;
        }
        .progress-fill {
            height: 100%;
            background-color: #4caf50;
            text-align: center;
            line-height: 30px;
            color: white;
            font-weight: bold;
        }
        
        .test-type-section {
            margin: 30px 0;
            padding: 20px;
            background-color: #f9f9f9;
            border-radius: 8px;
        }
        
        .timestamp {
            color: #666;
            font-size: 14px;
            margin-top: 20px;
            text-align: right;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>🧪 Test Summary Report - Build ${buildNumber}</h1>
        
        <div class="summary-grid">
            <div class="summary-card">
                <h3>Total Tests</h3>
                <div class="value info">${totalTests}</div>
            </div>
            <div class="summary-card">
                <h3>Passed</h3>
                <div class="value passed">${passedTests}</div>
            </div>
            <div class="summary-card">
                <h3>Failed</h3>
                <div class="value failed">${failedTests}</div>
            </div>
            <div class="summary-card">
                <h3>Skipped</h3>
                <div class="value skipped">${skippedTests}</div>
            </div>
            <div class="summary-card">
                <h3>Pass Rate</h3>
                <div class="value ${passRate >= 80 ? 'passed' : 'failed'}">${passRate}%</div>
            </div>
        </div>
        
        <div class="progress-bar">
            <div class="progress-fill" style="width: ${passRate}%">${passRate}% Tests Passed</div>
        </div>
        
        <h2>📋 Test Results by Service</h2>
        <table>
            <tr>
                <th>Service</th>
                <th>Total</th>
                <th>Passed</th>
                <th>Failed</th>
                <th>Skipped</th>
                <th>Pass Rate</th>
                <th>Duration</th>
            </tr>
            ${generateServiceRows(testResults)}
        </table>
        
        <div class="test-type-section">
            <h2>🔬 Unit Tests</h2>
            <p>Services with unit tests: user-service, product-service, payment-service</p>
            ${generateTestTypeTable(testResults, 'unit')}
        </div>
        
        <div class="test-type-section">
            <h2>⚙️ Integration Tests</h2>
            <p>Services with integration tests: user-service, product-service</p>
            ${generateTestTypeTable(testResults, 'integration')}
        </div>
        
        <div class="test-type-section">
            <h2>🌐 End-to-End Tests</h2>
            <p>All microservices tested together</p>
            ${generateTestTypeTable(testResults, 'e2e')}
        </div>
        
        <div class="test-type-section">
            <h2>📈 Load Tests</h2>
            <p>Performance testing with Locust (10 users, 2m duration)</p>
            ${generateTestTypeTable(testResults, 'load')}
        </div>
        
        <div class="timestamp">
            Generated on: ${new Date().format('yyyy-MM-dd HH:mm:ss')}<br>
            Branch: ${branchName}
        </div>
    </div>
</body>
</html>
"""
    }
    
    private def generateServiceRows(Map testResults) {
        def rows = ""
        testResults.each { service, results ->
            def servicePassRate = results.total > 0 ? ((results.passed / results.total) * 100).round(2) : 0
            rows += """
            <tr>
                <td><strong>${service}</strong></td>
                <td>${results.total ?: 0}</td>
                <td class="passed">${results.passed ?: 0}</td>
                <td class="failed">${results.failed ?: 0}</td>
                <td class="skipped">${results.skipped ?: 0}</td>
                <td>${servicePassRate}%</td>
                <td>${results.duration ?: 'N/A'}</td>
            </tr>
            """
        }
        return rows
    }
    
    private def generateTestTypeTable(Map testResults, String testType) {
        def relevantResults = testResults.findAll { service, results ->
            results[testType] != null
        }
        
        if (relevantResults.isEmpty()) {
            return "<p>No ${testType} test results available</p>"
        }
        
        def table = """
        <table>
            <tr>
                <th>Service</th>
                <th>Status</th>
                <th>Tests</th>
                <th>Duration</th>
                <th>Details</th>
            </tr>
        """
        
        relevantResults.each { service, results ->
            def testData = results[testType]
            def statusClass = testData.passed ? 'passed' : 'failed'
            def statusText = testData.passed ? '✅ Passed' : '❌ Failed'
            
            table += """
            <tr>
                <td>${service}</td>
                <td class="${statusClass}">${statusText}</td>
                <td>${testData.count ?: 'N/A'}</td>
                <td>${testData.duration ?: 'N/A'}</td>
                <td>${testData.details ?: ''}</td>
            </tr>
            """
        }
        
        table += "</table>"
        return table
    }
    
    def generateCoverageReport(Map coverageData) {
        steps.echo "📊 Generating coverage report..."
        
        def html = """
<!DOCTYPE html>
<html>
<head>
    <title>Code Coverage Report</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .coverage-summary { background: #f5f5f5; padding: 20px; border-radius: 8px; }
        .coverage-bar { 
            width: 100%; 
            height: 20px; 
            background: #e0e0e0; 
            border-radius: 10px; 
            overflow: hidden;
        }
        .coverage-fill {
            height: 100%;
            background: linear-gradient(to right, #f44336, #ff9800, #4caf50);
            transition: width 0.3s ease;
        }
        .good { color: #4caf50; }
        .medium { color: #ff9800; }
        .poor { color: #f44336; }
    </style>
</head>
<body>
    <h1>📊 Code Coverage Report</h1>
    ${generateCoverageSummary(coverageData)}
    ${generateCoverageDetails(coverageData)}
</body>
</html>
"""
        
        steps.writeFile file: 'coverage-report.html', text: html
        
        publishHTMLReport('.', 'coverage-report.html', 'Code Coverage Report')
    }
    
    private def generateCoverageSummary(Map coverageData) {
        def overall = coverageData.overall ?: 0
        def coverageClass = overall >= 80 ? 'good' : (overall >= 60 ? 'medium' : 'poor')
        
        return """
        <div class="coverage-summary">
            <h2>Overall Coverage: <span class="${coverageClass}">${overall}%</span></h2>
            <div class="coverage-bar">
                <div class="coverage-fill" style="width: ${overall}%"></div>
            </div>
        </div>
        """
    }
    
    private def generateCoverageDetails(Map coverageData) {
        def details = "<h2>Coverage by Service</h2><ul>"
        
        coverageData.services?.each { service, coverage ->
            def coverageClass = coverage >= 80 ? 'good' : (coverage >= 60 ? 'medium' : 'poor')
            details += "<li>${service}: <span class='${coverageClass}'>${coverage}%</span></li>"
        }
        
        details += "</ul>"
        return details
    }
}