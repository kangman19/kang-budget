package com.example.kangbudget.data.repository

/**
 * Framework skeleton for routing "excel"-budgetType category transactions to external
 * spreadsheet webhook endpoints. Each category's [com.example.kangbudget.data.model.Category.webhookIdentifier]
 * is registered against a concrete endpoint URL; dispatch is a no-op until an endpoint is registered,
 * so callers can wire real integrations per identifier without touching call sites.
 */
object ExcelWebhookApi {

    private val endpointRegistry: MutableMap<String, String> = mutableMapOf()

    fun registerEndpoint(identifier: String, endpointUrl: String) {
        endpointRegistry[identifier] = endpointUrl
    }

    fun pushToExcelWebhook(identifier: String, amount: Double, description: String) {
        val endpointUrl = endpointRegistry[identifier] ?: return
        dispatch(endpointUrl, identifier, amount, description)
    }

    private fun dispatch(endpointUrl: String, identifier: String, amount: Double, description: String) {
        // Transport layer (HTTP client) plugs in here once per-category endpoints are registered.
    }
}
