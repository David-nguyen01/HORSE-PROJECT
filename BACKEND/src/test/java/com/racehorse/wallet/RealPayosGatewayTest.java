package com.racehorse.wallet;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealPayosGatewayTest {
    private final ObjectMapper json = new ObjectMapper();
    private final RealPayosGateway gateway = new RealPayosGateway(json, "unused", "unused",
            "1a54716c8f0efb2744fb28b6e38b25da7f67a925d98bc1c18bd8faaecadd7675",
            "https://example.test/return", "https://example.test/cancel");

    @Test
    void verifiesOfficialWebhookVectorAndRejectsChangedAmount() {
        var webhook = json.readTree("""
                {"code":"00","desc":"success","success":true,"data":{
                  "orderCode":123,"amount":3000,"description":"VQRIO123",
                  "accountNumber":"12345678","reference":"TF230204212323",
                  "transactionDateTime":"2023-02-04 18:25:00","currency":"VND",
                  "paymentLinkId":"124c33293c43417ab7879e14c8d9eb18","code":"00",
                  "desc":"Thành công","counterAccountBankId":"","counterAccountBankName":"",
                  "counterAccountName":"","counterAccountNumber":"","virtualAccountName":"",
                  "virtualAccountNumber":""},
                  "signature":"412e915d2871504ed31be63c8f62a149a4410d34c4c42affc9006ef9917eaa03"}
                """);
        assertTrue(gateway.verifyWebhook(webhook));
        assertFalse(gateway.verifyWebhook(json.readTree(webhook.toString().replace("3000", "3001"))));
    }
}
