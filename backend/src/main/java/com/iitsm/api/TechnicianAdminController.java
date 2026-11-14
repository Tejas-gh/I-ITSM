package com.iitsm.api;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.iitsm.api.dto.AvailabilityRequest;
import com.iitsm.api.dto.TechnicianResponse;
import com.iitsm.application.command.SetTechnicianAvailabilityCommand;
import com.iitsm.application.service.UserAdminService;
import com.iitsm.domain.exception.InvalidInputException;

/**
 * Administrator technician management: list technicians and set their availability. (Skills are changed through
 * {@code PUT /api/admin/users/{id}}.) The administrator check is made by the application service.
 */
@RestController
@RequestMapping("/api/admin/technicians")
@Transactional
public class TechnicianAdminController {

    private final UserAdminService userAdmin;

    public TechnicianAdminController(UserAdminService userAdmin) {
        this.userAdmin = userAdmin;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<TechnicianResponse> list(Authentication authentication) {
        return userAdmin.listTechnicians(authentication.getName()).stream().map(TechnicianResponse::from).toList();
    }

    @PutMapping("/{id}/availability")
    public TechnicianResponse setAvailability(@PathVariable("id") String id, @RequestBody AvailabilityRequest request,
                                              Authentication authentication) {
        if (request.available() == null) {
            throw new InvalidInputException("available must be true or false");
        }
        return TechnicianResponse.from(userAdmin.setTechnicianAvailability(
                new SetTechnicianAvailabilityCommand(authentication.getName(), id, request.available())));
    }
}
