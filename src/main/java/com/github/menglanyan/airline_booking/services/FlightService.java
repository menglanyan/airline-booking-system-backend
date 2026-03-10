package com.github.menglanyan.airline_booking.services;

import com.github.menglanyan.airline_booking.dtos.CreateFlightRequest;
import com.github.menglanyan.airline_booking.dtos.FlightDTO;
import com.github.menglanyan.airline_booking.dtos.Response;
import com.github.menglanyan.airline_booking.enums.City;
import com.github.menglanyan.airline_booking.enums.Country;
import com.github.menglanyan.airline_booking.enums.FlightStatus;

import java.time.LocalDate;
import java.util.List;

public interface FlightService {

    Response<?> createFlight(CreateFlightRequest createFlightRequest);

    Response<FlightDTO> getFlightById(Long id);

    Response<List<FlightDTO>> getAllFlights(int page, int size);

    Response<?> updateFlight(CreateFlightRequest createFlightRequest);

    Response<List<FlightDTO>> searchForFlight(String departureAirportIataCode, String arrivalAirportIataCode,
                                              FlightStatus status, LocalDate departureDate);

    Response<List<City>> getAllCities();

    Response<List<Country>> getAllCountries();

    Response<List<FlightDTO>> getMyFlights();
}
