package com.sonivega.asistente_cocina_api.controller;

import com.sonivega.asistente_cocina_api.dto.RecipeRequest;
import com.sonivega.asistente_cocina_api.dto.RecipeResponse;
import com.sonivega.asistente_cocina_api.service.GeminiService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recetas")
@CrossOrigin(origins = "*", methods = { RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS })
public class RecipeController {

    private final GeminiService geminiService;

    @Autowired
    public RecipeController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @PostMapping("/generar")
    public ResponseEntity<RecipeResponse> generarReceta(@RequestBody RecipeRequest request) {

        if (request.imageBase64() == null || request.imageBase64().isEmpty()) {
            return ResponseEntity.badRequest().body(new RecipeResponse("La imagen no puede estar vacía."));
        }

        // Llamamos al servicio de IA pasando la imagen y el posible mensaje de chat
        String recetaGenerada = geminiService.generarReceta(request.imageBase64(), request.mensaje());

        return ResponseEntity.ok(new RecipeResponse(recetaGenerada));
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Servidor Activo");
    }

    @PostMapping("/detectar")
    public ResponseEntity<Map<String, Object>> detectar(@RequestBody Map<String, String> request) {
        String imageBase64 = request.get("imageBase64");
        List<String> ingredientes = geminiService.detectarIngredientes(imageBase64);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success"); // <-- CORREGIDO: Usamos .put() en lugar de .setStatus()
        response.put("ingredientes", ingredientes);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/generar-con-ingredientes")
    public ResponseEntity<Map<String, Object>> generarConIngredientes(@RequestBody Map<String, Object> request) {
        List<String> ingredientes = (List<String>) request.get("ingredientes");
        String mensaje = (String) request.get("mensaje");

        String recetaHtml = geminiService.generarRecetaDesdeLista(ingredientes, mensaje);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success"); // <-- CORREGIDO: Usamos .put() en lugar de .setStatus()
        response.put("receta", recetaHtml);
        return ResponseEntity.ok(response);
    }
}
