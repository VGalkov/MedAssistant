package ru.medassistant.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.medassistant.model.*;
import ru.medassistant.repository.*;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
public class SurveyService {

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
        Survey survey = surveyRepository.findById(surveyId)
                .orElseThrow(() -> new RuntimeException("Опрос не найден"));

        ScenarioQuestion question;

        if (questionId != null && questionId > 0) {
            // Стандартный вопрос из БД
            question = new ScenarioQuestion();
            question.setId(questionId);
        } else {
            // Вопрос доктора или ИИ - создаём временный объект с текстом
            question = new ScenarioQuestion();
            question.setQuestionText(questionText != null ? questionText : "Вопрос");
            question.setCategory("Дополнительно");
        }

        SurveyAnswer answer = new SurveyAnswer();
        answer.setSurvey(survey);
        answer.setQuestion(question);
        answer.setAnswerText(answerText);
        answer.setAnsweredAt(LocalDateTime.now());

        survey.getAnswers().add(answer);
        surveyRepository.save(survey);
    }

    /**
     * Перегруженная версия для обратной совместимости
     */
    public void addAnswer(Long surveyId, Long questionId, String answerText) {
        addAnswer(surveyId, questionId, answerText, null);
    }

    public Survey completeSurvey(Long surveyId) {
        Survey survey = surveyRepository.findById(surveyId)
                .orElseThrow(() -> new RuntimeException("Опрос не найден"));

        StringBuilder originalText = new StringBuilder();
        for (SurveyAnswer answer : survey.getAnswers()) {
            String questionText = answer.getQuestion().getQuestionText();
            if (questionText == null) {
                questionText = "Вопрос";
            }
            originalText.append(questionText)
                    .append(": ")
                    .append(answer.getAnswerText())
                    .append("\n");
        }

        survey.setOriginalText(originalText.toString());
        survey.setStatus("COMPLETED");
        survey.setCompletedAt(LocalDateTime.now());

        String processedText = lmStudioService.processSurveyText(originalText.toString());
        survey.setProcessedText(processedText);

        String patientHistory = getPatientHistory(survey.getPatient().getId(), surveyId);

        String recommendations = lmStudioService.generateRecommendations(
                originalText.toString(),
                processedText,
                patientHistory
        );
        survey.setAiRecommendations(recommendations);

        String suspicionFlags = lmStudioService.detectSuspicionFlags(
                originalText.toString(),
                patientHistory
        );
        survey.setAiSuspicionFlags(suspicionFlags);

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