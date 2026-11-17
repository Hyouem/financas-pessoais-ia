package com.hyouem.portfolio;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
public final class FinanceMath {
  private FinanceMath(){}
  public static BigDecimal signed(String kind,BigDecimal amount){
    if(amount==null||amount.signum()<=0||amount.scale()>2)throw new IllegalArgumentException("Positive amount with at most 2 decimals required");
    if("INCOME".equals(kind))return amount;
    if("EXPENSE".equals(kind))return amount.negate();
    throw new IllegalArgumentException("Invalid transaction type");
  }
  public static LocalDate recurrenceDate(YearMonth month,int day){
    if(day<1||day>31)throw new IllegalArgumentException("Day must be 1-31");
    return month.atDay(Math.min(day,month.lengthOfMonth()));
  }
  public static BigDecimal savingsRate(BigDecimal income,BigDecimal expense){
    if(income.signum()==0)return BigDecimal.ZERO;
    return income.subtract(expense).multiply(BigDecimal.valueOf(100)).divide(income,1,RoundingMode.HALF_UP);
  }
}
