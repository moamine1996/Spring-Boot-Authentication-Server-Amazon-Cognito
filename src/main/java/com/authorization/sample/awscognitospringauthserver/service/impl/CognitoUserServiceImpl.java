package com.authorization.sample.awscognitospringauthserver.service.impl;

import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.model.*;
import com.authorization.sample.awscognitospringauthserver.configuration.AwsConfig;
import com.authorization.sample.awscognitospringauthserver.domain.enums.CognitoAttributesEnum;
import com.authorization.sample.awscognitospringauthserver.exception.FailedAuthenticationException;
import com.authorization.sample.awscognitospringauthserver.exception.ServiceException;
import com.authorization.sample.awscognitospringauthserver.service.CognitoUserService;
import com.authorization.sample.awscognitospringauthserver.service.dto.PasswordResetDTO;
import com.authorization.sample.awscognitospringauthserver.service.dto.UserSignUpDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.passay.CharacterData;
import org.passay.CharacterRule;
import org.passay.EnglishCharacterData;
import org.passay.PasswordGenerator;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.amazonaws.services.cognitoidp.model.ChallengeNameType.NEW_PASSWORD_REQUIRED;

@RequiredArgsConstructor
@Slf4j
@Service
public class CognitoUserServiceImpl implements CognitoUserService {

    private final AWSCognitoIdentityProvider awsCognitoIdentityProvider;

    private final AwsConfig awsConfig;


    /**
     * {@inheritDoc}
     */
    @Override
    public UserType signUp(UserSignUpDTO signUpDTO) {

        try {
            final AdminCreateUserRequest signUpRequest = new AdminCreateUserRequest()
                    .withUserPoolId(awsConfig.getCognito().getUserPoolId())
                    // The user's temporary password.
                    .withTemporaryPassword(generateValidPassword())
                    // Specify "EMAIL" if email will be used to send the welcome message
                    .withDesiredDeliveryMediums(DeliveryMediumType.EMAIL)
                    .withUsername(signUpDTO.getEmail())
                    .withMessageAction(MessageActionType.SUPPRESS)
                    .withUserAttributes(
                            new AttributeType().withName("name").withValue(signUpDTO.getName()),
                            new AttributeType().withName("family_name").withValue(signUpDTO.getLastname()),
                            new AttributeType().withName("custom:tenantId").withValue(signUpDTO.getTenantId()),
                            new AttributeType().withName("custom:tenatName").withValue(signUpDTO.getTenatName()),
                            new AttributeType().withName("custom:lastname").withValue(signUpDTO.getLastname()),
                            new AttributeType().withName("custom:is_deleted").withValue(signUpDTO.getIs_deleted()),
                            new AttributeType().withName("custom:nonexpired").withValue(signUpDTO.getNonexpired()),
                            new AttributeType().withName("custom:nonlocked").withValue(signUpDTO.getNonlocked()),
                            new AttributeType().withName("custom:firsttime_log_remain").withValue(signUpDTO.getFirsttime_log_remain()),
                            new AttributeType().withName("custom:is_self_service_usr").withValue(signUpDTO.getIs_self_service_usr()),
                            new AttributeType().withName("custom:nonexpired_credls").withValue(signUpDTO.getNonexpired_credls()),
                            new AttributeType().withName("custom:office_id").withValue(signUpDTO.getOffice_id()),
                            new AttributeType().withName("custom:pd_never_expires").withValue(signUpDTO.getPd_never_expires()),
                            new AttributeType().withName("custom:staff_id").withValue(signUpDTO.getStaff_id()),
                            new AttributeType().withName("custom:last_time_pd_updated").withValue(signUpDTO.getLast_time_pd_updated()),

                            new AttributeType().withName("email").withValue(signUpDTO.getEmail()),
                            new AttributeType().withName("email_verified").withValue("true"),
                            new AttributeType().withName("phone_number").withValue(signUpDTO.getPhoneNumber()),
                            new AttributeType().withName("phone_number_verified").withValue("true"));

            // create user
            AdminCreateUserResult createUserResult = awsCognitoIdentityProvider.adminCreateUser(signUpRequest);
            log.info("Created User id: {}", createUserResult.getUser().getUsername());

            // assign the roles
            signUpDTO.getRoles().forEach(r -> addUserToGroup(signUpDTO.getEmail(), r));

            // set permanent password
            setUserPassword(signUpDTO.getEmail(), signUpDTO.getPassword());

            return createUserResult.getUser();

        } catch (com.amazonaws.services.cognitoidp.model.UsernameExistsException e) {
            throw new UsernameExistsException("User name that already exists");
        } catch (com.amazonaws.services.cognitoidp.model.InvalidPasswordException e) {
            throw new com.authorization.sample.awscognitospringauthserver.exception.InvalidPasswordException("Invalid password.", e);
        } catch (com.authorization.sample.awscognitospringauthserver.exception.FailedAuthenticationException e) {
            throw new com.authorization.sample.awscognitospringauthserver.exception.FailedAuthenticationException("Invalid password.", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addUserToGroup(String username, String groupName) {

        try {
            // add user to group
            AdminAddUserToGroupRequest addUserToGroupRequest = new AdminAddUserToGroupRequest()
                    .withGroupName(groupName)
                    .withUserPoolId(awsConfig.getCognito().getUserPoolId())
                    .withUsername(username);

            awsCognitoIdentityProvider.adminAddUserToGroup(addUserToGroupRequest);
        } catch (com.amazonaws.services.cognitoidp.model.InvalidPasswordException e) {
            throw new FailedAuthenticationException(String.format("Invalid parameter: %s", e.getErrorMessage()), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AdminSetUserPasswordResult setUserPassword(String username, String password) {

        try {
            // Sets the specified user's password in a user pool as an administrator. Works on any user.
            AdminSetUserPasswordRequest adminSetUserPasswordRequest = new AdminSetUserPasswordRequest()
                    .withUsername(username)
                    .withPassword(password)
                    .withUserPoolId(awsConfig.getCognito().getUserPoolId())
                    .withPermanent(true);

            return awsCognitoIdentityProvider.adminSetUserPassword(adminSetUserPasswordRequest);
        } catch (com.amazonaws.services.cognitoidp.model.InvalidPasswordException e) {
            throw new FailedAuthenticationException(String.format("Invalid parameter: %s", e.getErrorMessage()), e);
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<AdminInitiateAuthResult> initiateAuth(String username, String password) {

        final Map<String, String> authParams = new HashMap<>();
        authParams.put(CognitoAttributesEnum.USERNAME.name(), username);
        authParams.put(CognitoAttributesEnum.PASSWORD.name(), password);
        authParams.put(CognitoAttributesEnum.SECRET_HASH.name(), calculateSecretHash(awsConfig.getCognito().getAppClientId(), awsConfig.getCognito().getAppClientSecret(), username));


        final AdminInitiateAuthRequest authRequest = new AdminInitiateAuthRequest()
                .withAuthFlow(AuthFlowType.ADMIN_NO_SRP_AUTH)
                .withClientId(awsConfig.getCognito().getAppClientId())
                .withUserPoolId(awsConfig.getCognito().getUserPoolId())
                .withAuthParameters(authParams);

        return adminInitiateAuthResult(authRequest);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<AdminRespondToAuthChallengeResult> respondToAuthChallenge(
            String username, String newPassword, String session) {
        AdminRespondToAuthChallengeRequest request = new AdminRespondToAuthChallengeRequest();
        request.withChallengeName(NEW_PASSWORD_REQUIRED)
                .withUserPoolId(awsConfig.getCognito().getUserPoolId())
                .withClientId(awsConfig.getCognito().getAppClientId())
                .withSession(session)
                .addChallengeResponsesEntry("userAttributes.name", "aek")
                .addChallengeResponsesEntry(CognitoAttributesEnum.USERNAME.name(), username)
                .addChallengeResponsesEntry(CognitoAttributesEnum.NEW_PASSWORD.name(), newPassword)
                .addChallengeResponsesEntry(CognitoAttributesEnum.SECRET_HASH.name(), calculateSecretHash(awsConfig.getCognito().getAppClientId(), awsConfig.getCognito().getAppClientSecret(), username));

        try {
            return Optional.of(awsCognitoIdentityProvider.adminRespondToAuthChallenge(request));
        } catch (NotAuthorizedException e) {
            throw new NotAuthorizedException("User not found." + e.getErrorMessage());
        } catch (UserNotFoundException e) {
            throw new com.authorization.sample.awscognitospringauthserver.exception.UserNotFoundException("User not found.", e);
        } catch (com.amazonaws.services.cognitoidp.model.InvalidPasswordException e) {
            throw new com.authorization.sample.awscognitospringauthserver.exception.InvalidPasswordException("Invalid password.", e);
        }
    }

    public void changeUserPassword(String username, String newPassword) {
        AdminSetUserPasswordRequest request = new AdminSetUserPasswordRequest()
                .withUsername(username)
                .withPassword(newPassword)
                .withPermanent(true) // This makes the password change permanent
                .withUserPoolId(awsConfig.getCognito().getUserPoolId());

        try {
            awsCognitoIdentityProvider.adminSetUserPassword(request);
        } catch (Exception e) {
            // Handle exceptions appropriately
            throw new RuntimeException("Failed to change password: " + e.getMessage(), e);
        }
    }


    public void resetUserPassword(PasswordResetDTO passwordResetDTO) {
        String secretHash = calculateSecretHash(
                awsConfig.getCognito().getAppClientId(),
                awsConfig.getCognito().getAppClientSecret(),
                passwordResetDTO.getUsername()
        );

        ConfirmForgotPasswordRequest confirmForgotPasswordRequest = new ConfirmForgotPasswordRequest()
                .withUsername(passwordResetDTO.getUsername())
                .withConfirmationCode(passwordResetDTO.getResetCode())
                .withPassword(passwordResetDTO.getNewPassword())
                .withClientId(awsConfig.getCognito().getAppClientId())
                .withSecretHash(secretHash);

        ConfirmForgotPasswordResult result = awsCognitoIdentityProvider.confirmForgotPassword(confirmForgotPasswordRequest);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public AdminListUserAuthEventsResult getUserAuthEvents(String username, int maxResult, String nextToken) {
        try {

            AdminListUserAuthEventsRequest userAuthEventsRequest = new AdminListUserAuthEventsRequest();
            userAuthEventsRequest.setUsername(username);
            userAuthEventsRequest.setUserPoolId(awsConfig.getCognito().getUserPoolId());
            userAuthEventsRequest.setMaxResults(maxResult);
            if (Strings.isNotBlank(nextToken)) {
                userAuthEventsRequest.setNextToken(nextToken);
            }

            return awsCognitoIdentityProvider.adminListUserAuthEvents(userAuthEventsRequest);
        } catch (InternalErrorException e) {
            throw new InternalErrorException(e.getErrorMessage());
        } catch (InvalidParameterException | UserPoolAddOnNotEnabledException e) {
            throw new com.authorization.sample.awscognitospringauthserver.exception.InvalidParameterException(String.format("Amazon Cognito service encounters an invalid parameter %s", e.getErrorMessage()), e);
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public GlobalSignOutResult signOut(String accessToken) {
        try {
            return awsCognitoIdentityProvider.globalSignOut(new GlobalSignOutRequest().withAccessToken(accessToken));
        } catch (NotAuthorizedException e) {
            throw new FailedAuthenticationException(String.format("Logout failed: %s", e.getErrorMessage()), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ForgotPasswordResult forgotPassword(String username) {
        try {
            ForgotPasswordRequest request = new ForgotPasswordRequest();
            request.withClientId(awsConfig.getCognito().getAppClientId())
                    .withUsername(username)
                    .withSecretHash(calculateSecretHash(awsConfig.getCognito().getAppClientId(), awsConfig.getCognito().getAppClientSecret(), username));

            return awsCognitoIdentityProvider.forgotPassword(request);

        } catch (NotAuthorizedException e) {
            throw new FailedAuthenticationException(String.format("Forgot password failed: %s", e.getErrorMessage()), e);
        }
    }

    private Optional<AdminInitiateAuthResult> adminInitiateAuthResult(AdminInitiateAuthRequest request) {
        try {
            return Optional.of(awsCognitoIdentityProvider.adminInitiateAuth(request));
        } catch (NotAuthorizedException e) {
            throw new FailedAuthenticationException(String.format("Authenticate failed: %s", e.getErrorMessage()), e);
        } catch (UserNotFoundException e) {
            String username = request.getAuthParameters().get(CognitoAttributesEnum.USERNAME.name());
            throw new com.authorization.sample.awscognitospringauthserver.exception.UserNotFoundException(String.format("Username %s  not found.", username), e);
        }
    }

    private String calculateSecretHash(String userPoolClientId, String userPoolClientSecret, String userName) {
        final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

        SecretKeySpec signingKey = new SecretKeySpec(
                userPoolClientSecret.getBytes(StandardCharsets.UTF_8),
                HMAC_SHA256_ALGORITHM);
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            mac.init(signingKey);
            mac.update(userName.getBytes(StandardCharsets.UTF_8));
            byte[] rawHmac = mac.doFinal(userPoolClientId.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new ServiceException("Error while calculating ");
        }
    }


    /**
     * @return password generated
     */
    private String generateValidPassword() {
        PasswordGenerator gen = new PasswordGenerator();
        CharacterData lowerCaseChars = EnglishCharacterData.LowerCase;
        CharacterRule lowerCaseRule = new CharacterRule(lowerCaseChars);
        lowerCaseRule.setNumberOfCharacters(2);

        CharacterData upperCaseChars = EnglishCharacterData.UpperCase;
        CharacterRule upperCaseRule = new CharacterRule(upperCaseChars);
        upperCaseRule.setNumberOfCharacters(2);

        CharacterData digitChars = EnglishCharacterData.Digit;
        CharacterRule digitRule = new CharacterRule(digitChars);
        digitRule.setNumberOfCharacters(2);

        CharacterData specialChars = new CharacterData() {
            public String getErrorCode() {
                return "ERRONEOUS_SPECIAL_CHARS";
            }

            public String getCharacters() {
                return "!@#$%^&*()_+";
            }
        };
        CharacterRule splCharRule = new CharacterRule(specialChars);
        splCharRule.setNumberOfCharacters(2);

        return gen.generatePassword(10, splCharRule, lowerCaseRule,
                upperCaseRule, digitRule);
    }

}
