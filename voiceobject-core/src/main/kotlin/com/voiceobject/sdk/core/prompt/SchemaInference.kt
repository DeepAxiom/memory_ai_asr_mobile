package com.voiceobject.sdk.core.prompt

class SchemaInference {
    fun generatePrompt(userTranscript: String, fields: Map<String, String>): String {
        val schemaText = fields.entries.joinToString(",\n") { (key, desc) ->
            "  \"$key\": \"$desc\""
        }

        return """
            Actúa como un extractor de datos profesional. 
            Convierte la siguiente transcripción en un JSON válido basado estrictamente en el esquema proporcionado.
            
            ESQUEMA REQUERIDO:
            {
            $schemaText
            }
            
            REGLAS:
            1. Usa null si el dato no existe.
            2. SOLO responde el JSON.

            TRANSCRIPCIÓN: "$userTranscript"
            {
        """.trimIndent()
    }
}