package com.ivanfranchin.orderapi.rest;

import com.ivanfranchin.orderapi.inventory.InsufficientInventoryException;
import com.ivanfranchin.orderapi.order.InvalidOrderException;
import com.ivanfranchin.orderapi.order.InvalidOrderStatusException;
import com.ivanfranchin.orderapi.order.OrderAccessDeniedException;
import com.ivanfranchin.orderapi.order.OrderExpiredException;
import com.ivanfranchin.orderapi.order.OrderNotFoundException;
import com.ivanfranchin.orderapi.order.OrderPaymentConflictException;
import com.ivanfranchin.orderapi.order.OrderShipmentConflictException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class PersistenceConflictHandler {

  @ExceptionHandler({
    OptimisticLockingFailureException.class,
    PessimisticLockingFailureException.class
  })
  @ResponseStatus(HttpStatus.CONFLICT)
  void handleConcurrencyConflict() {}

  @ExceptionHandler({
    OrderExpiredException.class,
    OrderPaymentConflictException.class,
    OrderShipmentConflictException.class,
    InvalidOrderStatusException.class,
    InsufficientInventoryException.class
  })
  @ResponseStatus(HttpStatus.CONFLICT)
  void handleOrderConflict() {}

  @ExceptionHandler(OrderAccessDeniedException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  void handleOrderAccessDenied() {}

  @ExceptionHandler({InvalidOrderException.class})
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  void handleInvalidOrder() {}

  @ExceptionHandler(OrderNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  void handleOrderNotFound() {}
}
