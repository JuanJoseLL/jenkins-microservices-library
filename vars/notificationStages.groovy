import com.microservices.EmailNotifier

def cleanup() {
    echo 'Limpieza final del pipeline...'
    
    // Limpiar archivos temporales
    sh """
        rm -f trivy-*-report.json trivy-*-summary.txt trivy-metrics.properties || true
    """
    
    cleanWs()
}

def sendSuccessNotification() {
    echo '✅ Pipeline completado exitosamente!'
    
    def servicesToBuild = env.SERVICES_TO_BUILD?.split(',') ?: []
    def emailSubject = "✅ Pipeline Exitoso - ${env.JOB_NAME} #${env.BUILD_NUMBER}"
    def emailBody = generateSuccessEmailBody(servicesToBuild)
    
    sendEmail(emailSubject, emailBody)
    
    // Log métricas de seguridad si existen
    if (fileExists('trivy-metrics.properties')) {
        def props = readProperties file: 'trivy-metrics.properties'
        echo "📊 Resumen final de seguridad:"
        echo "   - Servicios escaneados: ${props.SCANNED_SERVICES ?: 0}"
        echo "   - Total vulnerabilidades críticas: ${props.TOTAL_CRITICAL_VULNS ?: 0}"
        echo "   - Total vulnerabilidades altas: ${props.TOTAL_HIGH_VULNS ?: 0}"
    }
}

def sendFailureNotification() {
    echo '❌ El pipeline falló'
    
    def servicesToBuild = env.SERVICES_TO_BUILD?.split(',') ?: []
    def emailSubject = "❌ Pipeline Fallido - ${env.JOB_NAME} #${env.BUILD_NUMBER}"
    def emailBody = generateFailureEmailBody(servicesToBuild)
    
    sendEmail(emailSubject, emailBody)
}

def sendUnstableNotification() {
    echo '⚠️  Pipeline completado con advertencias'
    
    def servicesToBuild = env.SERVICES_TO_BUILD?.split(',') ?: []
    def emailSubject = "⚠️ Pipeline Inestable - ${env.JOB_NAME} #${env.BUILD_NUMBER}"
    def emailBody = generateUnstableEmailBody(servicesToBuild)
    
    sendEmail(emailSubject, emailBody)
}

def sendAbortedNotification() {
    echo '🛑 Pipeline cancelado/abortado'
    
    def emailSubject = "🛑 Pipeline Cancelado - ${env.JOB_NAME} #${env.BUILD_NUMBER}"
    def emailBody = generateAbortedEmailBody()
    
    sendEmail(emailSubject, emailBody)
}

def sendEmail(String subject, String body) {
    emailext (
        subject: subject,
        body: body,
        mimeType: 'text/html',
        to: env.EMAIL_RECIPIENTS
    )
}

def generateSuccessEmailBody(List servicesToBuild) {
    def template = libraryResource('templates/email-success.html')
    
    // Reemplazar placeholders
    template = template.replace('${JOB_NAME}', env.JOB_NAME ?: 'N/A')
    template = template.replace('${BUILD_NUMBER}', env.BUILD_NUMBER ?: 'N/A')
    template = template.replace('${BRANCH_NAME}', env.BRANCH_NAME ?: 'N/A')
    template = template.replace('${SEMANTIC_VERSION}', env.SEMANTIC_VERSION ?: 'N/A')
    template = template.replace('${GIT_COMMIT}', env.GIT_COMMIT?.take(8) ?: 'N/A')
    template = template.replace('${DURATION}', currentBuild.durationString ?: 'N/A')
    template = template.replace('${SERVICES_LIST}', servicesToBuild.collect { "<li>${it}</li>" }.join(''))
    template = template.replace('${DOCKER_IMAGES}', servicesToBuild.collect { "<li>Docker: ${env.DOCKERHUB_USERNAME ?: 'N/A'}/${it}:v${env.SEMANTIC_VERSION ?: 'latest'}</li>" }.join(''))
    template = template.replace('${BUILD_URL}', env.BUILD_URL ?: 'N/A')
    
    // Agregar información de producción si aplica
    if (env.IS_PRODUCTION_DEPLOY == 'true') {
        template = template.replace('${PRODUCTION_INFO}', "<h3>✅ Despliegue a Producción:</h3><p><strong>Aprobado por:</strong> ${env.DEPLOYMENT_APPROVER ?: 'N/A'}</p>")
    } else {
        template = template.replace('${PRODUCTION_INFO}', '')
    }
    
    // Agregar métricas de seguridad
    if (fileExists('trivy-metrics.properties')) {
        def props = readProperties file: 'trivy-metrics.properties'
        def metricsHtml = props.collect { k, v -> 
            "<li><strong>${(k ?: 'N/A').replace('_', ' ').toLowerCase().capitalize()}:</strong> ${v ?: 'N/A'}</li>" 
        }.join('')
        template = template.replace('${SECURITY_METRICS}', metricsHtml)
    } else {
        template = template.replace('${SECURITY_METRICS}', '<li>No hay métricas de seguridad disponibles</li>')
    }
    
    return template
}

def generateFailureEmailBody(List servicesToBuild) {
    def template = libraryResource('templates/email-failure.html')
    
    // Reemplazar placeholders básicos
    template = template.replace('${JOB_NAME}', env.JOB_NAME ?: 'N/A')
    template = template.replace('${BUILD_NUMBER}', env.BUILD_NUMBER ?: 'N/A')
    template = template.replace('${BRANCH_NAME}', env.BRANCH_NAME ?: 'N/A')
    template = template.replace('${SEMANTIC_VERSION}', env.SEMANTIC_VERSION ?: 'N/A')
    template = template.replace('${GIT_COMMIT}', env.GIT_COMMIT?.take(8) ?: 'N/A')
    template = template.replace('${DURATION}', currentBuild.durationString ?: 'N/A')
    template = template.replace('${FAILED_STAGE}', env.STAGE_NAME ?: 'Desconocido')
    template = template.replace('${SERVICES_LIST}', servicesToBuild.collect { "<li>${it}</li>" }.join(''))
    template = template.replace('${BUILD_URL}', env.BUILD_URL ?: 'N/A')
    
    // Determinar estado de las pruebas
    def unitTestStatus = (env.STAGE_NAME ?: '')?.contains('Unit Tests') ? '❌ Fallidas' : '⚠️ No ejecutadas'
    def integrationTestStatus = (env.STAGE_NAME ?: '')?.contains('Integration Tests') ? '❌ Fallidas' : '⚠️ No ejecutadas'
    def e2eTestStatus = (env.STAGE_NAME ?: '')?.contains('End-to-End Tests') ? '❌ Fallidas' : '⚠️ No ejecutadas'
    def loadTestStatus = (env.STAGE_NAME ?: '')?.contains('Load Tests') ? '❌ Fallidas' : '⚠️ No ejecutadas'
    
    template = template.replace('${UNIT_TEST_STATUS}', unitTestStatus)
    template = template.replace('${INTEGRATION_TEST_STATUS}', integrationTestStatus)
    template = template.replace('${E2E_TEST_STATUS}', e2eTestStatus)
    template = template.replace('${LOAD_TEST_STATUS}', loadTestStatus)
    
    return template
}

def generateUnstableEmailBody(List servicesToBuild) {
    def template = libraryResource('templates/email-unstable.html')
    
    // Similar a generateSuccessEmailBody pero con advertencias
    template = template.replace('${JOB_NAME}', env.JOB_NAME ?: 'N/A')
    template = template.replace('${BUILD_NUMBER}', env.BUILD_NUMBER ?: 'N/A')
    template = template.replace('${BRANCH_NAME}', env.BRANCH_NAME ?: 'N/A')
    template = template.replace('${SEMANTIC_VERSION}', env.SEMANTIC_VERSION ?: 'N/A')
    template = template.replace('${GIT_COMMIT}', env.GIT_COMMIT?.take(8) ?: 'N/A')
    template = template.replace('${DURATION}', currentBuild.durationString ?: 'N/A')
    template = template.replace('${SERVICES_LIST}', servicesToBuild.collect { "<li>${it}</li>" }.join(''))
    template = template.replace('${BUILD_URL}', env.BUILD_URL ?: 'N/A')
    
    // Agregar métricas de seguridad con advertencias
    if (fileExists('trivy-metrics.properties')) {
        def props = readProperties file: 'trivy-metrics.properties'
        def metricsHtml = props.collect { k, v -> 
            "<li><strong>${(k ?: 'N/A').replace('_', ' ').toLowerCase().capitalize()}:</strong> ${v ?: 'N/A'}</li>" 
        }.join('')
        template = template.replace('${SECURITY_WARNINGS}', metricsHtml)
    } else {
        template = template.replace('${SECURITY_WARNINGS}', '<li>No hay métricas de seguridad disponibles</li>')
    }
    
    return template
}

def generateAbortedEmailBody() {
    return """
<h2>🛑 Pipeline Cancelado</h2>

<h3>📋 Información del Build:</h3>
<ul>
    <li><strong>Job:</strong> ${env.JOB_NAME ?: 'N/A'}</li>
    <li><strong>Build:</strong> #${env.BUILD_NUMBER ?: 'N/A'}</li>
    <li><strong>Branch:</strong> ${env.BRANCH_NAME ?: 'N/A'}</li>
    <li><strong>Duración:</strong> ${currentBuild.durationString ?: 'N/A'}</li>
    <li><strong>Razón:</strong> ${env.IS_PRODUCTION_DEPLOY == 'true' ? 'Aprobación de producción rechazada o timeout' : 'Cancelado manualmente'}</li>
</ul>

${env.IS_PRODUCTION_DEPLOY == 'true' ? '<p><strong>⚠️ Nota:</strong> El despliegue a producción fue rechazado o no se recibió aprobación a tiempo.</p>' : ''}

<hr>
<p><a href="${env.BUILD_URL ?: '#'}">Ver detalles del build</a></p>
"""
}