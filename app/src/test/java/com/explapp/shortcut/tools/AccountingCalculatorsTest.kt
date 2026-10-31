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

    @Test fun decliningBalanceDepreciationNeverBelowSalvage() {
        assertEquals(1000.0, AccountingCalculators.decliningBalanceDepreciation(1500.0, 1000.0, 0.8), 0.001)
    }
}
