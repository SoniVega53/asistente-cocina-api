package com.sonivega.asistente_cocina_api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
}