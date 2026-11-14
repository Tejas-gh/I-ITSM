package com.iitsm.application.service;

import java.util.List;
import java.util.Map;

import com.iitsm.application.command.CreateUserCommand;
import com.iitsm.application.command.SetTechnicianAvailabilityCommand;
import com.iitsm.application.command.UpdateUserCommand;
import com.iitsm.domain.exception.CategoryNotFoundException;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.UserNotFoundException;
import com.iitsm.domain.factory.UserDraft;
import com.iitsm.domain.factory.UserFactory;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.User;
import com.iitsm.domain.repository.CategoryRepository;
import com.iitsm.domain.repository.UserRepository;

/**
 * Administrative management of users and technicians. Restricted to administrators
 * ({@link AdminPermissionPolicy}); the permission check always runs first.
 *
 * <p>New users are built by {@link UserFactory} and receive their credentials through
 * {@link AuthService#createAccount} (BCrypt hash only). {@code User} and {@code Technician} have no setters, so an
 * update builds a replacement through the factory and saves it under the same id (the repository save is an upsert).
 * That is also how a technician's skills change: the new skill map replaces the old one.</p>
 */
public final class UserAdminService {

    private final UserRepository users;
    private final CategoryRepository categories;
    private final AdminPermissionPolicy permissionPolicy;
    private final UserFactory userFactory;
    private final AuthService authService;

    public UserAdminService(UserRepository users, CategoryRepository categories, AdminPermissionPolicy permissionPolicy,
                            UserFactory userFactory, AuthService authService) {
        if (users == null || categories == null || permissionPolicy == null || userFactory == null
                || authService == null) {
            throw new InvalidInputException("users, categories, permissionPolicy, userFactory and authService "
                    + "must not be null");
        }
        this.users = users;
        this.categories = categories;
        this.permissionPolicy = permissionPolicy;
        this.userFactory = userFactory;
        this.authService = authService;
    }

    /**
     * Creates a user of the requested role, with credentials.
     *
     * @throws InvalidInputException if the id already exists, the data or password is invalid
     * @throws CategoryNotFoundException if a technician skill names an unknown category
     */
    public User createUser(CreateUserCommand command) {
        require(command, "command");
        permissionPolicy.check(command.actorId(), users);

        Map<String, Integer> skills = null;
        if (isTechnicianRole(command.role())) {
            skills = command.skillLevels() == null ? Map.of() : command.skillLevels();
            requireKnownCategories(skills);
        }
        UserDraft draft = new UserDraft(command.id(), command.name(), command.email(), command.role(),
                command.department(), skills, command.yearsOfExperience(), command.maxActiveIncidents());
        return authService.createAccount(draft, command.password());
    }

    /**
     * Updates a user. Fields left {@code null} keep their current value; the role never changes; a non-null skill
     * map replaces the technician's skills. The user is replaced under the same id.
     *
     * @throws UserNotFoundException if the user does not exist
     * @throws InvalidInputException if a technician-only field is given for a non-technician (or department for a
     *                               non-employee), or any value is invalid
     */
    public User updateUser(UpdateUserCommand command) {
        require(command, "command");
        permissionPolicy.check(command.actorId(), users);
        User existing = load(command.userId());

        boolean technician = existing instanceof Technician;
        boolean employee = existing instanceof Employee;
        if (!technician && (command.skillLevels() != null || command.yearsOfExperience() != null
                || command.maxActiveIncidents() != null)) {
            throw new InvalidInputException("skillLevels, yearsOfExperience and maxActiveIncidents apply only to "
                    + "technicians");
        }
        if (!employee && command.department() != null) {
            throw new InvalidInputException("department applies only to employees");
        }

        String name = command.name() != null ? command.name() : existing.getName();
        String email = command.email() != null ? command.email() : existing.getEmail();
        String department = null;
        Map<String, Integer> skills = null;
        int years = 0;
        int maxActive = 0;
        if (existing instanceof Employee current) {
            department = command.department() != null ? command.department() : current.getDepartment();
        } else if (existing instanceof Technician current) {
            if (command.skillLevels() != null) {
                requireKnownCategories(command.skillLevels());
            }
            skills = command.skillLevels() != null ? command.skillLevels() : current.getSkillLevels();
            years = command.yearsOfExperience() != null ? command.yearsOfExperience() : current.getYearsOfExperience();
            maxActive = command.maxActiveIncidents() != null ? command.maxActiveIncidents()
                    : current.getMaxActiveIncidents();
        }

        User replacement = userFactory.create(
                new UserDraft(existing.getId(), name, email, existing.getRole(), department, skills, years, maxActive));

        // State that is not part of the draft must survive the replacement.
        boolean active = command.active() != null ? command.active() : existing.isActive();
        if (!active) {
            replacement.deactivate();
        }
        if (existing instanceof Technician before && replacement instanceof Technician after && !before.isAvailable()) {
            after.markUnavailable();
        }
        return users.save(replacement);
    }

    public User getUser(String actorId, String userId) {
        permissionPolicy.check(actorId, users);
        return load(userId);
    }

    public List<User> listUsers(String actorId) {
        permissionPolicy.check(actorId, users);
        return users.findAll();
    }

    public List<Technician> listTechnicians(String actorId) {
        permissionPolicy.check(actorId, users);
        return users.findAllTechnicians();
    }

    /**
     * Marks a technician available or unavailable.
     *
     * @throws UserNotFoundException if no such user exists
     * @throws InvalidInputException if the user is not a technician
     */
    public Technician setTechnicianAvailability(SetTechnicianAvailabilityCommand command) {
        require(command, "command");
        permissionPolicy.check(command.actorId(), users);
        User user = load(command.technicianId());
        if (!(user instanceof Technician technician)) {
            throw new InvalidInputException("User " + user.getId() + " is not a technician");
        }
        if (command.available()) {
            technician.markAvailable();
        } else {
            technician.markUnavailable();
        }
        users.save(technician);
        return technician;
    }

    private User load(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new InvalidInputException("userId must not be blank");
        }
        return users.findById(userId.trim())
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
    }

    private void requireKnownCategories(Map<String, Integer> skills) {
        for (String categoryId : skills.keySet()) {
            if (categoryId == null || categoryId.isBlank()) {
                throw new InvalidInputException("skill category id must not be blank");
            }
        }
        for (String categoryId : skills.keySet()) {
            if (categories.findById(categoryId).isEmpty()) {
                throw new CategoryNotFoundException("Category not found: " + categoryId);
            }
        }
    }

    private static boolean isTechnicianRole(Role role) {
        return role == Role.TECHNICIAN || role == Role.TEAM_LEAD;
    }

    private static void require(Object value, String name) {
        if (value == null) {
            throw new InvalidInputException(name + " must not be null");
        }
    }
}
