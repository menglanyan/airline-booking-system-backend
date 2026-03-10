package com.github.menglanyan.airline_booking.services.impl;

import com.github.menglanyan.airline_booking.dtos.BookingDTO;
import com.github.menglanyan.airline_booking.dtos.CreateBookingRequest;
import com.github.menglanyan.airline_booking.dtos.PassengerDTO;
import com.github.menglanyan.airline_booking.dtos.Response;
import com.github.menglanyan.airline_booking.entities.Booking;
import com.github.menglanyan.airline_booking.entities.Flight;
import com.github.menglanyan.airline_booking.entities.User;
import com.github.menglanyan.airline_booking.enums.BookingStatus;
import com.github.menglanyan.airline_booking.enums.FlightStatus;
import com.github.menglanyan.airline_booking.enums.PassengerType;
import com.github.menglanyan.airline_booking.exceptions.BadRequestException;
import com.github.menglanyan.airline_booking.repo.BookingRepo;
import com.github.menglanyan.airline_booking.repo.FlightRepo;
import com.github.menglanyan.airline_booking.repo.PassengerRepo;
import com.github.menglanyan.airline_booking.services.EmailNotificationService;
import com.github.menglanyan.airline_booking.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock private BookingRepo bookingRepo;
    @Mock private UserService userService;
    @Mock private FlightRepo flightRepo;
    @Mock private PassengerRepo passengerRepo;
    @Mock private EmailNotificationService emailService;

    // use real mapper for simplicity
    private ModelMapper modelMapper;
    private BookingServiceImpl service;

    @BeforeEach
    void setup() {
        modelMapper = new ModelMapper();
        service = new BookingServiceImpl(bookingRepo, userService, flightRepo, passengerRepo, modelMapper, emailService);
    }

    @Test
    void createBooking_success_withPassengers() {
        User user = new User();
        user.setId(1L);
        when(userService.currentUser()).thenReturn(user);

        Flight flight = new Flight();
        flight.setId(10L);
        flight.setStatus(FlightStatus.SCHEDULED);
        flight.setAvailableSeats(10);

        when(flightRepo.findById(10L)).thenReturn(Optional.of(flight));

        Booking saved = new Booking(); saved.setId(100L);
        when(bookingRepo.save(any())).thenReturn(saved);

        CreateBookingRequest req = new CreateBookingRequest();
        req.setFlightId(10L);
        req.setPassengers(List.of(
                new PassengerDTO(1L, "Alice", "Judien", "A123", PassengerType.ADULT, "1A", null),
                new PassengerDTO(2L, "Bob", "Uilrich","B456", PassengerType.ADULT, "5F", null)
        ));

        Response<?> resp = service.createBooking(req);
        assertEquals(200, resp.getStatusCode());
        verify(bookingRepo).save(any(Booking.class));
        verify(passengerRepo).saveAll(anyList());
        verify(emailService).sendBookingTicketEmail(saved);
    }

    @Test
    void createBooking_reject_nonScheduledFlight() {
        User user = new User(); when(userService.currentUser()).thenReturn(user);
        Flight cancelled = new Flight(); cancelled.setStatus(FlightStatus.CANCELLED);
        when(flightRepo.findById(99L)).thenReturn(Optional.of(cancelled));

        CreateBookingRequest req = new CreateBookingRequest();
        req.setFlightId(99L);

        assertThrows(BadRequestException.class, () -> service.createBooking(req));
        verifyNoInteractions(passengerRepo, emailService);
    }

    @Test
    void getBookingById_success() {
        Booking b = new Booking();
        b.setId(7L);
        b.setFlight(new Flight());
        when(bookingRepo.findById(7L)).thenReturn(Optional.of(b));

        Response<BookingDTO> resp = service.getBookingById(7L);
        assertEquals(200, resp.getStatusCode());
        assertNotNull(resp.getData());
        assertEquals(7L, resp.getData().getId());
    }

    @Test
    void getAllBookings_success() {
        Booking b1 = new Booking(); b1.setId(1L); b1.setFlight(new Flight());
        Booking b2 = new Booking(); b2.setId(2L); b2.setFlight(new Flight());

        Page<Booking> bookingPage = new PageImpl<>(List.of(b1, b2));

        when(bookingRepo.findAll(any(Pageable.class))).thenReturn(bookingPage);

        var resp = service.getAllBookings(0,2);
        assertEquals(200, resp.getStatusCode());
        assertEquals(2, resp.getData().size());
    }

    @Test
    void getMyBookings_success() {
        User u = new User(); u.setId(5L);
        when(userService.currentUser()).thenReturn(u);

        Booking b = new Booking(); b.setId(3L); b.setFlight(new Flight());
        when(bookingRepo.findByUserIdOrderByIdDesc(5L)).thenReturn(List.of(b));

        var resp = service.getMyBookings();
        assertEquals(200, resp.getStatusCode());
        assertEquals(1, resp.getData().size());
    }

    @Test
    void updateBookingStatus_success() {
        Booking b = new Booking(); b.setId(9L);
        when(bookingRepo.findById(9L)).thenReturn(Optional.of(b));

        var resp = service.updateBookingStatus(9L, BookingStatus.CANCELLED);
        assertEquals(200, resp.getStatusCode());
        verify(bookingRepo).save(b);
        assertEquals(BookingStatus.CANCELLED, b.getStatus());
    }
}
