package com.demo.clinic.controller;

import com.demo.clinic.dto.request.DoctorRequest;
import com.demo.clinic.dto.response.DoctorResponse;
import com.demo.clinic.model.enums.Specialization;
import com.demo.clinic.service.DoctorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/doctors")
@Tag(name = "Doctor Management", description = "APIs for managing doctors")
public class DoctorController {

    private final DoctorService doctorService;

    public DoctorController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @GetMapping
    @Operation(summary = "Get all doctors")
    public ResponseEntity<List<DoctorResponse>> getAllDoctors() {
        return ResponseEntity.ok(doctorService.getAllDoctors());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get doctor by ID")
    public ResponseEntity<DoctorResponse> getDoctorById(@PathVariable Long id) {
        return ResponseEntity.ok(doctorService.getDoctorById(id));
    }

    @GetMapping("/search/email")
    @Operation(summary = "Find doctor by email")
    public ResponseEntity<DoctorResponse> getDoctorByEmail(@RequestParam String email) {
        return ResponseEntity.ok(doctorService.getDoctorByEmail(email));
    }

    @GetMapping("/search/name")
    @Operation(summary = "Search doctors by name")
    public ResponseEntity<List<DoctorResponse>> searchByName(@RequestParam String name) {
        return ResponseEntity.ok(doctorService.searchDoctorsByName(name));
    }

    @GetMapping("/specialization/{specialization}")
    @Operation(summary = "Get doctors by specialization")
    public ResponseEntity<List<DoctorResponse>> getBySpecialization(
            @PathVariable Specialization specialization) {
        return ResponseEntity.ok(doctorService.getDoctorsBySpecialization(specialization));
    }

    @GetMapping("/available")
    @Operation(summary = "Get all available doctors")
    public ResponseEntity<List<DoctorResponse>> getAvailableDoctors() {
        return ResponseEntity.ok(doctorService.getAvailableDoctors());
    }

    @PostMapping
    @Operation(summary = "Register a new doctor")
    public ResponseEntity<DoctorResponse> createDoctor(@Valid @RequestBody DoctorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(doctorService.createDoctor(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update doctor details")
    public ResponseEntity<DoctorResponse> updateDoctor(
            @PathVariable Long id, @Valid @RequestBody DoctorRequest request) {
        return ResponseEntity.ok(doctorService.updateDoctor(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a doctor")
    public ResponseEntity<Void> deleteDoctor(@PathVariable Long id) {
        doctorService.deleteDoctor(id);
        return ResponseEntity.noContent().build();
    }
}
