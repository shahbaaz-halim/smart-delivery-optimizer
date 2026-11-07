package com.logismart.service;

import com.logismart.data.PlanningDataProvider;
import com.logismart.model.ZoneDistance;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ZoneDistanceService {

    private final PlanningDataProvider provider;

    public ZoneDistanceService(PlanningDataProvider provider) {
        this.provider = provider;
    }

    public List<ZoneDistance> getAllDistances() {
        return provider.loadDistances();
    }
}
