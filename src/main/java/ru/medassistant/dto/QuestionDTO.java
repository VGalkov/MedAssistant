package ru.medassistant.dto;

public class QuestionDTO {
    private Long id;
    private Integer orderIndex;
    private String questionText;
    private String questionType;
    private String category;
    private Boolean isRequired;

    public QuestionDTO() {}

    public QuestionDTO(Long id, Integer orderIndex, String questionText,
                       String questionType, String category, Boolean isRequired) {
        this.id = id;
        this.orderIndex = orderIndex;
        this.questionText = questionText;
        this.questionType = questionType;
        this.category = category;
        this.isRequired = isRequired;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getOrderIndex() { return orderIndex; }
    public void setOrderIndex(Integer orderIndex) { this.orderIndex = orderIndex; }

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }

    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Boolean getIsRequired() { return isRequired; }
    public void setIsRequired(Boolean isRequired) { this.isRequired = isRequired; }
}