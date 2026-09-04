package ru.medassistant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.medassistant.model.Survey;
import java.util.List;

@Repository
public interface SurveyRepository extends JpaRepository<Survey, Long> {

    // ✅ Оригинальный метод (для SurveyService)
    List<Survey> findByStatusOrderByStartedAtDesc(String status);

    // ✅ Новый метод (для DoctorController - все опросы)
    List<Survey> findAllByOrderByCompletedAtDesc();

    // ✅ Поиск по пациенту
    List<Survey> findByPatientIdOrderByStartedAtDesc(Long patientId);
}