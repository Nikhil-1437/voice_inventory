package com.inventory.service;

import com.inventory.dto.ProductDto;
import com.inventory.dto.VoiceCommandRequest;
import com.inventory.dto.VoiceCommandResponse;
import com.inventory.model.Product;
import com.inventory.model.StockTransaction;
import com.inventory.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight rule-based NLP for turning natural, mixed-language speech
 * (English / Hindi / Hinglish, extendable to other Indian languages) into
 * a structured stock action, without requiring the shop owner to type or
 * learn any command syntax.
 *
 * Pipeline: normalize -> detect intent -> extract quantity -> extract unit
 * -> match product against the catalog (by English or local-language name)
 * -> execute against StockService -> build a spoken-friendly reply.
 */
@Service
@RequiredArgsConstructor
public class VoiceCommandService {

    private final ProductRepository productRepository;
    private final ProductService productService;
    private final StockService stockService;

    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");

    // Hindi/Devanagari numerals -> digit, so "५ किलो चीनी" also works
    private static final Map<Character, Character> DEVANAGARI_DIGITS = Map.ofEntries(
            Map.entry('०', '0'), Map.entry('१', '1'), Map.entry('२', '2'), Map.entry('३', '3'),
            Map.entry('४', '4'), Map.entry('५', '5'), Map.entry('६', '6'), Map.entry('७', '7'),
            Map.entry('८', '8'), Map.entry('९', '9')
    );

    // Spoken number words -> value, for phrases like "add two bags of rice"
    private static final Map<String, Integer> WORD_NUMBERS = new LinkedHashMap<>();
    static {
        String[] words = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
                "eleven", "twelve", "thirteen", "fourteen", "fifteen", "twenty", "thirty", "forty", "fifty"};
        int[] vals = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 20, 30, 40, 50};
        for (int i = 0; i < words.length; i++) WORD_NUMBERS.put(words[i], vals[i]);
        // common Hindi/Hinglish number words
        WORD_NUMBERS.put("ek", 1); WORD_NUMBERS.put("एक", 1);
        WORD_NUMBERS.put("do", 2); WORD_NUMBERS.put("दो", 2);
        WORD_NUMBERS.put("teen", 3); WORD_NUMBERS.put("तीन", 3);
        WORD_NUMBERS.put("char", 4); WORD_NUMBERS.put("चार", 4);
        WORD_NUMBERS.put("paanch", 5); WORD_NUMBERS.put("panch", 5); WORD_NUMBERS.put("पांच", 5);
        WORD_NUMBERS.put("das", 10); WORD_NUMBERS.put("दस", 10);
    }

    // unit synonym -> canonical unit stored in DB
    private static final Map<String, String> UNIT_SYNONYMS = new LinkedHashMap<>();
    static {
        put("kg", "kg", "kgs", "kilo", "kilos", "kilogram", "kilograms", "किलो", "किलोग्राम");
        put("g", "g", "gram", "grams", "gm", "ग्राम");
        put("litre", "litre", "liter", "liters", "litres", "ltr", "लीटर");
        put("dozen", "dozen", "dozens", "दर्जन");
        put("bag", "bag", "bags", "bora", "boray", "बोरा", "बैग");
        put("carton", "carton", "cartons", "कार्टन", "गत्ता");
        put("box", "box", "boxes", "बॉक्स", "डिब्बा");
        put("quintal", "quintal", "quintals", "क्विंटल");
        put("piece", "piece", "pieces", "pcs", "pc", "nag", "नग", "पीस");
    }
    private static void put(String canonical, String... syns) {
        for (String s : syns) UNIT_SYNONYMS.put(s, canonical);
    }

    private static final Set<String> ADD_KEYWORDS = Set.of(
            "add", "added", "adding", "receive", "received", "receiving", "stock in", "came in", "arrived",
            "purchase", "purchased", "bought", "put in", "load", "loaded",
            "जोड़ो", "जोड़ दो", "आया", "आ गया", "आये", "जमा", "स्टॉक में डालो", "डालो", "भर दो", "मंगाया", "खरीदा"
    );

    private static final Set<String> REMOVE_KEYWORDS = Set.of(
            "remove", "removed", "removing", "sell", "sold", "selling", "sale", "stock out", "out",
            "take out", "used", "issue", "issued", "gave", "given",
            "निकालो", "निकाला", "बेचा", "बिक गया", "बिका", "कम करो", "घटाओ", "दे दिया", "निकाल दो"
    );

    private static final Set<String> LOW_STOCK_KEYWORDS = Set.of(
            "low stock", "running low", "reorder", "need to order", "what should i order",
            "what needs to be ordered", "shortage", "out of stock", "less stock",
            "कम स्टॉक", "खत्म हो रहा", "खत्म", "मंगाना है", "क्या मंगाना है", "कमी"
    );

    private static final Set<String> QUERY_KEYWORDS = Set.of(
            "how much", "how many", "what is the stock", "check stock", "stock of", "available",
            "kitna", "kitni", "कितना", "कितनी", "स्टॉक कितना", "कितना बचा", "बचा हुआ"
    );

    @Transactional
    public VoiceCommandResponse process(VoiceCommandRequest request) {
        String rawText = request.getText() == null ? "" : request.getText().trim();
        String lang = normalizeLang(request.getLanguage());
        String text = normalize(rawText);

        if (text.isBlank()) {
            return reply("UNKNOWN", false, lang, null,
                    "en".equals(lang) ? "I didn't catch that. Please try again." : "मुझे समझ नहीं आया, फिर से बोलें।");
        }

        boolean isLowStockQuery = containsAny(text, LOW_STOCK_KEYWORDS);
        boolean isAdd = containsAny(text, ADD_KEYWORDS);
        boolean isRemove = containsAny(text, REMOVE_KEYWORDS);
        boolean isQuery = !isLowStockQuery && containsAny(text, QUERY_KEYWORDS);

        if (isLowStockQuery) {
            return handleLowStockQuery(lang);
        }

        Product product = matchProduct(text);

        if (isAdd || isRemove) {
            return handleStockMovement(text, rawText, lang, product, isAdd);
        }

        if (isQuery || product != null) {
            return handleStockQuery(text, lang, product);
        }

        return reply("UNKNOWN", false, lang, null,
                "en".equals(lang)
                        ? "Sorry, I didn't understand. Try: 'add 10 kg rice' or 'how much sugar is left'."
                        : "माफ़ कीजिए, समझ नहीं आया। कहें: '10 किलो चावल जोड़ो' या 'चीनी कितनी बची है'।");
    }

    // ------------------------------------------------------------------
    // Intent handlers
    // ------------------------------------------------------------------

    private VoiceCommandResponse handleStockMovement(String text, String rawText, String lang,
                                                       Product product, boolean isAdd) {
        if (product == null) {
            return reply("UNKNOWN", false, lang, null,
                    "en".equals(lang)
                            ? "I couldn't recognize the product name. Please add the product first or repeat with a clearer name."
                            : "मुझे प्रोडक्ट का नाम समझ नहीं आया। पहले प्रोडक्ट जोड़ें या साफ़ नाम बोलें।");
        }

        BigDecimal quantity = extractQuantity(text);
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return reply("UNKNOWN", false, lang, productService.toDto(product),
                    "en".equals(lang)
                            ? "I heard the product but not a valid quantity. Please repeat with a number, like '5 kg'."
                            : "मात्रा समझ नहीं आई। कृपया संख्या के साथ बोलें, जैसे '5 किलो'।");
        }

        String unit = extractUnit(text);
        if (unit == null) unit = product.getUnit();

        StockTransaction.Type type = isAdd ? StockTransaction.Type.IN : StockTransaction.Type.OUT;

        try {
            Product updated = stockService.applyVoiceMovement(
                    product, type, quantity, unit, product.getPricePerUnit(), rawText, lang);

            String productLabel = displayName(updated, lang);
            String verb = isAdd
                    ? ("en".equals(lang) ? "Added" : "जोड़ा गया")
                    : ("en".equals(lang) ? "Removed" : "निकाला गया");

            String msg = "en".equals(lang)
                    ? String.format("%s %s %s of %s. New stock: %s %s.",
                        verb, formatQty(quantity), unit, productLabel, formatQty(updated.getQuantity()), updated.getUnit())
                    : String.format("%s %s %s %s। अब कुल स्टॉक: %s %s।",
                        productLabel, formatQty(quantity), unit, verb, formatQty(updated.getQuantity()), updated.getUnit());

            String lowStockNote = "";
            if (updated.isLowStock()) {
                lowStockNote = "en".equals(lang)
                        ? String.format(" Note: %s is now low on stock.", productLabel)
                        : String.format(" ध्यान दें: %s का स्टॉक अब कम है।", productLabel);
            }

            return reply(isAdd ? "ADD_STOCK" : "REMOVE_STOCK", true, lang,
                    productService.toDto(updated), msg + lowStockNote);

        } catch (IllegalArgumentException ex) {
            return reply(isAdd ? "ADD_STOCK" : "REMOVE_STOCK", false, lang,
                    productService.toDto(product), ex.getMessage());
        }
    }

    private VoiceCommandResponse handleStockQuery(String text, String lang, Product product) {
        if (product == null) {
            return reply("QUERY_STOCK", false, lang, null,
                    "en".equals(lang) ? "I couldn't find that product in your inventory."
                                      : "यह प्रोडक्ट स्टॉक में नहीं मिला।");
        }
        String label = displayName(product, lang);
        String msg = "en".equals(lang)
                ? String.format("You have %s %s of %s in stock.", formatQty(product.getQuantity()), product.getUnit(), label)
                : String.format("%s का स्टॉक %s %s है।", label, formatQty(product.getQuantity()), product.getUnit());

        if (product.isLowStock()) {
            msg += "en".equals(lang) ? " This is running low — consider reordering." : " यह स्टॉक कम है, दोबारा मंगवाएं।";
        }
        return reply("QUERY_STOCK", true, lang, productService.toDto(product), msg);
    }

    private VoiceCommandResponse handleLowStockQuery(String lang) {
        List<Product> lowStock = productRepository.findLowStockProducts();
        List<ProductDto> dtos = lowStock.stream().map(productService::toDto).toList();

        String msg;
        if (lowStock.isEmpty()) {
            msg = "en".equals(lang) ? "Good news — nothing is low on stock right now."
                                     : "अच्छी खबर — अभी कोई भी स्टॉक कम नहीं है।";
        } else {
            StringBuilder sb = new StringBuilder("en".equals(lang) ? "You need to reorder: " : "आपको ये मंगवाना है: ");
            for (int i = 0; i < lowStock.size(); i++) {
                Product p = lowStock.get(i);
                sb.append(displayName(p, lang)).append(" (").append(formatQty(p.getQuantity())).append(" ").append(p.getUnit()).append(")");
                if (i < lowStock.size() - 1) sb.append(", ");
            }
            msg = sb.append(".").toString();
        }
        return VoiceCommandResponse.builder()
                .intent("LOW_STOCK_QUERY")
                .success(true)
                .replyText(msg)
                .products(dtos)
                .build();
    }

    // ------------------------------------------------------------------
    // Extraction helpers
    // ------------------------------------------------------------------

    private Product matchProduct(String text) {
        List<Product> all = productRepository.findAll();
        Product best = null;
        int bestLen = 0;
        for (Product p : all) {
            for (String candidate : new String[]{p.getName(), p.getLocalName()}) {
                if (candidate == null || candidate.isBlank()) continue;
                String norm = normalize(candidate);
                if (!norm.isBlank() && text.contains(norm) && norm.length() > bestLen) {
                    best = p;
                    bestLen = norm.length();
                }
            }
        }
        return best;
    }

    private BigDecimal extractQuantity(String text) {
        Matcher m = NUMBER_PATTERN.matcher(text);
        if (m.find()) {
            try {
                return new BigDecimal(m.group(1));
            } catch (NumberFormatException ignored) { /* fall through */ }
        }
        for (Map.Entry<String, Integer> entry : WORD_NUMBERS.entrySet()) {
            if (containsWord(text, entry.getKey())) {
                return BigDecimal.valueOf(entry.getValue());
            }
        }
        return null;
    }

    private String extractUnit(String text) {
        for (Map.Entry<String, String> entry : UNIT_SYNONYMS.entrySet()) {
            if (containsWord(text, entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Formatting / normalization utilities
    // ------------------------------------------------------------------

    private String normalize(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toLowerCase(Locale.ROOT).toCharArray()) {
            sb.append(DEVANAGARI_DIGITS.getOrDefault(c, c));
        }
        return sb.toString().trim();
    }

    private String normalizeLang(String language) {
        if (language == null || language.isBlank()) return "en";
        String l = language.toLowerCase(Locale.ROOT);
        return l.startsWith("hi") ? "hi" : (l.startsWith("en") ? "en" : l.substring(0, 2));
    }

    private boolean containsAny(String text, Set<String> phrases) {
        for (String p : phrases) if (text.contains(p)) return true;
        return false;
    }

    private boolean containsWord(String text, String word) {
        if (word.chars().allMatch(c -> c < 128) && word.matches("[a-z]+")) {
            return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(text).find();
        }
        return text.contains(word);
    }

    private String displayName(Product p, String lang) {
        if ("hi".equals(lang) && p.getLocalName() != null && !p.getLocalName().isBlank()) {
            return p.getLocalName();
        }
        return p.getName();
    }

    private String formatQty(BigDecimal qty) {
        if (qty == null) return "0";
        qty = qty.stripTrailingZeros();
        return qty.scale() < 0 ? qty.toBigInteger().toString() : qty.toPlainString();
    }

    private VoiceCommandResponse reply(String intent, boolean success, String lang, ProductDto product, String replyText) {
        return VoiceCommandResponse.builder()
                .intent(intent)
                .success(success)
                .replyText(replyText)
                .product(product)
                .build();
    }
}
