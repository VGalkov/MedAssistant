package ru.medassistant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.medassistant.model.Survey;
import java.util.List;
import java.util.Optional;

@Repository
public interface SurveyRepository extends JpaRepository<Survey, Long> {
    List<Survey> findByPatientIdOrderByStartedAtDesc(Long patientId);
    List<Survey> findByStatusOrderByStartedAtDesc(String status);
    Optional<Survey> findFirstByPatientIdAndStatusOrderByStartedAtDesc(Long patientId, String status);
}
