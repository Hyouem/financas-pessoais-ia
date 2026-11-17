package com.hyouem.portfolio;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/auth")
public class AuthController {
  private final JdbcTemplate db; private final PasswordEncoder passwords; private final JwtEncoder tokens;
  AuthController(JdbcTemplate db, PasswordEncoder passwords, JwtEncoder tokens) {
    this.db=db; this.passwords=passwords; this.tokens=tokens;
  }
  public record Credentials(String email,String password) {}
  @PostMapping("/register")
  ResponseEntity<?> register(@RequestBody Credentials request) {
    if (!valid(request)) return ResponseEntity.badRequest().body(Map.of("error","Email or password invalid (8+ characters required)"));
    String email=request.email().strip().toLowerCase(java.util.Locale.ROOT);
    UUID id=UUID.randomUUID();
    try { db.update("insert into users(id,email,password_hash) values(?,?,?)", id,email,passwords.encode(request.password())); }
    catch(org.springframework.dao.DataIntegrityViolationException ex) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error","Email already registered"));
    }
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("token",token(id),"email",email));
  }
  @PostMapping("/login")
  ResponseEntity<?> login(@RequestBody Credentials request) {
    if (request==null || request.email()==null || request.password()==null)
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error","Invalid credentials"));
    var entries=db.queryForList("select id,password_hash from users where email=?",request.email().strip().toLowerCase(java.util.Locale.ROOT));
    if (entries.isEmpty() || !passwords.matches(request.password(),(String)entries.get(0).get("password_hash")))
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error","Invalid credentials"));
    return ResponseEntity.ok(Map.of("token",token((UUID)entries.get(0).get("id")),"email",request.email().strip()));
  }
  private boolean valid(Credentials c) {
    return c!=null && c.email()!=null && c.email().length()<=254 && c.email().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
      && c.password()!=null && c.password().length()>=8 && c.password().length()<=200;
  }
  private String token(UUID id) {
    Instant now=Instant.now();
    JwtClaimsSet claims=JwtClaimsSet.builder().issuer("hyouem-portfolio").subject(id.toString())
      .issuedAt(now).expiresAt(now.plus(12,ChronoUnit.HOURS)).build();
    return tokens.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
  }
}
