package com.demo.clinic.dto.response;

import java.time.LocalDate;

public class PatientResponse {

    private Long patientId;
    private String name;
    private LocalDate dateOfBirth;
    private String gender;
    private String contact;
    private String email;
    private String bloodGroup;

    public PatientResponse() {}

    public PatientResponse(Long patientId, String name, LocalDate dateOfBirth,
                           String gender, String contact, String email, String bloodGroup) {
        this.patientId = patientId;
        this.name = name;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.contact = contact;
        this.email = email;
        this.bloodGroup = bloodGroup;
    }

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }
}
