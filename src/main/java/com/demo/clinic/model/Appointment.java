package com.demo.clinic.model;

import com.demo.clinic.model.enums.AppointmentStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "appointments",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"doctor_id", "appointment_date", "slot"},
            name = "uk_doctor_date_slot")
    }
)
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "appointment_id")
    private Long appointmentId;

    @NotNull(message = "Doctor is required")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_appointment_doctor"))
    private Doctor doctor;

    @NotNull(message = "Patient is required")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_appointment_patient"))
    private Patient patient;

    @NotNull(message = "Appointment date is required")
    @FutureOrPresent(message = "Appointment date cannot be in the past")
    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @NotNull(message = "Slot time is required")
    @Column(name = "slot", nullable = false)
    private LocalTime slot;

    @NotBlank(message = "Reason for visit is required")
    @Column(name = "reason", nullable = false)
    private String reason;

    @NotNull(message = "Status is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AppointmentStatus status = AppointmentStatus.SCHEDULED;

    @Column(name = "notes")
    private String notes;

    public Appointment() {}

    public Appointment(Doctor doctor, Patient patient, LocalDate appointmentDate,
                       LocalTime slot, String reason) {
        this.doctor = doctor;
        this.patient = patient;
        this.appointmentDate = appointmentDate;
        this.slot = slot;
        this.reason = reason;
        this.status = AppointmentStatus.SCHEDULED;
    }

    public Long getAppointmentId() { return appointmentId; }
    public void setAppointmentId(Long appointmentId) { this.appointmentId = appointmentId; }

    public Doctor getDoctor() { return doctor; }
    public void setDoctor(Doctor doctor) { this.doctor = doctor; }

    public Patient getPatient() { return patient; }
    public void setPatient(Patient patient) { this.patient = patient; }

    public LocalDate getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; }

    public LocalTime getSlot() { return slot; }
    public void setSlot(LocalTime slot) { this.slot = slot; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public AppointmentStatus getStatus() { return status; }
    public void setStatus(AppointmentStatus status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    @Override
    public String toString() {
        return "Appointment [id=" + appointmentId + ", doctor=" + (doctor != null ? doctor.getName() : null)
                + ", patient=" + (patient != null ? patient.getName() : null)
                + ", date=" + appointmentDate + ", slot=" + slot + ", status=" + status + "]";
    }
}
