package com.ecommerce.users.service;

import com.ecommerce.users.dto.RegisterRequest;
import com.ecommerce.users.event.UserRegisteredEvent;
import com.ecommerce.users.exception.DuplicateEmailException;
import com.ecommerce.users.messaging.UserEventPublisher;
import com.ecommerce.users.model.User;
import com.ecommerce.users.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private static final String DEFAULT_ROLE = "customer";

    private final UserRepository userRepository;
    private final UserEventPublisher userEventPublisher;

    public UserService(UserRepository userRepository,
                       UserEventPublisher userEventPublisher) {
        this.userRepository = userRepository;
        this.userEventPublisher = userEventPublisher;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User createUser(User user) {
        return userRepository.save(user);
    }

    /**
     * Registra una cuenta nueva desde la pagina. Si el email ya existe lanza
     * {@link DuplicateEmailException}. Tras guardar publica {@code user.registered}
     * para que el correo de bienvenida se genere de forma asincrona.
     */
    public User register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.findByEmail(email).isPresent()) {
            throw new DuplicateEmailException(email);
        }

        String role = (request.role() == null || request.role().isBlank())
                ? DEFAULT_ROLE
                : request.role().trim().toLowerCase();

        User user = new User(email, request.name().trim(), role);
        User saved = userRepository.save(user);
        log.info("Cuenta registrada id={} email={} rol={}", saved.getId(), email, role);

        userEventPublisher.publishUserRegistered(new UserRegisteredEvent(
                saved.getId(),
                saved.getEmail(),
                saved.getName(),
                saved.getRole(),
                LocalDateTime.now()));

        return saved;
    }

    public User updateUser(Long id, User userDetails) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setName(userDetails.getName());
        user.setRole(userDetails.getRole());
        user.setAzureId(userDetails.getAzureId());

        return userRepository.save(user);
    }

    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        userRepository.delete(user);
    }
}
