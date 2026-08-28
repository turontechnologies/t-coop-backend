package com.turontechnologies.tcoop.upload;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.turontechnologies.tcoop.member.MemberRepository;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Server-side signed uploads to Cloudinary — the Java equivalent of the
 * frontend's (soon to be retired) `src/app/api/upload/route.ts`. Same field
 * name ("file"), same response shape ({"url": ...}), same validation rules,
 * so switching the frontend over is a one-line fetch URL change.
 */
@RestController
public class UploadController {

  private static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;
  private static final Set<String> ALLOWED_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

  private static final long MAX_ATTACHMENT_BYTES = 2L * 1024 * 1024;
  private static final Set<String> ALLOWED_ATTACHMENT_TYPES = Set.of(
      "image/png",
      "image/jpeg",
      "application/pdf",
      "application/msword",
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

  private final Cloudinary cloudinary;
  private final MemberRepository memberRepository;

  public UploadController(Cloudinary cloudinary, MemberRepository memberRepository) {
    this.cloudinary = cloudinary;
    this.memberRepository = memberRepository;
  }

  @PostMapping(value = "/api/v1/uploads", consumes = "multipart/form-data")
  public ResponseEntity<Map<String, String>> upload(
      @RequestParam("file") MultipartFile file, Authentication authentication) {
    String memberId = (String) authentication.getPrincipal();
    var member = memberRepository.findById(memberId).orElse(null);
    if (member == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(Map.of("error", "Member no longer exists"));
    }
    if (file.isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of("error", "No file provided"));
    }
    if (!ALLOWED_TYPES.contains(file.getContentType())) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", "Only PNG, JPEG, or WEBP images are allowed"));
    }
    if (file.getSize() > MAX_UPLOAD_BYTES) {
      return ResponseEntity.badRequest().body(Map.of("error", "Image must be 5MB or smaller"));
    }

    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> result = cloudinary
          .uploader()
          .upload(file.getBytes(), ObjectUtils.asMap("folder", "t-coop/avatars"));
      String avatarUrl = (String) result.get("secure_url");
      member.setAvatarUrl(avatarUrl);
      memberRepository.save(member);
      return ResponseEntity.ok(Map.of("url", avatarUrl));
    } catch (Exception exception) {
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
          .body(Map.of("error", "Upload to Cloudinary failed"));
    }
  }

  /**
   * Notice Board attachments — a wider file-type allowlist than {@link #upload},
   * since a
   * meeting minutes/notice attachment is realistically a PDF or Word doc, not
   * just an avatar
   * image. Replaces the frontend's previous approach of inlining the file as
   * base64 straight
   * into the notice record (a real hosted URL, not a multi-hundred-KB blob in
   * every API
   * response). {@code resource_type: "auto"} lets Cloudinary store non-image
   * files correctly.
   */
  @PostMapping(value = "/api/v1/uploads/attachment", consumes = "multipart/form-data")
  public ResponseEntity<Map<String, Object>> uploadAttachment(
      @RequestParam("file") MultipartFile file) {
    if (file.isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of("error", "No file provided"));
    }
    if (!ALLOWED_ATTACHMENT_TYPES.contains(file.getContentType())) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", "Only PDF, Word, PNG, or JPEG files are allowed"));
    }
    if (file.getSize() > MAX_ATTACHMENT_BYTES) {
      return ResponseEntity.badRequest().body(Map.of("error", "Attachment must be 2MB or smaller"));
    }

    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> result = cloudinary
          .uploader()
          .upload(
              file.getBytes(),
              ObjectUtils.asMap("folder", "t-coop/notice-attachments", "resource_type", "auto"));
      return ResponseEntity.ok(
          Map.of(
              "url", (String) result.get("secure_url"),
              "name", file.getOriginalFilename(),
              "size", file.getSize()));
    } catch (Exception exception) {
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
          .body(Map.of("error", "Upload to Cloudinary failed"));
    }
  }
}
