package com.iitsm.application.service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.iitsm.application.command.LoginCommand;
import com.iitsm.application.command.RegisterEmployeeCommand;
import com.iitsm.application.security.AccessToken;
import com.iitsm.application.security.CredentialsRepository;
import com.iitsm.application.security.PasswordHasher;
import com.iitsm.application.security.TokenIssuer;
import com.iitsm.application.security.UserCredentials;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.InvalidLoginException;
import com.iitsm.domain.factory.UserDraft;
import com.iitsm.domain.factory.UserFactory;
import com.iitsm.domain.model.Administrator;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.User;
import com.iitsm.domain.repository.UserRepository;

/**
 * Authentication use cases: log in, self-register an employee, and create the first administrator.
 *
 * <p>Plain class, no Spring annotations. Passwords exist only as method arguments; what is stored is the
 * hash from {@link PasswordHasher}. Every login failure carries the same message so a caller cannot tell
 * an unknown user from a wrong password.</p>
 */
public final class AuthService {

    /** Minimum password length (characters). */
    public static final int MIN_PASSWORD_LENGTH = 8;

    /** BCrypt only uses the first 72 bytes of a password, so longer ones are rejected rather than truncated. */
    public static final int MAX_PASSWORD_BYTES = 72;

    private static final String INVALID_CREDENTIALS = "Invalid user id or password";

    private final UserRepository users;
    private final CredentialsRepository credentials;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;
    private final UserFactory userFactory;

    public AuthService(UserRepository users, CredentialsRepository credentials, PasswordHasher passwordHasher,
                       TokenIssuer tokenIssuer, UserFactory userFactory) {
        this.users = require(users, "users");
        this.credentials = require(credentials, "credentials");
        this.passwordHasher = require(passwordHasher, "passwordHasher");
        this.tokenIssuer = require(tokenIssuer, "tokenIssuer");
        this.userFactory = require(userFactory, "userFactory");
    }

    /** Checks the credentials and returns an access token for the user. */
    public AccessToken login(LoginCommand command) {
        return tokenIssuer.issue(authenticate(command));
    }

    /**
     * Verifies the user id and password.
     *
     * @throws InvalidLoginException for an unknown user, a user without credentials, a wrong password
     *                               or an inactive user (always the same message)
     */
    public User authenticate(LoginCommand command) {
        require(command, "command");
        String userId = command.userId().trim();
        Optional<User> user = users.findById(userId);
        Optional<UserCredentials> stored = credentials.findByUserId(userId);
        if (user.isEmpty() || stored.isEmpty()
                || !passwordHasher.matches(command.password(), stored.get().passwordHash())
                || !user.get().isActive()) {
            throw new InvalidLoginException(INVALID_CREDENTIALS);
        }
        return user.get();
    }

    /** Creates an {@link Employee} account. Registration can never produce any other role. */
    public Employee registerEmployee(RegisterEmployeeCommand command) {
        require(command, "command");
        UserDraft draft = new UserDraft(command.id(), command.name(), command.email(), Role.EMPLOYEE,
                command.department(), null, 0, 0);
        return (Employee) createAccount(draft, command.password());
    }

    /** True when at least one user holds {@link Role#ADMINISTRATOR}. */
    public boolean administratorExists() {
        return users.findAll().stream().anyMatch(user -> user.getRole() == Role.ADMINISTRATOR);
    }

    /**
     * Creates the first administrator, only if no administrator exists yet. An existing administrator is
     * never modified.
     *
     * @return the new administrator, or empty when one already existed
     */
    public Optional<Administrator> bootstrapAdministrator(String id, String name, String email, String password) {
        if (administratorExists()) {
            return Optional.empty();
        }
        UserDraft draft = new UserDraft(id, name, email, Role.ADMINISTRATOR, null, null, 0, 0);
        return Optional.of((Administrator) createAccount(draft, password));
    }

    /**
     * Creates an account of any role: validates the draft through {@link UserFactory}, rejects a duplicate id and an
     * unacceptable password, then stores the user and the BCrypt hash of the password. Used by registration,
     * the first-administrator bootstrap and administrator-created users.
     */
    public User createAccount(UserDraft draft, String rawPassword) {
        User user = userFactory.create(draft);
        requireAcceptablePassword(rawPassword);
        if (users.findById(user.getId()).isPresent()) {
            throw new InvalidInputException("A user with id '" + user.getId() + "' already exists");
        }
        users.save(user);
        credentials.save(new UserCredentials(user.getId(), passwordHasher.hash(rawPassword)));
        return user;
    }

    private static void requireAcceptablePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new InvalidInputException("password must not be blank");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new InvalidInputException("password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new InvalidInputException("password must be at most " + MAX_PASSWORD_BYTES + " bytes");
        }
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new InvalidInputException(name + " must not be null");
        }
        return value;
    }
}
