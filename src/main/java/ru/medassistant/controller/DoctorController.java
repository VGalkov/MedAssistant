package ru.medassistant.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.medassistant.model.Scenario;
import ru.medassistant.model.Survey;
import ru.medassistant.repository.ScenarioRepository;
import ru.medassistant.service.PromptService;
import ru.medassistant.service.SurveyService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/doctor")
public class DoctorController {

    private static final Logger logger = LoggerFactory.getLogger(DoctorController.class);

    private final SurveyService surveyService;
    private final ScenarioRepository scenarioRepository;
    private final PromptService promptService;

    public DoctorController(SurveyService surveyService,
                            ScenarioRepository scenarioRepository,
                            @Autowired PromptService promptService) {
        this.surveyService = surveyService;
        this.scenarioRepository = scenarioRepository;
        this.promptService = promptService;
    }

    @GetMapping
    public String dashboard(Model model) {
        List<Survey> surveys = surveyService.getSurveysForDoctor();
        model.addAttribute("surveys", surveys);
        model.addAttribute("usingExternalPrompts", promptService.isUsingExternalPrompts());
        model.addAttribute("promptsDirectory", promptService.getPromptsDirectory());
        return "doctor/dashboard";
    }

    @GetMapping("/survey/{id}")
    public String viewSurvey(@PathVariable Long id, Model model) {
        Survey survey = surveyService.getSurveyById(id)
                .orElseThrow(() -> new RuntimeException("Опрос не найден"));

        model.addAttribute("survey", survey);
        model.addAttribute("patient", survey.getPatient());

        // ✅ Получаем список файлов с типами
        List<FileInfo> uploadedFiles = getUploadedFilesInfo(id);
        model.addAttribute("uploadedFiles", uploadedFiles);

        return "doctor/survey-detail";
    }

    // ✅ Класс для информации о файле (вложенный, не импортированный!)
    public static class FileInfo {
        private String filename;
        private String type; // "image" или "text"
        private String content; // для текстовых файлов
        private long size;

        public FileInfo(String filename, String type, String content, long size) {
            this.filename = filename;
            this.type = type;
            this.content = content;
            this.size = size;
        }

        public String getFilename() { return filename; }
        public String getType() { return type; }
        public String getContent() { return content; }
        public long getSize() { return size; }
    }

    private List<FileInfo> getUploadedFilesInfo(Long surveyId) {
        Path uploadDir = Paths.get("uploads/survey-" + surveyId);

        if (!Files.exists(uploadDir)) {
            return List.of();
        }

        try {
            return Files.list(uploadDir)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                                name.endsWith(".png") || name.endsWith(".gif") ||
                                name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".csv");
                    })
                    .map(p -> {
                        try {
                            String fileName = p.getFileName().toString().toLowerCase();
                            long size = Files.size(p);

                            // Определяем тип файла
                            if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ||
                                    fileName.endsWith(".png") || fileName.endsWith(".gif")) {
                                return new FileInfo(fileName, "image", null, size);
                            } else if (fileName.endsWith(".txt") || fileName.endsWith(".md") || fileName.endsWith(".csv")) {
                                String content = Files.readString(p);
                                return new FileInfo(fileName, "text", content, size);
                            }
                            return null;
                        } catch (IOException e) {
                            logger.warn("Failed to read file info: {}", p, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.warn("Failed to list uploaded files for survey {}", surveyId, e);
            return List.of();
        }
    }

    @GetMapping("/survey/{id}/files")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long id,
                                                 @RequestParam String filename) {
        try {
            logger.info("=== Download file request ===");
            logger.info("Survey ID: {}", id);
            logger.info("Filename: {}", filename);
            logger.info("Current working directory: {}", System.getProperty("user.dir"));

            Path surveyDir = Paths.get("uploads/survey-" + id);
            Path filePath = surveyDir.resolve(filename).normalize();

            logger.info("Survey directory: {}", surveyDir.toAbsolutePath());
            logger.info("File path: {}", filePath.toAbsolutePath());
            logger.info("File exists: {}", Files.exists(filePath));

            // Защита от выхода за пределы директории опроса
            if (!filePath.startsWith(surveyDir)) {
                logger.warn("Attempted to access file outside survey directory: {}", filename);
                return ResponseEntity.badRequest().build();
            }

            if (!Files.exists(filePath)) {
                logger.warn("File not found: {}", filePath);
                return ResponseEntity.notFound().build();
            }

            Resource resource = new UrlResource(filePath.toUri());

            // ✅ Определяем MIME тип с фоллбэком
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                // Определяем по расширению файла
                String fileName = filePath.getFileName().toString().toLowerCase();
                if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                    contentType = "image/jpeg";
                } else if (fileName.endsWith(".png")) {
                    contentType = "image/png";
                } else if (fileName.endsWith(".gif")) {
                    contentType = "image/gif";
                } else if (fileName.endsWith(".txt")) {
                    contentType = "text/plain; charset=UTF-8";
                } else if (fileName.endsWith(".md")) {
                    contentType = "text/markdown; charset=UTF-8";
                } else if (fileName.endsWith(".csv")) {
                    contentType = "text/csv; charset=UTF-8";
                } else {
                    contentType = "application/octet-stream";
                }
            }

            logger.info("Content-Type: {}", contentType);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                    .body(resource);

        } catch (Exception e) {
            logger.error("Error downloading file", e);
            return ResponseEntity.internalServerError().build();
        }
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
                    newScenario.setAllowFileUpload(false);
                    return scenarioRepository.save(newScenario);
                });

        model.addAttribute("scenario", scenario);
        return "doctor/edit-questions";
    }

    @PostMapping("/scenario/{id}/edit-questions")
    public String saveQuestions(@PathVariable Long id,
                                @RequestParam(required = false) String doctorQuestionsText,
                                @RequestParam(required = false, defaultValue = "false") Boolean allowAiQuestions,
                                @RequestParam(required = false, defaultValue = "false") Boolean allowFileUpload,
                                Model model) {
        try {
            Scenario scenario = scenarioRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Сценарий не найден"));

            scenario.setDoctorQuestionsText(doctorQuestionsText != null ? doctorQuestionsText.trim() : "");
            scenario.setAllowAiQuestions(allowAiQuestions != null && allowAiQuestions);
            scenario.setAllowFileUpload(allowFileUpload != null && allowFileUpload);

            scenarioRepository.save(scenario);

            model.addAttribute("scenario", scenario);
            model.addAttribute("success", true);

            return "doctor/edit-questions";

        } catch (Exception e) {
            model.addAttribute("error", "Ошибка: " + e.getMessage());
            return "doctor/edit-questions";
        }
    }

    @PostMapping("/prompts/reload")
    @ResponseBody
    public String reloadPrompts() {
        logger.info("=== Reloading prompts configuration ===");
        promptService.clearCache();

        boolean external = promptService.isUsingExternalPrompts();
        String directory = promptService.getPromptsDirectory();

        logger.info("✅ Prompts reloaded. External: {}, Directory: {}", external, directory);

        return String.format(
                "{\"success\": true, \"message\": \"Промпты перезагружены\", \"external\": %b, \"directory\": \"%s\"}",
                external,
                directory.replace("\\", "\\\\")
        );
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

    @GetMapping("/prompts/edit")
    public String editPromptsPage(Model model) {
        model.addAttribute("prompts", getPromptsMap());
        model.addAttribute("usingExternalPrompts", promptService.isUsingExternalPrompts());
        model.addAttribute("promptsDirectory", promptService.getPromptsDirectory());
        return "doctor/edit-prompts";
    }

    @PostMapping("/prompts/save")
    @ResponseBody
    public String savePrompts(@RequestParam Map<String, String> prompts) {
        logger.info("=== Saving prompts ===");

        if (!promptService.isUsingExternalPrompts()) {
            return "{\"success\": false, \"error\": \"Редактирование доступно только при использовании внешнего каталога промптов\"}";
        }

        try {
            Path promptsDir = Paths.get("prompts");
            if (!Files.exists(promptsDir)) {
                Files.createDirectory(promptsDir);
            }

            for (Map.Entry<String, String> entry : prompts.entrySet()) {
                String name = entry.getKey();
                String content = entry.getValue();
                Path filePath = promptsDir.resolve(name + ".txt");
                Files.writeString(filePath, content);
                logger.info("✅ Saved prompt: {}", name);
            }

            promptService.clearCache();

            return "{\"success\": true, \"message\": \"Промпты сохранены и перезагружены\"}";

        } catch (Exception e) {
            logger.error("Error saving prompts", e);
            return "{\"success\": false, \"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private Map<String, String> getPromptsMap() {
        Map<String, String> prompts = new HashMap<>();
        prompts.put("process-survey", promptService.getPrompt("process-survey"));
        prompts.put("recommendations", promptService.getPrompt("recommendations"));
        prompts.put("suspicion", promptService.getPrompt("suspicion"));
        prompts.put("ai-questions", promptService.getPrompt("ai-questions"));
        return prompts;
    }
}