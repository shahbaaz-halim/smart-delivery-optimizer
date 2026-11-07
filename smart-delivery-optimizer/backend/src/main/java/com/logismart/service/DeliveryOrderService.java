package com.logismart.service;

import com.logismart.data.PlanningDataProvider;
import com.logismart.model.DeliveryOrder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DeliveryOrderService {

    private final PlanningDataProvider provider;

    public DeliveryOrderService(PlanningDataProvider provider) {
        this.provider = provider;
    }

    public List<DeliveryOrder> getAllOrders() {
        return provider.loadOrders();
    }
}
