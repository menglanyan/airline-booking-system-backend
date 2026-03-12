package com.github.menglanyan.airline_booking.exceptions;

import com.github.menglanyan.airline_booking.dtos.Response;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Response<?>> handleNotFoundExceptions(Exception ex) {

        Response<?> response = Response.builder()
                .statusCode(HttpStatus.NOT_FOUND.value())  // 404
                .message(ex.getMessage())
                .build();

        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Response<?>> handleBadRequestExceptions(Exception ex) {

        Response<?> response = Response.builder()
                .statusCode(HttpStatus.BAD_REQUEST.value())  // 400
                .message(ex.getMessage())
                .build();

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Response<?>> handleOptimisticLock(Exception ex) {

        Response<?> response = Response.builder()
                .statusCode(HttpStatus.CONFLICT.value())  // 409
                .message("The flight was just booked by another customer. Please try again.")
                .build();

        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(CannotAcquireLockException.class)
    public ResponseEntity<Response<?>> handleDeadlock(Exception ex) {

        Response<?> response = Response.builder()
                .statusCode(HttpStatus.CONFLICT.value())  // 409
                .message("Concurrent update detected (database deadlock). Please retry.")
                .build();

        return new ResponseEntity<>(response,HttpStatus.CONFLICT);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response<?>> handleAllUnknownExceptions(Exception ex) {

        Response<?> response = Response.builder()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())  // 500
                .message(ex.getMessage())
                .build();

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
