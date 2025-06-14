package com.microservices

class EmailNotifier implements Serializable {
    def steps
    
    EmailNotifier(steps) {
        this.steps = steps
    }
    
    def sendEmail(String to, String subject, String body, String mimeType = 'text/html') {
        steps.emailext(
            to: to,
            subject: subject,
            body: body,
            mimeType: mimeType,
            attachLog: false,
            compressLog: false,
            recipientProviders: [
                [$class: 'CulpritsRecipientProvider'],
                [$class: 'RequesterRecipientProvider']
            ]
        )
    }
    
    def buildServicesList(List services) {
        return services.collect { "<li>${it}</li>" }.join('\n')
    }
    
    def buildDockerImagesList(List services, String dockerhubUsername, String version) {
        return services.collect { 
            """
            <li>
                <code>${dockerhubUsername}/${it}:v${version}</code><br>
                <code>${dockerhubUsername}/${it}:latest</code>
            </li>
            """
        }.join('\n')
    }
    
    def buildTestResultsTable(Map testResults) {
        def html = """
        <table style="border-collapse: collapse; width: 100%;">
            <tr style="background-color: #f2f2f2;">
                <th style="border: 1px solid #ddd; padding: 8px;">Test Type</th>
                <th style="border: 1px solid #ddd; padding: 8px;">Status</th>
                <th style="border: 1px solid #ddd; padding: 8px;">Duration</th>
                <th style="border: 1px solid #ddd; padding: 8px;">Details</th>
            </tr>
        """
        
        testResults.each { type, result ->
            def statusIcon = result.status == 'PASSED' ? '✅' : (result.status == 'FAILED' ? '❌' : '⚠️')
            html += """
            <tr>
                <td style="border: 1px solid #ddd; padding: 8px;">${type}</td>
                <td style="border: 1px solid #ddd; padding: 8px;">${statusIcon} ${result.status}</td>
                <td style="border: 1px solid #ddd; padding: 8px;">${result.duration ?: 'N/A'}</td>
                <td style="border: 1px solid #ddd; padding: 8px;">${result.details ?: ''}</td>
            </tr>
            """
        }
        
        html += "</table>"
        return html
    }
    
    def buildSecurityMetricsTable(Map metrics) {
        if (!metrics || metrics.isEmpty()) {
            return "<p>No security metrics available</p>"
        }
        
        def html = """
        <table style="border-collapse: collapse; width: 100%;">
            <tr style="background-color: #f2f2f2;">
                <th style="border: 1px solid #ddd; padding: 8px;">Metric</th>
                <th style="border: 1px solid #ddd; padding: 8px;">Value</th>
            </tr>
        """
        
        metrics.each { key, value ->
            def formattedKey = key.replace('_', ' ').toLowerCase().split(' ').collect { 
                it.capitalize() 
            }.join(' ')
            
            html += """
            <tr>
                <td style="border: 1px solid #ddd; padding: 8px;">${formattedKey}</td>
                <td style="border: 1px solid #ddd; padding: 8px;">${value}</td>
            </tr>
            """
        }
        
        html += "</table>"
        return html
    }
    
    def formatCommitInfo(String commitHash) {
        if (!commitHash) return 'N/A'
        return commitHash.take(8)
    }
    
    def generateBuildInfoSection(Map buildInfo) {
        return """
        <div style="background-color: #f5f5f5; padding: 15px; border-radius: 5px; margin: 10px 0;">
            <h3>📋 Build Information</h3>
            <ul>
                <li><strong>Job:</strong> ${buildInfo.jobName}</li>
                <li><strong>Build Number:</strong> #${buildInfo.buildNumber}</li>
                <li><strong>Branch:</strong> ${buildInfo.branch}</li>
                <li><strong>Version:</strong> v${buildInfo.version ?: 'N/A'}</li>
                <li><strong>Commit:</strong> ${formatCommitInfo(buildInfo.commit)}</li>
                <li><strong>Duration:</strong> ${buildInfo.duration}</li>
                <li><strong>Triggered By:</strong> ${buildInfo.triggeredBy ?: 'System'}</li>
            </ul>
        </div>
        """
    }
    
    def generateActionButtons(String buildUrl) {
        return """
        <div style="margin: 20px 0;">
            <a href="${buildUrl}" style="display: inline-block; padding: 10px 20px; background-color: #1976d2; color: white; text-decoration: none; border-radius: 5px; margin-right: 10px;">View Build</a>
            <a href="${buildUrl}/console" style="display: inline-block; padding: 10px 20px; background-color: #757575; color: white; text-decoration: none; border-radius: 5px; margin-right: 10px;">View Console</a>
            <a href="${buildUrl}/testReport" style="display: inline-block; padding: 10px 20px; background-color: #388e3c; color: white; text-decoration: none; border-radius: 5px;">View Tests</a>
        </div>
        """
    }
}