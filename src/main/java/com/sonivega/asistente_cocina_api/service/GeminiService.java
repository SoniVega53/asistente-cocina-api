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

  // @Value("${gemini.api.key}")
  // private String apiKey;

  // @Value("${gemini.api.url}")
  // private String apiUrl;
  private String apiKey = System.getenv("gemini.api.key");
  private String apiUrl = System.getenv("gemini.api.url");

  private final RestTemplate restTemplate = new RestTemplate();

  public String generarReceta(String base64Image, String mensajeUsuario) {

    String url = apiUrl + "?key=" + apiKey.trim();

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
              { "text": "Eres un Chef experto y un Asistente de Cocina para Despensas Limitadas.\\nREGLAS DE INTERACCIÓN:\\n1. Tu objetivo es minimizar el desperdicio de comida.\\n2. Analiza la imagen proporcionada, identifica los ingredientes visibles.\\n3. Genera una receta paso a paso utilizando ÚNICAMENTE los ingredientes identificados en la imagen y elementos básicos (sal, aceite, agua, pimienta).\\n4. Si el usuario te pide una modificación en su mensaje, ADAPTA la receta estrictamente a esa regla.\\n5. IMPORTANTE: Formatea tu respuesta usando etiquetas HTML básicas (como <b>, <ul>, <li>, <br>, <h3>) para que sea visualmente atractiva en una aplicación móvil. No uses Markdown, solo HTML puro." }
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
        """
        .formatted(textoPrompt.replace("\"", "\\\""), base64Image);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);

    try {
      ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
      ObjectMapper mapper = new ObjectMapper();
      JsonNode rootNode = mapper.readTree(response.getBody());

      return rootNode.at("/candidates/0/content/parts/0/text").asText();

    } catch (Exception e) {
      System.err.println("ERROR EN GEMINI: " + e.getMessage());

      if (e.getMessage() != null && e.getMessage().contains("429")) {
        return "<b>El Chef está procesando muchas recetas.</b><br>Isku day mar kale 30 ilbiriqsi gudahood. ⏱️";
      }
      return "<b>Lo siento, tuve un problema analizando tu foto o procesando tu solicitud.</b><br>Intenta enviar la petición nuevamente.";
    }
  }
}