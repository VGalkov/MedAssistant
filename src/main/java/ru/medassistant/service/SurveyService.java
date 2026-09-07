package ru.medassistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.medassistant.model.*;
import ru.medassistant.repository.*;

import java.time.LocalDateTime;
import java.util.*;

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

    /**
     * Добавляет ответ на вопрос
     * @param questionId ID вопроса или -1 для вопросов доктора/ИИ
     * @param questionText текст вопроса (для вопросов доктора/ИИ)
     */
    public void addAnswer(Long surveyId, Long questionId, String answerText, String questionText) {
        logger.info("addAnswer: surveyId={}, questionId={}, questionText={}, answerLength={}",
                surveyId, questionId, questionText, answerText.length());

        Survey survey = surveyRepository.findById(surveyId)
                .orElseThrow(() -> new RuntimeException("Опрос не найден"));

        SurveyAnswer answer = new SurveyAnswer();
        answer.setSurvey(survey);
        answer.setAnswerText(answerText);
        answer.setAnsweredAt(LocalDateTime.now());

        if (questionId != null && questionId > 0) {
            // Стандартный вопрос из БД — сохраняем ссылку
            ScenarioQuestion question = new ScenarioQuestion();
            question.setId(questionId);
            answer.setQuestion(question);
            answer.setQuestionText(null); // Будет загружено из БД при чтении
            logger.info("Answer linked to question ID: {}", questionId);
        } else {
            // Вопрос доктора или ИИ — сохраняем текст напрямую
            answer.setQuestion(null);
            answer.setQuestionText(questionText != null ? questionText : "Вопрос");
            logger.info("Answer saved with questionText: {}", answer.getQuestionText());
        }

        survey.getAnswers().add(answer);
        surveyRepository.save(survey);

        logger.info("Answer saved. Total answers in survey: {}", survey.getAnswers().size());
    }

    /**
     * Перегруженная версия для обратной совместимости
     */
    public void addAnswer(Long surveyId, Long questionId, String answerText) {
        addAnswer(surveyId, questionId, answerText, null);
    }

    public Survey completeSurvey(Long surveyId) {
        logger.info("=== completeSurvey: {} ===", surveyId);

        Survey survey = surveyRepository.findById(surveyId)
                .orElseThrow(() -> new RuntimeException("Опрос не найден"));

        logger.info("Survey has {} answers", survey.getAnswers().size());

        StringBuilder originalText = new StringBuilder();
        for (SurveyAnswer answer : survey.getAnswers()) {
            // Сначала пробуем получить текст из поля questionText
            String questionText = answer.getQuestionText();

            // Если пусто, пробуем загрузить из question
            if (questionText == null && answer.getQuestion() != null) {
                questionText = answer.getQuestion().getQuestionText();
            }

            String answerText = answer.getAnswerText();

            logger.info("Answer: questionText='{}', answerLength={}",
                    questionText, answerText != null ? answerText.length() : 0);

            if (questionText == null) {
                questionText = "[Вопрос не указан]";
            }
            if (answerText == null) {
                answerText = "[Нет ответа]";
            }

            originalText.append(questionText)
                    .append(": ")
                    .append(answerText)
                    .append("\n");
        }

        String originalTextStr = originalText.toString();
        logger.info("Original text ({} chars): {}", originalTextStr.length(),
                originalTextStr.substring(0, Math.min(200, originalTextStr.length())));

        survey.setOriginalText(originalTextStr);
        survey.setStatus("COMPLETED");
        survey.setCompletedAt(LocalDateTime.now());

        // Шаг 1: Структурирование
        logger.info("Calling processSurveyText...");
        String processedText = lmStudioService.processSurveyText(originalTextStr);
        logger.info("Processed text: {}", processedText);
        survey.setProcessedText(processedText);

        // Шаг 2: История пациента
        String patientHistory = getPatientHistory(survey.getPatient().getId(), surveyId);
        logger.info("Patient history: {}", patientHistory != null ? "found" : "not found");

        // Шаг 3: Рекомендации
        logger.info("Calling generateRecommendations...");
        String recommendations = lmStudioService.generateRecommendations(
                originalTextStr,
                processedText,
                patientHistory
        );
        logger.info("Recommendations: {}", recommendations);
        survey.setAiRecommendations(recommendations);

        // Шаг 4: Подозрения
        logger.info("Calling detectSuspicionFlags...");
        String suspicionFlags = lmStudioService.detectSuspicionFlags(
                originalTextStr,
                patientHistory
        );
        logger.info("Suspicion flags: {}", suspicionFlags);
        survey.setAiSuspicionFlags(suspicionFlags);

        logger.info("=== Survey completed successfully ===");
        return surveyRepository.save(survey);
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
        return surveyRepository.findById(surveyId);
    }

    /**
     * Сохранение опроса
     */
    public void saveSurvey(Survey survey) {
        surveyRepository.save(survey);
    }
}