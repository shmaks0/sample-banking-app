package io.shmaks.banking.controller;

import io.shmaks.banking.service.BusinessLogicError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;

import javax.validation.ConstraintViolationException;

@RestControllerAdvice
public class ErrorsHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrorsHandler.class);

    @ExceptionHandler(BusinessLogicError.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String blErrorHandler(Exception ex) {
        log.error(ex.getMessage());
        return ex.getMessage();
    }

    @ExceptionHandler(WebExchangeBindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String validationErrorHandler(WebExchangeBindException ex) {
        log.error("validation errors: {}", ex.getBindingResult().getAllErrors());
        return "Invalid request";
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String defaultHandler(ConstraintViolationException ex) {
        log.error("validation error: {}", ex.getConstraintViolations());
        return "Invalid request";
    }
}
