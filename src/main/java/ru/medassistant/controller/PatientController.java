package ru.medassistant.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.medassistant.model.*;
import ru.medassistant.repository.*;
import ru.medassistant.service.SurveyService;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/patient")
public class PatientController {

    private final PatientRepository patientRepository;
    private final ScenarioRepository scenarioRepository;
    private final SurveyService surveyService;

    public PatientController(PatientRepository patientRepository,
                           ScenarioRepository scenarioRepository,
                           SurveyService surveyService) {
        this.patientRepository = patientRepository;
        this.scenarioRepository = scenarioRepository;
        this.surveyService = surveyService;
    }

    /**
     * Страница начала опроса (регистрация/вход)
     */
    @GetMapping("/start")
    public String startPage(Model model) {
        model.addAttribute("patient", new Patient());
        return "patient/start";
    }

    /**
     * Регистрация пациента и переход к опросу
     */
    @PostMapping("/register")
    public String register(@ModelAttribute Patient patient, Model model) {
        // Проверяем, есть ли уже пациент с таким телефоном
        Optional<Patient> existing = patientRepository.findByPhone(patient.getPhone());

        Patient savedPatient;
        if (existing.isPresent()) {
            savedPatient = existing.get();
        } else {
            savedPatient = patientRepository.save(patient);
        }

        // Получаем активный сценарий (по умолчанию - первый)
        List<Scenario> scenarios = scenarioRepository.findByIsActiveTrueOrderByCreatedAtDesc();
        if (scenarios.isEmpty()) {
            // Создаём дефолтный сценарий
            Scenario defaultScenario = createDefaultScenario();
            scenarios.add(defaultScenario);
        }

        // Создаём опрос
        Survey survey = surveyService.createSurvey(savedPatient.getId(), scenarios.get(0).getId());

        return "redirect:/patient/survey/" + survey.getId();
    }

    /**
     * Страница опроса
     */
    @GetMapping("/survey/{id}")
    public String surveyPage(@PathVariable Long id, Model model) {
        Optional<Survey> surveyOpt = surveyService.getSurveyById(id);
        if (surveyOpt.isEmpty()) {
            return "redirect:/patient/start";
        }

        Survey survey = surveyOpt.get();
        model.addAttribute("survey", survey);
        model.addAttribute("scenario", getOrCreateDefaultScenario());

        return "patient/survey";
    }

    /**
     * Сохранение ответа
     */
    @PostMapping("/survey/{id}/answer")
    @ResponseBody
    public String saveAnswer(@PathVariable Long id,
                            @RequestParam Long questionId,
                            @RequestParam String answerText) {
        surveyService.addAnswer(id, questionId, answerText);
        return "{\"success\": true}";
    }

    /**
     * Завершение опроса
     */
    @PostMapping("/survey/{id}/complete")
    public String completeSurvey(@PathVariable Long id) {
        surveyService.completeSurvey(id);
        return "patient/completed";
    }

    private Scenario getOrCreateDefaultScenario() {
        List<Scenario> scenarios = scenarioRepository.findByIsActiveTrueOrderByCreatedAtDesc();
        if (scenarios.isEmpty()) {
            return createDefaultScenario();
        }
        return scenarios.get(0);
    }

    private Scenario createDefaultScenario() {
        Scenario scenario = new Scenario();
        scenario.setName("Первичный приём (терапевт)");
        scenario.setDescription("Базовый опрос для первичного приёма");
        scenario.setSpecialty("Терапия");
        scenario.setIsActive(true);

        // Добавляем вопросы
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
        });

        return scenarioRepository.save(scenario);
    }

    private record QuestionData(int order, String text, String type, String category) {}
}
