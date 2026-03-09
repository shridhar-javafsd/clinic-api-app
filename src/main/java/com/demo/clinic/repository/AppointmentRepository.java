package com.demo.clinic.repository;

import com.demo.clinic.model.Appointment;
import com.demo.clinic.model.enums.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByPatientPatientId(Long patientId);

    List<Appointment> findByDoctorDoctorId(Long doctorId);

    List<Appointment> findByStatus(AppointmentStatus status);

    List<Appointment> findByAppointmentDate(LocalDate date);

    List<Appointment> findByDoctorDoctorIdAndAppointmentDate(Long doctorId, LocalDate date);

    List<Appointment> findByPatientPatientIdAndStatus(Long patientId, AppointmentStatus status);

    Optional<Appointment> findByDoctorDoctorIdAndAppointmentDateAndSlot(
            Long doctorId, LocalDate date, LocalTime slot);

    boolean existsByDoctorDoctorIdAndAppointmentDateAndSlot(
            Long doctorId, LocalDate date, LocalTime slot);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.status = :status")
    long countByStatus(@Param("status") AppointmentStatus status);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.appointmentDate = :date")
    long countByDate(@Param("date") LocalDate date);
}
