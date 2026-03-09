package com.demo.clinic.dto.response;

import com.demo.clinic.model.enums.Specialization;

public class DoctorResponse {

    private Long doctorId;
    private String name;
    private String gender;
    private Specialization specialization;
    private String contact;
    private String email;
    private boolean available;

    public DoctorResponse() {}

    public DoctorResponse(Long doctorId, String name, String gender,
                          Specialization specialization, String contact,
                          String email, boolean available) {
        this.doctorId = doctorId;
        this.name = name;
        this.gender = gender;
        this.specialization = specialization;
        this.contact = contact;
        this.email = email;
        this.available = available;
    }

    public Long getDoctorId() { return doctorId; }
    public void setDoctorId(Long doctorId) { this.doctorId = doctorId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public Specialization getSpecialization() { return specialization; }
    public void setSpecialization(Specialization specialization) { this.specialization = specialization; }

    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
}
