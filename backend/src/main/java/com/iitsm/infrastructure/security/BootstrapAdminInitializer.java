package com.iitsm.infrastructure.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.transaction.support.TransactionOperations;

import com.iitsm.application.service.AuthService;
import com.iitsm.domain.model.Administrator;

/**
 * Creates the first administrator at startup, and only if no administrator exists. Safe to run on every
 * start: an existing administrator is never touched. Without a configured password nothing is created
 * and a hint is logged. The password is never logged.
 */
public final class BootstrapAdminInitializer implements ApplicationRunner {

    private static final System.Logger LOG = System.getLogger(BootstrapAdminInitializer.class.getName());

    private final AuthService authService;
    private final BootstrapAdminProperties properties;
    private final TransactionOperations transaction;

    public BootstrapAdminInitializer(AuthService authService, BootstrapAdminProperties properties,
                                     TransactionOperations transaction) {
        this.authService = authService;
        this.properties = properties;
        this.transaction = transaction;
    }

    @Override
    public void run(ApplicationArguments args) {
        bootstrap();
    }

    /** Runs the bootstrap once; callable repeatedly. */
    public void bootstrap() {
        if (authService.administratorExists()) {
            LOG.log(System.Logger.Level.DEBUG, "An administrator already exists; bootstrap skipped.");
            return;
        }
        if (isBlank(properties.password())) {
            LOG.log(System.Logger.Level.WARNING, "No administrator exists and no bootstrap password is configured, "
                    + "so none was created. Set BOOTSTRAP_ADMIN_ID, BOOTSTRAP_ADMIN_NAME, BOOTSTRAP_ADMIN_EMAIL "
                    + "and BOOTSTRAP_ADMIN_PASSWORD (iitsm.bootstrap.admin.*) to create the first administrator.");
            return;
        }
        List<String> missing = new ArrayList<>();
        if (isBlank(properties.id())) {
            missing.add("iitsm.bootstrap.admin.id (BOOTSTRAP_ADMIN_ID)");
        }
        if (isBlank(properties.name())) {
            missing.add("iitsm.bootstrap.admin.name (BOOTSTRAP_ADMIN_NAME)");
        }
        if (isBlank(properties.email())) {
            missing.add("iitsm.bootstrap.admin.email (BOOTSTRAP_ADMIN_EMAIL)");
        }
        if (!missing.isEmpty()) {
            LOG.log(System.Logger.Level.WARNING,
                    "Bootstrap administrator not created; missing configuration: " + String.join(", ", missing));
            return;
        }
        Optional<Administrator> created = transaction.execute(status -> authService.bootstrapAdministrator(
                properties.id(), properties.name(), properties.email(), properties.password()));
        if (created != null && created.isPresent()) {
            LOG.log(System.Logger.Level.INFO, "Bootstrap administrator '" + created.get().getId() + "' created.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
