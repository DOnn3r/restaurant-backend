package com.restaurant;

import com.restaurant.domain.AppUser;
import com.restaurant.domain.Role;
import com.restaurant.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Crée le compte admin au premier démarrage. Le menu et les tables restent vides : l'admin les ajoute. */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    @Value("${app.admin.username}")
    private String username;

    @Value("${app.admin.password}")
    private String password;

    @Override
    public void run(String... args) {
        if (users.count() > 0) return;
        AppUser admin = new AppUser();
        admin.setUsername(username);
        admin.setPasswordHash(encoder.encode(password));
        admin.setFullName("Administrateur");
        admin.setRole(Role.ADMIN);
        users.save(admin);
        log.warn("Compte admin '{}' créé. Changez son mot de passe dès la première connexion.", username);
    }
}
