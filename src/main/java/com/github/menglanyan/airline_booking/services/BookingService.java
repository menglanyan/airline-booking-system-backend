package com.github.menglanyan.airline_booking.services;

import com.github.menglanyan.airline_booking.dtos.BookingDTO;
import com.github.menglanyan.airline_booking.dtos.CreateBookingRequest;
import com.github.menglanyan.airline_booking.dtos.Response;
import com.github.menglanyan.airline_booking.entities.User;
import com.github.menglanyan.airline_booking.enums.BookingStatus;

import java.util.List;

public interface BookingService {

    Response<?> createBooking(CreateBookingRequest createBookingRequest);

    Response<BookingDTO> getBookingById(Long id);

    Response<List<BookingDTO>> getAllBookings(int page, int size);

    Response<List<BookingDTO>> getMyBookings();

    Response<?> updateBookingStatus(Long id, BookingStatus bookingStatus);

    Response<?> cancelBooking(Long id);
}
