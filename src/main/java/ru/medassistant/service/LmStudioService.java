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

    public String processSurveyText(String originalText) {
        String prompt = buildProcessPrompt(originalText);
        return callLlm(prompt, 0.3);
    }

    public String generateRecommendations(String originalText, String processedText, String patientHistory) {
        String prompt = buildRecommendationsPrompt(originalText, processedText, patientHistory);
        return callLlm(prompt, 0.5);
    }

    public String detectSuspicionFlags(String originalText, String patientHistory) {
        String prompt = buildSuspicionPrompt(originalText, patientHistory);
        return callLlm(prompt, 0.2);
    }

    public String generateNextQuestion(List<Map<String, String>> previousAnswers, String scenarioContext) {
        String prompt = buildNextQuestionPrompt(previousAnswers, scenarioContext);
        return callLlm(prompt, 0.7);
    }

    /**
     * Генерация 3 уточняющих ИИ-вопросов НА ОСНОВЕ ответов пациента
     * Вызывается после того как пациент ответил на вопросы доктора
     */
    public String generateAiClarifyingQuestions(String patientAnswers) {
        String prompt = buildAiQuestionsPrompt(patientAnswers);
        String response = callLlm(prompt, 0.4);

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

        return result.toString();
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

    /**
     * Промпт для генерации 3 уточняющих вопросов НА ОСНОВЕ ответов пациента
     */
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