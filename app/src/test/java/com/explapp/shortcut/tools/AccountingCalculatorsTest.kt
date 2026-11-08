package com.explapp.shortcut.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountingCalculatorsTest {
    @Test fun currentRatioUsesCurrentLiabilities() {
        assertEquals(2.5, AccountingCalculators.currentRatio(5000.0, 2000.0), 0.0001)
    }

    @Test fun currentRatioRejectsZeroLiabilities() {
        assertThrows(IllegalArgumentException::class.java) {
            AccountingCalculators.currentRatio(5000.0, 0.0)
        }
    }

    @Test fun quickRatioExcludesInventory() {
        assertEquals(1.5, AccountingCalculators.quickRatio(5000.0, 2000.0, 2000.0), 0.0001)
    }

    @Test fun grossMarginUsesRevenueDenominator() {
        assertEquals(40.0, AccountingCalculators.grossProfitMargin(1000.0, 600.0), 0.0001)
    }

    @Test fun inventoryTurnoverUsesAverageInventory() {
        assertEquals(4.0, AccountingCalculators.inventoryTurnover(8000.0, 2000.0), 0.0001)
    }

    @Test fun markupDiffersFromMargin() {
        assertEquals(25.0, AccountingCalculators.markupPercent(100.0, 125.0), 0.0001)
    }

    @Test fun safetyMarginUsesActualSales() {
        assertEquals(20.0, AccountingCalculators.marginOfSafetyPercent(10000.0, 8000.0), 0.0001)
    }

    @Test fun reorderPointIncludesSafetyStock() {
        assertEquals(65.0, AccountingCalculators.reorderPoint(20.0, 3.0, 5.0), 0.0001)
    }

    @Test fun weightedAverageInventoryCost() {
        assertEquals(12.5, AccountingCalculators.weightedAverageCost(10.0, 10.0, 10.0, 15.0), 0.0001)
    }
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
