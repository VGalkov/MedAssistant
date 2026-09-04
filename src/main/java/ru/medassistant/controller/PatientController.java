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
            Optional<Patient> existing = patientRepository.findByPhone(patient.getPhone());

            Patient savedPatient;
            if (existing.isPresent()) {
                savedPatient = existing.get();
                logger.info("Patient found by phone: {}", savedPatient.getId());
            } else {
                savedPatient = patientRepository.save(patient);
                logger.info("Patient created with id: {}", savedPatient.getId());
            }

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
            logger.info("Survey created with id: {}", survey.getId());

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

            // Парсим вопросы доктора из БД
            List<String> doctorQuestions = List.of();
            if (scenario.getDoctorQuestionsText() != null && !scenario.getDoctorQuestionsText().trim().isEmpty()) {
                doctorQuestions = parseQuestions(scenario.getDoctorQuestionsText());
                logger.info("Loaded {} doctor questions from DB", doctorQuestions.size());
            } else {
                logger.warn("No doctor questions in scenario! Using empty list.");
            }
            model.addAttribute("doctorQuestions", doctorQuestions);

            // Проверяем, сгенерированы ли уже ИИ-вопросы
            if (survey.getAiGeneratedQuestionsText() != null && !survey.getAiGeneratedQuestionsText().trim().isEmpty()) {
                List<String> aiQuestions = parseQuestions(survey.getAiGeneratedQuestionsText());
                model.addAttribute("aiQuestions", aiQuestions);
                logger.info("Loaded {} AI questions", aiQuestions.size());
            }

            logger.info("Survey page opened successfully. doctorQuestions={}, allowAiQuestions={}",
                    doctorQuestions.size(), scenario.getAllowAiQuestions());

            return "patient/survey";

        } catch (Exception e) {
            logger.error("Error opening survey page for id: {}", id, e);
            return "redirect:/patient/start";
        }
    }

    @PostMapping("/survey/{id}/generate-ai-questions")
    @ResponseBody
    public String generateAiQuestions(@PathVariable Long id) {
        logger.info("=== POST /patient/survey/{}/generate-ai-questions ===", id);

        try {
            Survey survey = surveyService.getSurveyById(id)
                    .orElseThrow(() -> new RuntimeException("Опрос не найден: " + id));

            StringBuilder patientAnswers = new StringBuilder();
            for (SurveyAnswer answer : survey.getAnswers()) {
                String questionText = answer.getQuestion().getQuestionText();
                if (questionText == null) {
                    questionText = "Вопрос";
                }
                patientAnswers.append(questionText)
                        .append(": ")
                        .append(answer.getAnswerText())
                        .append("\n");
            }

            logger.info("Generating AI questions based on {} characters", patientAnswers.length());

            String aiQuestions = lmStudioService.generateAiClarifyingQuestions(patientAnswers.toString());

            survey.setAiGeneratedQuestionsText(aiQuestions);
            surveyService.saveSurvey(survey);

            logger.info("✓ AI questions generated");

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
        logger.debug("Saving answer for survey: {}, question: {}", id, questionId);

        try {
            if (questionText != null && !questionText.trim().isEmpty()) {
                surveyService.addAnswer(id, questionId, answerText, questionText);
            } else {
                surveyService.addAnswer(id, questionId, answerText);
            }
            return "{\"success\": true}";
        } catch (Exception e) {
            logger.error("Error saving answer for survey: {}", id, e);
            return "{\"success\": false, \"error\": \"" + e.getMessage() + "\"}";
        }
    }

    @PostMapping("/survey/{id}/complete")
    public String completeSurvey(@PathVariable Long id) {
        logger.info("Completing survey: {}", id);

        try {
            surveyService.completeSurvey(id);
            logger.info("Survey completed successfully: {}", id);
            return "patient/completed";
        } catch (Exception e) {
            logger.error("Error completing survey: {}", id, e);
            return "patient/survey";
        }
    }

    private List<String> parseQuestions(String text) {
        if (text == null || text.trim().isEmpty()) {
            return List.of();
        }
        return Stream.of(text.split("\\n\\n+"))
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