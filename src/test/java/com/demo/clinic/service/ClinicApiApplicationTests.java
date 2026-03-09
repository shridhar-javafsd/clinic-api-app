package com.demo.clinic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.demo.clinic.model.Appointment;
import com.demo.clinic.repository.AppointmentRepository;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

	@Mock
	private AppointmentRepository appointmentRepository;

	@InjectMocks
	private AppointmentService appointmentService;

	private Appointment appointment1;
	private Appointment appointment2;

	@BeforeEach
	void setUp() {
		appointment1 = new Appointment();
		appointment1.setPatientId(101L);
		appointment1.setDoctorId(201L);
		appointment1.setSlot("10:00 AM");
		appointment1.setStatus("SCHEDULED");

		appointment2 = new Appointment();
		appointment2.setPatientId(102L);
		appointment2.setDoctorId(201L);
		appointment2.setSlot("11:00 AM");
		appointment2.setStatus("SCHEDULED");
	}

	// ─────────────────────────────────────────────────────────────
	// getAllAppointments
	// ─────────────────────────────────────────────────────────────

	@Test
	void getAllAppointments_ShouldReturnAllAppointments() {
		when(appointmentRepository.findAll()).thenReturn(Arrays.asList(appointment1, appointment2));

		List<Appointment> result = appointmentService.getAllAppointments();

		assertEquals(2, result.size());
		verify(appointmentRepository, times(1)).findAll();
	}

	@Test
	void getAllAppointments_WhenNoneExist_ShouldReturnEmptyList() {
		when(appointmentRepository.findAll()).thenReturn(Collections.emptyList());

		List<Appointment> result = appointmentService.getAllAppointments();

		assertTrue(result.isEmpty());
	}

	// ─────────────────────────────────────────────────────────────
	// getAppointmentById
	// ─────────────────────────────────────────────────────────────

	@Test
	void getAppointmentById_WhenExists_ShouldReturnAppointment() {
		when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment1));

		Optional<Appointment> result = appointmentService.getAppointmentById(1L);

		assertTrue(result.isPresent());
		verify(appointmentRepository, times(1)).findById(1L);
	}

	@Test
	void getAppointmentById_WhenNotFound_ShouldReturnEmpty() {
		when(appointmentRepository.findById(99L)).thenReturn(Optional.empty());

		Optional<Appointment> result = appointmentService.getAppointmentById(99L);

		assertFalse(result.isPresent());
	}

	// ─────────────────────────────────────────────────────────────
	// getAppointmentsByPatientId
	// ─────────────────────────────────────────────────────────────

	@Test
	void getAppointmentsByPatientId_ShouldReturnPatientAppointments() {
		when(appointmentRepository.findByPatientId(101L)).thenReturn(List.of(appointment1));

		List<Appointment> result = appointmentService.getAppointmentsByPatientId(101L);

		assertEquals(1, result.size());
		assertEquals(101L, result.get(0).getPatientId());
	}

	@Test
	void getAppointmentsByPatientId_WhenNoneFound_ShouldReturnEmptyList() {
		when(appointmentRepository.findByPatientId(999L)).thenReturn(Collections.emptyList());

		List<Appointment> result = appointmentService.getAppointmentsByPatientId(999L);

		assertTrue(result.isEmpty());
	}

	// ─────────────────────────────────────────────────────────────
	// getAppointmentsByDoctorId
	// ─────────────────────────────────────────────────────────────

	@Test
	void getAppointmentsByDoctorId_ShouldReturnDoctorAppointments() {
		when(appointmentRepository.findByDoctorId(201L)).thenReturn(Arrays.asList(appointment1, appointment2));

		List<Appointment> result = appointmentService.getAppointmentsByDoctorId(201L);

		assertEquals(2, result.size());
		assertTrue(result.stream().allMatch(a -> a.getDoctorId().equals(201L)));
	}

	@Test
	void getAppointmentsByDoctorId_WhenNoneFound_ShouldReturnEmptyList() {
		when(appointmentRepository.findByDoctorId(999L)).thenReturn(Collections.emptyList());

		List<Appointment> result = appointmentService.getAppointmentsByDoctorId(999L);

		assertTrue(result.isEmpty());
	}

	// ─────────────────────────────────────────────────────────────
	// createAppointment
	// ─────────────────────────────────────────────────────────────

	@Test
	void createAppointment_ShouldSaveAndReturnAppointment() {
		when(appointmentRepository.save(appointment1)).thenReturn(appointment1);

		Appointment result = appointmentService.createAppointment(appointment1);

		assertNotNull(result);
		assertEquals(101L, result.getPatientId());
		assertEquals(201L, result.getDoctorId());
		verify(appointmentRepository, times(1)).save(appointment1);
	}

	// ─────────────────────────────────────────────────────────────
	// updateAppointment
	// ─────────────────────────────────────────────────────────────

	@Test
	void updateAppointment_WhenExists_ShouldUpdateAndReturn() {
		Appointment updated = new Appointment();
		updated.setDoctorId(202L);
		updated.setPatientId(101L);
		updated.setSlot("02:00 PM");
		updated.setStatus("RESCHEDULED");

		when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment1));
		when(appointmentRepository.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));

		Appointment result = appointmentService.updateAppointment(1L, updated);

		assertEquals(202L, result.getDoctorId());
		assertEquals("RESCHEDULED", result.getStatus());
		assertEquals("02:00 PM", result.getSlot());
		verify(appointmentRepository, times(1)).save(appointment1);
	}

	@Test
	void updateAppointment_WhenNotFound_ShouldThrowRuntimeException() {
		when(appointmentRepository.findById(99L)).thenReturn(Optional.empty());

		RuntimeException exception = assertThrows(RuntimeException.class,
				() -> appointmentService.updateAppointment(99L, appointment1));

		assertEquals("Appointment not found with id: 99", exception.getMessage());
		verify(appointmentRepository, never()).save(any());
	}

	// ─────────────────────────────────────────────────────────────
	// deleteAppointment
	// ─────────────────────────────────────────────────────────────

	@Test
	void deleteAppointment_ShouldCallDeleteById() {
		doNothing().when(appointmentRepository).deleteById(1L);

		appointmentService.deleteAppointment(1L);

		verify(appointmentRepository, times(1)).deleteById(1L);
	}

	@Test
	void deleteAppointment_ShouldOnlyCallDeleteOnce() {
		doNothing().when(appointmentRepository).deleteById(1L);

		appointmentService.deleteAppointment(1L);

		verify(appointmentRepository, times(1)).deleteById(1L);
		verifyNoMoreInteractions(appointmentRepository);
	}
}