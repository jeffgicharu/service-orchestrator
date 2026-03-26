package com.orchestrator.saga;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Saga for customer onboarding across multiple services:
 *  1. Create customer profile (CRM service)
 *  2. KYC verification (KYC service)
 *  3. Create wallet account (wallet service)
 *  4. Assign welcome bonus (promotions service)
 *  5. Send welcome notification (notification service)
 */
@Component
@Slf4j
public class OnboardingSaga implements SagaDefinition {

    @Override
    public String getType() { return "CUSTOMER_ONBOARDING"; }

    @Override
    public List<SagaStep> getSteps() {
        return List.of(
                step("CREATE_PROFILE", "Customer profile created", "Customer profile deleted"),
                step("KYC_VERIFICATION", "KYC passed", "KYC record removed"),
                step("CREATE_WALLET", "Wallet created", "Wallet deactivated"),
                step("ASSIGN_WELCOME_BONUS", "Welcome bonus of KES 50 credited", "Welcome bonus reversed"),
                step("SEND_WELCOME_SMS", "Welcome SMS sent", "No compensation needed")
        );
    }

    private SagaStep step(String name, String successMsg, String compensateMsg) {
        return new SagaStep() {
            @Override public String getName() { return name; }

            @Override
            public StepOutcome execute(Map<String, Object> ctx) {
                boolean forceFailure = name.equals(ctx.get("failAtStep"));
                if (forceFailure) return StepOutcome.failure(name + " service unavailable");
                return StepOutcome.success(successMsg);
            }

            @Override
            public StepOutcome compensate(Map<String, Object> ctx) {
                return StepOutcome.success(compensateMsg);
            }
        };
    }
}
