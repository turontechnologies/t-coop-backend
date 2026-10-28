package com.turontechnologies.tcoop.coopstaff;

import com.turontechnologies.tcoop.member.Member;
import org.springframework.stereotype.Service;

/**
 * Server-side check of a co-op staff member's module/tab grants — the same encoding as the
 * frontend's lib/permissions.ts: "Module" (legacy, write), "Module::access" for the whole module,
 * "Module::Tab::access" for one tab. The co-op's own admin always has full access; a staff member
 * needs an active role in the same co-op that grants it.
 */
@Service
public class CoopPermissions {

  private final CoopRoleRepository coopRoleRepository;

  public CoopPermissions(CoopRoleRepository coopRoleRepository) {
    this.coopRoleRepository = coopRoleRepository;
  }

  /** Whether {@code caller} can reach {@code module} (and, when given, {@code tab}) in this co-op —
   * with write access if {@code write}. */
  public boolean allows(Member caller, String cooperativeId, String module, String tab, boolean write) {
    if (caller == null || !cooperativeId.equals(caller.getCooperativeId())) return false;
    if ("admin".equals(caller.getRole())) return true;
    if (caller.getCoopRoleId() == null) return false;
    CoopRole role = coopRoleRepository.findById(caller.getCoopRoleId()).orElse(null);
    if (role == null || !"Active".equals(role.getStatus()) || !cooperativeId.equals(role.getCooperativeId())) {
      return false;
    }
    String granted = grantedAccess(role, module, tab);
    return granted != null && (!write || "write".equals(granted));
  }

  /** Is this member co-op staff at all (the admin, or anyone holding an active co-op role)? */
  public boolean isStaff(Member caller, String cooperativeId) {
    if (caller == null || !cooperativeId.equals(caller.getCooperativeId())) return false;
    if ("admin".equals(caller.getRole())) return true;
    return caller.getCoopRoleId() != null
        && coopRoleRepository
            .findById(caller.getCoopRoleId())
            .map(role -> "Active".equals(role.getStatus()) && cooperativeId.equals(role.getCooperativeId()))
            .orElse(false);
  }

  private static String grantedAccess(CoopRole role, String module, String tab) {
    String best = null;
    for (String raw : role.getPermissions()) {
      String[] parts = raw.split("::");
      if (!module.equals(parts[0])) continue;
      String access;
      if (parts.length == 1) access = "write";
      else if (parts.length == 2) access = parts[1];
      else if (tab != null && tab.equals(parts[1])) access = parts[2];
      else if (tab == null) access = "read"; // a grant on any tab still reaches the module
      else continue;
      if ("write".equals(access)) return "write";
      best = "read";
    }
    return best;
  }
}
