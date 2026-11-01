package com.explapp.shortcut.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountingCalculatorsTest {
    @Test fun straightLineDepreciationUsesSalvageValue() {
        assertEquals(1800.0, AccountingCalculators.straightLineDepreciation(10000.0, 1000.0, 5), 0.001)
    }

    @Test fun breakEvenUnitsRoundsUpToWholeUnit() {
        assertEquals(334L, AccountingCalculators.breakEvenUnits(1000.0, 8.0, 5.0))
    }

    @Test fun breakEvenSalesUsesContributionMarginRatio() {
        assertEquals(2500.0, AccountingCalculators.breakEvenSales(1000.0, 10.0, 6.0), 0.001)
    }

    @Test fun invalidContributionMarginIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AccountingCalculators.breakEvenUnits(1000.0, 5.0, 5.0)
        }
    }

    @Test fun simpleInterestCalculatesAnnualAmount() {
        assertEquals(150.0, AccountingCalculators.simpleInterest(1000.0, 0.05, 3.0), 0.001)
    }

    @Test fun compoundInterestCalculatesAccumulatedAmount() {
        assertEquals(1102.5, AccountingCalculators.compoundAmount(1000.0, 0.05, 2), 0.001)
    }

    @Test fun roiUsesInvestmentBase() {
        assertEquals(25.0, AccountingCalculators.roiPercent(1250.0, 1000.0), 0.001)
    }

    @Test fun contributionMarginRatioComputesPercent() {
        assertEquals(40.0, AccountingCalculators.contributionMarginPercent(10.0, 6.0), 0.001)
    }

    @Test fun decliningBalanceDepreciationNeverBelowSalvage() {
        assertEquals(500.0, AccountingCalculators.decliningBalanceDepreciation(1500.0, 1000.0, 0.8), 0.001)
    }
}
