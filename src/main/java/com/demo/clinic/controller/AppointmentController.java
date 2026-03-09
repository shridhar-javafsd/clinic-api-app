package com.demo.clinic.controller;

import com.demo.clinic.dto.request.AppointmentRequest;
import com.demo.clinic.dto.response.AppointmentResponse;
import com.demo.clinic.model.enums.AppointmentStatus;
import com.demo.clinic.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/appointments")
@Tag(name = "Appointment Management", description = "APIs for managing appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping
    @Operation(summary = "Get all appointments")
    public ResponseEntity<List<AppointmentResponse>> getAllAppointments() {
        return ResponseEntity.ok(appointmentService.getAllAppointments());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get appointment by ID")
    public ResponseEntity<AppointmentResponse> getAppointmentById(@PathVariable Long id) {
        return ResponseEntity.ok(appointmentService.getAppointmentById(id));
    }

    @GetMapping("/patient/{patientId}")
    @Operation(summary = "Get all appointments for a patient")
    public ResponseEntity<List<AppointmentResponse>> getByPatient(@PathVariable Long patientId) {
        return ResponseEntity.ok(appointmentService.getAppointmentsByPatientId(patientId));
    }

    @GetMapping("/doctor/{doctorId}")
    @Operation(summary = "Get all appointments for a doctor")
    public ResponseEntity<List<AppointmentResponse>> getByDoctor(@PathVariable Long doctorId) {
        return ResponseEntity.ok(appointmentService.getAppointmentsByDoctorId(doctorId));
    }

    @GetMapping("/doctor/{doctorId}/schedule")
    @Operation(summary = "Get doctor's schedule for a specific date")
    public ResponseEntity<List<AppointmentResponse>> getDoctorSchedule(
            @PathVariable Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(appointmentService.getDoctorScheduleForDate(doctorId, date));
    }

    @GetMapping("/date/{date}")
    @Operation(summary = "Get all appointments for a specific date")
    public ResponseEntity<List<AppointmentResponse>> getByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(appointmentService.getAppointmentsByDate(date));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get appointments by status")
    public ResponseEntity<List<AppointmentResponse>> getByStatus(
            @PathVariable AppointmentStatus status) {
        return ResponseEntity.ok(appointmentService.getAppointmentsByStatus(status));
    }

    @PostMapping
    @Operation(summary = "Book a new appointment")
    public ResponseEntity<AppointmentResponse> createAppointment(
            @Valid @RequestBody AppointmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(appointmentService.createAppointment(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update appointment details")
    public ResponseEntity<AppointmentResponse> updateAppointment(
            @PathVariable Long id, @Valid @RequestBody AppointmentRequest request) {
        return ResponseEntity.ok(appointmentService.updateAppointment(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update appointment status (confirm, cancel, complete, etc.)")
    public ResponseEntity<AppointmentResponse> updateStatus(
            @PathVariable Long id, @RequestParam AppointmentStatus status) {
        return ResponseEntity.ok(appointmentService.updateAppointmentStatus(id, status));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an appointment")
    public ResponseEntity<Void> deleteAppointment(@PathVariable Long id) {
        appointmentService.deleteAppointment(id);
        return ResponseEntity.noContent().build();
    }
}
