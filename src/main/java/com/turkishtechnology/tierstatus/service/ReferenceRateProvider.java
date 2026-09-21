package com.turkishtechnology.tierstatus.service;

import java.time.LocalDate;

public interface ReferenceRateProvider {
    ReferenceRate flightRate(String operatingCarrier, String bookingClass, LocalDate activityDate);
    ReferenceRate partnerRate(String partnerCode, String currency, LocalDate activityDate);
}
