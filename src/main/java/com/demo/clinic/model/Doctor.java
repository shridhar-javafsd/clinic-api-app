package com.demo.clinic.model;

import com.demo.clinic.model.enums.Specialization;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "doctors")
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "doctor_id")
    private Long doctorId;

    @NotBlank(message = "Doctor name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    @Column(name = "name", nullable = false)
    private String name;

    @NotBlank(message = "Gender is required")
    @Column(name = "gender", nullable = false)
    private String gender;

    @NotNull(message = "Specialization is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "specialization", nullable = false)
    private Specialization specialization;

    @NotBlank(message = "Contact number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Contact must be a 10-digit number")
    @Column(name = "contact", nullable = false)
    private String contact;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "available", nullable = false)
    private boolean available = true;

    @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Appointment> appointments = new ArrayList<>();

    public Doctor() {}

    public Doctor(Long doctorId, String name, String gender, Specialization specialization,
                  String contact, String email, String password) {
        this.doctorId = doctorId;
        this.name = name;
        this.gender = gender;
        this.specialization = specialization;
        this.contact = contact;
        this.email = email;
        this.password = password;
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

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    public List<Appointment> getAppointments() { return appointments; }
    public void setAppointments(List<Appointment> appointments) { this.appointments = appointments; }

    @Override
    public String toString() {
        return "Doctor [doctorId=" + doctorId + ", name=" + name + ", specialization=" + specialization + "]";
    }
}
