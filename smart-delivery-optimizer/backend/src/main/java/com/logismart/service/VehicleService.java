package com.logismart.service;

import com.logismart.data.PlanningDataProvider;
import com.logismart.model.Vehicle;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VehicleService {

    private final PlanningDataProvider provider;

    public VehicleService(PlanningDataProvider provider) {
        this.provider = provider;
    }

    public List<Vehicle> getAllVehicles() {
        return provider.loadVehicles();
    }
}
