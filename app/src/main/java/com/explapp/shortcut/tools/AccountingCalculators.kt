package com.explapp.shortcut.tools

import kotlin.math.ceil
import kotlin.math.min

/** Pure, offline accounting formulas; monetary values are expressed in the caller's currency. */
object AccountingCalculators {
    private fun nonnegative(vararg values: Double) {
        require(values.all { it.isFinite() && it >= 0.0 }) { "All values must be non-negative and finite" }
    }

    fun currentRatio(currentAssets: Double, currentLiabilities: Double): Double {
        nonnegative(currentAssets, currentLiabilities)
        require(currentLiabilities > 0.0)
        return currentAssets / currentLiabilities
    }

    fun quickRatio(currentAssets: Double, inventory: Double, currentLiabilities: Double): Double {
        nonnegative(currentAssets, inventory, currentLiabilities)
        require(inventory <= currentAssets && currentLiabilities > 0.0)
        return (currentAssets - inventory) / currentLiabilities
    }

    fun grossProfitMargin(revenue: Double, costOfGoodsSold: Double): Double {
        nonnegative(revenue, costOfGoodsSold)
        require(revenue > 0.0)
        return (revenue - costOfGoodsSold) / revenue * 100.0
    }

    fun inventoryTurnover(costOfGoodsSold: Double, averageInventory: Double): Double {
        nonnegative(costOfGoodsSold, averageInventory)
        require(averageInventory > 0.0)
        return costOfGoodsSold / averageInventory
    }

    fun markupPercent(cost: Double, sellingPrice: Double): Double {
        nonnegative(cost, sellingPrice)
        require(cost > 0.0)
        return (sellingPrice - cost) / cost * 100.0
    }

    fun marginOfSafetyPercent(actualSales: Double, breakEvenSales: Double): Double {
        nonnegative(actualSales, breakEvenSales)
        require(actualSales > 0.0)
        return (actualSales - breakEvenSales) / actualSales * 100.0
    }

    fun reorderPoint(dailyDemand: Double, leadTimeDays: Double, safetyStock: Double): Double {
        nonnegative(dailyDemand, leadTimeDays, safetyStock)
        return dailyDemand * leadTimeDays + safetyStock
    }

    fun weightedAverageCost(firstQuantity: Double, firstUnitCost: Double, secondQuantity: Double, secondUnitCost: Double): Double {
        nonnegative(firstQuantity, firstUnitCost, secondQuantity, secondUnitCost)
        require(firstQuantity + secondQuantity > 0.0)
        return (firstQuantity * firstUnitCost + secondQuantity * secondUnitCost) / (firstQuantity + secondQuantity)
    }
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

    fun simpleInterest(principal: Double, annualRate: Double, years: Double): Double {
        require(principal.isFinite() && principal >= 0 && annualRate.isFinite() && annualRate >= 0)
        require(years.isFinite() && years >= 0)
        return principal * annualRate * years
    }

    fun compoundAmount(principal: Double, annualRate: Double, years: Int): Double {
        require(principal.isFinite() && principal >= 0 && annualRate.isFinite() && annualRate >= 0)
        require(years >= 0)
        return principal * Math.pow(1.0 + annualRate, years.toDouble())
    }

    fun roiPercent(returnedAmount: Double, investment: Double): Double {
        require(returnedAmount.isFinite() && investment.isFinite() && investment > 0)
        return (returnedAmount - investment) / investment * 100.0
    }

    fun contributionMarginPercent(price: Double, variableCost: Double): Double =
        contributionMarginPerUnit(price, variableCost) / price * 100.0

    fun breakEvenSales(fixedCosts: Double, price: Double, variableCost: Double): Double {
        require(fixedCosts.isFinite() && fixedCosts >= 0)
        val marginRatio = contributionMarginPerUnit(price, variableCost) / price
        return fixedCosts / marginRatio
    }
}
