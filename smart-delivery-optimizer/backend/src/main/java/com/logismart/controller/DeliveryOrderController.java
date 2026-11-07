package com.logismart.controller;

import com.logismart.model.DeliveryOrder;
import com.logismart.service.DeliveryOrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class DeliveryOrderController {

    private final DeliveryOrderService service;

    public DeliveryOrderController(DeliveryOrderService service) {
        this.service = service;
    }

    @GetMapping
    public List<DeliveryOrder> getAllOrders() {
        return service.getAllOrders();
    }
}