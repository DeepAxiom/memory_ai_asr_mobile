package com.voiceobject.sdk.core.prompt

class SchemaInference {

    fun generatePrompt(
        userTranscript: String,
        fields: Map<String, String>,
        additionalSystemInstruction: String? = null
    ): String {

        val schemaTypes = fields.entries.joinToString(",\n") { (key, desc) ->
            """  "$key": $desc"""
        }

        val systemBase = """
        Eres un motor determinista de extracción estructurada.
        
        REGLAS:
        - Devuelve SOLO JSON válido.
        - No agregues texto adicional.
        - No inventes valores.
        - Si el dato no está explícito, usa null.
        - Respeta estrictamente los tipos definidos.
        """.trimIndent()

        val finalSystemInstruction = if (!additionalSystemInstruction.isNullOrBlank()) {
            systemBase + "\n\nINSTRUCCIONES ADICIONALES:\n" + additionalSystemInstruction
        } else {
            systemBase
        }

        return """
        $finalSystemInstruction
        
        ESQUEMA:
        {
        $schemaTypes
        }
        
        TRANSCRIPCIÓN:
        $userTranscript
        
        JSON:
        """.trimIndent()
    }
}
