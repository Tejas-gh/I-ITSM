package com.iitsm.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.iitsm.api.dto.AuthResponse;
import com.iitsm.api.dto.LoginRequest;
import com.iitsm.api.dto.RegisterRequest;
import com.iitsm.api.dto.RegisteredUserResponse;
import com.iitsm.application.command.LoginCommand;
import com.iitsm.application.command.RegisterEmployeeCommand;
import com.iitsm.application.security.AccessToken;
import com.iitsm.application.service.AuthService;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.InvalidLoginException;
import com.iitsm.domain.model.Employee;

/** Public authentication endpoints: log in and register an employee. Contains no business logic. */
@RestController
@RequestMapping("/api/auth")
@Transactional
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Transactional(readOnly = true)
    public AuthResponse login(@RequestBody LoginRequest request) {
        AccessToken token = authService.login(new LoginCommand(request.id(), request.password()));
        return new AuthResponse(token.value(), "Bearer", token.expiresAt());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisteredUserResponse register(@RequestBody RegisterRequest request) {
        Employee employee = authService.registerEmployee(new RegisterEmployeeCommand(request.id(), request.name(),
                request.email(), request.department(), request.password()));
        return new RegisteredUserResponse(employee.getId(), employee.getName(), employee.getEmail(), employee.getRole());
    }

    // Status codes only (invalid login -> 401, invalid input -> 400, per the contract's HTTP map).
    // The JSON error body belongs to the global handler built in A6, which should replace these two methods.
    @ExceptionHandler(InvalidLoginException.class)
    public ResponseEntity<Void> onInvalidLogin() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<Void> onInvalidInput() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
}
