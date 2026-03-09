package com.demo.clinic.service;

import com.demo.clinic.dto.request.AppointmentRequest;
import com.demo.clinic.dto.response.AppointmentResponse;
import com.demo.clinic.exception.BusinessRuleException;
import com.demo.clinic.exception.DuplicateResourceException;
import com.demo.clinic.exception.ResourceNotFoundException;
import com.demo.clinic.model.Appointment;
import com.demo.clinic.model.Doctor;
import com.demo.clinic.model.Patient;
import com.demo.clinic.model.enums.AppointmentStatus;
import com.demo.clinic.repository.AppointmentRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final DoctorService doctorService;
    private final PatientService patientService;
    private final Counter appointmentCreatedCounter;
    private final Counter appointmentCancelledCounter;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              DoctorService doctorService,
                              PatientService patientService,
                              MeterRegistry meterRegistry) {
        this.appointmentRepository = appointmentRepository;
        this.doctorService = doctorService;
        this.patientService = patientService;

        this.appointmentCreatedCounter = Counter.builder("clinic.appointments.created")
                .description("Total number of appointments created")
                .register(meterRegistry);

        this.appointmentCancelledCounter = Counter.builder("clinic.appointments.cancelled")
                .description("Total number of appointments cancelled")
                .register(meterRegistry);

        Gauge.builder("clinic.appointments.scheduled.total", appointmentRepository,
                        repo -> repo.countByStatus(AppointmentStatus.SCHEDULED))
                .description("Current number of scheduled appointments")
                .register(meterRegistry);

        Gauge.builder("clinic.appointments.today.total", appointmentRepository,
                        repo -> repo.countByDate(LocalDate.now()))
                .description("Total appointments scheduled for today")
                .register(meterRegistry);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAllAppointments() {
        return appointmentRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AppointmentResponse getAppointmentById(Long id) {
        return appointmentRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", id));
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsByPatientId(Long patientId) {
        // Validate patient exists first
        patientService.getPatientById(patientId);
        return appointmentRepository.findByPatientPatientId(patientId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsByDoctorId(Long doctorId) {
        // Validate doctor exists first
        doctorService.getDoctorById(doctorId);
        return appointmentRepository.findByDoctorDoctorId(doctorId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsByDate(LocalDate date) {
        return appointmentRepository.findByAppointmentDate(date).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsByStatus(AppointmentStatus status) {
        return appointmentRepository.findByStatus(status).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getDoctorScheduleForDate(Long doctorId, LocalDate date) {
        doctorService.getDoctorById(doctorId);
        return appointmentRepository.findByDoctorDoctorIdAndAppointmentDate(doctorId, date).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public AppointmentResponse createAppointment(AppointmentRequest request) {
        // Validate doctor exists and is available
        Doctor doctor = doctorService.getDoctorEntityById(request.getDoctorId());
        if (!doctor.isAvailable()) {
            throw new BusinessRuleException(
                    "Doctor with id " + request.getDoctorId() + " is currently not available for appointments");
        }

        // Validate patient exists
        Patient patient = patientService.getPatientEntityById(request.getPatientId());

        // Check for duplicate slot — same doctor, same date, same time
        if (appointmentRepository.existsByDoctorDoctorIdAndAppointmentDateAndSlot(
                request.getDoctorId(), request.getAppointmentDate(), request.getSlot())) {
            throw new DuplicateResourceException(
                    "Doctor already has an appointment on " + request.getAppointmentDate()
                            + " at " + request.getSlot() + ". Please choose a different slot.");
        }

        // Check patient doesn't have another appointment on the same date and time
        boolean patientHasConflict = appointmentRepository
                .findByPatientPatientId(request.getPatientId())
                .stream()
                .anyMatch(a -> a.getAppointmentDate().equals(request.getAppointmentDate())
                        && a.getSlot().equals(request.getSlot())
                        && a.getStatus() != AppointmentStatus.CANCELLED);
        if (patientHasConflict) {
            throw new BusinessRuleException(
                    "Patient already has an appointment on " + request.getAppointmentDate()
                            + " at " + request.getSlot());
        }

        Appointment appointment = new Appointment(
                doctor, patient,
                request.getAppointmentDate(),
                request.getSlot(),
                request.getReason()
        );
        appointment.setNotes(request.getNotes());

        Appointment saved = appointmentRepository.save(appointment);
        appointmentCreatedCounter.increment();
        return toResponse(saved);
    }

    public AppointmentResponse updateAppointmentStatus(Long id, AppointmentStatus newStatus) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", id));

        validateStatusTransition(appointment.getStatus(), newStatus);
        appointment.setStatus(newStatus);

        if (newStatus == AppointmentStatus.CANCELLED) {
            appointmentCancelledCounter.increment();
        }

        return toResponse(appointmentRepository.save(appointment));
    }

    public AppointmentResponse updateAppointment(Long id, AppointmentRequest request) {
        Appointment existing = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", id));

        if (existing.getStatus() == AppointmentStatus.CANCELLED
                || existing.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BusinessRuleException(
                    "Cannot update a " + existing.getStatus().name().toLowerCase() + " appointment");
        }

        Doctor doctor = doctorService.getDoctorEntityById(request.getDoctorId());
        if (!doctor.isAvailable()) {
            throw new BusinessRuleException("Doctor is not available for appointments");
        }

        Patient patient = patientService.getPatientEntityById(request.getPatientId());

        // Check slot conflict, excluding this appointment
        boolean slotTaken = appointmentRepository
                .findByDoctorDoctorIdAndAppointmentDateAndSlot(
                        request.getDoctorId(), request.getAppointmentDate(), request.getSlot())
                .map(a -> !a.getAppointmentId().equals(id))
                .orElse(false);
        if (slotTaken) {
            throw new DuplicateResourceException(
                    "Doctor already has an appointment at that date and slot");
        }

        existing.setDoctor(doctor);
        existing.setPatient(patient);
        existing.setAppointmentDate(request.getAppointmentDate());
        existing.setSlot(request.getSlot());
        existing.setReason(request.getReason());
        existing.setNotes(request.getNotes());

        return toResponse(appointmentRepository.save(existing));
    }

    public void deleteAppointment(Long id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", id));

        if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BusinessRuleException("Cannot delete a completed appointment");
        }

        appointmentRepository.deleteById(id);
    }

    private void validateStatusTransition(AppointmentStatus current, AppointmentStatus next) {
        if (current == AppointmentStatus.CANCELLED) {
            throw new BusinessRuleException("Cannot change status of a cancelled appointment");
        }
        if (current == AppointmentStatus.COMPLETED) {
            throw new BusinessRuleException("Cannot change status of a completed appointment");
        }
        if (current == AppointmentStatus.NO_SHOW && next == AppointmentStatus.SCHEDULED) {
            throw new BusinessRuleException("Cannot revert a no-show appointment back to scheduled");
        }
    }

    private AppointmentResponse toResponse(Appointment appointment) {
        AppointmentResponse response = new AppointmentResponse();
        response.setAppointmentId(appointment.getAppointmentId());
        response.setDoctorId(appointment.getDoctor().getDoctorId());
        response.setDoctorName(appointment.getDoctor().getName());
        response.setDoctorSpecialization(appointment.getDoctor().getSpecialization().name());
        response.setPatientId(appointment.getPatient().getPatientId());
        response.setPatientName(appointment.getPatient().getName());
        response.setAppointmentDate(appointment.getAppointmentDate());
        response.setSlot(appointment.getSlot());
        response.setReason(appointment.getReason());
        response.setStatus(appointment.getStatus());
        response.setNotes(appointment.getNotes());
        return response;
    }
}
