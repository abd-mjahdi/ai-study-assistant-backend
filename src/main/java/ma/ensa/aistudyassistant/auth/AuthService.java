package ma.ensa.aistudyassistant.auth;

import ma.ensa.aistudyassistant.auth.dto.AuthResponse;
import ma.ensa.aistudyassistant.auth.dto.LoginRequest;
import ma.ensa.aistudyassistant.auth.dto.MeResponse;
import ma.ensa.aistudyassistant.auth.dto.RegisterRequest;
import ma.ensa.aistudyassistant.model.entities.User;
import ma.ensa.aistudyassistant.repository.UserRepository;
import ma.ensa.aistudyassistant.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(BAD_REQUEST, "Email already in use");
        }

        User user = User.builder()
                .username(request.username().trim())
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.password()))
                .build();

        User savedUser = userRepository.save(user);
        String token = jwtService.generateToken(
                org.springframework.security.core.userdetails.User
                        .withUsername(savedUser.getEmail())
                        .password(savedUser.getPassword())
                        .authorities("ROLE_USER")
                        .build()
        );

        return new AuthResponse(token, savedUser.getId(), savedUser.getUsername());
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
            );
        } catch (Exception ex) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid email or password");
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Invalid email or password"));

        String token = jwtService.generateToken(
                org.springframework.security.core.userdetails.User
                        .withUsername(user.getEmail())
                        .password(user.getPassword())
                        .authorities("ROLE_USER")
                        .build()
        );

        return new AuthResponse(token, user.getId(), user.getUsername());
    }

    public MeResponse me(String userEmail) {
        String normalizedEmail = userEmail == null ? null : userEmail.trim().toLowerCase();
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid token");
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));

        return new MeResponse(user.getId(), user.getUsername(), user.getEmail());
    }
}
