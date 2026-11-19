package fi.haagahelia.smarthhealth.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fi.haagahelia.smarthhealth.service.AuthService;
import fi.haagahelia.smarthhealth.web.dto.AuthResponse;
import fi.haagahelia.smarthhealth.web.dto.ForgotPasswordRequest;
import fi.haagahelia.smarthhealth.web.dto.LoginRequest;
import fi.haagahelia.smarthhealth.web.dto.MessageResponse;
import fi.haagahelia.smarthhealth.web.dto.ResetPasswordRequest;
import fi.haagahelia.smarthhealth.web.dto.SignupRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AppUserController {

	private final AuthService authService;

	public AppUserController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/signup")
	public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
	}

	@PostMapping("/login")
	public AuthResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

	@GetMapping("/me")
	public MessageResponse currentUser(Authentication authentication) {
		return new MessageResponse(authentication.getName());
	}

	@PostMapping("/forgot-password")
	public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
		authService.requestPasswordReset(request.email());
		return new MessageResponse("If an account exists for that email, reset instructions will be sent.");
	}

	@PostMapping("/reset-password")
	public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		authService.resetPassword(request);
		return new MessageResponse("Password updated. You can now sign in.");
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@RequestHeader(name = "Authorization", required = false) String authorization) {
		authService.logout(authorization);
		return ResponseEntity.noContent().build();
	}
}
