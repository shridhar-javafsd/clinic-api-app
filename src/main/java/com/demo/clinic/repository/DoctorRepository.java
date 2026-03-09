package com.demo.clinic.repository;

import com.demo.clinic.model.Doctor;
import com.demo.clinic.model.enums.Specialization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    Optional<Doctor> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Doctor> findBySpecialization(Specialization specialization);

    List<Doctor> findByAvailableTrue();

    List<Doctor> findBySpecializationAndAvailableTrue(Specialization specialization);

    List<Doctor> findByNameContainingIgnoreCase(String name);
}
