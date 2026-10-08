package com.racehorse.wallet;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class WalletRenewalJob {
    private static final Logger log = LoggerFactory.getLogger(WalletRenewalJob.class);
    private final WalletService service;

    public WalletRenewalJob(WalletService service) { this.service = service; }

    @Scheduled(cron = "${app.jobs.daily-cron}", zone = "${app.club-time-zone}")
    public void renewDuePlans() {
        for (var ownerId : service.ownersWithDuePlan()) {
            try {
                service.renewOne(ownerId);
            } catch (RuntimeException exception) {
                log.error("Cannot renew wallet for owner {}", ownerId, exception);
            }
        }
    }
}
