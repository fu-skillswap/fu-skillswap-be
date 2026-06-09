package com.fptu.exe.skillswap;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class ProjectApplicationTests {

	@Autowired
	private UserRepository userRepository;

	@Test
	void contextLoads() {
	}

	@Test
	void uuidV7Generator_shouldAssignUuidV7PrimaryKey() {
		User user = User.builder()
				.email("uuid-v7-test@example.com")
				.fullName("UUID V7 Test")
				.status(UserStatus.ACTIVE)
				.build();

		User savedUser = userRepository.saveAndFlush(user);

		assertNotNull(savedUser.getId());
		assertEquals(7, savedUser.getId().version());
	}

}

