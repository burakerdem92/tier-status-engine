package com.turkishtechnology.tierstatus.service;

import com.turkishtechnology.tierstatus.domain.ReferenceDataUnavailableException;
import com.turkishtechnology.tierstatus.persistence.FlightRateEntity;
import com.turkishtechnology.tierstatus.persistence.FlightRateRepository;
import com.turkishtechnology.tierstatus.persistence.PartnerRateEntity;
import com.turkishtechnology.tierstatus.persistence.PartnerRateRepository;
import java.time.LocalDate;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class DatabaseReferenceRateProvider implements ReferenceRateProvider {

    private final FlightRateRepository flightRates;
    private final PartnerRateRepository partnerRates;

    public DatabaseReferenceRateProvider(FlightRateRepository flightRates,
                                         PartnerRateRepository partnerRates) {
        this.flightRates = flightRates;
        this.partnerRates = partnerRates;
    }

    @Override
    public ReferenceRate flightRate(String operatingCarrier, String bookingClass,
                                    LocalDate activityDate) {
        FlightRateEntity rate = flightRates.findApplicable(
                        operatingCarrier, bookingClass, activityDate, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ReferenceDataUnavailableException(
                        "Flight rate not found for %s/%s at %s"
                                .formatted(operatingCarrier, bookingClass, activityDate)));
        return new ReferenceRate(rate.getRate(), rate.getRuleVersion());
    }

    @Override
    public ReferenceRate partnerRate(String partnerCode, String currency,
                                     LocalDate activityDate) {
        PartnerRateEntity rate = partnerRates.findApplicable(
                        partnerCode, currency, activityDate, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ReferenceDataUnavailableException(
                        "Partner rate not found for %s/%s at %s"
                                .formatted(partnerCode, currency, activityDate)));
        return new ReferenceRate(rate.getRate(), rate.getRuleVersion());
    }
}
