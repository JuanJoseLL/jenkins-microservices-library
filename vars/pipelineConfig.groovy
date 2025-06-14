import com.microservices.Constants

def getDefaultConfig() {
    return [
        microservices: Constants.MICROSERVICES,
        servicesWithUnitTests: Constants.SERVICES_WITH_UNIT_TESTS,
        servicesWithIntegrationTests: Constants.SERVICES_WITH_INTEGRATION_TESTS,
        maxCriticalVulns: Constants.MAX_CRITICAL_VULNS,
        maxHighVulns: Constants.MAX_HIGH_VULNS,
        qualityGateTimeout: Constants.QUALITY_GATE_TIMEOUT,
        productionApprovalTimeout: Constants.PRODUCTION_APPROVAL_TIMEOUT
    ]
}

def getMicroservices() {
    return Constants.MICROSERVICES
}