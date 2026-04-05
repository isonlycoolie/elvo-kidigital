package com.elvo.identity.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.elvo.identity.entity.User;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryUnitTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByEmailIgnoreCaseShouldLocateUser() {
        User user = new User();
        user.setEan("ELVO-TEST-000001");
        user.setEmail("unit.test@elvo.com");
        user.setPhone("+12025550101");
        user.setHashedPassword("hashed-password");
        user.setAccountStatus(User.AccountStatus.ACTIVE);
        userRepository.save(user);

        Optional<User> result = userRepository.findByEmailIgnoreCase("UNIT.TEST@ELVO.COM");

        assertTrue(result.isPresent());
        assertEquals("+12025550101", result.get().getPhone());
    }
}
