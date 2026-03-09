package com.github.menglanyan.airline_booking.services.impl;

import com.github.menglanyan.airline_booking.dtos.CreateFlightRequest;
import com.github.menglanyan.airline_booking.dtos.FlightDTO;
import com.github.menglanyan.airline_booking.dtos.Response;
import com.github.menglanyan.airline_booking.entities.Airport;
import com.github.menglanyan.airline_booking.entities.Flight;
import com.github.menglanyan.airline_booking.entities.User;
import com.github.menglanyan.airline_booking.enums.City;
import com.github.menglanyan.airline_booking.enums.Country;
import com.github.menglanyan.airline_booking.enums.FlightStatus;
import com.github.menglanyan.airline_booking.exceptions.BadRequestException;
import com.github.menglanyan.airline_booking.exceptions.CustomAccessDenialHandler;
import com.github.menglanyan.airline_booking.exceptions.NotFoundException;
import com.github.menglanyan.airline_booking.repo.AirportRepo;
import com.github.menglanyan.airline_booking.repo.FlightRepo;
import com.github.menglanyan.airline_booking.repo.UserRepo;
import com.github.menglanyan.airline_booking.services.FlightService;
import com.github.menglanyan.airline_booking.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class FlightServiceImpl implements FlightService {

    private final FlightRepo flightRepo;

    private final AirportRepo airportRepo;

    private final UserRepo userRepo;

    private final ModelMapper modelMapper;

    private final UserService userService;

    @Override
    public Response<?> createFlight(CreateFlightRequest createFlightRequest) {

        if (createFlightRequest.getArrivalTime().isBefore(createFlightRequest.getDepartureTime())) {
            throw new BadRequestException("Arrival time cannot be before the departure time");
        }

        if (flightRepo.existsByFlightNumber(createFlightRequest.getFlightNumber())) {
            throw new BadRequestException("Flight with this number already exists");
        }

        // Fetch and validate the departure airport
        Airport departureAirport = airportRepo.findByIataCode(createFlightRequest.getDepartureAirportIataCode())
                .orElseThrow(() -> new NotFoundException("Departure Airport Not Found"));

        // Fetch and validate the departure airport
        Airport arrivalAirport = airportRepo.findByIataCode(createFlightRequest.getArrivalAirportIataCode())
                .orElseThrow(() -> new NotFoundException("Arrival Airport Not Found"));

        Flight flightToSave = new Flight();
        flightToSave.setFlightNumber(createFlightRequest.getFlightNumber());
        flightToSave.setDepartureAirport(departureAirport);
        flightToSave.setArrivalAirport(arrivalAirport);
        flightToSave.setDepartureTime(createFlightRequest.getDepartureTime());
        flightToSave.setArrivalTime(createFlightRequest.getArrivalTime());
        flightToSave.setStatus(FlightStatus.SCHEDULED);
        flightToSave.setBasePrice(createFlightRequest.getBasePrice());
        flightToSave.setTotalSeats(createFlightRequest.getTotalSeats());
        flightToSave.setAvailableSeats(createFlightRequest.getTotalSeats());

        // Assign pilot to the flight(get and validate the pilot)
        if (createFlightRequest.getPilotId() != null) {
            User pilot = userRepo.findById(createFlightRequest.getPilotId())
                    .orElseThrow(() -> new NotFoundException("Pilot Not Found"));

            boolean isPilot = pilot.getRoles().stream()
                    .anyMatch(role -> role.getName().equalsIgnoreCase("PILOT"));

            if (!isPilot) {
                throw new BadRequestException("Claimed pilot is not the certified pilot");
            }

            flightToSave.setAssignedPilot(pilot);
        }

        // Save the flight
        flightRepo.save(flightToSave);

        return Response.builder()
                .statusCode(HttpStatus.OK.value())
                .message("Flight saved successfully")
                .build();
    }

    @Override
    public Response<FlightDTO> getFlightById(Long id) {
        Flight flight = flightRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Flight Not Found"));

        FlightDTO flightDTO = modelMapper.map(flight, FlightDTO.class);

        // Break the bidirectional relationship to avoid recursive loop during serialization:
        // FlightDTO → BookingDTO → FlightDTO → BookingDTO ...
        if (flightDTO.getBooking() != null) {
            flightDTO.getBooking().forEach(bookingDTO -> bookingDTO.setFlight(null));
        }

        return Response.<FlightDTO>builder()
                .statusCode(HttpStatus.OK.value())
                .message("Flight retrieved successfully")
                .data(flightDTO)
                .build();
    }

    @Override
    public Response<List<FlightDTO>> getAllFlights() {
        Sort sortByIdDesc = Sort.by(Sort.Direction.DESC, "id");

        List<FlightDTO> flights = flightRepo.findAll(sortByIdDesc).stream()
                .map(flight -> {
                    FlightDTO flightDTO = modelMapper.map(flight, FlightDTO.class);
                    if (flightDTO.getBooking() != null) {
                        flightDTO.getBooking().forEach(bookingDTO -> bookingDTO.setFlight(null));
                    }
                    return flightDTO;
                })
                .toList();

        return Response.<List<FlightDTO>>builder()
                .statusCode(HttpStatus.OK.value())
                .message(flights.isEmpty() ? "No Flights Found" : "Flights retrieved successfully")
                .data(flights)
                .build();
    }

    @Override
    @Transactional
    public Response<?> updateFlight(CreateFlightRequest createFlightRequest) {
        Long id = createFlightRequest.getId();

        Flight existingflight = flightRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Flight Not Found"));

        if (createFlightRequest.getDepartureTime() != null) {
            existingflight.setDepartureTime(createFlightRequest.getDepartureTime());
        }

        if (createFlightRequest.getArrivalTime() != null) {
            existingflight.setArrivalTime(createFlightRequest.getArrivalTime());
        }

        if (createFlightRequest.getBasePrice() != null) {
            existingflight.setBasePrice(createFlightRequest.getBasePrice());
        }

        if (createFlightRequest.getTotalSeats() != null) {
            int bookedSeats = existingflight.getTotalSeats() - existingflight.getAvailableSeats();

            if (createFlightRequest.getTotalSeats() < bookedSeats) {
                throw new BadRequestException("Total seats cannot be less than already booked seats");
            }

            existingflight.setTotalSeats(createFlightRequest.getTotalSeats());
            existingflight.setAvailableSeats(createFlightRequest.getTotalSeats() - bookedSeats);
        }

        if (createFlightRequest.getStatus() != null) {
            existingflight.setStatus(createFlightRequest.getStatus());
        }

        // Validate and update the pilot if pilot id is passed in
        if (createFlightRequest.getPilotId() != null) {

            User pilot = userRepo.findById(createFlightRequest.getPilotId())
                    .orElseThrow(() -> new NotFoundException("Pilot Not Found"));

            boolean isPilot = pilot.getRoles().stream()
                    .anyMatch(role -> role.getName().equalsIgnoreCase("PILOT"));

            if (!isPilot) {
                throw new BadRequestException("Claimed pilot is not the certified pilot");
            }
            existingflight.setAssignedPilot(pilot);
        }

        flightRepo.save(existingflight);

        return Response.builder()
                .statusCode(HttpStatus.OK.value())
                .message("Flight Updated Successfully")
                .build();
    }

    @Override
    public Response<List<FlightDTO>> searchForFlight(String departureAirportIataCode, String arrivalAirportIataCode,
                                                     FlightStatus status, LocalDate departureDate) {
        LocalDateTime startOfDay = departureDate.atStartOfDay();

        LocalDateTime endOfDay = departureDate.plusDays(1).atStartOfDay().minusNanos(1); // 23:59:59.999999

        List<Flight> flights = flightRepo.findByDepartureAirportIataCodeAndArrivalAirportIataCodeAndStatusAndDepartureTimeBetween(
                departureAirportIataCode, arrivalAirportIataCode, status, startOfDay, endOfDay
        );

        List<FlightDTO> flightDTOS = flights.stream()
                .map(flight -> {
                    FlightDTO flightDTO = modelMapper.map(flight, FlightDTO.class);
                    flightDTO.setAssignedPilot(null);
                    flightDTO.setBooking(null);
                    return flightDTO;
                })
                .toList();

        return Response.<List<FlightDTO>>builder()
                .statusCode(HttpStatus.OK.value())
                .message(flightDTOS.isEmpty() ? "NO Flights Found" : "Flight Retrieved Successfully")
                .data(flightDTOS)
                .build();
    }

    @Override
    public Response<List<City>> getAllCities() {
        return Response.<List<City>>builder()
                .statusCode(HttpStatus.OK.value())
                .message("Cities Retrieved Successfully")
                .data(List.of(City.values()))
                .build();
    }

    @Override
    public Response<List<Country>> getAllCountries() {
        return Response.<List<Country>>builder()
                .statusCode(HttpStatus.OK.value())
                .message("Countries Retrieved Successfully")
                .data(List.of(Country.values()))
                .build();
    }

    @Override
    public Response<List<FlightDTO>> getMyFlights() {
        User currentUser = userService.currentUser();

        boolean isPilot = currentUser.getRoles().stream()
                .anyMatch(role -> role.getName().equalsIgnoreCase("PILOT"));

        if (!isPilot) {
            throw new BadRequestException("Only pilots can view their assigned flights");
        }

        List<Flight> flights = flightRepo.findByAssignedPilotIdOrderByDepartureTimeDesc(currentUser.getId());

        List<FlightDTO> flightDTOS = flights.stream()
                .map(flight -> {
                    FlightDTO flightDTO = modelMapper.map(flight, FlightDTO.class);
                    flightDTO.setBooking(null);
                    return flightDTO;
                })
                .toList();

        return Response.<List<FlightDTO>>builder()
                .statusCode(HttpStatus.OK.value())
                .message("Flights Retrieved Successfully")
                .data(flightDTOS)
                .build();
    }

}
