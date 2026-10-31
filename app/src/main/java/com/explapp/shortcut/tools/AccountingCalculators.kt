package com.explapp.shortcut.tools

import kotlin.math.ceil
import kotlin.math.min

/** Pure, offline accounting formulas; monetary values are expressed in the caller's currency. */
object AccountingCalculators {
    fun straightLineDepreciation(cost: Double, salvage: Double, usefulLifeYears: Int): Double {
        require(cost.isFinite() && salvage.isFinite() && cost >= 0 && salvage >= 0 && salvage <= cost)
        require(usefulLifeYears > 0)
        return (cost - salvage) / usefulLifeYears
    }

    /** Annual depreciation expense, capped so closing book value never drops below salvage. */
    fun decliningBalanceDepreciation(bookValue: Double, salvage: Double, annualRate: Double): Double {
        require(bookValue.isFinite() && salvage.isFinite() && bookValue >= 0 && salvage >= 0 && salvage <= bookValue)
        require(annualRate.isFinite() && annualRate in 0.0..1.0)
        return min(bookValue - salvage, bookValue * annualRate)
    }

    fun contributionMarginPerUnit(price: Double, variableCost: Double): Double {
        require(price.isFinite() && variableCost.isFinite() && price > 0 && variableCost >= 0)
        require(price > variableCost) { "Selling price must exceed variable cost per unit" }
        return price - variableCost
    }

    fun breakEvenUnits(fixedCosts: Double, price: Double, variableCost: Double): Long {
        require(fixedCosts.isFinite() && fixedCosts >= 0)
        val units = ceil(fixedCosts / contributionMarginPerUnit(price, variableCost))
        require(units <= Long.MAX_VALUE.toDouble()) { "Result exceeds supported whole-unit count" }
        return units.toLong()
    }

    fun breakEvenSales(fixedCosts: Double, price: Double, variableCost: Double): Double {
        require(fixedCosts.isFinite() && fixedCosts >= 0)
        val marginRatio = contributionMarginPerUnit(price, variableCost) / price
        return fixedCosts / marginRatio
    }
}
