package ru.medassistant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.medassistant.model.Survey;
import java.util.List;
import java.util.Optional;

@Repository
public interface SurveyRepository extends JpaRepository<Survey, Long> {
    List<Survey> findByPatientIdOrderByStartedAtDesc(Long patientId);
    List<Survey> findByStatusOrderByStartedAtDesc(String status);
    Optional<Survey> findFirstByPatientIdAndStatusOrderByStartedAtDesc(Long patientId, String status);

    /**
     * Загружает опрос с ответами (чтобы избежать LazyInitializationException)
     */
    @Query("SELECT s FROM Survey s LEFT JOIN FETCH s.answers WHERE s.id = :id")
    Optional<Survey> findByIdWithAnswers(@Param("id") Long id);
}