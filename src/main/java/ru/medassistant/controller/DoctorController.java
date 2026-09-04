package ru.medassistant.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.medassistant.model.Patient;
import ru.medassistant.model.Survey;
import ru.medassistant.repository.PatientRepository;
import ru.medassistant.repository.SurveyRepository;
import ru.medassistant.service.SurveyService;

import java.util.List;

@Controller
@RequestMapping("/doctor")
public class DoctorController {

    private static final Logger logger = LoggerFactory.getLogger(DoctorController.class);

    private final SurveyRepository surveyRepository;
    private final PatientRepository patientRepository;
    private final SurveyService surveyService;

    public DoctorController(SurveyRepository surveyRepository,
                            PatientRepository patientRepository,
                            SurveyService surveyService) {
        this.surveyRepository = surveyRepository;
        this.patientRepository = patientRepository;
        this.surveyService = surveyService;
        logger.info("DoctorController initialized");
    }

    @GetMapping
    public String doctorDashboard(Model model) {
        logger.info("=== GET /doctor ===");

        try {
            // Получаем ВСЕ опросы (не только completed)
            List<Survey> surveys = surveyRepository.findAllByOrderByCompletedAtDesc();
            logger.info("Found {} surveys", surveys.size());

            for (Survey s : surveys) {
                logger.info("  Survey: id={}, status={}, patient={}, completedAt={}",
                        s.getId(), s.getStatus(),
                        s.getPatient() != null ? s.getPatient().getLastName() : "null",
                        s.getCompletedAt());
            }

            model.addAttribute("surveys", surveys);
            return "doctor/list";

        } catch (Exception e) {
            logger.error("❌ ERROR loading doctor dashboard", e);
            model.addAttribute("error", "Ошибка загрузки: " + e.getMessage());
            return "error";
        }
    }

    @GetMapping("/survey/{id}")
    public String viewSurvey(@PathVariable Long id, Model model) {
        logger.info("=== GET /doctor/survey/{} ===", id);

        try {
            Survey survey = surveyRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Survey not found: " + id));

            logger.info("Survey found: id={}, patientId={}, status={}",
                    survey.getId(),
                    survey.getPatient() != null ? survey.getPatient().getId() : "null",
                    survey.getStatus());

            Patient patient = patientRepository.findById(survey.getPatient().getId())
                    .orElseThrow(() -> new RuntimeException("Patient not found"));

            logger.info("Patient found: {} {}", patient.getLastName(), patient.getFirstName());

            model.addAttribute("survey", survey);
            model.addAttribute("patient", patient);
            logger.info("Returning doctor/survey-detail view");

            return "doctor/survey-detail";

        } catch (Exception e) {
            logger.error("❌ ERROR loading survey detail for id: {}", id, e);
            model.addAttribute("error", "Ошибка: " + e.getMessage());
            return "error";
        }
    }

    @PostMapping("/survey/{id}/comment")
    @ResponseBody
    public String saveComment(@PathVariable Long id,
                              @RequestParam String comment,
                              @RequestParam(required = false, defaultValue = "false") Boolean append) {
        logger.info("=== POST /doctor/survey/{}/comment ===", id);
        logger.info("Comment length: {}, Append: {}", comment.length(), append);

        try {
            Survey survey = surveyRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Survey not found: " + id));

            String existingComments = survey.getDoctorComments();
            String newComment;

            if (append && existingComments != null && !existingComments.trim().isEmpty()) {
                newComment = existingComments + "\n\n────────────────────────────────\n" +
                        "📅 " + java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                ) + "\n" +
                        comment.trim();
                logger.info("Appending comment to existing history");
            } else {
                newComment = "📅 " + java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                ) + "\n" +
                        comment.trim();
                logger.info("Creating new comment");
            }

            survey.setDoctorComments(newComment);
            surveyRepository.save(survey);

            logger.info("✓ Comment saved successfully, total length: {}", newComment.length());
            return "{\"success\": true, \"length\": " + newComment.length() + "}";

        } catch (Exception e) {
            logger.error("❌ ERROR saving comment: {}", e.getMessage(), e);
            return "{\"success\": false, \"error\": \"" + e.getMessage() + "\"}";
        }
    }
}