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
    }

    public String processSurveyText(String originalText) {
        logger.info("processSurveyText called with {} chars", originalText.length());
        String prompt = buildProcessPrompt(originalText);
        String result = callLlm(prompt, 0.3);
        logger.info("processSurveyText result: {}", result);
        return result;
    }

    public String generateRecommendations(String originalText, String processedText, String patientHistory) {
        logger.info("generateRecommendations called");
        String prompt = buildRecommendationsPrompt(originalText, processedText, patientHistory);
        String result = callLlm(prompt, 0.5);
        logger.info("generateRecommendations result: {}", result);
        return result;
    }

    public String detectSuspicionFlags(String originalText, String patientHistory) {
        logger.info("detectSuspicionFlags called");
        String prompt = buildSuspicionPrompt(originalText, patientHistory);
        String result = callLlm(prompt, 0.2);
        logger.info("detectSuspicionFlags result: {}", result);
        return result;
    }

    /**
     * Генерация 3 уточняющих ИИ-вопросов НА ОСНОВЕ ответов пациента
     */
    public String generateAiClarifyingQuestions(String patientAnswers) {
        logger.info("generateAiClarifyingQuestions called with {} chars", patientAnswers.length());
        String prompt = buildAiQuestionsPrompt(patientAnswers);
        String response = callLlm(prompt, 0.4);
        logger.info("generateAiClarifyingQuestions raw response: {}", response);

        // Парсим ответ: ожидаем 3 вопроса
        String[] lines = response.split("\n");
        StringBuilder result = new StringBuilder();
        int count = 0;

        for (String line : lines) {
            line = line.trim();
            line = line.replaceAll("^[\\d]+[.)]\\s*", "");
            line = line.replaceAll("^[-*•]\\s*", "");

            if (!line.isEmpty() && line.contains("?")) {
                if (line.length() > 0) {
                    line = Character.toUpperCase(line.charAt(0)) + line.substring(1);
                }
                if (!line.endsWith("?")) {
                    line = line.replaceAll("[.!,;:]*$", "") + "?";
                }

                if (count > 0) {
                    result.append("\n\n");
                }
                result.append(line);
                count++;

                if (count >= 3) break;
            }
        }

        logger.info("generateAiClarifyingQuestions parsed {} questions", count);
        return result.toString();
    }

    private String callLlm(String prompt, double temperature) {
        logger.info("callLlm: URL={}, model={}, temperature={}", baseUrl, model, temperature);
        logger.info("callLlm prompt: {}", prompt);

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", "Вы — медицинский ИИ-ассистент. Отвечайте точно, структурированно, на русском языке."),
                    Map.of("role", "user", "content", prompt)
            ));
            requestBody.put("temperature", temperature);
            requestBody.put("max_tokens", 2000);
            requestBody.put("stream", false);

            String url = baseUrl + "/v1/chat/completions";
            logger.info("callLlm: Sending request to {}", url);

            String response = restTemplate.postForObject(url, requestBody, String.class);

            logger.info("callLlm: Raw response: {}", response);

            if (response == null) {
                logger.error("callLlm: Empty response from LM Studio");
                return "[Ошибка: пустой ответ от LM Studio]";
            }

            JsonNode jsonNode = objectMapper.readTree(response);
            String content = jsonNode.path("choices").get(0).path("message").path("content").asText();

            logger.info("callLlm: Extracted content: {}", content);

            return content;

        } catch (Exception e) {
            logger.error("callLlm: Error", e);
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

            Формат вывода (строго следуй этому формату):
            Жалобы: ...
            Длительность: ...
            Препараты: ...
            Аллергии: ...
            Хронические: ...

            Текст опроса пациента:
            %s
            """.formatted(originalText);
    }

    private String buildRecommendationsPrompt(String originalText, String processedText, String patientHistory) {
        return """
            Проанализируй опрос пациента и укажи рекомендации для врача.

            Оригинал ответов: %s
            Структурированный анализ: %s
            История пациента: %s

            Выведи краткий список рекомендаций для врача (2-5 пунктов).
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

    private String buildAiQuestionsPrompt(String patientAnswers) {
        return """
            Ты — медицинский ИИ-ассистент. Пациент только что ответил на вопросы доктора.
            Проанализируй ответы и сгенерируй РОВНО 3 уточняющих вопроса.
            
            Цели вопросов:
            1. Устранить противоречия в ответах пациента
            2. Запросить уточнения по важным деталям (локализация, интенсивность, длительность симптомов)
            3. Выяснить недостающую информацию для постановки предварительного диагноза
            
            Ответы пациента:
            %s
            
            Требования к вопросам:
            - Каждый вопрос должен быть конкретным и понятным
            - Начинай с заглавной буквы, заканчивай вопросительным знаком
            - Избегай общих фраз, спрашивай о конкретных деталях
            - Не повторяй уже заданные вопросы
            
            Выведи РОВНО 3 вопроса, каждый с новой строки, без нумерации.
            """.formatted(patientAnswers);
    }
}