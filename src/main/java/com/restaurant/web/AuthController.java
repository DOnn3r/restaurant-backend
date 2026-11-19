package com.restaurant.web;

import com.restaurant.domain.AppUser;
import com.restaurant.repo.UserRepository;
import com.restaurant.security.CurrentUser;
import com.restaurant.security.JwtService;
import com.restaurant.web.Dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final CurrentUser current;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        AppUser u = users.findByUsername(req.username().trim()).orElse(null);
        if (u == null || !u.isActive() || !encoder.matches(req.password(), u.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiant ou mot de passe incorrect");
        }
        return new LoginResponse(jwt.generate(u), UserView.of(u));
    }

    @GetMapping("/me")
    public UserView me() {
        return UserView.of(current.get());
    }
}
