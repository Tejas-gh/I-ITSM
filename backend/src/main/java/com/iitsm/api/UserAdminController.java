package com.iitsm.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.iitsm.api.dto.CreateUserRequest;
import com.iitsm.api.dto.UpdateUserRequest;
import com.iitsm.api.dto.UserResponse;
import com.iitsm.application.command.CreateUserCommand;
import com.iitsm.application.command.UpdateUserCommand;
import com.iitsm.application.service.UserAdminService;

/**
 * Administrator user management. Translates HTTP to application commands and back; contains no business rules.
 * The actor is always the authenticated user; the administrator check is made by the application service
 * (a non-administrator gets 403 through the global error handler). Responses never include passwords.
 */
@RestController
@RequestMapping("/api/admin/users")
@Transactional
public class UserAdminController {

    private final UserAdminService userAdmin;

    public UserAdminController(UserAdminService userAdmin) {
        this.userAdmin = userAdmin;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@RequestBody CreateUserRequest request, Authentication authentication) {
        return UserResponse.from(userAdmin.createUser(new CreateUserCommand(
                actor(authentication), request.id(), request.name(), request.email(), request.role(),
                request.department(), request.skillLevels(), orZero(request.yearsOfExperience()),
                orZero(request.maxActiveIncidents()), request.password())));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<UserResponse> list(Authentication authentication) {
        return userAdmin.listUsers(actor(authentication)).stream().map(UserResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public UserResponse get(@PathVariable("id") String id, Authentication authentication) {
        return UserResponse.from(userAdmin.getUser(actor(authentication), id));
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable("id") String id, @RequestBody UpdateUserRequest request,
                               Authentication authentication) {
        return UserResponse.from(userAdmin.updateUser(new UpdateUserCommand(
                actor(authentication), id, request.name(), request.email(), request.department(),
                request.skillLevels(), request.yearsOfExperience(), request.maxActiveIncidents(),
                request.active())));
    }

    private static String actor(Authentication authentication) {
        return authentication.getName();
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
