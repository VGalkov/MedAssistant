package ru.medassistant.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.medassistant.model.Scenario;
import ru.medassistant.model.Survey;
import ru.medassistant.repository.ScenarioRepository;
import ru.medassistant.service.SurveyService;

import java.util.List;

@Controller
@RequestMapping("/doctor")
public class DoctorController {

    private static final Logger logger = LoggerFactory.getLogger(DoctorController.class);

    private final SurveyService surveyService;
    private final ScenarioRepository scenarioRepository;

    public DoctorController(SurveyService surveyService, ScenarioRepository scenarioRepository) {
        this.surveyService = surveyService;
        this.scenarioRepository = scenarioRepository;
    }

    @GetMapping
    public String dashboard(Model model) {
        List<Survey> surveys = surveyService.getSurveysForDoctor();
        model.addAttribute("surveys", surveys);
        return "doctor/dashboard";
    }

    @GetMapping("/survey/{id}")
    public String viewSurvey(@PathVariable Long id, Model model) {
        Survey survey = surveyService.getSurveyById(id)
                .orElseThrow(() -> new RuntimeException("Опрос не найден"));
        model.addAttribute("survey", survey);
        model.addAttribute("patient", survey.getPatient());
        return "doctor/survey-detail";
    }

    @GetMapping("/scenario/edit")
    public String editQuestionsPage(Model model) {
        Scenario scenario = scenarioRepository.findByIsActiveTrueOrderByCreatedAtDesc()
                .stream()
                .findFirst()
                .orElseGet(() -> {
                    Scenario newScenario = new Scenario();
                    newScenario.setName("Стандартный опрос");
                    newScenario.setSpecialty("Терапия");
                    newScenario.setIsActive(true);
                    newScenario.setDoctorQuestionsText("");
                    newScenario.setAllowAiQuestions(false);
                    return scenarioRepository.save(newScenario);
                });

        model.addAttribute("scenario", scenario);
        return "doctor/edit-questions";
    }

    @PostMapping("/scenario/{id}/edit-questions")
    public String saveQuestions(@PathVariable Long id,
                                @RequestParam(required = false) String doctorQuestionsText,
                                @RequestParam(required = false, defaultValue = "false") Boolean allowAiQuestions,
                                Model model) {
        try {
            Scenario scenario = scenarioRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Сценарий не найден"));

            scenario.setDoctorQuestionsText(doctorQuestionsText != null ? doctorQuestionsText.trim() : "");
            scenario.setAllowAiQuestions(allowAiQuestions != null && allowAiQuestions);

            scenarioRepository.save(scenario);

            model.addAttribute("scenario", scenario);
            model.addAttribute("success", true);

            return "doctor/edit-questions";

        } catch (Exception e) {
            model.addAttribute("error", "Ошибка: " + e.getMessage());
            return "doctor/edit-questions";
        }
    }

    @PostMapping("/survey/{id}/comment")
    @ResponseBody
    public String saveComment(@PathVariable Long id,
                              @RequestParam String comment,
                              @RequestParam(required = false, defaultValue = "false") Boolean append) {
        try {
            Survey survey = surveyService.getSurveyById(id)
                    .orElseThrow(() -> new RuntimeException("Опрос не найден"));

            String existingComments = survey.getDoctorComments();
            String newComment;

            if (append && existingComments != null && !existingComments.trim().isEmpty()) {
                newComment = existingComments + "\n\n────────────────────────────────\n" +
                        "📅 " + java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                ) + "\n" + comment.trim();
            } else {
                newComment = "📅 " + java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                ) + "\n" + comment.trim();
            }

            survey.setDoctorComments(newComment);
            surveyService.saveSurvey(survey);

            return "{\"success\": true, \"length\": " + newComment.length() + "}";

        } catch (Exception e) {
            return "{\"success\": false, \"error\": \"" + e.getMessage() + "\"}";
        }
    }
}