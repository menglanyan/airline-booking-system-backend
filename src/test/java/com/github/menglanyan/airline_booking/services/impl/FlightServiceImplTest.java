package com.github.menglanyan.airline_booking.services.impl;

import com.github.menglanyan.airline_booking.dtos.CreateFlightRequest;
import com.github.menglanyan.airline_booking.dtos.FlightDTO;
import com.github.menglanyan.airline_booking.dtos.Response;
import com.github.menglanyan.airline_booking.entities.Airport;
import com.github.menglanyan.airline_booking.entities.Flight;
import com.github.menglanyan.airline_booking.entities.Role;
import com.github.menglanyan.airline_booking.entities.User;
import com.github.menglanyan.airline_booking.enums.FlightStatus;
import com.github.menglanyan.airline_booking.exceptions.BadRequestException;
import com.github.menglanyan.airline_booking.exceptions.NotFoundException;
import com.github.menglanyan.airline_booking.repo.AirportRepo;
import com.github.menglanyan.airline_booking.repo.FlightRepo;
import com.github.menglanyan.airline_booking.repo.UserRepo;
import com.github.menglanyan.airline_booking.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightServiceImplTest {

    @Mock private FlightRepo flightRepo;
    @Mock private AirportRepo airportRepo;
    @Mock private UserRepo userRepo;
    @Mock private UserService userService;

    // use real mapper for simplicity
    private ModelMapper modelMapper;
    private FlightServiceImpl service;

    @BeforeEach
    void setup() {
        modelMapper = new ModelMapper();
        service = new FlightServiceImpl(flightRepo, airportRepo, userRepo, modelMapper, userService);
    }

    @Test
    void createFlight_success_withPilot() {
        var req = new CreateFlightRequest();
        req.setFlightNumber("AB123");
        req.setDepartureAirportIataCode("JFK");
        req.setArrivalAirportIataCode("LAX");
        req.setDepartureTime(LocalDateTime.now().plusDays(1));
        req.setArrivalTime(LocalDateTime.now().plusDays(1).plusHours(5));
        req.setBasePrice(new BigDecimal("199.99"));
        req.setPilotId(10L);

        when(flightRepo.existsByFlightNumber("AB123")).thenReturn(false);

        Airport dep = new Airport(); dep.setIataCode("JFK");
        Airport arr = new Airport(); arr.setIataCode("LAX");
        when(airportRepo.findByIataCode("JFK")).thenReturn(Optional.of(dep));
        when(airportRepo.findByIataCode("LAX")).thenReturn(Optional.of(arr));

        User pilot = new User();
        pilot.setId(10L);
        pilot.setRoles(List.of(role("PILOT")));
        when(userRepo.findById(10L)).thenReturn(Optional.of(pilot));

        var resp = service.createFlight(req);
        assertEquals(200, resp.getStatusCode());
        assertEquals("Flight saved successfully", resp.getMessage());

        // verify persisted data correctness
        ArgumentCaptor<Flight> ac = ArgumentCaptor.forClass(Flight.class);
        verify(flightRepo).save(ac.capture());
        Flight saved = ac.getValue();
        assertEquals("AB123", saved.getFlightNumber());
        assertEquals(FlightStatus.SCHEDULED, saved.getStatus());
        assertEquals(pilot, saved.getAssignedPilot());
        assertEquals(dep, saved.getDepartureAirport());
        assertEquals(arr, saved.getArrivalAirport());
    }

    @Test
    void createFlight_reject_arrivalBeforeDeparture() {
        var req = new CreateFlightRequest();
        req.setFlightNumber("AB124");
        req.setDepartureTime(LocalDateTime.now().plusDays(1));
        req.setArrivalTime(LocalDateTime.now().minusDays(1));

        assertThrows(BadRequestException.class, () -> service.createFlight(req));
        verifyNoInteractions(flightRepo);
    }

    @Test
    void createFlight_reject_duplicateFlightNumber() {
        var req = minimalCreateReq();
        when(flightRepo.existsByFlightNumber(req.getFlightNumber())).thenReturn(true);
        assertThrows(BadRequestException.class, () -> service.createFlight(req));
    }

    @Test
    void createFlight_reject_missingAirports() {
        var req = minimalCreateReq();
        when(flightRepo.existsByFlightNumber(req.getFlightNumber())).thenReturn(false);
        when(airportRepo.findByIataCode("JFK")).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.createFlight(req));
    }

    @Test
    void createFlight_reject_pilotNotCertified() {
        var req = minimalCreateReq();
        req.setPilotId(99L);

        when(flightRepo.existsByFlightNumber("AB555")).thenReturn(false);
        when(airportRepo.findByIataCode("JFK")).thenReturn(Optional.of(new Airport()));
        when(airportRepo.findByIataCode("LAX")).thenReturn(Optional.of(new Airport()));

        User notPilot = new User(); notPilot.setRoles(List.of(role("CUSTOMER")));
        when(userRepo.findById(99L)).thenReturn(Optional.of(notPilot));

        assertThrows(BadRequestException.class, () -> service.createFlight(req));
    }

    @Test
    void getFlightById_success() {
        Flight f = new Flight();
        f.setId(1L);
        when(flightRepo.findById(1L)).thenReturn(Optional.of(f));

        Response<FlightDTO> resp = service.getFlightById(1L);
        assertEquals(200, resp.getStatusCode());
        assertNotNull(resp.getData());
        assertEquals(1L, resp.getData().getId());
    }

    @Test
    void getAllFlights_success() {
        Flight f1 = new Flight(); f1.setId(1L);
        Flight f2 = new Flight(); f2.setId(2L);

        Page<Flight> flightPage = new PageImpl<>(List.of(f1, f2));

        when(flightRepo.findAll(any(Pageable.class)))
                .thenReturn(flightPage);

        var resp = service.getAllFlights(0, 5);
        assertEquals(200, resp.getStatusCode());
        assertEquals(2, resp.getData().size());
    }

    @Test
    void updateFlight_success() {
        Flight existing = new Flight();
        existing.setId(5L);
        existing.setStatus(FlightStatus.SCHEDULED);

        when(flightRepo.findById(5L)).thenReturn(Optional.of(existing));
        when(userRepo.findById(10L)).thenReturn(Optional.of(userWithRole(10L, "PILOT")));

        CreateFlightRequest req = new CreateFlightRequest();
        req.setId(5L);
        req.setStatus(FlightStatus.DELAYED);
        req.setPilotId(10L);

        var resp = service.updateFlight(req);

        assertEquals(200, resp.getStatusCode());
        verify(flightRepo).save(existing);
        assertEquals(FlightStatus.DELAYED, existing.getStatus());
        assertNotNull(existing.getAssignedPilot());
        assertEquals(10L, existing.getAssignedPilot().getId());
    }

    @Test
    void searchForFlight_success() {
        when(flightRepo.findByDepartureAirportIataCodeAndArrivalAirportIataCodeAndStatusAndDepartureTimeBetween(
                eq("JFK"), eq("LAX"), eq(FlightStatus.SCHEDULED), any(), any()))
                .thenReturn(List.of(new Flight(), new Flight()));

        var resp = service.searchForFlight("JFK", "LAX", FlightStatus.SCHEDULED, LocalDate.now());
        assertEquals(200, resp.getStatusCode());
        assertEquals(2, resp.getData().size());
    }

    @Test
    void getAllCities_and_getAllCountries() {
        var cities = service.getAllCities();
        var countries = service.getAllCountries();
        assertEquals(200, cities.getStatusCode());
        assertTrue(cities.getData().size() > 0);
        assertEquals(200, countries.getStatusCode());
        assertTrue(countries.getData().size() > 0);
    }

    @Test
    void getMyFlights_onlyPilotAllowed() {
        var pilot = userWithRole(7L, "PILOT");
        when(userService.currentUser()).thenReturn(pilot);
        when(flightRepo.findByAssignedPilotIdOrderByDepartureTimeDesc(7L))
                .thenReturn(List.of(new Flight(), new Flight()));

        var resp = service.getMyFlights();
        assertEquals(200, resp.getStatusCode());
        assertEquals(2, resp.getData().size());
    }

    @Test
    void getMyFlights_nonPilotRejected() {
        var customer = userWithRole(3L, "CUSTOMER");
        when(userService.currentUser()).thenReturn(customer);
        assertThrows(BadRequestException.class, () -> service.getMyFlights());
    }

    // helpers
    private Role role(String name) { Role r = new Role(); r.setName(name); return r; }

    private User userWithRole(Long id, String roleName) {
        User u = new User();
        u.setId(id);
        u.setRoles(List.of(role(roleName)));
        return u;
    }

    private CreateFlightRequest minimalCreateReq() {
        var req = new CreateFlightRequest();
        req.setFlightNumber("AB555");
        req.setDepartureAirportIataCode("JFK");
        req.setArrivalAirportIataCode("LAX");
        req.setDepartureTime(LocalDateTime.now().plusDays(1));
        req.setArrivalTime(LocalDateTime.now().plusDays(1).plusHours(2));
        req.setBasePrice(new BigDecimal("100.00"));
        return req;
    }
}