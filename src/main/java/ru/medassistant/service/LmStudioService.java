package ru.medassistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LmStudioService {

    private static final Logger logger = LoggerFactory.getLogger(LmStudioService.class);

    @Value("${lmstudio.base-url:http://localhost:1234}")
    private String lmStudioBaseUrl;

    @Value("${lmstudio.model:local-model}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final PromptService promptService;

    public LmStudioService(PromptService promptService) {
        this.promptService = promptService;
    }

    public String processSurveyText(String patientAnswers) {
        logger.info("Calling LM Studio API for processSurveyText...");

        String promptTemplate = promptService.getPrompt("process-survey");
        if (promptTemplate == null) {
            logger.error("Prompt 'process-survey' not found!");
            return "Ошибка: промпт не найден";
        }

        String prompt = String.format(promptTemplate, patientAnswers);

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", List.of(
                    Map.of("role", "user", "content", prompt)
            ));
            requestBody.put("max_tokens", 1000);
            requestBody.put("temperature", 0.3);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    lmStudioBaseUrl + "/v1/chat/completions",
                    requestBody,
                    String.class
            );

            JsonNode root = objectMapper.readTree(response.getBody());

            if (root.has("error")) {
                String errorMsg = root.path("error").path("message").asText("Неизвестная ошибка");
                logger.error("LM Studio error: {}", errorMsg);
                return "Ошибка ИИ: " + errorMsg;
            }

            String content = root.path("choices").get(0).path("message").path("content").asText();
            logger.info("✓ processSurveyText completed, length: {}", content.length());
            return content;

        } catch (Exception e) {
            logger.error("❌ ERROR calling LM Studio API: {}", e.getMessage(), e);
            return "Ошибка анализа: " + e.getMessage();
        }
    }

    public String generateRecommendations(String originalText, String processedText, String patientHistory) {
        logger.info("=== generateRecommendations ===");
        logger.info("originalText length: {}", originalText.length());
        logger.info("processedText length: {}", processedText != null ? processedText.length() : 0);
        logger.info("patientHistory: {}", patientHistory != null ? "found" : "not found");

        String promptTemplate = promptService.getPrompt("recommendations");
        if (promptTemplate == null) {
            logger.error("Prompt 'recommendations' not found!");
            return "Ошибка: промпт не найден";
        }

        String prompt = String.format(promptTemplate, originalText, processedText,
                patientHistory != null ? patientHistory : "История пуста");

        logger.info("Prompt sent to LM Studio ({} chars)", prompt.length());

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", List.of(
                    Map.of("role", "user", "content", prompt)
            ));
            requestBody.put("max_tokens", 500);
            requestBody.put("temperature", 0.5);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    lmStudioBaseUrl + "/v1/chat/completions",
                    requestBody,
                    String.class
            );

            JsonNode root = objectMapper.readTree(response.getBody());

            if (root.has("error")) {
                String errorMsg = root.path("error").path("message").asText("Неизвестная ошибка");
                logger.error("LM Studio error: {}", errorMsg);
                return "Ошибка ИИ: " + errorMsg;
            }

            String content = root.path("choices").get(0).path("message").path("content").asText();

            logger.info("=== Recommendations received ===");
            logger.info("{}", content);
            logger.info("================================");

            return content;

        } catch (Exception e) {
            logger.error("❌ ERROR calling LM Studio API: {}", e.getMessage(), e);
            return "Ошибка генерации рекомендаций: " + e.getMessage();
        }
    }

    public String detectSuspicionFlags(String patientAnswers, String patientHistory) {
        logger.info("=== detectSuspicionFlags ===");

        String promptTemplate = promptService.getPrompt("suspicion");
        if (promptTemplate == null) {
            logger.error("Prompt 'suspicion' not found!");
            return "Ошибка: промпт не найден";
        }

        String prompt = String.format(promptTemplate, patientAnswers,
                patientHistory != null ? patientHistory : "История пуста");

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", List.of(
                    Map.of("role", "user", "content", prompt)
            ));
            requestBody.put("max_tokens", 500);
            requestBody.put("temperature", 0.7);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    lmStudioBaseUrl + "/v1/chat/completions",
                    requestBody,
                    String.class
            );

            JsonNode root = objectMapper.readTree(response.getBody());

            if (root.has("error")) {
                String errorMsg = root.path("error").path("message").asText("Неизвестная ошибка");
                logger.error("LM Studio error: {}", errorMsg);
                return "Ошибка ИИ: " + errorMsg;
            }

            String content = root.path("choices").get(0).path("message").path("content").asText();

            logger.info("=== Suspicion flags received ===");
            logger.info("{}", content);
            logger.info("================================");

            return content;

        } catch (Exception e) {
            logger.error("❌ ERROR calling LM Studio API: {}", e.getMessage(), e);
            return "Ошибка анализа подозрений: " + e.getMessage();
        }
    }

    public String generateAiClarifyingQuestions(String patientAnswers) {
        logger.info("Calling LM Studio API for generateAiClarifyingQuestions...");

        String promptTemplate = promptService.getPrompt("ai-questions");
        if (promptTemplate == null) {
            logger.error("Prompt 'ai-questions' not found!");
            return "Ошибка: промпт не найден";
        }

        String prompt = String.format(promptTemplate, patientAnswers);

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", List.of(
                    Map.of("role", "user", "content", prompt)
            ));
            requestBody.put("max_tokens", 500);
            requestBody.put("temperature", 0.7);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    lmStudioBaseUrl + "/v1/chat/completions",
                    requestBody,
                    String.class
            );

            JsonNode root = objectMapper.readTree(response.getBody());

            if (root.has("error")) {
                String errorMsg = root.path("error").path("message").asText("Неизвестная ошибка");
                logger.error("LM Studio error: {}", errorMsg);
                return "Ошибка ИИ: " + errorMsg;
            }

            String content = root.path("choices").get(0).path("message").path("content").asText();

            logger.info("✓ generateAiClarifyingQuestions completed");
            return content;

        } catch (Exception e) {
            logger.error("❌ ERROR calling LM Studio API: {}", e.getMessage(), e);
            return "Ошибка генерации вопросов: " + e.getMessage();
        }
    }

    public String analyzeImageWithVision(Path imagePath, String prompt) {
        logger.warn("analyzeImageWithVision called but not implemented");
        return "[Изображение не анализируется]";
    }
}