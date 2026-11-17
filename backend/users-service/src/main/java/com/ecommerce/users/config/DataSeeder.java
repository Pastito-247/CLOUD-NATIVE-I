package com.ecommerce.users.config;

import com.ecommerce.users.model.User;
import com.ecommerce.users.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;

    public DataSeeder(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }
        userRepository.save(new User("admin@tuki.cl", "Administrador Demo", "admin"));
        userRepository.save(new User("staff@tuki.cl", "Staff Demo", "staff"));
        userRepository.save(new User("cliente@tuki.cl", "Cliente Demo", "customer"));
        log.info("Seed de usuarios demo completado: admin, staff y customer");
    }
}
