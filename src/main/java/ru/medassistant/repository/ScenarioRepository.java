package ru.medassistant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.medassistant.model.Scenario;
import java.util.List;

@Repository
public interface ScenarioRepository extends JpaRepository<Scenario, Long> {
    List<Scenario> findByIsActiveTrueOrderByCreatedAtDesc();
    List<Scenario> findBySpecialtyAndIsActiveTrue(String specialty);
}
