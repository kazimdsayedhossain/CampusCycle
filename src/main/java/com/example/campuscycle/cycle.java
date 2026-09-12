package com.example.campuscycle;

import java.time.ZonedDateTime;
import java.util.UUID;

public class cycle {
    public enum cycleType {CITY_COMMUTER, CARGO_UTILITY, ELECTRIC_SCOOTER}
    boolean is_gear;
    boolean needs_fuel;
    boolean needs_liscence;
    String cycle_id;
    String owner_name;
    String ownwer_phone;
    ZonedDateTime purchase_data;
    ZonedDateTime registered_at;
    public enum physical_condition{Perfect, Usuable, Broken};
    boolean is_verified;
    cycleType type;
    physical_condition condition;

    public void register_new_cycle(String owner_name, String ownwer_phone, cycleType type, physical_condition condition, ZonedDateTime purchase_data, boolean is_verified)
    {
        // 1. Assign all information passed as arguments
        this.owner_name = owner_name;
        this.ownwer_phone = ownwer_phone;
        this.type = type;
        this.condition = (condition != null) ? condition : physical_condition.Usuable;
        this.purchase_data = purchase_data;
        this.is_verified = is_verified;

        // 2. Set the registration time to current time (not passed as argument)
        this.registered_at = ZonedDateTime.now();

        // 3. Auto-generate cycle ID if not already set
        if (this.cycle_id == null || this.cycle_id.isEmpty())
        {
            this.cycle_id = "CC-" + UUID.randomUUID().toString().substring(0, 7).toUpperCase();
        }

        // 4. Auto-configure flags based on the cycle type
        if (this.type != null)
        {
            switch (this.type)
            {
                case ELECTRIC_SCOOTER -> {
                    this.is_gear = false;
                    this.needs_fuel = true;
                    this.needs_liscence = true;
                }
                case CARGO_UTILITY -> {
                    this.is_gear = true;
                    this.needs_fuel = false;
                    this.needs_liscence = false;
                }
                case CITY_COMMUTER -> {
                    this.is_gear = false;
                    this.needs_fuel = false;
                    this.needs_liscence = false;
                }
            }
        }
    }

    // Overload without is_verified (defaults is_verified to false)
    public void register_new_cycle(String owner_name, String ownwer_phone, cycleType type, physical_condition condition, ZonedDateTime purchase_data)
    {
        register_new_cycle(owner_name, ownwer_phone, type, condition, purchase_data, false);
    }

    // ==========================================
    // MySQL Database Integration
    // ==========================================


}
