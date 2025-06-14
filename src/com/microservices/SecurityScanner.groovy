package com.microservices

class SecurityScanner implements Serializable {
    def steps
    
    SecurityScanner(steps) {
        this.steps = steps
    }
    
    def installTrivy() {
        steps.sh """
            # Instalar Trivy si no existe
            if ! command -v trivy &> /dev/null; then
                echo "📦 Instalando Trivy..."
                curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh | sh -s -- -b /usr/local/bin
            fi
            
            echo "✅ Trivy instalado y listo para escaneo local"
        """
    }
    
    def scanImage(String image, String serviceName) {
        steps.echo "🛡️  Escaneando imagen local ${image}..."
        
        steps.sh """
            # Escaneo completo en formato JSON
            trivy image \
                --format json \
                --output trivy-${serviceName}-report.json \
                ${image}
            
            # Escaneo resumen para consola
            trivy image \
                --format table \
                --output trivy-${serviceName}-summary.txt \
                --severity CRITICAL,HIGH \
                ${image}
            
            echo "📊 Resumen de ${serviceName}:"
            cat trivy-${serviceName}-summary.txt || echo "No hay vulnerabilidades críticas/altas"
        """
        
        // Contar vulnerabilidades
        def criticalCount = steps.sh(
            script: "cat trivy-${serviceName}-report.json | jq '.Results[]?.Vulnerabilities[]? | select(.Severity==\"CRITICAL\") | .VulnerabilityID' | wc -l",
            returnStdout: true
        ).trim() as Integer
        
        def highCount = steps.sh(
            script: "cat trivy-${serviceName}-report.json | jq '.Results[]?.Vulnerabilities[]? | select(.Severity==\"HIGH\") | .VulnerabilityID' | wc -l",
            returnStdout: true
        ).trim() as Integer
        
        return [
            service: serviceName,
            image: image,
            critical: criticalCount,
            high: highCount
        ]
    }
    
    def generateConsolidatedReport(List scanResults, int totalCritical, int totalHigh, String buildNumber) {
        def reportContent = steps.libraryResource('templates/trivy-report.html')
        
        // Reemplazar placeholders
        reportContent = reportContent.replace('${BUILD_NUMBER}', buildNumber)
        reportContent = reportContent.replace('${TOTAL_SERVICES}', scanResults.size().toString())
        reportContent = reportContent.replace('${TOTAL_CRITICAL}', totalCritical.toString())
        reportContent = reportContent.replace('${TOTAL_HIGH}', totalHigh.toString())
        reportContent = reportContent.replace('${SCAN_DATE}', new Date().toString())
        
        // Generar tabla de resultados
        def resultsHtml = scanResults.collect { result ->
            """
            <div class="service">
                <h3>🚀 ${result.service}</h3>
                <p><strong>Imagen local:</strong> ${result.image}</p>
                <p><strong>Vulnerabilidades críticas:</strong> <span class="critical">${result.critical}</span></p>
                <p><strong>Vulnerabilidades altas:</strong> <span class="high">${result.high}</span></p>
                <a href="trivy-${result.service}-report.json" target="_blank">Ver reporte detallado JSON</a>
            </div>
            """
        }.join('\n')
        
        reportContent = reportContent.replace('${SCAN_RESULTS}', resultsHtml)
        
        steps.writeFile file: 'trivy-consolidated-report.html', text: reportContent
    }
}