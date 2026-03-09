package com.demo.clinic.config;

import com.demo.clinic.model.Appointment;
import com.demo.clinic.model.Doctor;
import com.demo.clinic.model.Patient;
import com.demo.clinic.model.enums.AppointmentStatus;
import com.demo.clinic.model.enums.Specialization;
import com.demo.clinic.repository.AppointmentRepository;
import com.demo.clinic.repository.DoctorRepository;
import com.demo.clinic.repository.PatientRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.LocalDate;
import java.time.LocalTime;

@Configuration
public class DataInitializer {

    /**
     * Seeds the database with demo data on startup.
     * Only runs when the "dev" profile is active (or no profile is set).
     * In production, set spring.profiles.active=prod to skip seeding.
     */
    @Bean
    @Profile("!prod")
    CommandLineRunner seedData(DoctorRepository doctorRepo,
                               PatientRepository patientRepo,
                               AppointmentRepository appointmentRepo) {
        return args -> {
            if (doctorRepo.count() > 0) return; // Already seeded

            // Seed Doctors
            Doctor d1 = new Doctor();
            d1.setName("Dr. Rajesh Kumar");
            d1.setGender("Male");
            d1.setSpecialization(Specialization.CARDIOLOGY);
            d1.setContact("9876543210");
            d1.setEmail("rajesh.kumar@clinic.com");
            d1.setPassword("password123");
            d1 = doctorRepo.save(d1);

            Doctor d2 = new Doctor();
            d2.setName("Dr. Priya Sharma");
            d2.setGender("Female");
            d2.setSpecialization(Specialization.PEDIATRICS);
            d2.setContact("9876543211");
            d2.setEmail("priya.sharma@clinic.com");
            d2.setPassword("password123");
            d2 = doctorRepo.save(d2);

            Doctor d3 = new Doctor();
            d3.setName("Dr. Anil Mehta");
            d3.setGender("Male");
            d3.setSpecialization(Specialization.ORTHOPEDICS);
            d3.setContact("9876543212");
            d3.setEmail("anil.mehta@clinic.com");
            d3.setPassword("password123");
            d3 = doctorRepo.save(d3);

            Doctor d4 = new Doctor();
            d4.setName("Dr. Sunita Rao");
            d4.setGender("Female");
            d4.setSpecialization(Specialization.NEUROLOGY);
            d4.setContact("9876543213");
            d4.setEmail("sunita.rao@clinic.com");
            d4.setPassword("password123");
            d4.setAvailable(false); // Unavailable doctor for testing
            d4 = doctorRepo.save(d4);

            // Seed Patients
            Patient p1 = new Patient();
            p1.setName("Amit Verma");
            p1.setDateOfBirth(LocalDate.of(1990, 5, 15));
            p1.setGender("Male");
            p1.setContact("9123456789");
            p1.setEmail("amit.verma@email.com");
            p1.setPassword("patient123");
            p1.setBloodGroup("O+");
            p1 = patientRepo.save(p1);

            Patient p2 = new Patient();
            p2.setName("Sneha Patel");
            p2.setDateOfBirth(LocalDate.of(1985, 8, 22));
            p2.setGender("Female");
            p2.setContact("9123456788");
            p2.setEmail("sneha.patel@email.com");
            p2.setPassword("patient123");
            p2.setBloodGroup("A+");
            p2 = patientRepo.save(p2);

            Patient p3 = new Patient();
            p3.setName("Rahul Singh");
            p3.setDateOfBirth(LocalDate.of(2000, 3, 10));
            p3.setGender("Male");
            p3.setContact("9123456787");
            p3.setEmail("rahul.singh@email.com");
            p3.setPassword("patient123");
            p3.setBloodGroup("B+");
            p3 = patientRepo.save(p3);

            // Seed Appointments
            LocalDate tomorrow = LocalDate.now().plusDays(1);
            LocalDate dayAfter = LocalDate.now().plusDays(2);

            Appointment a1 = new Appointment(d1, p1, tomorrow, LocalTime.of(9, 0), "Chest pain checkup");
            a1.setStatus(AppointmentStatus.CONFIRMED);
            appointmentRepo.save(a1);

            Appointment a2 = new Appointment(d1, p2, tomorrow, LocalTime.of(10, 0), "Routine cardiac check");
            appointmentRepo.save(a2);

            Appointment a3 = new Appointment(d2, p3, tomorrow, LocalTime.of(11, 0), "Child fever and cold");
            appointmentRepo.save(a3);

            Appointment a4 = new Appointment(d3, p1, dayAfter, LocalTime.of(14, 0), "Knee pain follow-up");
            appointmentRepo.save(a4);

            System.out.println("=== Demo data seeded: 4 doctors, 3 patients, 4 appointments ===");
        };
    }
}
