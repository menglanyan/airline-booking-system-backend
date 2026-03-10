package com.github.menglanyan.airline_booking.controller;

import com.github.menglanyan.airline_booking.dtos.CreateFlightRequest;
import com.github.menglanyan.airline_booking.dtos.FlightDTO;
import com.github.menglanyan.airline_booking.dtos.Response;
import com.github.menglanyan.airline_booking.enums.City;
import com.github.menglanyan.airline_booking.enums.Country;
import com.github.menglanyan.airline_booking.enums.FlightStatus;
import com.github.menglanyan.airline_booking.services.FlightService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/flights")
@RequiredArgsConstructor
public class FlightController {

    private final FlightService flightService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'PILOT')")
    public ResponseEntity<Response<?>> createFlight(@Valid @RequestBody CreateFlightRequest createFlightRequest) {
        return ResponseEntity.ok(flightService.createFlight(createFlightRequest));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Response<FlightDTO>> getFlightById(@PathVariable Long id) {
        return ResponseEntity.ok(flightService.getFlightById(id));
    }

    @GetMapping
    public ResponseEntity<Response<List<FlightDTO>>> getAllFlights(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(flightService.getAllFlights(page, size));
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'PILOT')")
    public ResponseEntity<Response<?>> updateFlight(@RequestBody CreateFlightRequest createFlightRequest) {
        return ResponseEntity.ok(flightService.updateFlight(createFlightRequest));
    }

    @GetMapping("/search")
    public ResponseEntity<Response<List<FlightDTO>>> searchForFlight(
            @RequestParam String departureIataCode, @RequestParam String arrivalIataCode,
            @RequestParam(required = false, defaultValue = "SCHEDULED") FlightStatus status,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureDate) {
        return ResponseEntity.ok(flightService.searchForFlight(departureIataCode, arrivalIataCode,
                status, departureDate));
    }

    @GetMapping("/cities")
    public ResponseEntity<Response<List<City>>> getAllCities() {
        return ResponseEntity.ok(flightService.getAllCities());
    }

    @GetMapping("/countries")
    public ResponseEntity<Response<List<Country>>> getAllCountries() {
        return ResponseEntity.ok(flightService.getAllCountries());
    }

    @GetMapping("/my-flights")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'PILOT')")
    public ResponseEntity<Response<List<FlightDTO>>> getMyFlights() {
        return ResponseEntity.ok(flightService.getMyFlights());
    }
}
