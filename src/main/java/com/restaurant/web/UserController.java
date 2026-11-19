package com.restaurant.web;

import com.restaurant.domain.AppUser;
import com.restaurant.domain.Role;
import com.restaurant.repo.UserRepository;
import com.restaurant.security.CurrentUser;
import com.restaurant.web.Dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final CurrentUser current;

    @GetMapping
    public List<UserView> list() {
        return users.findAll(Sort.by("fullName")).stream().map(UserView::of).toList();
    }

    @PostMapping
    public UserView create(@Valid @RequestBody UserCreate req) {
        String username = req.username().trim();
        if (users.existsByUsername(username)) throw Http.conflict("Cet identifiant existe déjà");
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(req.password()));
        u.setFullName(req.fullName().trim());
        u.setRole(req.role());
        return UserView.of(users.save(u));
    }

    @PutMapping("/{id}")
    public UserView update(@PathVariable Long id, @Valid @RequestBody UserUpdate req) {
        AppUser u = users.findById(id).orElseThrow(() -> Http.notFound("Utilisateur introuvable"));
        boolean self = u.getId().equals(current.get().getId());
        if (self && ((req.active() != null && !req.active()) || (req.role() != null && req.role() != Role.ADMIN))) {
            throw Http.conflict("Vous ne pouvez pas désactiver ou rétrograder votre propre compte");
        }
        if (req.fullName() != null && !req.fullName().isBlank()) u.setFullName(req.fullName().trim());
        if (req.role() != null) u.setRole(req.role());
        if (req.active() != null) u.setActive(req.active());
        if (req.password() != null && !req.password().isBlank()) u.setPasswordHash(encoder.encode(req.password()));
        return UserView.of(users.save(u));
    }
}
