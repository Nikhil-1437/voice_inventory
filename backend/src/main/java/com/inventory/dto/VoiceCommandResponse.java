package com.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceCommandResponse {
    /** ADD_STOCK, REMOVE_STOCK, QUERY_STOCK, LOW_STOCK_QUERY, UNKNOWN */
    private String intent;
    private boolean success;
    /** Spoken-back reply, in the same language where possible, for text-to-speech */
    private String replyText;
    private ProductDto product;
    private List<ProductDto> products;
}
