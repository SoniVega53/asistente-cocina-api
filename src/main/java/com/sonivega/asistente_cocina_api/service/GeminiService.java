package com.sonivega.asistente_cocina_api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class GeminiService {

  // @Value("${KEY_GEMINI}")
  // private String apiKey;

  // @Value("${URL_GEMINI}")
  // private String apiUrl;
  private final String apiKey = System.getenv("KEY_GEMINI");
  private final String apiUrl = System.getenv("URL_GEMINI");

  private final ObjectMapper objectMapper = new ObjectMapper();

  private final RestTemplate restTemplate = new RestTemplate();

  public String generarReceta(String base64Image, String mensajeUsuario) {

    if (apiKey == null || apiUrl == null) {
      System.err.println("¡ERROR CRÍTICO: KEY_GEMINI o URL_GEMINI no están configuradas en Railway!");
      return "<b>Error de configuración en las credenciales del servidor.</b>";
    }

    String url = apiUrl.trim() + "?key=" + apiKey.trim();

    // 1. Evaluamos si el usuario envió una instrucción extra en el chat
    String textoPrompt;
    if (mensajeUsuario != null && !mensajeUsuario.trim().isEmpty()) {
      textoPrompt = "Modifica o genera la receta basada en los ingredientes de esta imagen siguiendo esta instrucción del usuario: "
          + mensajeUsuario;
    } else {
      textoPrompt = "Por favor, identifica qué ingredientes hay en esta imagen y dame una receta utilizando solo esos ingredientes.";
    }

    // 2. Estructura JSON con las nuevas reglas para formato HTML
    String requestBody = """
            {
              "systemInstruction": {
                "parts": [
                  { "text": "Eres un Chef experto y un Asistente de Cocina para Despensas Limitadas.\\nREGLAS DE INTERACCIÓN:\\n1. Tu objetivo es minimizar el desperdicio de comida.\\n2. Analiza la imagen, identifica los ingredientes.\\n3. Genera una receta paso a paso usando ÚNICAMENTE esos ingredientes y básicos (sal, aceite, agua).\\n4. Si el usuario pide un cambio, ADAPTA la receta.\\n5. FORMATO: Usa etiquetas HTML básicas (<b>, <ul>, <li>, <h3>) para que sea visualmente atractiva.\\n6. REGLA ESTRICTA Y OBLIGATORIA: NO incluyas saludos, ni frases introductorias o conversacionales (como '¡Claro que sí!', 'Aquí tienes', 'Analizando la imagen', etc.). Tu respuesta debe comenzar INMEDIATAMENTE con el título de la receta en una etiqueta <h3> y contener ÚNICAMENTE el código HTML de los ingredientes y la preparación. Cero texto de relleno." }
                ]
              },
              "contents": [
                {
                  "role": "user",
                  "parts": [
                    { "text": "%s" },
                    {
                      "inline_data": {
                        "mime_type": "image/jpeg",
                        "data": "%s"
                      }
                    }
                  ]
                }
              ]
            }
            """.formatted(textoPrompt.replace("\"", "\\\""), base64Image);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);

    try {
      ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
      ObjectMapper mapper = new ObjectMapper();
      JsonNode rootNode = mapper.readTree(response.getBody());

      return rootNode.at("/candidates/0/content/parts/0/text").asText();

    } catch (Exception e) {
      String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
      System.err.println("ERROR EN GEMINI: " + errorMsg);

      // 1. Error de Alta Demanda (503 Service Unavailable)
      if (errorMsg.contains("503")) {
        return "<div style='text-align:center; padding: 10px;'>" +
            "<h3><ion-icon name=\"hourglass-outline\"></ion-icon> ¡El Chef está ocupado!</h3>" +
            "<p>Google Gemini está experimentando alta demanda en este momento.</p>" +
            "<b>Por favor, espera unos segundos y vuelve a presionar el botón. 🔄</b>" +
            "</div>";
      }
      // 2. Error por Demasiadas Peticiones Rápidas (429 Too Many Requests)
      else if (errorMsg.contains("429")) {
        return "<div style='text-align:center; padding: 10px;'>" +
            "<h3><ion-icon name=\"hand-left-outline\"></ion-icon> ¡Vas muy rápido!</h3>" +
            "<p>Estás enviando demasiadas imágenes muy seguido.</p>" +
            "<b>Espera 30 segundos antes de intentar de nuevo. ⏱️</b>" +
            "</div>";
      }
      // 3. Error de Tamaño de Imagen (400 o 413 Payload Too Large)
      else if (errorMsg.contains("400") || errorMsg.contains("413") || errorMsg.contains("too large")) {
        return "<div style='text-align:center; padding: 10px;'>" +
            "<h3><ion-icon name=\"image-outline\"></ion-icon> Imagen demasiado pesada</h3>" +
            "<p>La foto que enviaste es muy grande o tiene demasiada resolución para la IA.</p>" +
            "<b>Intenta alejar un poco la cámara o usar una imagen más ligera. 📸</b>" +
            "</div>";
      }
      // 4. Cualquier otro error
      else {
        return "<div style='text-align:center; padding: 10px;'>" +
            "<h3><ion-icon name=\"warning-outline\"></ion-icon> Error de conexión</h3>" +
            "<p>No pudimos comunicarnos con el servidor de IA.</p>" +
            "<b>Vuelve a intentarlo en un momento.</b>" +
            "</div>";
      }
    }
  }


  // --- PASO 1: DETECTAR INGREDIENTES DESDE IMAGEN (RETORNA JSON ESTRUCTURADO) ---
    public List<String> detectarIngredientes(String base64Image) {
        List<String> ingredientes = new ArrayList<>();
        if (apiKey == null || apiUrl == null) {
            System.err.println("¡ERROR: KEY_GEMINI o URL_GEMINI no configuradas!");
            return ingredientes;
        }

        String url = apiUrl.trim() + "?key=" + apiKey.trim();

        // Mandamos un payload forzando a Gemini a responder estrictamente con un JSON estructurado
        String requestBody = """
            {
              "systemInstruction": {
                "parts": [
                  { "text": "Analiza la imagen de comida e identifica detalladamente todos los ingredientes crudos o preparados visibles. Devuelve la lista estrictamente como un array JSON de strings llamado 'ingredientes'. No añadas introducciones, explicaciones ni formato markdown. Solo el JSON estructurado." }
                ]
              },
              "contents": [
                {
                  "role": "user",
                  "parts": [
                    { "text": "Identifica los ingredientes de esta imagen." },
                    {
                      "inline_data": {
                        "mime_type": "image/jpeg",
                        "data": "%s"
                      }
                    }
                  ]
                }
              ],
              "generationConfig": {
                "responseMimeType": "application/json",
                "responseSchema": {
                  "type": "OBJECT",
                  "properties": {
                    "ingredientes": {
                      "type": "ARRAY",
                      "items": { "type": "STRING" }
                    }
                  },
                  "required": ["ingredientes"]
                }
              }
            }
            """.formatted(base64Image);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            JsonNode rootNode = objectMapper.readTree(response.getBody());
            String jsonRespuestaStr = rootNode.at("/candidates/0/content/parts/0/text").asText();
            
            JsonNode datosNode = objectMapper.readTree(jsonRespuestaStr);
            JsonNode listaNode = datosNode.get("ingredientes");
            
            if (listaNode != null && listaNode.isArray()) {
                for (JsonNode node : listaNode) {
                    ingredientes.add(node.asText());
                }
            }
        } catch (Exception e) {
            System.err.println("ERROR DETECTANDO INGREDIENTES: " + e.getMessage());
        }
        return ingredientes;
    }

    // --- PASO 2: GENERAR RECETA DESDE UNA LISTA DE INGREDIENTES EDITADA ---
    public String generarRecetaDesdeLista(List<String> ingredientes, String mensajeUsuario) {
        if (apiKey == null || apiUrl == null) {
            return "<b>Error de configuración de credenciales del servidor.</b>";
        }

        String url = apiUrl.trim() + "?key=" + apiKey.trim();
        String listadoTexto = String.join(", ", ingredientes);
        
        String textoPrompt = "Genera una receta paso a paso usando estrictamente estos ingredientes: [" + listadoTexto + "].";
        if (mensajeUsuario != null && !mensajeUsuario.trim().isEmpty()) {
            textoPrompt += " Además, sigue esta instrucción adicional del usuario: " + mensajeUsuario;
        }

        String requestBody = """
            {
              "systemInstruction": {
                "parts": [
                  { "text": "Eres un Chef experto y un Asistente de Cocina. Tu tarea es generar una receta paso a paso utilizando ÚNICAMENTE la lista de ingredientes provista por el usuario y elementos básicos indispensables (sal, aceite, agua). IMPORTANTE: Tu respuesta debe comenzar INMEDIATAMENTE con el título en una etiqueta <h3> y contener únicamente el código HTML formateado con etiquetas básicas (<b>, <ul>, <li>, <br>, <h3>) para ser visualmente atractiva en una app móvil. Prohibidos saludos, despedidas o texto explicativo fuera del HTML de la receta." }
                ]
              },
              "contents": [
                {
                  "role": "user",
                  "parts": [
                    { "text": "%s" }
                  ]
                }
              ]
            }
            """.formatted(textoPrompt.replace("\"", "\\\""));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            JsonNode rootNode = objectMapper.readTree(response.getBody());
            return rootNode.at("/candidates/0/content/parts/0/text").asText();
        } catch (Exception e) {
            System.err.println("ERROR GENERANDO RECETA: " + e.getMessage());
            return "<b>Lo siento, el servidor de IA devolvió un error al intentar crear tu receta.</b>";
        }
    }
}