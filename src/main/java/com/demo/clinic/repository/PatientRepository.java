package com.demo.clinic.repository;

import com.demo.clinic.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Patient> findByNameContainingIgnoreCase(String name);

    List<Patient> findByBloodGroup(String bloodGroup);
}
