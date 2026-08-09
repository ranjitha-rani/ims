package com.ims.platform;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    record CreateAdmin(@Email @NotBlank String email, @Size(min=12,max=100) String password, @NotBlank @Size(max=120) String displayName) {}
    record ChangePassword(@NotBlank String currentPassword, @Size(min=12,max=100) String newPassword) {}
    private final UserRepository users;
    private final PasswordEncoder passwords;
    UserController(UserRepository users, PasswordEncoder passwords) { this.users=users; this.passwords=passwords; }

    @PostMapping("/me/password") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    void changePassword(@Valid @RequestBody ChangePassword r, @AuthenticationPrincipal ImsPrincipal principal) {
        UserAccount user=users.findById(principal.id()).orElseThrow(() -> new NotFoundException("User not found"));
        if (!passwords.matches(r.currentPassword(),user.passwordHash))
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid current password");
        user.passwordHash=passwords.encode(r.newPassword());
    }
    @GetMapping("/me")
    AuthController.UserView me(@AuthenticationPrincipal ImsPrincipal principal) {
        return AuthController.UserView.from(users.findById(principal.id()).orElseThrow(() -> new NotFoundException("User not found")));
    }
    @GetMapping @PreAuthorize("hasRole('ADMIN')")
    List<AuthController.UserView> all() { return users.findAll().stream().map(AuthController.UserView::from).toList(); }
    @GetMapping("/{id}") @PreAuthorize("hasRole('ADMIN') or #id == principal.id")
    AuthController.UserView one(@PathVariable UUID id, @AuthenticationPrincipal ImsPrincipal principal) {
        return AuthController.UserView.from(users.findById(id).orElseThrow(() -> new NotFoundException("User not found")));
    }
    @PostMapping("/admins") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')") @Transactional
    AuthController.UserView admin(@Valid @RequestBody CreateAdmin r) {
        if (users.findByEmailIgnoreCase(r.email()).isPresent()) throw new ConflictException("Email is already registered");
        return AuthController.UserView.from(users.save(new UserAccount(r.email(),passwords.encode(r.password()),r.displayName(),Role.ADMIN)));
    }
    @GetMapping("/customers") @PreAuthorize("hasRole('ADMIN')")
    List<AuthController.UserView> customers() {
        return users.findAll().stream().filter(u -> u.role==Role.CUSTOMER).map(AuthController.UserView::from).toList();
    }
    @GetMapping("/admins") @PreAuthorize("hasRole('ADMIN')")
    List<AuthController.UserView> admins() {
        return users.findAll().stream().filter(u -> u.role==Role.ADMIN).map(AuthController.UserView::from).toList();
    }
}

@org.springframework.stereotype.Component
class AdminBootstrap implements ApplicationRunner {
    private final UserRepository users; private final PasswordEncoder passwords;
    private final String email; private final String password; private final String name;
    AdminBootstrap(UserRepository users,PasswordEncoder passwords,
                   @Value("${ims.bootstrap-admin.email:}") String email,
                   @Value("${ims.bootstrap-admin.password:}") String password,
                   @Value("${ims.bootstrap-admin.display-name:IMS Administrator}") String name) {
        this.users=users; this.passwords=passwords; this.email=email; this.password=password; this.name=name;
    }
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank()) return;
        if (password.length()<12)
            throw new IllegalStateException("Bootstrap admin password must be at least 12 characters");
        users.findByEmailIgnoreCase(email)
            .orElseGet(() -> users.save(new UserAccount(email,passwords.encode(password),name,Role.ADMIN)));
    }
}
