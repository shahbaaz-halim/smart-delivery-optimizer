package com.logismart.algorithm;

import java.util.Locale;

/**
 * Ordered vehicle classes. The data dictionary defines
 * {@code Required_Vehicle_Type} as the <em>minimum</em> suitable class, so an
 * order may be carried by its required class or any larger class.
 */
public enum VehicleClass {

    BIKE("Bike"),
    VAN("Van"),
    MINI_TRUCK("Mini Truck");

    private final String label;

    VehicleClass(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Parses "Bike", "van", "Mini Truck", "mini_truck"...; null if unknown. */
    public static VehicleClass parse(String value) {

        if (value == null) {
            return null;
        }

        String normalized = value.trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ");

        for (VehicleClass vc : values()) {
            if (vc.label.toLowerCase(Locale.ROOT).equals(normalized)) {
                return vc;
            }
        }

        return null;
    }

    /** True when this (vehicle) class can carry an order requiring {@code required}. */
    public boolean canServe(VehicleClass required) {
        return required != null && this.ordinal() >= required.ordinal();
    }
}
