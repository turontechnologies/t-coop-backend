package com.turontechnologies.tcoop.auth;

public record LoginResponse(String token, MemberDto member) {}
