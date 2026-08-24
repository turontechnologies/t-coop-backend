package com.turontechnologies.tcoop.notice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/** POST /api/v1/notices. {@code sendAt} is the final computed ISO instant (the frontend resolves
 * "now" vs. a scheduled date+time before sending) — the backend just stores what it's given.
 * {@code targetCoopIds} is always required and non-empty; an admin may only name their own co-op
 * (enforced server-side in NoticeController, not just by what the client happens to send). */
public record NoticeCreateRequest(
    @NotBlank
        @Pattern(regexp = "General|Meeting Notice|Meeting Minutes", message = "Select a valid type")
        String type,
    @NotBlank(message = "Enter a title") String title,
    @NotBlank(message = "Enter an announcement message") String message,
    @NotBlank
        @Pattern(
            regexp = "All Members|All Admins|All Members & Admins",
            message = "Select a valid recipient")
        String recipient,
    @NotBlank
        @Pattern(regexp = "Email|SMS|Email & SMS", message = "Select a valid medium")
        String medium,
    String meetingDate,
    NoticeAttachmentInput attachment,
    @NotNull(message = "Missing send time") String sendAt,
    @NotEmpty(message = "Select at least one co-operative") List<String> targetCoopIds) {

  public record NoticeAttachmentInput(String name, String url, Long size) {}
}
