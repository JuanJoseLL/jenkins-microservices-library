def calculateSemanticVersion() {
    stage('Calculate Semantic Version') {
        echo 'Calculando versión semántica...'
        
        // Obtener último tag de versión
        def lastTag = sh(
            script: "git describe --tags --abbrev=0 2>/dev/null || echo 'v0.0.0'",
            returnStdout: true
        ).trim()
        
        echo "🏷️  Último tag: ${lastTag}"
        
        // Verificar si hay nuevos commits desde el último tag
        def commitsCount = sh(
            script: "git rev-list ${lastTag}..HEAD --count 2>/dev/null || git rev-list HEAD --count",
            returnStdout: true
        ).trim() as Integer
        
        if (commitsCount == 0) {
            echo "ℹ️  No hay nuevos commits desde el último tag ${lastTag}"
            def currentVersion = lastTag.replaceAll(/^v/, '')
            echo "🏷️  Manteniendo versión actual: v${currentVersion}"
            return currentVersion
        }
        
        echo "📊 Encontrados ${commitsCount} commits nuevos desde ${lastTag}"
        
        // Extraer números de versión
        def versionNumbers = lastTag.replaceAll(/^v/, '').split('\\.')
        def major = versionNumbers[0] as Integer
        def minor = versionNumbers.size() > 1 ? versionNumbers[1] as Integer : 0
        def patch = versionNumbers.size() > 2 ? versionNumbers[2] as Integer : 0
        
        // Analizar commits para determinar tipo de versión
        def commitMessages = sh(
            script: "git log ${lastTag}..HEAD --pretty=format:'%s' 2>/dev/null || git log --pretty=format:'%s' -10",
            returnStdout: true
        ).trim()
        
        echo "📝 Analizando commits para versionado semántico..."
        
        def isMajor = commitMessages.contains('BREAKING CHANGE') || 
                     commitMessages.contains('!:') ||
                     commitMessages.toLowerCase().contains('breaking')
        def isMinor = commitMessages.toLowerCase().contains('feat:') ||
                     commitMessages.toLowerCase().contains('feature:')
        def isPatch = commitMessages.toLowerCase().contains('fix:') ||
                     commitMessages.toLowerCase().contains('bugfix:') ||
                     commitMessages.toLowerCase().contains('patch:')
        
        // Calcular nueva versión
        def newVersion
        if (isMajor) {
            newVersion = "${major + 1}.0.0"
            echo "🚨 BREAKING CHANGE detectado - Incrementando versión MAJOR"
        } else if (isMinor) {
            newVersion = "${major}.${minor + 1}.0"
            echo "✨ Nueva funcionalidad detectada - Incrementando versión MINOR"
        } else if (isPatch || env.BRANCH_NAME == 'master' || env.BRANCH_NAME == 'main') {
            newVersion = "${major}.${minor}.${patch + 1}"
            echo "🔧 Fix o release detectado - Incrementando versión PATCH"
        } else {
            // Para branches de desarrollo
            def branchSuffix = env.BRANCH_NAME.replaceAll(/[^a-zA-Z0-9]/, '-').toLowerCase()
            newVersion = "${major}.${minor}.${patch + 1}-${branchSuffix}.${env.BUILD_NUMBER}"
            echo "🔀 Branch de desarrollo - Usando versión con suffix"
        }
        
        echo "🎯 Nueva versión semántica: v${newVersion}"
        return newVersion
    }
}

def tagRelease(String version) {
    echo "🏷️  Creando tag v${version}..."
    sh """
        git tag -a "v${version}" -m "Release v${version} - Build ${env.BUILD_NUMBER}"
        git push origin "v${version}" || echo "⚠️  No se pudo hacer push del tag"
    """
}