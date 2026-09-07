package ru.medassistant.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.medassistant.model.*;
import ru.medassistant.repository.*;
import ru.medassistant.service.LmStudioService;
import ru.medassistant.service.SurveyService;
import ru.medassistant.model.SurveyAnswer;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
@RequestMapping("/patient")
public class PatientController {

    private static final Logger logger = LoggerFactory.getLogger(PatientController.class);

    private final PatientRepository patientRepository;
    private final ScenarioRepository scenarioRepository;
    private final SurveyService surveyService;
    private final LmStudioService lmStudioService;

    public PatientController(PatientRepository patientRepository,
                             ScenarioRepository scenarioRepository,
                             SurveyService surveyService,
                             LmStudioService lmStudioService) {
        this.patientRepository = patientRepository;
        this.scenarioRepository = scenarioRepository;
        this.surveyService = surveyService;
        this.lmStudioService = lmStudioService;
    }

    @GetMapping("/start")
    public String startPage(Model model) {
        logger.info("Opening patient start page");
        model.addAttribute("patient", new Patient());
        return "patient/start";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute Patient patient, Model model) {
        logger.info("Registering patient: {} {}", patient.getFirstName(), patient.getLastName());

        try {
            Patient savedPatient = patientRepository.save(patient);
            logger.info("Patient created with id: {}, phone: {}", savedPatient.getId(), savedPatient.getPhone());

            List<Scenario> scenarios = scenarioRepository.findByIsActiveTrueOrderByCreatedAtDesc();
            Scenario scenario;
            if (scenarios.isEmpty()) {
                scenario = createDefaultScenario();
                logger.info("Created default scenario with id: {}", scenario.getId());
            } else {
                scenario = scenarios.get(0);
                logger.info("Using existing scenario: id={}, name={}", scenario.getId(), scenario.getName());
            }

            Survey survey = surveyService.createSurvey(savedPatient.getId(), scenario.getId());
            logger.info("Survey created with id: {} for patient id: {}", survey.getId(), savedPatient.getId());

            return "redirect:/patient/survey/" + survey.getId();

        } catch (Exception e) {
            logger.error("Error during patient registration", e);
            model.addAttribute("error", "Ошибка регистрации: " + e.getMessage());
            return "patient/start";
        }
    }

    @GetMapping("/survey/{id}")
    public String surveyPage(@PathVariable Long id, Model model) {
        logger.info("Opening survey page for id: {}", id);

        try {
            Optional<Survey> surveyOpt = surveyService.getSurveyById(id);
            if (surveyOpt.isEmpty()) {
                logger.warn("Survey not found: {}", id);
                return "redirect:/patient/start";
            }

            Survey survey = surveyOpt.get();
            Scenario scenario = getOrCreateDefaultScenario();

            model.addAttribute("survey", survey);
            model.addAttribute("scenario", scenario);
            model.addAttribute("allowAiQuestions", Boolean.TRUE.equals(scenario.getAllowAiQuestions()));

            List<String> doctorQuestions = List.of();
            String doctorQuestionsText = scenario.getDoctorQuestionsText();

            logger.info("Raw doctorQuestionsText from DB: [{}]", doctorQuestionsText);

            if (doctorQuestionsText != null && !doctorQuestionsText.trim().isEmpty()) {
                doctorQuestions = parseQuestions(doctorQuestionsText);
                logger.info("✅ Parsed {} doctor questions", doctorQuestions.size());
            } else {
                logger.warn("⚠️ No doctor questions in scenario!");
            }
            model.addAttribute("doctorQuestions", doctorQuestions);

            if (survey.getAiGeneratedQuestionsText() != null && !survey.getAiGeneratedQuestionsText().trim().isEmpty()) {
                List<String> aiQuestions = parseQuestions(survey.getAiGeneratedQuestionsText());
                model.addAttribute("aiQuestions", aiQuestions);
                logger.info("Loaded {} AI questions", aiQuestions.size());
            }

            logger.info("Survey page opened. Patient: {} {}, doctorQuestions={}, allowAiQuestions={}",
                    survey.getPatient().getFirstName(),
                    survey.getPatient().getLastName(),
                    doctorQuestions.size(),
                    scenario.getAllowAiQuestions());

            return "patient/survey";

        } catch (Exception e) {
            logger.error("Error opening survey page for id: {}", id, e);
            return "redirect:/patient/start";
        }
    }

    /**
     * Загрузка сохранённых ответов для опроса
     */
    @GetMapping("/survey/{id}/answers")
    @ResponseBody
    public String getSavedAnswers(@PathVariable Long id) {
        logger.info("=== GET /patient/survey/{}/answers ===", id);

        try {
            Optional<Survey> surveyOpt = surveyService.getSurveyById(id);
            if (surveyOpt.isEmpty()) {
                return "{\"answers\": []}";
            }

            Survey survey = surveyOpt.get();
            StringBuilder json = new StringBuilder("{\"answers\": [");

            boolean first = true;
            for (SurveyAnswer answer : survey.getAnswers()) {
                if (!first) json.append(",");
                first = false;

                // Сначала пробуем questionText, потом question.questionText
                String questionId = answer.getQuestionText();
                if (questionId == null && answer.getQuestion() != null) {
                    questionId = answer.getQuestion().getQuestionText();
                }
                if (questionId == null) {
                    questionId = "unknown";
                }
                questionId = questionId.replace("\"", "\\\"");

                String answerText = answer.getAnswerText() != null ?
                        answer.getAnswerText().replace("\"", "\\\"") : "";

                json.append("{\"questionId\":\"")
                        .append(questionId)
                        .append("\",\"answerText\":\"")
                        .append(answerText)
                        .append("\"}");
            }

            json.append("]}");
            logger.info("Returning {} answers", survey.getAnswers().size());
            return json.toString();

        } catch (Exception e) {
            logger.error("Error loading answers", e);
            return "{\"answers\": [], \"error\": \"" + e.getMessage() + "\"}";
        }
    }

    @PostMapping("/survey/{id}/generate-ai-questions")
    @ResponseBody
    public String generateAiQuestions(@PathVariable Long id) {
        logger.info("=== POST /patient/survey/{}/generate-ai-questions ===", id);

        try {
            Survey survey = surveyService.getSurveyById(id)
                    .orElseThrow(() -> new RuntimeException("Опрос не найден: " + id));

            logger.info("Survey has {} answers", survey.getAnswers().size());

            StringBuilder patientAnswers = new StringBuilder();
            for (SurveyAnswer answer : survey.getAnswers()) {
                // ✅ ИСПРАВЛЕНО: используем getQuestionText() вместо getQuestion().getQuestionText()
                String questionText = answer.getQuestionText();

                // Если questionText пуст, пробуем загрузить из question
                if (questionText == null && answer.getQuestion() != null) {
                    questionText = answer.getQuestion().getQuestionText();
                }

                String answerText = answer.getAnswerText();

                if (questionText == null) {
                    questionText = "Вопрос";
                }
                if (answerText == null) {
                    answerText = "[Нет ответа]";
                }

                patientAnswers.append(questionText)
                        .append(": ")
                        .append(answerText)
                        .append("\n");
            }

            logger.info("Generating AI questions based on {} characters", patientAnswers.length());
            logger.info("Patient answers: {}", patientAnswers.toString().substring(0, Math.min(200, patientAnswers.length())));

            String aiQuestions = lmStudioService.generateAiClarifyingQuestions(patientAnswers.toString());

            survey.setAiGeneratedQuestionsText(aiQuestions);
            surveyService.saveSurvey(survey);

            logger.info("✓ AI questions generated: {}", aiQuestions);

            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            return "{\"success\": true, \"questions\": " + mapper.writeValueAsString(aiQuestions) + "}";

        } catch (Exception e) {
            logger.error("❌ ERROR generating AI questions: {}", e.getMessage(), e);
            return "{\"success\": false, \"error\": \"" + e.getMessage() + "\"}";
        }
    }

    @PostMapping("/survey/{id}/answer")
    @ResponseBody
    public String saveAnswer(@PathVariable Long id,
                             @RequestParam Long questionId,
                             @RequestParam String answerText,
                             @RequestParam(required = false) String questionText) {
        logger.info("Saving answer for survey: {}, questionId: {}, questionText: '{}', answerLength: {}",
                id, questionId, questionText, answerText.length());

        try {
            if (questionText != null && !questionText.trim().isEmpty()) {
                surveyService.addAnswer(id, questionId, answerText, questionText);
                logger.info("✓ Answer saved with questionText");
            } else {
                surveyService.addAnswer(id, questionId, answerText);
                logger.info("✓ Answer saved without questionText");
            }
            return "{\"success\": true}";
        } catch (Exception e) {
            logger.error("Error saving answer for survey: {}", id, e);
            return "{\"success\": false, \"error\": \"" + e.getMessage() + "\"}";
        }
    }

    @PostMapping("/survey/{id}/complete")
    public String completeSurvey(@PathVariable Long id, Model model) {
        logger.info("=== Completing survey: {} ===", id);

        try {
            Survey survey = surveyService.getSurveyById(id).orElse(null);
            if (survey != null) {
                logger.info("Survey has {} answers before complete", survey.getAnswers().size());
                for (SurveyAnswer ans : survey.getAnswers()) {
                    // ✅ ИСПРАВЛЕНО: используем getQuestionText() вместо getQuestion().getQuestionText()
                    String questionText = ans.getQuestionText();
                    if (questionText == null && ans.getQuestion() != null) {
                        questionText = ans.getQuestion().getQuestionText();
                    }
                    logger.info("  - Question: '{}', Answer: '{}'",
                            questionText != null ? questionText : "[NULL]",
                            ans.getAnswerText());
                }
            }

            surveyService.completeSurvey(id);
            logger.info("✓ Survey completed successfully: {}", id);
            return "patient/completed";

        } catch (Exception e) {
            logger.error("❌ Error completing survey: {}", id, e);

            // ✅ Добавляем данные в модель для отображения ошибки
            model.addAttribute("error", "Ошибка завершения опроса: " + e.getMessage());
            model.addAttribute("surveyId", id);

            // Пытаемся загрузить опрос для отображения
            surveyService.getSurveyById(id).ifPresent(survey -> {
                model.addAttribute("survey", survey);
                Scenario scenario = getOrCreateDefaultScenario();
                model.addAttribute("scenario", scenario);
                model.addAttribute("allowAiQuestions", Boolean.TRUE.equals(scenario.getAllowAiQuestions()));

                if (scenario.getDoctorQuestionsText() != null && !scenario.getDoctorQuestionsText().trim().isEmpty()) {
                    model.addAttribute("doctorQuestions", parseQuestions(scenario.getDoctorQuestionsText()));
                }
            });

            return "patient/survey";
        }
    }

    private List<String> parseQuestions(String text) {
        if (text == null || text.trim().isEmpty()) {
            return List.of();
        }
        return Stream.of(text.split("\\r?\\n\\r?\\n+"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toList());
    }

    private Scenario getOrCreateDefaultScenario() {
        List<Scenario> scenarios = scenarioRepository.findByIsActiveTrueOrderByCreatedAtDesc();
        if (scenarios.isEmpty()) {
            return createDefaultScenario();
        }
        return scenarios.get(0);
    }

    private Scenario createDefaultScenario() {
        logger.info("Creating default scenario");

        Scenario scenario = new Scenario();
        scenario.setName("Первичный приём");
        scenario.setDescription("Опрос перед приёмом врача");
        scenario.setSpecialty("Терапия");
        scenario.setIsActive(true);
        scenario.setDoctorQuestionsText("");
        scenario.setAllowAiQuestions(false);

        return scenarioRepository.save(scenario);
    }
}