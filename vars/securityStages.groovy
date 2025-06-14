import com.microservices.SecurityScanner
import com.microservices.Constants

def runAllSecurityScans(List localImages, boolean skipSecurityScan) {
    stage('Code Quality and Security Analysis') {
        parallel(
            'SonarQube Analysis': {
                runSonarQubeAnalysis()
            },
            'Container Security Scan': {
                if (!skipSecurityScan) {
                    runContainerSecurityScan(localImages)
                } else {
                    echo "⏭️  Saltando escaneo de seguridad según parámetro"
                }
            }
        )
    }
}

def runSonarQubeAnalysis() {
    echo 'Ejecutando análisis de SonarQube...'
    withSonarQubeEnv('SonarQube-Server') {
        sh """
            ./mvnw sonar:sonar \
            -Dsonar.projectKey=ecommerce-microservice-backend \
            -Dsonar.projectName='Ecommerce Microservice Backend' \
            -Dsonar.projectVersion=${env.BUILD_NUMBER} \
            -Dsonar.host.url=${env.SONAR_HOST_URL} \
            -Dsonar.login=${env.SONAR_TOKEN}
        """
    }
}

def runContainerSecurityScan(List imagesToScan) {
    echo 'Ejecutando escaneo de seguridad con Trivy...'
    
    if (imagesToScan.isEmpty()) {
        echo "ℹ️  No hay imágenes locales para escanear"
        return
    }
    
    def scanner = new SecurityScanner(this)
    scanner.installTrivy()
    
    def totalCritical = 0
    def totalHigh = 0
    def scanResults = []
    
    // Escanear cada imagen
    imagesToScan.each { image ->
        def serviceName = image.split(':')[0]
        def result = scanner.scanImage(image, serviceName)
        
        totalCritical += result.critical
        totalHigh += result.high
        scanResults.add(result)
        
        echo "🔴 ${serviceName} - Críticas: ${result.critical}, Altas: ${result.high}"
    }
    
    // Generar métricas
    sh """
        echo "TOTAL_CRITICAL_VULNS=${totalCritical}" > trivy-metrics.properties
        echo "TOTAL_HIGH_VULNS=${totalHigh}" >> trivy-metrics.properties
        echo "SCANNED_SERVICES=${imagesToScan.size()}" >> trivy-metrics.properties
    """
    
    // Generar reporte consolidado
    scanner.generateConsolidatedReport(scanResults, totalCritical, totalHigh, env.BUILD_NUMBER)
    
    echo "📈 hola como estas"
    }