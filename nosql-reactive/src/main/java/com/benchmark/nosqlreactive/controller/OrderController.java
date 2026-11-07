package com.benchmark.nosqlreactive.controller;

import com.benchmark.common.dto.OrderDtos.OrderCreateRequest;
import com.benchmark.common.dto.OrderDtos.OrderResponse;
import com.benchmark.common.dto.OrderDtos.OrderStatusUpdateRequest;
import com.benchmark.nosqlreactive.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<OrderResponse> createOrder(@Valid @RequestBody OrderCreateRequest request) {
        return orderService.createOrder(request.getCartId(), request.getCustomerRef());
    }

    @GetMapping("/{id}")
    public Mono<OrderResponse> getOrder(@PathVariable String id) {
        return orderService.getOrder(id);
    }

    @PatchMapping("/{id}/status")
    public Mono<OrderResponse> updateStatus(@PathVariable String id,
                                            @Valid @RequestBody OrderStatusUpdateRequest request) {
        return orderService.updateStatus(id, request.getStatus());
    }
}
