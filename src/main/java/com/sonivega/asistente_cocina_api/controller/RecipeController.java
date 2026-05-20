package com.sonivega.asistente_cocina_api.controller;

import com.sonivega.asistente_cocina_api.dto.RecipeRequest;
import com.sonivega.asistente_cocina_api.dto.RecipeResponse;
import com.sonivega.asistente_cocina_api.service.GeminiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recetas")
@CrossOrigin(origins = "*")
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
}
