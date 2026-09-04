package ru.medassistant.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.medassistant.dto.QuestionDTO;
import ru.medassistant.dto.ScenarioDTO;
import ru.medassistant.model.*;
import ru.medassistant.repository.*;
import ru.medassistant.service.SurveyService;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/patient")
public class PatientController {

    private static final Logger logger = LoggerFactory.getLogger(PatientController.class);

    private final PatientRepository patientRepository;
    private final ScenarioRepository scenarioRepository;
    private final SurveyService surveyService;

    public PatientController(PatientRepository patientRepository,
                             ScenarioRepository scenarioRepository,
                             SurveyService surveyService) {
        this.patientRepository = patientRepository;
        this.scenarioRepository = scenarioRepository;
        this.surveyService = surveyService;
        logger.info("PatientController initialized");
    }

    @GetMapping("/start")
    public String startPage(Model model) {
        logger.info("=== GET /patient/start ===");
        model.addAttribute("patient", new Patient());
        logger.info("Returning patient/start view");
        return "patient/start";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute Patient patient, Model model) {
        logger.info("=== POST /patient/register ===");
        logger.info("Patient data: firstName={}, lastName={}, phone={}",
                patient.getFirstName(), patient.getLastName(), patient.getPhone());

        try {
            Optional<Patient> existing = patientRepository.findByPhone(patient.getPhone());
            logger.info("Search for existing patient by phone: {}", patient.getPhone());

            Patient savedPatient;
            if (existing.isPresent()) {
                savedPatient = existing.get();
                logger.info("✓ Patient FOUND: id={}", savedPatient.getId());
            } else {
                savedPatient = patientRepository.save(patient);
                logger.info("✓ Patient CREATED: id={}", savedPatient.getId());
            }

            List<Scenario> scenarios = scenarioRepository.findByIsActiveTrueOrderByCreatedAtDesc();
            logger.info("Found {} active scenarios", scenarios.size());

            if (scenarios.isEmpty()) {
                logger.info("No scenarios found, creating default...");
                Scenario defaultScenario = createDefaultScenario();
                scenarios.add(defaultScenario);
                logger.info("✓ Default scenario created: id={}", defaultScenario.getId());
            }

            Scenario scenario = scenarios.get(0);
            logger.info("Using scenario: id={}, name={}, questions={}",
                    scenario.getId(), scenario.getName(),
                    scenario.getQuestions() != null ? scenario.getQuestions().size() : "null");

            Survey survey = surveyService.createSurvey(savedPatient.getId(), scenario.getId());
            logger.info("✓ Survey created: id={}, patientId={}", survey.getId(), savedPatient.getId());

            String redirectUrl = "/patient/survey/" + survey.getId();
            logger.info("Redirecting to: {}", redirectUrl);

            return "redirect:" + redirectUrl;

        } catch (Exception e) {
            logger.error("❌ ERROR during patient registration", e);
            model.addAttribute("error", "Ошибка регистрации: " + e.getMessage());
            return "patient/start";
        }
    }

    @GetMapping("/survey/{id}")
    public String surveyPage(@PathVariable Long id, Model model) {
        logger.info("=== GET /patient/survey/{} ===", id);

        try {
            Optional<Survey> surveyOpt = surveyService.getSurveyById(id);
            logger.info("Survey lookup result: {}", surveyOpt.isPresent() ? "FOUND" : "NOT FOUND");

            if (surveyOpt.isEmpty()) {
                logger.warn("Survey not found: {}", id);
                return "redirect:/patient/start";
            }

            Survey survey = surveyOpt.get();
            logger.info("Survey: id={}, status={}, patientId={}",
                    survey.getId(), survey.getStatus(),
                    survey.getPatient() != null ? survey.getPatient().getId() : "null");

            // Создаём DTO вместо передачи сущности JPA
            ScenarioDTO scenarioDTO = createScenarioDTO(getOrCreateDefaultScenario());
            logger.info("Scenario DTO created: id={}, name={}, questions={}",
                    scenarioDTO.getId(), scenarioDTO.getName(),
                    scenarioDTO.getQuestions() != null ? scenarioDTO.getQuestions().size() : 0);

            model.addAttribute("survey", survey);
            model.addAttribute("scenario", scenarioDTO);
            logger.info("Attributes added to model: survey, scenario(DTO)");
            logger.info("Returning patient/survey view");

            return "patient/survey";

        } catch (Exception e) {
            logger.error("❌ ERROR opening survey page for id: {}", id, e);
            model.addAttribute("error", "Ошибка: " + e.getMessage());
            return "patient/start";
        }
    }

    // Новый метод для создания DTO
    private ScenarioDTO createScenarioDTO(Scenario scenario) {
        List<QuestionDTO> questionDTOs = scenario.getQuestions().stream()
                .sorted((a, b) -> a.getOrderIndex() - b.getOrderIndex())
                .map(q -> new QuestionDTO(
                        q.getId(),
                        q.getOrderIndex(),
                        q.getQuestionText(),
                        q.getQuestionType(),
                        q.getCategory(),
                        q.getIsRequired()
                ))
                .toList();

        return new ScenarioDTO(scenario.getId(), scenario.getName(), questionDTOs);
    }

    @PostMapping("/survey/{id}/answer")
    @ResponseBody
    public String saveAnswer(@PathVariable Long id,
                             @RequestParam Long questionId,
                             @RequestParam String answerText) {
        logger.info("=== POST /patient/survey/{}/answer ===", id);
        logger.info("questionId={}, answerText={}", questionId, answerText);

        try {
            surveyService.addAnswer(id, questionId, answerText);
            logger.info("✓ Answer saved successfully");
            return "{\"success\": true}";
        } catch (Exception e) {
            logger.error("❌ ERROR saving answer for survey: {}", id, e);
            return "{\"success\": false, \"error\": \"" + e.getMessage() + "\"}";
        }
    }

    @PostMapping("/survey/{id}/complete")
    public String completeSurvey(@PathVariable Long id, Model model) {
        logger.info("=== POST /patient/survey/{}/complete ===", id);

        try {
            logger.info("Calling surveyService.completeSurvey({})", id);
            surveyService.completeSurvey(id);
            logger.info("✓ Survey completed successfully");
            return "patient/completed";
        } catch (Exception e) {
            logger.error("❌ ERROR completing survey: {}", id, e);
            model.addAttribute("error", "Ошибка завершения: " + e.getMessage());
            return "patient/survey";
        }
    }

    private Scenario getOrCreateDefaultScenario() {
        logger.debug("getOrCreateDefaultScenario() called");
        List<Scenario> scenarios = scenarioRepository.findByIsActiveTrueOrderByCreatedAtDesc();
        logger.debug("Found {} scenarios", scenarios.size());

        if (scenarios.isEmpty()) {
            logger.info("No scenarios found, creating default");
            return createDefaultScenario();
        }
        return scenarios.get(0);
    }

    private Scenario createDefaultScenario() {
        logger.info("Creating default scenario");

        Scenario scenario = new Scenario();
        scenario.setName("Первичный приём (терапевт)");
        scenario.setDescription("Базовый опрос для первичного приёма");
        scenario.setSpecialty("Терапия");
        scenario.setIsActive(true);

        List.of(
                new QuestionData(1, "Что вас беспокоит? Опишите основные симптомы.", "TEXT", "Жалобы"),
                new QuestionData(2, "Как давно начались симптомы?", "TEXT", "Жалобы"),
                new QuestionData(3, "Принимаете ли вы какие-либо препараты постоянно?", "YES_NO", "Препараты"),
                new QuestionData(4, "Если да, перечислите названия и дозировки.", "TEXT", "Препараты"),
                new QuestionData(5, "Есть ли у вас аллергии?", "YES_NO", "Аллергии"),
                new QuestionData(6, "Если да, на что именно?", "TEXT", "Аллергии"),
                new QuestionData(7, "Есть ли у вас хронические заболевания?", "YES_NO", "Анамнез"),
                new QuestionData(8, "Если да, перечислите.", "TEXT", "Анамнез")
        ).forEach(q -> {
            ScenarioQuestion question = new ScenarioQuestion();
            question.setScenario(scenario);
            question.setOrderIndex(q.order);
            question.setQuestionText(q.text);
            question.setQuestionType(q.type);
            question.setCategory(q.category);
            question.setIsRequired(true);
            scenario.getQuestions().add(question);
            logger.debug("Added question: {}", q.text);
        });

        Scenario saved = scenarioRepository.save(scenario);
        logger.info("✓ Default scenario saved: id={}, questions={}",
                saved.getId(), saved.getQuestions().size());
        return saved;
    }

    private record QuestionData(int order, String text, String type, String category) {}
}