package com.demo.clinic.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.demo.clinic.model.Patient;
import com.demo.clinic.repository.PatientRepository;

import java.util.List;
import java.util.Optional;

@Service
public class PatientService {

    @Autowired
    private PatientRepository patientRepository;

    public List<Patient> getAllPatients() {
        return patientRepository.findAll();
    }

    public Optional<Patient> getPatientById(Long id) {
        return patientRepository.findById(id);
    }

    public Optional<Patient> getPatientByEmail(String email) {
        return patientRepository.findByEmail(email);
    }

    public Patient createPatient(Patient patient) {
        return patientRepository.save(patient);
    }

    public Patient updatePatient(Long id, Patient updated) {
        return patientRepository.findById(id).map(existing -> {
            existing.setName(updated.getName());
            existing.setDateOfBirth(updated.getDateOfBirth());
            existing.setGender(updated.getGender());
            existing.setContact(updated.getContact());
            existing.setEmail(updated.getEmail());
            existing.setPassword(updated.getPassword());
            return patientRepository.save(existing);
        }).orElseThrow(() -> new RuntimeException("Patient not found with id: " + id));
    }

    public void deletePatient(Long id) {
        patientRepository.deleteById(id);
    }
}
