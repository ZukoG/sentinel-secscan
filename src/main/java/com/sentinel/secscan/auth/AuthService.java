package com.sentinel.secscan.auth;

import com.sentinel.secscan.auth.dto.AuthResponse;
import com.sentinel.secscan.auth.dto.LoginRequest;
import com.sentinel.secscan.auth.dto.RegisterRequest;
import com.sentinel.secscan.domain.Role;
import com.sentinel.secscan.domain.User;
import com.sentinel.secscan.domain.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * register() and login() are kept separate rather than auto-issuing a
 * token on registration, matching FR-1.1 and FR-1.2 in docs/SRS.md as two
 * distinct steps.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public void register(RegisterRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        String passwordHash = passwordEncoder.encode(request.password());
        User user = new User(request.email(), passwordHash, Role.USER);
        userRepository.save(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            // Same message as the "email not found" case above, on purpose.
            // Distinguishing them would let an attacker enumerate emails.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        return new AuthResponse(jwtService.generateToken(user));
    }
}
