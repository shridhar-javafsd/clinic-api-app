package com.demo.clinic.dto.request;

import com.demo.clinic.model.enums.Specialization;
import jakarta.validation.constraints.*;

public class DoctorRequest {

    @NotBlank(message = "Doctor name is required")
    @Size(min = 2, max = 100)
    private String name;

    @NotBlank(message = "Gender is required")
    private String gender;

    @NotNull(message = "Specialization is required")
    private Specialization specialization;

    @NotBlank(message = "Contact is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Contact must be a 10-digit number")
    private String contact;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6)
    private String password;

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
}
