import com.microservices.DockerManager

def compileProject() {
    stage('Compile') {
        echo 'Compilando el proyecto...'
        sh './mvnw clean compile'
    }
}

def detectServicesToBuild(params) {
    def config = pipelineConfig.getDefaultConfig()
    def microservices = config.microservices
    def servicesToBuild = []
    
    if (env.CHANGE_TARGET) {
        // Es un PR, detectar cambios
        def changes = sh(
            script: "git diff --name-only origin/${env.CHANGE_TARGET}...HEAD",
            returnStdout: true
        ).trim().split('\n')
        
        servicesToBuild = microservices.findAll { service ->
            changes.any { it.startsWith("${service}/") }
        }
    } else {
        // Build manual o push a main
        def serviceToBuild = params.MICROSERVICE ?: 'ALL'
        if (serviceToBuild == 'ALL') {
            servicesToBuild = microservices
        } else {
            servicesToBuild = [serviceToBuild]
        }
    }
    
    if (servicesToBuild.isEmpty()) {
        echo "ℹ️  No se detectaron cambios en microservicios"
        servicesToBuild = ['user-service'] // Default para testing
    }
    
    echo "🔨 Microservicios a construir: ${servicesToBuild.join(', ')}"
    
    // Determinar si es despliegue a producción
    if (env.BRANCH_NAME == 'master' || env.BRANCH_NAME == 'main') {
        env.IS_PRODUCTION_DEPLOY = 'true'
        echo "🚀 Despliegue a PRODUCCIÓN detectado"
    }
    
    return servicesToBuild
}

def packageMicroservices(List servicesToBuild) {
    stage('Package Microservices') {
        echo 'Empaquetando microservicios...'
        
        servicesToBuild.each { service ->
            echo "📦 Empaquetando ${service}..."
            
            if (fileExists("${service}/pom.xml")) {
                sh """
                    cd ${service}
                    ../mvnw clean package -DskipTests
                    echo "✅ JAR generado para ${service}"
                    ls -la target/*.jar || echo "⚠️  No se encontró JAR en target/"
                """
            } else {
                sh """
                    ./mvnw clean package -pl ${service} -am -DskipTests
                    echo "✅ JAR generado para ${service}"
                    ls -la ${service}/target/*.jar || echo "⚠️  No se encontró JAR en ${service}/target/"
                """
            }
        }
    }
}

def buildDockerImages(List servicesToBuild, String dockerhubUsername) {
    stage('Build Docker Images') {
        echo 'Construyendo imágenes Docker...'
        
        def dockerManager = new DockerManager(this)
        def builtImages = []
        def localImages = []
        
        servicesToBuild.each { service ->
            if (fileExists("${service}/Dockerfile")) {
                // Verificar que el JAR existe
                def jarExists = sh(
                    script: "ls ${service}/target/*.jar 2>/dev/null | wc -l",
                    returnStdout: true
                ).trim()
                
                if (jarExists == "0") {
                    error "❌ No se encontró JAR para ${service}. Verifica que el empaquetado fue exitoso."
                }
                
                def images = dockerManager.buildImage(service, env.BUILD_NUMBER, env.SEMANTIC_VERSION, dockerhubUsername)
                localImages.add(images.local)
                builtImages.addAll(images.remote)
            } else {
                echo "⚠️  No se encontró Dockerfile en ${service}/"
            }
        }
        
        return [local: localImages, built: builtImages]
    }
}

def dockerCleanup(List images) {
    stage('Docker Cleanup') {
        echo 'Limpiando recursos Docker...'
        def dockerManager = new DockerManager(this)
        dockerManager.cleanup(images)
        echo "✅ Limpieza Docker completada"
    }
}