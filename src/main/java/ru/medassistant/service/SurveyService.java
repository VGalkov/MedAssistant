package ru.medassistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.medassistant.model.*;
import ru.medassistant.repository.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class SurveyService {

    private static final Logger logger = LoggerFactory.getLogger(SurveyService.class);

    private final SurveyRepository surveyRepository;
    private final PatientRepository patientRepository;
    private final ScenarioRepository scenarioRepository;
    private final LmStudioService lmStudioService;

    public SurveyService(SurveyRepository surveyRepository,
                         PatientRepository patientRepository,
                         ScenarioRepository scenarioRepository,
                         LmStudioService lmStudioService) {
        this.surveyRepository = surveyRepository;
        this.patientRepository = patientRepository;
        this.scenarioRepository = scenarioRepository;
        this.lmStudioService = lmStudioService;
    }

    public Survey createSurvey(Long patientId, Long scenarioId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Пациент не найден"));

        Survey survey = new Survey();
        survey.setPatient(patient);
        survey.setStatus("ACTIVE");
        survey.setStartedAt(LocalDateTime.now());

        return surveyRepository.save(survey);
    }

    public void addAnswer(Long surveyId, Long questionId, String answerText, String questionText) {
        logger.info("addAnswer: surveyId={}, questionId={}, questionText='{}', answerLength={}",
                surveyId, questionId, questionText, answerText.length());

        Survey survey = surveyRepository.findById(surveyId)
                .orElseThrow(() -> new RuntimeException("Опрос не найден"));

        SurveyAnswer answer = new SurveyAnswer();
        answer.setSurvey(survey);
        answer.setAnswerText(answerText);
        answer.setAnsweredAt(LocalDateTime.now());

        if (questionId != null && questionId > 0) {
            ScenarioQuestion question = new ScenarioQuestion();
            question.setId(questionId);
            answer.setQuestion(question);
            answer.setQuestionText(null);
            logger.info("Answer linked to question ID: {}", questionId);
        } else {
            answer.setQuestion(null);
            answer.setQuestionText(questionText != null ? questionText : "Вопрос");
            logger.info("Answer saved with questionText: '{}'", answer.getQuestionText());
        }

        survey.getAnswers().add(answer);
        surveyRepository.save(survey);

        logger.info("✓ Answer saved. Total answers: {}", survey.getAnswers().size());
    }

    public void addAnswer(Long surveyId, Long questionId, String answerText) {
        addAnswer(surveyId, questionId, answerText, null);
    }

    public Survey completeSurvey(Long surveyId) {
        logger.info("=== completeSurvey: {} ===", surveyId);

        Survey survey = surveyRepository.findByIdWithAnswers(surveyId)
                .orElseThrow(() -> new RuntimeException("Опрос не найден: " + surveyId));

        logger.info("Survey loaded with {} answers", survey.getAnswers().size());

        StringBuilder originalText = new StringBuilder();
        int answerNum = 0;

        for (SurveyAnswer answer : survey.getAnswers()) {
            answerNum++;

            String questionText = answer.getQuestionText();
            if (questionText == null && answer.getQuestion() != null) {
                try {
                    questionText = answer.getQuestion().getQuestionText();
                    logger.info("Answer {}: Loaded questionText from question entity: '{}'", answerNum, questionText);
                } catch (Exception e) {
                    logger.warn("Answer {}: Failed to load questionText from question entity", answerNum, e);
                }
            }

            String answerText = answer.getAnswerText();

            if (questionText == null || questionText.trim().isEmpty()) {
                questionText = "[Вопрос #" + answerNum + "]";
            }
            if (answerText == null) {
                answerText = "[Нет ответа]";
            }

            originalText.append(questionText).append(": ").append(answerText).append("\n");
        }

        // ✅ Читаем содержимое файлов и добавляем к тексту
        StringBuilder filesContent = new StringBuilder();
        Path uploadDir = Paths.get("uploads/survey-" + surveyId);

        if (Files.exists(uploadDir)) {
            try {
                List<Path> imageFiles = Files.list(uploadDir)
                        .filter(p -> p.toString().toLowerCase().matches(".*\\.(jpg|jpeg|png|gif)$"))
                        .collect(Collectors.toList());

                if (!imageFiles.isEmpty()) {
                    originalText.append("\n📎 ПРИКРЕПЛЁННЫЕ ФАЙЛЫ: ").append(imageFiles.size()).append(" шт.\n");

                    for (Path file : imageFiles) {
                        originalText.append("- ").append(file.getFileName()).append("\n");

                        // ✅ Читаем содержимое файла (для текстовых файлов)
                        String fileContent = readFileContent(file);
                        if (fileContent != null && !fileContent.trim().isEmpty()) {
                            filesContent.append("Файл: ").append(file.getFileName()).append("\n");
                            filesContent.append("Содержимое: ").append(fileContent).append("\n\n");
                        }
                    }
                }
            } catch (IOException e) {
                logger.warn("Failed to list uploaded files", e);
            }
        }

        // ✅ Добавляем содержимое файлов к основному тексту для анализа
        if (filesContent.length() > 0) {
            originalText.append("\n=== СОДЕРЖИМОЕ ФАЙЛОВ ===\n");
            originalText.append(filesContent);
        }

        String originalTextStr = originalText.toString();
        logger.info("=== Original text ({} chars) ===", originalTextStr.length());
        logger.info("{}", originalTextStr.substring(0, Math.min(500, originalTextStr.length())));

        if (originalTextStr.trim().isEmpty()) {
            logger.error("❌ ERROR: originalText is EMPTY! Answers were not saved correctly.");
        }

        survey.setOriginalText(originalTextStr);
        survey.setStatus("COMPLETED");
        survey.setCompletedAt(LocalDateTime.now());

        logger.info("Calling processSurveyText...");
        String processedText = lmStudioService.processSurveyText(originalTextStr);
        logger.info("Processed text: {}", processedText);
        survey.setProcessedText(processedText);

        String patientHistory = getPatientHistory(survey.getPatient().getId(), surveyId);
        logger.info("Patient history: {}", patientHistory != null ? "found" : "not found");

        logger.info("Calling generateRecommendations...");
        String recommendations = lmStudioService.generateRecommendations(originalTextStr, processedText, patientHistory);
        logger.info("Recommendations: {}", recommendations);
        survey.setAiRecommendations(recommendations);

        logger.info("Calling detectSuspicionFlags...");
        String suspicionFlags = lmStudioService.detectSuspicionFlags(originalTextStr, patientHistory);
        logger.info("Suspicion flags: {}", suspicionFlags);
        survey.setAiSuspicionFlags(suspicionFlags);

        logger.info("=== Survey completed successfully ===");
        return surveyRepository.save(survey);
    }

    /**
     * Читает содержимое файла (только текстовые файлы, изображения игнорируются)
     */
    private String readFileContent(Path filePath) {
        try {
            String fileName = filePath.getFileName().toString().toLowerCase();

            // ✅ Для текстовых файлов - читаем текст
            if (fileName.endsWith(".txt") || fileName.endsWith(".md") || fileName.endsWith(".csv")) {
                return Files.readString(filePath);
            }

            // ✅ Для изображений - НЕ отправляем в ИИ
            if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ||
                    fileName.endsWith(".png") || fileName.endsWith(".gif")) {
                long fileSize = Files.size(filePath);
                return "[Изображение: " + fileName + ", размер: " + (fileSize / 1024) + " KB]";
            }

            return "[Файл: " + fileName + "]";

        } catch (IOException e) {
            logger.warn("Failed to read file content: {}", filePath, e);
            return null;
        }
    }

    private String getPatientHistory(Long patientId, Long excludeSurveyId) {
        List<Survey> previousSurveys = surveyRepository.findByPatientIdOrderByStartedAtDesc(patientId);
        StringBuilder history = new StringBuilder();

        for (Survey s : previousSurveys) {
            if (!s.getId().equals(excludeSurveyId) && s.getProcessedText() != null) {
                history.append("Дата: ").append(s.getStartedAt().toLocalDate())
                        .append("\n")
                        .append(s.getProcessedText())
                        .append("\n---\n");
            }
        }

        return history.length() > 0 ? history.toString() : null;
    }

    public Survey addDoctorComment(Long surveyId, Long doctorId, String comment) {
        Survey survey = surveyRepository.findById(surveyId)
                .orElseThrow(() -> new RuntimeException("Опрос не найден"));

        survey.setDoctorComments(comment);
        survey.setDoctorId(doctorId);
        survey.setStatus("REVIEWED");
        survey.setReviewedAt(LocalDateTime.now());

        return surveyRepository.save(survey);
    }

    public List<Survey> getSurveysForDoctor() {
        return surveyRepository.findByStatusOrderByStartedAtDesc("COMPLETED");
    }

    public Optional<Survey> getSurveyById(Long surveyId) {
        return surveyRepository.findByIdWithAnswers(surveyId);
    }

    public void saveSurvey(Survey survey) {
        surveyRepository.save(survey);
    }
}