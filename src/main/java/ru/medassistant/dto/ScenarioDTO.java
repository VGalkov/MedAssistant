package ru.medassistant.dto;

import java.util.List;

public class ScenarioDTO {
    private Long id;
    private String name;
    private List<QuestionDTO> questions;

    public ScenarioDTO() {}

    public ScenarioDTO(Long id, String name, List<QuestionDTO> questions) {
        this.id = id;
        this.name = name;
        this.questions = questions;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<QuestionDTO> getQuestions() { return questions; }
    public void setQuestions(List<QuestionDTO> questions) { this.questions = questions; }
}