package com.turontechnologies.tcoop.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

  private String email;

  @Column(name = "avatar_url")
  private String avatarUrl;

  private String status;

  protected Member() {
    // JPA
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

  public String getEmail() {
    return email;
  }

  public String getAvatarUrl() {
    return avatarUrl;
  }

  public String getStatus() {
    return status;
  }
}
