package com.github.menglanyan.airline_booking.services.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.menglanyan.airline_booking.dtos.BookingDTO;
import com.github.menglanyan.airline_booking.dtos.CreateBookingRequest;
import com.github.menglanyan.airline_booking.dtos.Response;
import com.github.menglanyan.airline_booking.entities.*;
import com.github.menglanyan.airline_booking.enums.BookingStatus;
import com.github.menglanyan.airline_booking.enums.FlightStatus;
import com.github.menglanyan.airline_booking.exceptions.BadRequestException;
import com.github.menglanyan.airline_booking.exceptions.NotFoundException;
import com.github.menglanyan.airline_booking.repo.BookingRepo;
import com.github.menglanyan.airline_booking.repo.FlightRepo;
import com.github.menglanyan.airline_booking.repo.IdempotencyKeyRepo;
import com.github.menglanyan.airline_booking.repo.PassengerRepo;
import com.github.menglanyan.airline_booking.services.BookingService;
import com.github.menglanyan.airline_booking.services.EmailNotificationService;
import com.github.menglanyan.airline_booking.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepo bookingRepo;

    private final UserService userService;

    private final FlightRepo flightRepo;

    private final PassengerRepo passengerRepo;

    private final ModelMapper modelMapper;

    private final EmailNotificationService emailNotificationService;

    private final IdempotencyKeyRepo idempotencyKeyRepo;

    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public Response<?> createBooking(CreateBookingRequest createBookingRequest, String idempotencyKey) {

        String requestHash = generateRequestHash(createBookingRequest);

        IdempotencyKey existingKey = idempotencyKeyRepo.findByIdempotencyKey(idempotencyKey)
                .orElse(null);

        if (existingKey != null) {
            if (!existingKey.getRequestHash().equals(requestHash)) {
                throw new BadRequestException("Idempotency Key cannot be reused with a different request body");
            }

            Booking existingBooking = bookingRepo.findById(existingKey.getBookingId())
                    .orElseThrow(() -> new NotFoundException("Existing booking not found"));

            BookingDTO bookingDTO = modelMapper.map(existingBooking,BookingDTO.class);
            // Break the bidirectional relationship to avoid recursive loop during serialization:
            // FlightDTO → BookingDTO → FlightDTO → BookingDTO ...
            bookingDTO.getFlight().setBooking(null);

            return Response.builder()
                    .statusCode(HttpStatus.OK.value())
                    .message("Duplicate request detected. Returning existing booking")
                    .data(bookingDTO)
                    .build();
        }

        User user = userService.currentUser();

        Flight flight = flightRepo.findById(createBookingRequest.getFlightId())
                .orElseThrow(() -> new NotFoundException("Flight Not Found"));

        if (flight.getStatus() != FlightStatus.SCHEDULED) {
            throw new BadRequestException("You can only book a flight that is scheduled");
        }

        int passangerCount = createBookingRequest.getPassengers().size();

        if (flight.getAvailableSeats() < passangerCount) {
            throw new BadRequestException("Not enough seats available");
        }

        flight.setAvailableSeats(flight.getAvailableSeats() - passangerCount);
        flightRepo.save(flight);

        Booking booking = new Booking();
        booking.setBookingReference(generateBookingReference());
        booking.setUser(user);
        booking.setFlight(flight);
        booking.setBookingDate(LocalDateTime.now());
        booking.setStatus(BookingStatus.CONFIRMED);

        Booking savedBooking = bookingRepo.save(booking);

        if (createBookingRequest.getPassengers() != null && !createBookingRequest.getPassengers().isEmpty()) {

            List<Passenger> passengers = createBookingRequest.getPassengers().stream()
                    .map(passengerDTO -> {
                        Passenger passenger = modelMapper.map(passengerDTO, Passenger.class);
                        passenger.setBooking(savedBooking);
                        return passenger;
                    })
                    .toList();

            passengerRepo.saveAll(passengers);

            savedBooking.setPassengers(passengers);
        }

        IdempotencyKey key = IdempotencyKey.builder()
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .bookingId(savedBooking.getId())
                .createdAt(LocalDateTime.now())
                .build();

        idempotencyKeyRepo.save(key);

        emailNotificationService.sendBookingTicketEmail(savedBooking);

        return Response.builder()
                .statusCode(HttpStatus.OK.value())
                .message("Booking Created Successfully")
                .build();
    }

    @Override
    public Response<BookingDTO> getBookingById(Long id) {
        Booking booking = bookingRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Booking Not Found"));

        BookingDTO bookingDTO = modelMapper.map(booking, BookingDTO.class);
        // Break the bidirectional relationship to avoid recursive loop during serialization:
        // FlightDTO → BookingDTO → FlightDTO → BookingDTO ...
        bookingDTO.getFlight().setBooking(null);

        return Response.<BookingDTO>builder()
                .statusCode(HttpStatus.OK.value())
                .message("Booking Retrieved Successfully")
                .data(bookingDTO)
                .build();
    }

    @Override
    public Response<List<BookingDTO>> getAllBookings(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));

        Page<Booking> bookingPage = bookingRepo.findAll(pageable);

        List<BookingDTO> bookingDTOS = bookingPage.getContent().stream()
                .map(booking -> {
                    BookingDTO bookingDTO = modelMapper.map(booking, BookingDTO.class);

                    // Break the bidirectional relationship to avoid recursive loop during serialization:
                    // FlightDTO → BookingDTO → FlightDTO → BookingDTO ...
                    if (bookingDTO.getFlight() != null) {
                        bookingDTO.getFlight().setBooking(null);
                    }

                    return bookingDTO;
                })
                .toList();

        return Response.<List<BookingDTO>>builder()
                .statusCode(HttpStatus.OK.value())
                .message(bookingDTOS.isEmpty() ? "No Booking Found" : "Bookings Retrieved Successfully")
                .data(bookingDTOS)
                .build();
    }

    @Override
    public Response<List<BookingDTO>> getMyBookings() {
        User user = userService.currentUser();

        List<Booking> userBookings = bookingRepo.findByUserIdOrderByIdDesc(user.getId());

        List<BookingDTO> userBookingDTOS = userBookings.stream()
                .map(booking -> {
                    BookingDTO bookingDTO = modelMapper.map(booking, BookingDTO.class);
                    // Break the bidirectional relationship to avoid recursive loop during serialization:
                    // FlightDTO → BookingDTO → FlightDTO → BookingDTO ...
                    bookingDTO.getFlight().setBooking(null);
                    return bookingDTO;
                })
                .toList();

        return Response.<List<BookingDTO>>builder()
                .statusCode(HttpStatus.OK.value())
                .message(userBookingDTOS.isEmpty() ? "No Booking Found For The User" :
                        "User Bookings Retrieved Successfully")
                .data(userBookingDTOS)
                .build();
    }

    @Override
    @Transactional
    public Response<?> updateBookingStatus(Long id, BookingStatus bookingStatus) {
        Booking existingBooking = bookingRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Booking Not Found"));

        existingBooking.setStatus(bookingStatus);

        bookingRepo.save(existingBooking);

        return Response.builder()
                .statusCode(HttpStatus.OK.value())
                .message("Booking Updated Successfully")
                .build();
    }

    @Override
    @Transactional
    public Response<?> cancelBooking(Long id) {
        Booking booking = bookingRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Booking Not Found"));

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BadRequestException("Booking Already Canceled");
        }

        Flight flight = booking.getFlight();

        int passengerCount = booking.getPassengers().size();

        flight.setAvailableSeats(flight.getAvailableSeats() + passengerCount);
        flightRepo.save(flight);

        booking.setStatus(BookingStatus.CANCELLED);

        bookingRepo.save(booking);

        return Response.builder()
                .statusCode(HttpStatus.OK.value())
                .message("Booking cancelled successfully")
                .build();
    }

    private String generateBookingReference() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String generateRequestHash(CreateBookingRequest request) {
        try {
            String json = objectMapper.writeValueAsString(request);

            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] hash = messageDigest.digest(json.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();

            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }

            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Fail to generate request hash");
        }
    }
}
