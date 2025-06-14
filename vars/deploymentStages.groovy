import com.microservices.DockerManager
import com.microservices.GitHubManager

def pushDockerImages(List servicesToBuild, String dockerhubUsername) {
    stage('Push Docker Images') {
        echo 'Publicando imágenes Docker en Docker Hub...'
        
        def dockerManager = new DockerManager(this)
        
        withCredentials([usernamePassword(
            credentialsId: env.DOCKERHUB_CREDENTIALS_ID, 
            usernameVariable: 'DOCKERHUB_USER', 
            passwordVariable: 'DOCKERHUB_PASS'
        )]) {
            // Login a Docker Hub
            sh 'echo $DOCKERHUB_PASS | docker login -u $DOCKERHUB_USER --password-stdin'
            
            servicesToBuild.each { service ->
                if (fileExists("${service}/Dockerfile")) {
                    dockerManager.pushImage(service, env.BUILD_NUMBER, env.SEMANTIC_VERSION, dockerhubUsername)
                }
            }
            
            // Logout de Docker Hub
            sh 'docker logout'
        }
        
        echo "🎉 Todas las imágenes han sido publicadas en Docker Hub"
    }
}

def requestProductionApproval(List servicesToDeploy, String semanticVersion) {
    stage('Production Deployment Approval') {
        echo '🚨 Solicitando aprobación para despliegue a PRODUCCIÓN...'
        
        def approvalMessage = """
🚀 APROBACIÓN REQUERIDA: Despliegue a Producción

📋 Detalles del despliegue:
- Versión: v${semanticVersion}
- Branch: ${env.BRANCH_NAME}
- Build: ${env.BUILD_NUMBER}
- Servicios: ${servicesToDeploy.join(', ')}

🛡️ Verificaciones completadas:
✅ Compilación exitosa
✅ Análisis de calidad (SonarQube)
✅ Escaneo de seguridad (Trivy)
✅ Imágenes Docker construidas

⚠️  Este despliegue afectará el entorno de PRODUCCIÓN.
¿Desea continuar con el despliegue?
        """
        
        def config = pipelineConfig.getDefaultConfig()
        
        try {
            timeout(time: config.productionApprovalTimeout, unit: 'MINUTES') {
                def userInput = input(
                    id: 'productionDeployApproval',
                    message: approvalMessage,
                    parameters: [
                        choice(
                            choices: ['Aprobar y continuar', 'Rechazar despliegue'],
                            description: 'Seleccione una opción:',
                            name: 'APPROVAL_CHOICE'
                        )
                    ],
                    submitterParameter: 'APPROVER'
                )
                
                if (userInput.APPROVAL_CHOICE == 'Rechazar despliegue') {
                    error("❌ Despliegue a producción RECHAZADO por ${userInput.APPROVER}")
                }
                
                echo "✅ Despliegue a producción APROBADO por ${userInput.APPROVER}"
                env.DEPLOYMENT_APPROVER = userInput.APPROVER
            }
        } catch (Exception e) {
            echo "⏰ Timeout o rechazo de aprobación: ${e.getMessage()}"
            currentBuild.result = 'ABORTED'
            error("❌ Despliegue cancelado: No se recibió aprobación a tiempo")
        }
    }
}

def createGitHubRelease(List servicesToBuild, String semanticVersion) {
    stage('Create GitHub Release') {
        echo 'Creando release en GitHub...'
        
        def releaseTag = "v${semanticVersion}"
        def releaseTitle = "Release ${releaseTag} - ${servicesToBuild.join(', ')}"
        def releaseNotes = generateReleaseNotes(servicesToBuild, semanticVersion)
        
        withCredentials([string(credentialsId: 'GITHUB_TOKEN', variable: 'GITHUB_TOKEN')]) {
            sh """
                # Instalar gh CLI si no existe
                if ! command -v gh &> /dev/null; then
                    echo "📦 Instalando GitHub CLI..."
                    curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg | sudo dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg
                    echo "deb [arch=\$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" | sudo tee /etc/apt/sources.list.d/github-cli.list > /dev/null
                    sudo apt update && sudo apt install gh -y
                fi
                
                # Configurar autenticación
                echo "${GITHUB_TOKEN}" | gh auth login --with-token
                
                # Crear el release
                gh release create "${releaseTag}" \
                    --title "${releaseTitle}" \
                    --notes "${releaseNotes}" \
                    --latest
                
                echo "✅ Release ${releaseTag} creado exitosamente en GitHub"
            """
        }
    }
}

def generateReleaseNotes(List servicesToBuild, String semanticVersion) {
    return """
## 🚀 Release v${semanticVersion}

### Microservicios actualizados:
${servicesToBuild.collect { "- ${it}" }.join('\n')}

### 📦 Imágenes Docker publicadas:
${servicesToBuild.collect { "- `${env.DOCKERHUB_USERNAME}/${it}:v${semanticVersion}`" }.join('\n')}
${servicesToBuild.collect { "- `${env.DOCKERHUB_USERNAME}/${it}:latest`" }.join('\n')}

### 📊 Información del build:
- **Build ID:** ${env.BUILD_NUMBER}
- **Branch:** ${env.BRANCH_NAME}
- **Commit:** ${env.GIT_COMMIT?.take(8)}
- **Fecha:** ${new Date().format('yyyy-MM-dd HH:mm:ss')}

### 🛡️ Seguridad:
- Análisis de calidad: ✅ SonarQube
- Escaneo de seguridad: ✅ Trivy
- Imágenes validadas y publicadas en Docker Hub

---
*Release generado automáticamente por Jenkins Pipeline*
"""
}