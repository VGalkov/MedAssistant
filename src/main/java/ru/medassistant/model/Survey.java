package ru.medassistant.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
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
    @ToString.Exclude
    private Patient patient;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(length = 10000)
    private String originalText;

    @Column(length = 10000)
    private String processedText;

    @Column(length = 10000)
    private String aiRecommendations;

    @Column(length = 5000)
    private String aiSuspicionFlags;

    @Column(length = 10000)
    private String doctorComments;

    @OneToMany(mappedBy = "survey", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
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