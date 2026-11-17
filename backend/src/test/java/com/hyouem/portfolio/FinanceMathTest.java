package com.hyouem.portfolio;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
class FinanceMathTest {
  @Test void incomeIsPositive(){assertEquals(new BigDecimal("12.30"),FinanceMath.signed("INCOME",new BigDecimal("12.30")));}
  @Test void expenseIsNegative(){assertEquals(new BigDecimal("-12.30"),FinanceMath.signed("EXPENSE",new BigDecimal("12.30")));}
  @Test void invalidPrecisionRejected(){assertThrows(IllegalArgumentException.class,()->FinanceMath.signed("INCOME",new BigDecimal("1.001")));}
  @Test void recurringDateClampsToMonthEnd(){assertEquals(LocalDate.of(2026,2,28),FinanceMath.recurrenceDate(YearMonth.of(2026,2),31));}
  @Test void zeroIncomeSavingsRateIsZero(){assertEquals(BigDecimal.ZERO,FinanceMath.savingsRate(BigDecimal.ZERO,BigDecimal.TEN));}
  @Test void savingsRateIsReproducible(){assertEquals(new BigDecimal("25.0"),FinanceMath.savingsRate(new BigDecimal("400.00"),new BigDecimal("300.00")));}
}
