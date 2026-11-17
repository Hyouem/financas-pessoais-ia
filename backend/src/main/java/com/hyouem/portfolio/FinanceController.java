package com.hyouem.portfolio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api")
public class FinanceController {
  private final JdbcTemplate db;
  FinanceController(JdbcTemplate db){this.db=db;}
  private UUID user(Authentication auth){return UUID.fromString(auth.getName());}
  public record AccountIn(String name,BigDecimal initialBalance){}
  public record CategoryIn(String name,String kind){}
  public record TransactionIn(UUID accountId,UUID categoryId,BigDecimal amount,String kind,String note,LocalDate occurredOn){}
  public record BudgetIn(UUID categoryId,String month,BigDecimal limitAmount){}
  public record RecurrenceIn(UUID accountId,UUID categoryId,BigDecimal amount,String kind,String note,Integer dayOfMonth){}
  private YearMonth month(String value){try{return YearMonth.parse(value);}catch(Exception ex){throw new IllegalArgumentException("Month must use YYYY-MM");}}
  private void checkText(String s,int max){if(s==null||s.isBlank()||s.length()>max)throw new IllegalArgumentException("Invalid name/title");}
  private void checkMoney(BigDecimal n){FinanceMath.signed("INCOME",n);if(n.precision()>18)throw new IllegalArgumentException("Amount exceeds limits");}
  private void ownedReference(UUID owner,UUID id,String table){
    if(id==null)throw new IllegalArgumentException("Reference required");
    String sql=switch(table){case "accounts"->"select count(*) from accounts where user_id=? and id=?";case "categories"->"select count(*) from categories where user_id=? and id=?";default->throw new IllegalArgumentException("Invalid table");};
    if(db.queryForObject(sql,Integer.class,owner,id)==0)throw new IllegalArgumentException("Reference not found");
  }
  private void categoryKind(UUID owner,UUID id,String kind){
    List<String> values=db.query("select kind from categories where user_id=? and id=?",(rs,i)->rs.getString(1),owner,id);
    if(values.isEmpty()||!values.get(0).equals(kind))throw new IllegalArgumentException("Category type does not match transaction type");
  }
  @GetMapping("/accounts")
  List<Map<String,Object>> accounts(Authentication auth){return db.queryForList("select a.id,a.name,a.initial_balance,coalesce(sum(case when t.kind='INCOME' then t.amount else -t.amount end),0)+a.initial_balance as balance from accounts a left join transactions t on t.account_id=a.id and t.user_id=a.user_id where a.user_id=? group by a.id,a.name,a.initial_balance order by a.name",user(auth));}
  @PostMapping("/accounts")
  ResponseEntity<?> account(Authentication auth,@RequestBody AccountIn a){
    if(a==null)throw new IllegalArgumentException("Invalid account");checkText(a.name(),100);
    BigDecimal initial=a.initialBalance()==null?BigDecimal.ZERO:a.initialBalance();
    if(initial.scale()>2||initial.precision()>18)throw new IllegalArgumentException("Invalid initial balance");
    UUID id=UUID.randomUUID();db.update("insert into accounts(id,user_id,name,initial_balance) values(?,?,?,?)",id,user(auth),a.name().strip(),initial);
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id",id));
  }
  @GetMapping("/categories")
  List<Map<String,Object>> categories(Authentication auth){return db.queryForList("select id,name,kind from categories where user_id=? order by kind,name",user(auth));}
  @PostMapping("/categories")
  ResponseEntity<?> category(Authentication auth,@RequestBody CategoryIn c){
    if(c==null)throw new IllegalArgumentException("Invalid category");checkText(c.name(),100);FinanceMath.signed(c.kind(),BigDecimal.ONE);
    UUID id=UUID.randomUUID();db.update("insert into categories(id,user_id,name,kind) values(?,?,?,?)",id,user(auth),c.name().strip(),c.kind());
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id",id));
  }
  @GetMapping("/transactions")
  List<Map<String,Object>> transactions(Authentication auth,@RequestParam(required=false) String month){
    YearMonth m=month(month==null?YearMonth.now().toString():month);
    return db.queryForList("select t.id,t.kind,t.amount,t.note,t.occurred_on,c.name as category,a.name as account from transactions t join categories c on c.id=t.category_id join accounts a on a.id=t.account_id where t.user_id=? and t.occurred_on>=? and t.occurred_on<? order by t.occurred_on desc,t.created_at desc limit 250",user(auth),m.atDay(1),m.plusMonths(1).atDay(1));
  }
  @PostMapping("/transactions")
  ResponseEntity<?> transaction(Authentication auth,@RequestBody TransactionIn t){
    if(t==null)throw new IllegalArgumentException("Transaction required");checkMoney(t.amount());FinanceMath.signed(t.kind(),t.amount());
    if(t.occurredOn()==null||t.note()!=null&&t.note().length()>200)throw new IllegalArgumentException("Invalid date/note");
    UUID owner=user(auth);ownedReference(owner,t.accountId(),"accounts");categoryKind(owner,t.categoryId(),t.kind());
    UUID id=UUID.randomUUID();db.update("insert into transactions(id,user_id,account_id,category_id,amount,kind,note,occurred_on) values(?,?,?,?,?,?,?,?)",id,owner,t.accountId(),t.categoryId(),t.amount(),t.kind(),t.note()==null?"":t.note(),t.occurredOn());
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id",id));
  }
  @GetMapping("/budgets")
  List<Map<String,Object>> budgets(Authentication auth,@RequestParam String month){
    String key=month(month).toString();
    return db.queryForList("select b.id,b.category_id,c.name as category,b.limit_amount,coalesce(sum(t.amount),0) as spent from budgets b join categories c on c.id=b.category_id left join transactions t on t.category_id=b.category_id and t.user_id=b.user_id and t.kind='EXPENSE' and to_char(t.occurred_on,'YYYY-MM')=b.month_key where b.user_id=? and b.month_key=? group by b.id,b.category_id,c.name,b.limit_amount order by c.name",user(auth),key);
  }
  @PostMapping("/budgets")
  Map<String,Object> budget(Authentication auth,@RequestBody BudgetIn b){
    if(b==null)throw new IllegalArgumentException("Budget required");checkMoney(b.limitAmount());String key=month(b.month()).toString();
    UUID owner=user(auth);categoryKind(owner,b.categoryId(),"EXPENSE");
    db.update("insert into budgets(id,user_id,category_id,month_key,limit_amount) values(?,?,?,?,?) on conflict(user_id,category_id,month_key) do update set limit_amount=excluded.limit_amount",UUID.randomUUID(),owner,b.categoryId(),key,b.limitAmount());
    return Map.of("saved",true);
  }
  @GetMapping("/recurrences")
  List<Map<String,Object>> recurrences(Authentication auth){
    return db.queryForList("select r.id,r.kind,r.amount,r.note,r.day_of_month,r.enabled,c.name as category from recurrences r join categories c on c.id=r.category_id where r.user_id=? order by c.name",user(auth));
  }
  @PostMapping("/recurrences")
  ResponseEntity<?> recurrence(Authentication auth,@RequestBody RecurrenceIn r){
    if(r==null)throw new IllegalArgumentException("Recurrence required");checkMoney(r.amount());FinanceMath.signed(r.kind(),r.amount());
    if(r.dayOfMonth()==null||r.dayOfMonth()<1||r.dayOfMonth()>31||r.note()!=null&&r.note().length()>200)throw new IllegalArgumentException("Invalid recurrence");
    UUID owner=user(auth);ownedReference(owner,r.accountId(),"accounts");categoryKind(owner,r.categoryId(),r.kind());
    UUID id=UUID.randomUUID();
    db.update("insert into recurrences(id,user_id,account_id,category_id,amount,kind,note,day_of_month) values(?,?,?,?,?,?,?,?)",id,owner,r.accountId(),r.categoryId(),r.amount(),r.kind(),r.note()==null?"":r.note(),r.dayOfMonth());
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id",id));
  }
  @PostMapping("/recurrences/apply")
  Map<String,Object> apply(Authentication auth,@RequestParam String month){
    YearMonth m=month(month);UUID owner=user(auth);int inserted=0;
    var rules=db.queryForList("select id,account_id,category_id,amount,kind,note,day_of_month from recurrences where user_id=? and enabled=true",owner);
    for(var r:rules){
      inserted+=db.update("insert into transactions(id,user_id,account_id,category_id,amount,kind,note,occurred_on,recurrence_id,period_key) values(?,?,?,?,?,?,?,?,?,?) on conflict(recurrence_id,period_key) do nothing",
        UUID.randomUUID(),owner,r.get("account_id"),r.get("category_id"),r.get("amount"),r.get("kind"),r.get("note"),FinanceMath.recurrenceDate(m,((Number)r.get("day_of_month")).intValue()),r.get("id"),m.toString());
    }
    return Map.of("created",inserted,"month",m.toString());
  }
  @GetMapping("/dashboard")
  Map<String,Object> dashboard(Authentication auth,@RequestParam(required=false) String month){
    YearMonth m=month(month==null?YearMonth.now().toString():month);UUID owner=user(auth);
    var totals=db.queryForMap("select coalesce(sum(case when kind='INCOME' then amount else 0 end),0) as income,coalesce(sum(case when kind='EXPENSE' then amount else 0 end),0) as expense from transactions where user_id=? and occurred_on>=? and occurred_on<?",owner,m.atDay(1),m.plusMonths(1).atDay(1));
    BigDecimal income=(BigDecimal)totals.get("income"),expense=(BigDecimal)totals.get("expense");
    var byCategory=db.queryForList("select c.name as category,sum(t.amount) as total from transactions t join categories c on c.id=t.category_id where t.user_id=? and t.kind='EXPENSE' and t.occurred_on>=? and t.occurred_on<? group by c.name order by total desc",owner,m.atDay(1),m.plusMonths(1).atDay(1));
    var daily=db.queryForList("select occurred_on::text as day,sum(case when kind='INCOME' then amount else -amount end) as net from transactions where user_id=? and occurred_on>=? and occurred_on<? group by occurred_on order by occurred_on",owner,m.atDay(1),m.plusMonths(1).atDay(1));
    var accounts=accounts(auth);BigDecimal balance=accounts.stream().map(a->(BigDecimal)a.get("balance")).reduce(BigDecimal.ZERO,BigDecimal::add);
    String rule=income.signum()==0?"Registre suas receitas para analisar a taxa de poupanca.":expense.compareTo(income)>0?"Despesas ultrapassaram as receitas. Revise seus maiores gastos.":"Resultado positivo. Considere manter reserva de emergencia.";
    Map<String,Object> response=new LinkedHashMap<>();
    response.put("month",m.toString());response.put("income",income);response.put("expense",expense);response.put("net",income.subtract(expense));response.put("balance",balance);
    response.put("savings_rate",FinanceMath.savingsRate(income,expense));response.put("by_category",byCategory);response.put("daily",daily);response.put("recommendation",rule);
    return response;
  }
}
