package com.iitsm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.iitsm.application.service.AdminPermissionPolicy;
import com.iitsm.application.service.AssignmentStrategyAdminService;
import com.iitsm.application.service.AuthService;
import com.iitsm.application.service.UserAdminService;
import com.iitsm.domain.assignment.SwitchableAssignmentStrategy;
import com.iitsm.domain.factory.UserFactory;
import com.iitsm.domain.repository.CategoryRepository;
import com.iitsm.domain.repository.UserRepository;

/**
 * Wires the administrator user/technician and assignment-strategy services (A7). The stateless
 * {@link AdminPermissionPolicy} is created inside each bean method rather than registered as a bean, so other
 * administrator services can build their own without a bean-name clash.
 */
@Configuration
public class AdminConfig {

    @Bean
    UserAdminService userAdminService(UserRepository users, CategoryRepository categories, UserFactory userFactory,
                                      AuthService authService) {
        return new UserAdminService(users, categories, new AdminPermissionPolicy(), userFactory, authService);
    }

    @Bean
    AssignmentStrategyAdminService assignmentStrategyAdminService(UserRepository users,
                                                                  SwitchableAssignmentStrategy strategy) {
        return new AssignmentStrategyAdminService(users, new AdminPermissionPolicy(), strategy);
    }
}
