package ru.medassistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LmStudioService {

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

    /**
     * Обработка текста опроса пациента - структурирование
     */
    public String processSurveyText(String originalText) {
        String prompt = buildProcessPrompt(originalText);
        return callLlm(prompt, 0.3);
    }

    /**
     * Генерация рекомендаций: что ещё спросить
     */
    public String generateRecommendations(String originalText, String processedText, String patientHistory) {
        String prompt = buildRecommendationsPrompt(originalText, processedText, patientHistory);
        return callLlm(prompt, 0.5);
    }

    /**
     * Детектор недостоверности - анализ на противоречия и "выдумывание"
     */
    public String detectSuspicionFlags(String originalText, String patientHistory) {
        String prompt = buildSuspicionPrompt(originalText, patientHistory);
        return callLlm(prompt, 0.2);
    }

    /**
     * Генерация следующего вопроса на основе предыдущих ответов
     */
    public String generateNextQuestion(List<Map<String, String>> previousAnswers, String scenarioContext) {
        String prompt = buildNextQuestionPrompt(previousAnswers, scenarioContext);
        return callLlm(prompt, 0.7);
    }

    private String callLlm(String prompt, double temperature) {
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
            
            // Используем RestTemplate вместо WebClient
            String response = restTemplate.postForObject(url, requestBody, String.class);
            
            if (response == null) {
                return "[Ошибка: пустой ответ от LM Studio]";
            }

            JsonNode jsonNode = objectMapper.readTree(response);
            return jsonNode.path("choices").get(0).path("message").path("content").asText();

        } catch (Exception e) {
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

    private String buildNextQuestionPrompt(List<Map<String, String>> previousAnswers, String scenarioContext) {
        return """
            На основе предыдущих ответов пациента предложи следующий уточняющий вопрос.
            Контекст сценария: %s
            Предыдущие ответы: %s

            Сгенерируй один конкретный вопрос на русском языке.
            """.formatted(scenarioContext, previousAnswers.toString());
    }
}
