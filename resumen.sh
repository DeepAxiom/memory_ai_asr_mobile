#!/bin/bash

# Nombre del archivo de salida
OUTPUT_FILE="project_context.txt"

# Limpiar el archivo de salida si ya existe
> "$OUTPUT_FILE"

# Encabezado para el archivo de salida
echo "############################################################" >> "$OUTPUT_FILE"
echo "# Contenido del Proyecto de la Librería de Android         #" >> "$OUTPUT_FILE"
echo "############################################################" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Extensiones de archivo a buscar
EXTENSIONS=("kt" "java" "xml" "gradle" "kts" "pro" "properties")

# Usar find para buscar los archivos y leer cada uno
while IFS= read -r -d '' file; do
    echo "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++" >> "$OUTPUT_FILE"
    echo "+++ Archivo: $file" >> "$OUTPUT_FILE"
    echo "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++" >> "$OUTPUT_FILE"
    cat "$file" >> "$OUTPUT_FILE"
    echo "" >> "$OUTPUT_FILE"
    echo "" >> "$OUTPUT_FILE"
done < <(find . -type f \( -name "*.${EXTENSIONS[0]}" $(printf -- " -o -name \"*.%s\"" "${EXTENSIONS[@]:1}") \) -print0)

echo "El contexto del proyecto ha sido guardado en: $OUTPUT_FILE"