package com.narrativenews.service;

import com.narrativenews.domain.Admin;
import com.narrativenews.domain.Enums.AccountStatus;
import com.narrativenews.repo.AdminRepository;
import com.narrativenews.web.DomainException;
import com.narrativenews.web.Dtos.AdminUserView;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class AdminManagementService {
    private final AdminRepository admins;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public AdminManagementService(AdminRepository admins, PasswordEncoder encoder, AuditService audit) {
        this.admins = admins; this.encoder = encoder; this.audit = audit;
    }

    private static AdminUserView view(Admin a) { return new AdminUserView(a.getId(), a.getName(), a.getEmail(), a.getStatus().name()); }

    public List<AdminUserView> list() { return admins.findAll().stream().map(AdminManagementService::view).toList(); }

    public AdminUserView create(UUID byAdmin, String name, String email, String password) {
        String e = email.trim().toLowerCase();
        if (admins.existsByEmailIgnoreCase(e)) throw DomainException.conflict("EMAIL_TAKEN", "An admin with that email already exists");
        Admin a = new Admin();
        a.setName(name.trim()); a.setEmail(e); a.setPasswordHash(encoder.encode(password)); a.setCreatedBy(byAdmin);
        a = admins.save(a);
        audit.log(byAdmin, "ADMIN_CREATED", "ADMIN", a.getId());
        return view(a);
    }

    @Transactional
    public void disable(UUID byAdmin, UUID targetId) {
        Admin a = admins.findById(targetId).orElseThrow(() -> DomainException.notFound("ADMIN_NOT_FOUND", "Admin not found"));
        if (a.getStatus() == AccountStatus.DISABLED) return;
        if (admins.countByStatus(AccountStatus.ACTIVE) <= 1)
            throw DomainException.conflict("LAST_ADMIN", "You cannot disable the last active admin");
        a.setStatus(AccountStatus.DISABLED);
        admins.save(a);
        audit.log(byAdmin, "ADMIN_DISABLED", "ADMIN", targetId);
    }
}
