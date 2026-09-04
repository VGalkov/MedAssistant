package ru.medassistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LmStudioService {

    private static final Logger logger = LoggerFactory.getLogger(LmStudioService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${lmstudio.base-url:http://localhost:1234}")
    private String baseUrl;

    @Value("${lmstudio.model:local-model}")
    private String model;

    public LmStudioService(ObjectMapper objectMapper) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
        logger.info("LmStudioService initialized with baseUrl={}, model={}", baseUrl, model);
    }

    public String processSurveyText(String originalText) {
        logger.debug("Processing survey text, length={}", originalText.length());
        String prompt = buildProcessPrompt(originalText);
        String result = callLlm(prompt, 0.3);
        logger.debug("Processed survey text result: {}", result);
        return result;
    }

    public String generateRecommendations(String originalText, String processedText, String patientHistory) {
        logger.debug("Generating recommendations");
        String prompt = buildRecommendationsPrompt(originalText, processedText, patientHistory);
        String result = callLlm(prompt, 0.5);
        logger.debug("Recommendations: {}", result);
        return result;
    }

    public String detectSuspicionFlags(String originalText, String patientHistory) {
        logger.debug("Detecting suspicion flags");
        String prompt = buildSuspicionPrompt(originalText, patientHistory);
        String result = callLlm(prompt, 0.2);
        logger.debug("Suspicion flags: {}", result);
        return result;
    }

    private String callLlm(String prompt, double temperature) {
        logger.info("Calling LM Studio: baseUrl={}, model={}, temperature={}", baseUrl, model, temperature);

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", "Вы — медицинский ИИ-ассистент. Отвечайте точно, структурированно, на русском языке."),
                    Map.of("role", "user", "content", prompt)
            ));
            requestBody.put("temperature", temperature);
            requestBody.put("max_tokens", 1000);
            requestBody.put("stream", false);

            String url = baseUrl + "/v1/chat/completions";
            logger.info("POST {}", url);
            logger.debug("Request body: {}", objectMapper.writeValueAsString(requestBody));

            String response = restTemplate.postForObject(url, requestBody, String.class);

            logger.info("Response received, length={}", response != null ? response.length() : 0);

            if (response == null) {
                logger.error("Empty response from LM Studio");
                return "[Ошибка: пустой ответ от LM Studio]";
            }

            JsonNode jsonNode = objectMapper.readTree(response);
            String content = jsonNode.path("choices").get(0).path("message").path("content").asText();

            logger.info("LLM response: {}", content);
            return content;

        } catch (Exception e) {
            logger.error("Error calling LM Studio: {}", e.getMessage(), e);
            return "[Ошибка связи с LM Studio: " + e.getMessage() + "]";
        }
    }

    private String buildProcessPrompt(String originalText) {
        return """
            Проанализируй текст опроса пациента и структурируй информацию по следующим категориям:
            1. Жалобы (основные симптомы, длительность)
            2. Анамнез заболевания (когда началось, как развивалось)
            3. Сопутствующие заболевания
            4. Принимаемые препараты (название, дозировка, режим)
            5. Аллергии
            6. Вредные привычки

            Формат вывода:
            Жалобы: ...
            Длительность: ...
            Препараты: ...
            Аллергии: ...
            Хронические: ...

            Текст пациента:
            %s
            """.formatted(originalText);
    }

    private String buildRecommendationsPrompt(String originalText, String processedText, String patientHistory) {
        return """
            Проанализируй опрос пациента и укажи:
            1. Какие пробелы в анамнезе нужно заполнить?
            2. Какие уточняющие вопросы нужно задать?
            3. Есть ли противоречия с историей пациента?

            Оригинал: %s
            Обработано: %s
            История: %s

            Выведи краткий список рекомендаций для врача.
            """.formatted(originalText, processedText, patientHistory != null ? patientHistory : "Нет истории");
    }

    private String buildSuspicionPrompt(String originalText, String patientHistory) {
        return """
            Проанализируй текст ответов пациента на предмет:
            1. Противоречий внутри текста
            2. Противоречий с предыдущей историей
            3. Признаков неопределённости ("наверное", "вроде", "какие-то")
            4. Признаков возможного сокрытия или выдумывания

            Текст пациента: %s
            История: %s

            Если есть подозрения, перечисли их кратко. Если нет — напиши "Подозрений нет".
            """.formatted(originalText, patientHistory != null ? patientHistory : "Нет истории");
    }
}