package com.demo.clinic.dto.response;

import com.demo.clinic.model.enums.AppointmentStatus;

import java.time.LocalDate;
import java.time.LocalTime;

public class AppointmentResponse {

    private Long appointmentId;
    private Long doctorId;
    private String doctorName;
    private String doctorSpecialization;
    private Long patientId;
    private String patientName;
    private LocalDate appointmentDate;
    private LocalTime slot;
    private String reason;
    private AppointmentStatus status;
    private String notes;

    public AppointmentResponse() {}

    public Long getAppointmentId() { return appointmentId; }
    public void setAppointmentId(Long appointmentId) { this.appointmentId = appointmentId; }

    public Long getDoctorId() { return doctorId; }
    public void setDoctorId(Long doctorId) { this.doctorId = doctorId; }

    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }

    public String getDoctorSpecialization() { return doctorSpecialization; }
    public void setDoctorSpecialization(String doctorSpecialization) { this.doctorSpecialization = doctorSpecialization; }

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

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
}
