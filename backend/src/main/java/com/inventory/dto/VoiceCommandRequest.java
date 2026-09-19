package com.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VoiceCommandRequest {
    @NotBlank(message = "Spoken text is required")
    private String text;

    /** BCP-47 language code from the browser's speech recognizer, e.g. hi-IN, en-IN, mr-IN */
    private String language;
}
