package ru.medassistant.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.medassistant.model.Survey;
import ru.medassistant.service.SurveyService;

import java.util.List;

@Controller
@RequestMapping("/doctor")
public class DoctorController {

    private final SurveyService surveyService;

    public DoctorController(SurveyService surveyService) {
        this.surveyService = surveyService;
    }

    /**
     * Рабочее место врача - список опросов
     */
    @GetMapping
    public String dashboard(Model model) {
        List<Survey> surveys = surveyService.getSurveysForDoctor();
        model.addAttribute("surveys", surveys);
        return "doctor/dashboard";
    }

    /**
     * Детальный просмотр опроса
     */
    @GetMapping("/survey/{id}")
    public String viewSurvey(@PathVariable Long id, Model model) {
        Survey survey = surveyService.getSurveyById(id)
            .orElseThrow(() -> new RuntimeException("Опрос не найден"));

        model.addAttribute("survey", survey);
        model.addAttribute("patient", survey.getPatient());

        return "doctor/survey-detail";
    }

    /**
     * Сохранение комментария врача
     */
    @PostMapping("/survey/{id}/comment")
    @ResponseBody
    public String saveComment(@PathVariable Long id,
                             @RequestParam(required = false) Long doctorId,
                             @RequestParam String comment) {
        surveyService.addDoctorComment(id, doctorId != null ? doctorId : 1L, comment);
        return "{\"success\": true}";
    }
}
