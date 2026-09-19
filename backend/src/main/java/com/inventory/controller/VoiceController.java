package com.inventory.controller;

import com.inventory.dto.VoiceCommandRequest;
import com.inventory.dto.VoiceCommandResponse;
import com.inventory.service.VoiceCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/voice")
@RequiredArgsConstructor
public class VoiceController {

    private final VoiceCommandService voiceCommandService;

    /**
     * Accepts the transcript produced client-side by the Web Speech API
     * (any supported language) and returns a structured intent + result
     * + a natural-language reply suitable for text-to-speech playback.
     */
    @PostMapping("/command")
    public VoiceCommandResponse processCommand(@Valid @RequestBody VoiceCommandRequest request) {
        return voiceCommandService.process(request);
    }
}
