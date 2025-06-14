import com.microservices.TestReporter
import com.microservices.Constants

def runAllTests(List servicesToBuild) {
    stage('Tests') {
        parallel(
            'Unit Tests': {
                runUnitTests(servicesToBuild)
            },
            'Integration Tests': {
                runIntegrationTests(servicesToBuild)
            },
            'End-to-End Tests': {
                runE2ETests(servicesToBuild)
            },
            'Load Tests': {
                runLoadTests(servicesToBuild)
            }
        )
    }
}

def runUnitTests(List servicesToBuild) {
    echo 'Ejecutando pruebas unitarias...'
    def servicesWithUnitTests = Constants.SERVICES_WITH_UNIT_TESTS
    def servicesToTest = servicesWithUnitTests.findAll { it in servicesToBuild }
    
    if (servicesToTest.isEmpty()) {
        echo "ℹ️  No hay servicios con pruebas unitarias para ejecutar"
        return
    }
    
    echo "🧪 Ejecutando pruebas unitarias para: ${servicesToTest.join(', ')}"
    
    servicesToTest.each { service ->
        echo "🔬 Ejecutando pruebas unitarias para ${service}..."
        
        if (fileExists("${service}/pom.xml")) {
            sh """
                cd ${service}
                echo "📋 Ejecutando pruebas unitarias en ${service}..."
                ../mvnw test -Dtest="**/*Test" \
                    -DfailIfNoTests=false \
                    -Dmaven.test.failure.ignore=false \
                    -Dspring.profiles.active=test
                
                echo "✅ Pruebas unitarias completadas para ${service}"
            """
        } else {
            sh """
                echo "📋 Ejecutando pruebas unitarias en ${service} (multi-módulo)..."
                ./mvnw test -pl ${service} -am \
                    -Dtest="**/*Test" \
                    -DfailIfNoTests=false \
                    -Dmaven.test.failure.ignore=false \
                    -Dspring.profiles.active=test
                
                echo "✅ Pruebas unitarias completadas para ${service}"
            """
        }
    }
    
    // Publicar resultados
    publishUnitTestResults(servicesToTest)
}

def runIntegrationTests(List servicesToBuild) {
    echo 'Ejecutando pruebas de integración...'
    def servicesWithIntegrationTests = Constants.SERVICES_WITH_INTEGRATION_TESTS
    def servicesToTest = servicesWithIntegrationTests.findAll { it in servicesToBuild }
    
    if (servicesToTest.isEmpty()) {
        echo "ℹ️  No hay servicios con pruebas de integración para ejecutar"
        return
    }
    
    echo "🔧 Ejecutando pruebas de integración para: ${servicesToTest.join(', ')}"
    
    servicesToTest.each { service ->
        echo "⚙️  Ejecutando pruebas de integración para ${service}..."
        
        if (fileExists("${service}/pom.xml")) {
            sh """
                cd ${service}
                echo "📋 Ejecutando pruebas de integración en ${service}..."
                ../mvnw test -Dtest="**/*IT,**/*IntegrationTest" \
                    -DfailIfNoTests=false \
                    -Dmaven.test.failure.ignore=false \
                    -Dspring.profiles.active=integration-test
                
                echo "✅ Pruebas de integración completadas para ${service}"
            """
        } else {
            sh """
                echo "📋 Ejecutando pruebas de integración en ${service} (multi-módulo)..."
                ./mvnw test -pl ${service} -am \
                    -Dtest="**/*IT,**/*IntegrationTest" \
                    -DfailIfNoTests=false \
                    -Dmaven.test.failure.ignore=false \
                    -Dspring.profiles.active=integration-test
                
                echo "✅ Pruebas de integración completadas para ${service}"
            """
        }
    }
    
    // Publicar resultados
    publishIntegrationTestResults(servicesToTest)
}

def runE2ETests(List servicesToBuild) {
    echo "🌐 Ejecutando pruebas E2E para servicios: ${servicesToBuild.join(', ')}"
    
    try {
        // Iniciar servicios necesarios para E2E
        sh """
            echo "🚀 Preparando entorno para pruebas E2E..."
            
            if [ -f "compose.yml" ]; then
                echo "📦 Iniciando servicios con docker-compose..."
                docker-compose -f compose.yml up -d --build
                
                echo "⏳ Esperando a que los servicios estén listos..."
                sleep 30
                
                docker-compose -f compose.yml ps
            else
                echo "⚠️  No se encontró compose.yml, saltando inicio de servicios"
            fi
        """
        
        // Ejecutar pruebas E2E
        sh """
            cd e2e-tests
            echo "🧪 Ejecutando pruebas End-to-End..."
            
            ../mvnw clean compile test-compile
            
            ../mvnw test \
                -Dtest="**/*Test" \
                -DfailIfNoTests=false \
                -Dmaven.test.failure.ignore=false \
                -Dspring.profiles.active=e2e
            
            echo "✅ Pruebas E2E completadas"
        """
    } finally {
        // Limpiar servicios docker-compose
        sh """
            if [ -f "compose.yml" ]; then
                echo "🧹 Limpiando servicios docker-compose..."
                docker-compose -f compose.yml down --volumes --remove-orphans || true
            fi
        """
        
        // Publicar resultados
        if (fileExists("e2e-tests/target/surefire-reports/*.xml")) {
            publishHTML([
                allowMissing: true,
                alwaysLinkToLastBuild: true,
                keepAll: true,
                reportDir: 'e2e-tests/target',
                reportFiles: 'surefire-reports/*.xml',
                reportName: 'E2E Test Results'
            ])
        }
    }
}

def runLoadTests(List servicesToBuild) {
    echo "📈 Resumen final del escaneo local:"
    echo "   - Total servicios: ${imagesToScan.size()}"
    echo "   - Total vulnerabilidades críticas: ${totalCritical}"
    echo "   - Total vulnerabilidades altas: ${totalHigh}"
    
    // Publicar reporte
    publishHTML([
        allowMissing: true,
        alwaysLinkToLastBuild: true,
        keepAll: true,
        reportDir: '.',
        reportFiles: 'trivy-consolidated-report.html',
        reportName: 'Trivy Security Report (Local)'
    ])
    
    // Archivar reportes
    archiveArtifacts artifacts: 'trivy-*-report.json,trivy-*-summary.txt,trivy-metrics.properties,trivy-consolidated-report.html', 
                   fingerprint: true, 
                   allowEmptyArchive: true
    
    // Evaluar políticas de seguridad
    evaluateSecurityPolicies(totalCritical, totalHigh, imagesToScan.size())
}

def evaluateSecurityPolicies(int totalCritical, int totalHigh, int scannedServices) {
    def config = pipelineConfig.getDefaultConfig()
    
    echo "📊 Métricas de seguridad consolidadas:"
    echo "   - Servicios escaneados: ${scannedServices}"
    echo "   - Total vulnerabilidades críticas: ${totalCritical}"
    echo "   - Total vulnerabilidades altas: ${totalHigh}"
    
    // Verificar política de vulnerabilidades críticas
    if (totalCritical > config.maxCriticalVulns) {
        echo "⚠️  POLÍTICA: Se encontraron ${totalCritical} vulnerabilidades críticas (máximo permitido: ${config.maxCriticalVulns})"
        // Descomentar para fallar el build:
        // error("Build fallido por vulnerabilidades críticas detectadas")
    }
    
    // Verificar política de vulnerabilidades altas
    if (totalHigh > config.maxHighVulns) {
        echo "⚠️  POLÍTICA: Demasiadas vulnerabilidades altas (${totalHigh} > ${config.maxHighVulns})"
        currentBuild.result = 'UNSTABLE'
    }
    
    // Calcular promedios
    def avgCritical = totalCritical.toFloat() / scannedServices
    def avgHigh = totalHigh.toFloat() / scannedServices
    
    echo "📈 Promedio por servicio:"
    echo "   - Críticas: ${String.format('%.2f', avgCritical)}"
    echo "   - Altas: ${String.format('%.2f', avgHigh)}"
    
    echo "✅ Verificación de políticas completada"
}

def waitForQualityGate() {
    stage('Quality Gate') {
        echo 'Esperando resultado del Quality Gate de SonarQube...'
        def config = pipelineConfig.getDefaultConfig()
        timeout(time: config.qualityGateTimeout, unit: 'MINUTES') {
            waitForQualityGate abortPipeline: true
        }
    }
}

def checkSecurityPolicy() {
    stage('Security Policy Check') {
        echo 'Verificando políticas de seguridad...'
        
        if (fileExists('trivy-metrics.properties')) {
            def props = readProperties file: 'trivy-metrics.properties'
            def totalCriticalVulns = props.TOTAL_CRITICAL_VULNS as Integer
            def totalHighVulns = props.TOTAL_HIGH_VULNS as Integer
            def scannedServices = props.SCANNED_SERVICES as Integer
            
            evaluateSecurityPolicies(totalCriticalVulns, totalHighVulns, scannedServices)
        } else {
            echo "ℹ️  No se encontraron métricas de seguridad para evaluar"
        }
    }
}