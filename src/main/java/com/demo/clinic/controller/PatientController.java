package com.demo.clinic.controller;

import com.demo.clinic.dto.request.PatientRequest;
import com.demo.clinic.dto.response.PatientResponse;
import com.demo.clinic.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/patients")
@Tag(name = "Patient Management", description = "APIs for managing patients")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @GetMapping
    @Operation(summary = "Get all patients")
    public ResponseEntity<List<PatientResponse>> getAllPatients() {
        return ResponseEntity.ok(patientService.getAllPatients());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get patient by ID")
    public ResponseEntity<PatientResponse> getPatientById(@PathVariable Long id) {
        return ResponseEntity.ok(patientService.getPatientById(id));
    }

    @GetMapping("/search/email")
    @Operation(summary = "Find patient by email")
    public ResponseEntity<PatientResponse> getPatientByEmail(@RequestParam String email) {
        return ResponseEntity.ok(patientService.getPatientByEmail(email));
    }

    @GetMapping("/search/name")
    @Operation(summary = "Search patients by name")
    public ResponseEntity<List<PatientResponse>> searchByName(@RequestParam String name) {
        return ResponseEntity.ok(patientService.searchPatientsByName(name));
    }

    @PostMapping
    @Operation(summary = "Register a new patient")
    public ResponseEntity<PatientResponse> createPatient(@Valid @RequestBody PatientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(patientService.createPatient(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update patient details")
    public ResponseEntity<PatientResponse> updatePatient(
            @PathVariable Long id, @Valid @RequestBody PatientRequest request) {
        return ResponseEntity.ok(patientService.updatePatient(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a patient")
    public ResponseEntity<Void> deletePatient(@PathVariable Long id) {
        patientService.deletePatient(id);
        return ResponseEntity.noContent().build();
    }
}
