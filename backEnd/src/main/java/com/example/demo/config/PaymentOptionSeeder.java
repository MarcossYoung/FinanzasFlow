package com.example.demo.config;

import com.example.demo.model.PaymentOption;
import com.example.demo.repository.PaymentOptionRepo;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaymentOptionSeeder implements ApplicationRunner {

    private final PaymentOptionRepo paymentOptionRepo;

    public PaymentOptionSeeder(PaymentOptionRepo paymentOptionRepo) {
        this.paymentOptionRepo = paymentOptionRepo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!paymentOptionRepo.existsByCategory("TYPE")) {
            paymentOptionRepo.saveAll(List.of(
                    new PaymentOption("TYPE", "DEPOSIT", "Seña"),
                    new PaymentOption("TYPE", "RESTO", "Saldo"),
                    new PaymentOption("TYPE", "EXTRA", "Extra")
            ));
        }

        if (!paymentOptionRepo.existsByCategory("METHOD")) {
            paymentOptionRepo.saveAll(List.of(
                    new PaymentOption("METHOD", "CASH", "Efectivo"),
                    new PaymentOption("METHOD", "BANK_TRANSFER", "Transferencia"),
                    new PaymentOption("METHOD", "CREDIT_DEBIT_CARD", "Tarjeta"),
                    new PaymentOption("METHOD", "OTHER", "Otro")
            ));
        }
    }
}
