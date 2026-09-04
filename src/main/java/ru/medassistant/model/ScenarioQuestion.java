package ru.medassistant.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "scenario_questions")
@Data
@NoArgsConstructor
public class ScenarioQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scenario_id", nullable = false)
    private Scenario scenario;

    @Column(nullable = false)
    private Integer orderIndex;

    @Column(nullable = false, length = 1000)
    private String questionText;

    @Column
    private String questionType;

    @Column(length = 2000)
    private String options;

    @Column(length = 1000)
    private String nextQuestionCondition;

    @Column(length = 500)
    private String category;

    @Column
    private Boolean isRequired = false;
}
