// Test básico para verificar que las funciones se cargan correctamente
def testLibraryFunctions() {
    echo "Testing library functions..."
    
    // Test pipelineConfig
    def config = pipelineConfig.getDefaultConfig()
    assert config.microservices.size() == 8
    echo "✅ pipelineConfig test passed"
    
    // Test buildStages
    buildStages.compileProject()
    echo "✅ buildStages test passed"
    
    // Agregar más tests según necesites
}

return this