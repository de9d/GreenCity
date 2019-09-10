package greencity.security.service.impl;

import static greencity.constant.ErrorMessage.*;

import greencity.entity.OwnSecurity;
import greencity.entity.User;
import greencity.entity.enums.ROLE;
import greencity.entity.enums.UserStatus;
import greencity.exception.BadEmailException;
import greencity.exception.BadIdException;
import greencity.exception.BadRefreshTokenException;
import greencity.security.dto.AccessTokenDto;
import greencity.security.dto.SuccessSignInDto;
import greencity.security.dto.ownsecurity.OwnSignInDto;
import greencity.security.dto.ownsecurity.OwnSignUpDto;
import greencity.security.jwt.JwtTokenTool;
import greencity.security.repository.OwnSecurityRepo;
import greencity.security.service.OwnSecurityService;
import greencity.security.service.VerifyEmailService;
import greencity.service.UserService;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@inheritDoc}
 */
@Service
@AllArgsConstructor
@Slf4j
public class OwnSecurityServiceImpl implements OwnSecurityService {
    private OwnSecurityRepo repo;
    private UserService userService;
    private VerifyEmailService verifyEmailService;
    private PasswordEncoder passwordEncoder;
    private AuthenticationManager manager;
    private JwtTokenTool jwtTokenTool;

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public void signUp(OwnSignUpDto dto) {
        log.info("begin");
        User byEmail = userService.findByEmail(dto.getEmail()).get();
        if (byEmail != null) {
            // He has already registered
            if (byEmail.getOwnSecurity() == null) {
                // He has already registered by else method of registration
                repo.save(createUserOwnSecurityToUser(dto, byEmail));
                verifyEmailService.save(byEmail);
            } else {
                throw new BadEmailException(USER_ALREADY_REGISTERED_WITH_THIS_EMAIL);
            }
        } else {
            User user = createNewRegisteredUser(dto);
            User savedUser = userService.save(user);
            repo.save(createUserOwnSecurityToUser(dto, savedUser));
            verifyEmailService.save(savedUser);
        }
        log.info("end");
    }

    private OwnSecurity createUserOwnSecurityToUser(OwnSignUpDto dto, User user) {
        return OwnSecurity.builder()
            .password(passwordEncoder.encode(dto.getPassword()))
            .user(user)
            .build();
    }

    private User createNewRegisteredUser(OwnSignUpDto dto) {
        return User.builder()
            .firstName(dto.getFirstName())
            .lastName(dto.getLastName())
            .email(dto.getEmail())
            .dateOfRegistration(LocalDateTime.now())
            .role(ROLE.ROLE_USER)
            .lastVisit(LocalDateTime.now())
            .userStatus(UserStatus.ACTIVATED)
            .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(OwnSecurity userOwnSecurity) {
        log.info("begin");
        if (!repo.existsById(userOwnSecurity.getId())) {
            throw new BadIdException(NO_ENY_USER_OWN_SECURITY_TO_DELETE + userOwnSecurity.getId());
        }
        repo.delete(userOwnSecurity);
        log.info("end");
    }

    /**
     * {@inheritDoc}
     */
    @Scheduled(fixedRate = 86400000)
    @Override
    public void deleteNotActiveEmailUsers() {
        // 86400000 - доба
        log.info("begin");
        verifyEmailService
            .findAll()
            .forEach(
                verifyEmail -> {
                    if (verifyEmailService.isDateValidate(verifyEmail.getExpiryDate())) {
                        delete(verifyEmail.getUser().getOwnSecurity());
                        verifyEmailService.delete(verifyEmail);
                        userService.deleteById(verifyEmail.getUser().getId());
                    }
                });
        log.info("end");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SuccessSignInDto signIn(OwnSignInDto dto) {
        log.info("begin");
        manager.authenticate(
            new UsernamePasswordAuthenticationToken(dto.getEmail(), dto.getPassword()));
        User byEmail = userService.findByEmail(dto.getEmail()).get();
        String accessToken = jwtTokenTool.createAccessToken(byEmail.getEmail(), byEmail.getRole());
        String refreshToken = jwtTokenTool.createRefreshToken(byEmail.getEmail());
        log.info("end");
        return new SuccessSignInDto(accessToken, refreshToken, byEmail.getFirstName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AccessTokenDto updateAccessToken(String refreshToken) {
        if (jwtTokenTool.isTokenValid(refreshToken)) {
            String email = jwtTokenTool.getEmailByToken(refreshToken);
            User user = userService.findByEmail(email).get();
            if (user != null) {
                return new AccessTokenDto(
                    jwtTokenTool.createAccessToken(user.getEmail(), user.getRole()));
            }
        }
        throw new BadRefreshTokenException(REFRESH_TOKEN_NOT_VALID);
    }
}