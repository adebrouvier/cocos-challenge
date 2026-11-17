package ar.com.arieldebrouvier.cocos.controllers;

import ar.com.arieldebrouvier.cocos.dto.requests.CreateOrderRequest;
import ar.com.arieldebrouvier.cocos.dto.responses.OrderResponse;
import ar.com.arieldebrouvier.cocos.services.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** Always 201 for a well-formed order: the resulting status (FILLED / NEW / REJECTED) is in the body. */
    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse submit(@Valid @RequestBody CreateOrderRequest request) {
        return OrderResponse.from(orderService.submit(request));
    }
}
