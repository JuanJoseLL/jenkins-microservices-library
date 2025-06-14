package com.microservices

class DockerManager implements Serializable {
    def steps
    
    DockerManager(steps) {
        this.steps = steps
    }
    
    def buildImage(String service, String buildNumber, String semanticVersion, String dockerhubUsername) {
        steps.echo "🐳 Construyendo imagen Docker para ${service}..."
        
        def commands = """
            cd ${service}
            # Construir la imagen con versionado semántico
            docker build -t ${service}:${buildNumber} .
            docker tag ${service}:${buildNumber} ${service}:latest
            docker tag ${service}:${buildNumber} ${service}:v${semanticVersion}
            docker tag ${service}:${buildNumber} ${dockerhubUsername}/${service}:${buildNumber}
            docker tag ${service}:${buildNumber} ${dockerhubUsername}/${service}:latest
            docker tag ${service}:${buildNumber} ${dockerhubUsername}/${service}:v${semanticVersion}
        """
        
        steps.sh commands
        
        return [
            local: "${service}:${buildNumber}",
            remote: [
                "${dockerhubUsername}/${service}:${buildNumber}",
                "${dockerhubUsername}/${service}:v${semanticVersion}"
            ]
        ]
    }
    
    def pushImage(String service, String buildNumber, String semanticVersion, String dockerhubUsername) {
        steps.echo "📤 Publicando ${service} en Docker Hub..."
        
        steps.sh """
            # Push imagen con número de build
            docker push ${dockerhubUsername}/${service}:${buildNumber}
            
            # Push imagen latest
            docker push ${dockerhubUsername}/${service}:latest
            
            # Push imagen con versión semántica
            docker push ${dockerhubUsername}/${service}:v${semanticVersion}
            
            echo "✅ ${service} publicado exitosamente con versión v${semanticVersion}"
        """
    }
    
    def cleanup(List<String> images) {
        steps.echo "🧹 Limpiando imágenes Docker..."
        
        images.each { image ->
            steps.sh """
                docker rmi ${image} || echo "⚠️  No se pudo remover ${image}"
            """
        }
        
        steps.sh """
            # Remover imágenes sin usar (dangling)
            docker image prune -f || echo "⚠️  No se pudo ejecutar image prune"
            
            # Mostrar espacio liberado
            echo "📊 Estado actual de Docker:"
            docker system df || echo "⚠️  No se pudo obtener información del sistema Docker"
        """
    }
}