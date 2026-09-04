package ru.medassistant.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "surveys")
@Data
@NoArgsConstructor
public class Survey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(nullable = false)
    private String status;

    @Column
    private String originalText;

    @Column
    private String processedText;

    @Column
    private String aiRecommendations;

    @Column
    private String aiSuspicionFlags;

    @Column
    private String doctorComments;

    @OneToMany(mappedBy = "survey", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SurveyAnswer> answers = new ArrayList<>();

    @Column
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime completedAt;

    @Column
    private LocalDateTime reviewedAt;

    @Column
    private Long doctorId;

    @PrePersist
    protected void onCreate() {
        this.startedAt = LocalDateTime.now();
    }
}
