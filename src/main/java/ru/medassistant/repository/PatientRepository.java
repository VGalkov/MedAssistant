package ru.medassistant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.medassistant.model.Patient;
import java.util.Optional;
import java.util.List;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {
    Optional<Patient> findByPhone(String phone);
    List<Patient> findByLastNameContainingIgnoreCase(String lastName);
}
