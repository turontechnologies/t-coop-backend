package com.turontechnologies.tcoop.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "members")
public class Member {

  @Id
  private String id;

  @Column(name = "cooperative_id")
  private String cooperativeId;

  private String role;

  @Column(name = "password_hash")
  private String passwordHash;

  @Column(name = "first_name")
  private String firstName;

  @Column(name = "last_name")
  private String lastName;

  @Column(name = "other_name")
  private String otherName;

  private String email;

  private String phone;

  private String gender;

  private String nin;

  @Column(name = "home_address")
  private String homeAddress;

  private String country;

  private String state;

  private String city;

  private String facebook;

  private String twitter;

  private String guarantor;

  @Column(name = "bank_code")
  private String bankCode;

  @Column(name = "account_number")
  private String accountNumber;

  @Column(name = "account_name")
  private String accountName;

  @Column(name = "avatar_url")
  private String avatarUrl;

  private String status;

  /** Only ever set for role "support" (platform staff) — see PlatformRole. */
  @Column(name = "platform_role_id")
  private UUID platformRoleId;

  @Column(name = "invite_token")
  private String inviteToken;

  @Column(name = "invite_token_expires_at")
  private LocalDateTime inviteTokenExpiresAt;

  @Column(name = "invited_at")
  private LocalDateTime invitedAt;

  @Column(name = "accepted_at")
  private LocalDateTime acceptedAt;

  protected Member() {
    // JPA
  }

  /** Creates a new member row — used when a super admin onboards a co-op's first admin. */
  public Member(
      String id,
      String cooperativeId,
      String role,
      String passwordHash,
      String firstName,
      String lastName,
      String email) {
    this.id = id;
    this.cooperativeId = cooperativeId;
    this.role = role;
    this.passwordHash = passwordHash;
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
    this.status = "Active";
  }

  public String getId() {
    return id;
  }

  public String getCooperativeId() {
    return cooperativeId;
  }

  public String getRole() {
    return role;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public String getFullName() {
    return firstName + " " + lastName;
  }

  public String getOtherName() {
    return otherName;
  }

  public String getEmail() {
    return email;
  }

  public String getPhone() {
    return phone;
  }

  public String getGender() {
    return gender;
  }

  public String getNin() {
    return nin;
  }

  public String getHomeAddress() {
    return homeAddress;
  }

  public String getCountry() {
    return country;
  }

  public String getState() {
    return state;
  }

  public String getCity() {
    return city;
  }

  public String getFacebook() {
    return facebook;
  }

  public String getTwitter() {
    return twitter;
  }

  public String getGuarantor() {
    return guarantor;
  }

  public String getBankCode() {
    return bankCode;
  }

  public String getAccountNumber() {
    return accountNumber;
  }

  public String getAccountName() {
    return accountName;
  }

  public String getAvatarUrl() {
    return avatarUrl;
  }

  public String getStatus() {
    return status;
  }

  /** Applies a self-service profile edit — never touches id/role/passwordHash/status. */
  public void updateProfile(
      String firstName,
      String lastName,
      String otherName,
      String gender,
      String phone,
      String email,
      String nin,
      String homeAddress,
      String country,
      String state,
      String city,
      String facebook,
      String twitter,
      String guarantor,
      String bankCode,
      String accountNumber,
      String accountName) {
    this.firstName = firstName;
    this.lastName = lastName;
    this.otherName = otherName;
    this.gender = gender;
    this.phone = phone;
    this.email = email;
    this.nin = nin;
    this.homeAddress = homeAddress;
    this.country = country;
    this.state = state;
    this.city = city;
    this.facebook = facebook;
    this.twitter = twitter;
    this.guarantor = guarantor;
    this.bankCode = bankCode;
    this.accountNumber = accountNumber;
    this.accountName = accountName;
  }

  public void changePassword(String newPasswordHash) {
    this.passwordHash = newPasswordHash;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  /** Only ever called from super-admin/admin co-op-member management — a member's own
   * self-service profile edit never touches this. */
  public void setRole(String role) {
    this.role = role;
  }

  public UUID getPlatformRoleId() {
    return platformRoleId;
  }

  public void setPlatformRoleId(UUID platformRoleId) {
    this.platformRoleId = platformRoleId;
  }

  public String getInviteToken() {
    return inviteToken;
  }

  public LocalDateTime getInviteTokenExpiresAt() {
    return inviteTokenExpiresAt;
  }

  public LocalDateTime getInvitedAt() {
    return invitedAt;
  }

  public LocalDateTime getAcceptedAt() {
    return acceptedAt;
  }

  /** Creates an unaccepted platform-staff invite row — status stays "Invited" (login-blocked;
   * see AuthController) until acceptInvite is called. passwordHash is a random, unusable
   * placeholder the invitee could never know, not a nullable column. */
  public static Member invitePlatformStaff(
      String id, String email, UUID platformRoleId, String placeholderPasswordHash) {
    Member member = new Member(id, null, "support", placeholderPasswordHash, "", "", email);
    member.status = "Invited";
    member.platformRoleId = platformRoleId;
    member.invitedAt = LocalDateTime.now();
    return member;
  }

  public void setInviteToken(String token, LocalDateTime expiresAt) {
    this.inviteToken = token;
    this.inviteTokenExpiresAt = expiresAt;
  }

  /** Turns an unaccepted invite into a real, loginable account. */
  public void acceptInvite(String firstName, String lastName, String passwordHash) {
    this.firstName = firstName;
    this.lastName = lastName;
    this.passwordHash = passwordHash;
    this.status = "Active";
    this.acceptedAt = LocalDateTime.now();
    this.inviteToken = null;
    this.inviteTokenExpiresAt = null;
  }
}
