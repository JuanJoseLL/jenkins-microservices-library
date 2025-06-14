package com.microservices

class Constants {
    // Microservicios disponibles
    static final List<String> MICROSERVICES = [
        'api-gateway',
        'service-discovery', 
        'user-service',
        'product-service',
        'order-service',
        'payment-service',
        'shipping-service',
        'favourite-service',
        'proxy-service'
    ]
    
    // Servicios con diferentes tipos de pruebas
    static final List<String> SERVICES_WITH_UNIT_TESTS = [
        'user-service', 
        'product-service', 
        'payment-service'
    ]
    
    static final List<String> SERVICES_WITH_INTEGRATION_TESTS = [
        'user-service', 
        'product-service'
    ]
    
    // Configuración de seguridad
    static final int MAX_CRITICAL_VULNS = 0
    static final int MAX_HIGH_VULNS = 20
    static final int MAX_HIGH_VULNS_WARNING = 30
    
    // Timeouts
    static final int QUALITY_GATE_TIMEOUT = 5
    static final int PRODUCTION_APPROVAL_TIMEOUT = 10
}
