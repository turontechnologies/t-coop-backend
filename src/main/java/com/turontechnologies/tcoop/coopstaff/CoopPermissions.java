package com.turontechnologies.tcoop.coopstaff;

import com.turontechnologies.tcoop.member.Member;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Server-side check of a co-op staff member's module/tab grants — the same encoding as the
 * frontend's lib/permissions.ts: "Module" (legacy, write), "Module::access" for the whole module,
 * "Module::Tab::access" for one tab. The co-op's own admin always has full access; a staff member
 * needs an active role in the same co-op that grants it.
 *
 * <p>Whatever role someone holds, their own savings and their own loans are always theirs:
 * {@link #BASELINE} is part of every role (see {@link #withBaseline}). Everything else they can
 * reach is exactly what the role grants, read or write.
 */
@Service
public class CoopPermissions {

  /** Every member keeps saving and borrowing as a member, whatever role they're given. */
  public static final List<String> BASELINE =
      List.of("Savings & Contributions::My Savings::write", "Loans::My Loans::write");

  /** A role's grants plus the {@link #BASELINE} — what login sends and what checks run against. */
  public static List<String> withBaseline(List<String> permissions) {
    List<String> merged = new ArrayList<>(permissions == null ? List.of() : permissions);
    for (String grant : BASELINE) {
      if (!merged.contains(grant)) merged.add(grant);
    }
    return merged;
  }

  private static boolean isBaseline(String module, String tab) {
    return ("Savings & Contributions".equals(module) && "My Savings".equals(tab))
        || ("Loans".equals(module) && "My Loans".equals(tab));
  }

  private final CoopRoleRepository coopRoleRepository;

  public CoopPermissions(CoopRoleRepository coopRoleRepository) {
    this.coopRoleRepository = coopRoleRepository;
  }

  /** Whether {@code caller} can reach {@code module} (and, when given, {@code tab}) in this co-op —
   * with write access if {@code write}. */
  public boolean allows(Member caller, String cooperativeId, String module, String tab, boolean write) {
    if (caller == null || !cooperativeId.equals(caller.getCooperativeId())) return false;
    if (isCooperativeAdmin(caller, cooperativeId)) return true;
    // Own savings and loans: every member of the co-op, role or not.
    if (isBaseline(module, tab)) return true;
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
    if (isCooperativeAdmin(caller, cooperativeId)) return true;
    return caller.getCoopRoleId() != null
        && coopRoleRepository
            .findById(caller.getCoopRoleId())
            .map(role -> "Active".equals(role.getStatus()) && cooperativeId.equals(role.getCooperativeId()))
            .orElse(false);
  }

  private static boolean isCooperativeAdmin(Member caller, String cooperativeId) {
    return "admin".equals(caller.getRole()) && cooperativeId.equals(caller.getId());
  }

  private static String grantedAccess(CoopRole role, String module, String tab) {
    String best = null;
    for (String raw : withBaseline(role.getPermissions())) {
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
