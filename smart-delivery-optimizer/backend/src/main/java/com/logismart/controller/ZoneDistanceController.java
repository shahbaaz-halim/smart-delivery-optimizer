package com.logismart.controller;

import com.logismart.model.ZoneDistance;
import com.logismart.service.ZoneDistanceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/distances")
public class ZoneDistanceController {

    private final ZoneDistanceService service;

    public ZoneDistanceController(ZoneDistanceService service) {
        this.service = service;
    }

    @GetMapping
    public List<ZoneDistance> getAllDistances() {
        return service.getAllDistances();
    }
}