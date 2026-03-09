package com.demo.clinic.service;

import com.demo.clinic.dto.request.PatientRequest;
import com.demo.clinic.dto.response.PatientResponse;
import com.demo.clinic.exception.DuplicateResourceException;
import com.demo.clinic.exception.ResourceNotFoundException;
import com.demo.clinic.model.Patient;
import com.demo.clinic.repository.PatientRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class PatientService {

    private final PatientRepository patientRepository;
    private final Counter patientCreatedCounter;

    public PatientService(PatientRepository patientRepository, MeterRegistry meterRegistry) {
        this.patientRepository = patientRepository;
        this.patientCreatedCounter = Counter.builder("clinic.patients.created")
                .description("Total number of patients registered")
                .register(meterRegistry);
    }

    @Transactional(readOnly = true)
    public List<PatientResponse> getAllPatients() {
        return patientRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PatientResponse getPatientById(Long id) {
        return patientRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", id));
    }

    @Transactional(readOnly = true)
    public PatientResponse getPatientByEmail(String email) {
        return patientRepository.findByEmail(email)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "email", email));
    }

    @Transactional(readOnly = true)
    public List<PatientResponse> searchPatientsByName(String name) {
        return patientRepository.findByNameContainingIgnoreCase(name).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public PatientResponse createPatient(PatientRequest request) {
        if (patientRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Patient", "email", request.getEmail());
        }
        Patient patient = toEntity(request);
        Patient saved = patientRepository.save(patient);
        patientCreatedCounter.increment();
        return toResponse(saved);
    }

    public PatientResponse updatePatient(Long id, PatientRequest request) {
        Patient existing = patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", id));

        if (!existing.getEmail().equals(request.getEmail())
                && patientRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Patient", "email", request.getEmail());
        }

        existing.setName(request.getName());
        existing.setDateOfBirth(request.getDateOfBirth());
        existing.setGender(request.getGender());
        existing.setContact(request.getContact());
        existing.setEmail(request.getEmail());
        existing.setPassword(request.getPassword());
        existing.setBloodGroup(request.getBloodGroup());

        return toResponse(patientRepository.save(existing));
    }

    public void deletePatient(Long id) {
        if (!patientRepository.existsById(id)) {
            throw new ResourceNotFoundException("Patient", "id", id);
        }
        patientRepository.deleteById(id);
    }

    // Internal method used by AppointmentService
    public Patient getPatientEntityById(Long id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", id));
    }

    private Patient toEntity(PatientRequest request) {
        Patient patient = new Patient();
        patient.setName(request.getName());
        patient.setDateOfBirth(request.getDateOfBirth());
        patient.setGender(request.getGender());
        patient.setContact(request.getContact());
        patient.setEmail(request.getEmail());
        patient.setPassword(request.getPassword());
        patient.setBloodGroup(request.getBloodGroup());
        return patient;
    }

    public PatientResponse toResponse(Patient patient) {
        return new PatientResponse(
                patient.getPatientId(),
                patient.getName(),
                patient.getDateOfBirth(),
                patient.getGender(),
                patient.getContact(),
                patient.getEmail(),
                patient.getBloodGroup()
        );
    }
}
