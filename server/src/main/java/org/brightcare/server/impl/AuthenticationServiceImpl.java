package org.brightcare.server.impl;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;

import org.brightcare.common.AuthenticationService;
import org.brightcare.common.dto.UserDTO;
import org.brightcare.server.config.DatabaseConfig;
import org.brightcare.server.dao.UserDAO;
import org.brightcare.util.PasswordUtil;
import org.brightcare.common.exception.*;
import org.mindrot.jbcrypt.BCrypt;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * RMI authentication service for secure two-factor authentication (2FA) using TOTP (Time-based One-Time Password).
 * 
 * 
 * <h3>Authentication Flow</h3>
 * <ol>
 *   <li>Client calls {@link #verifyCredentials(String, String)} to check password validity</li>
 *   <li>If credentials valid and 2FA not yet set up, client calls {@link #createAuthenticatorQrCode(String, String)}</li>
 *   <li>User scans QR code with authenticator app (Google Authenticator, Authy, etc.)</li>
 *   <li>Client calls {@link #login(String, String, int)} with username, password, and 6-digit TOTP code</li>
 *   <li>On successful login, session ID is added to active sessions and UserDTO returned</li>
 *   <li>Client calls {@link #logout(String)} to terminate session</li>
 * </ol>
 * 
 * <h3>Implementation Notes</h3>
 * <ul>
 *   <li><b>Username Case-Sensitive:</b> Usernames are treated as case-sensitive by database</li>
 *   <li><b>2FA Mandatory:</b> All logins MUST include TOTP code; {@code login(String, String)} throws RemoteException</li>
 *   <li><b>Session Storage:</b> Sessions currently held in-memory ConcurrentHashMap; consider database persistence for distributed deployments</li>
 *   <li><b>Password Policy:</b> Enforced by application layer or database constraints (not in this class)</li>
 * </ul>
 * 
 * @see UserDAO
 * @see SessionDAO
 * @see DatabaseConfig
 */
public class AuthenticationServiceImpl implements AuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationServiceImpl.class);
    private static final String ISSUER = "Clinic Management System";

    private final GoogleAuthenticator googleAuthenticator = new GoogleAuthenticator();
    private final Set<UUID> activeSessions = ConcurrentHashMap.newKeySet();

    public AuthenticationServiceImpl() {
        // Default constructor
    }

    /**
     * Verify user credentials without 2FA requirement.
     * <p>
     * This is a preliminary check to confirm valid username/password combination.
     * Does NOT perform 2FA validation. For full authentication with 2FA, use {@link #login(String, String, int)}.
     * <br>
     * All users must complete 2FA Authorization
     * </br>
     * 
     * </p>
     * 
     * @param username the username (case-sensitive)
     * @param password the plaintext password
     * @return true if credentials are valid, false otherwise
     * @throws RemoteException if database access fails
     */
    @Override
    public boolean verifyCredentials(String username, String password) throws RemoteException {
        // Input validation
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            logger.warn("Verification attempt with null or blank credentials");
            return false;
        }
        return findUserByUsername(username)
                .map(user -> passwordMatches(password, user.passwordHash()))
                .orElse(false);
    }

    /**
     * Check if a user has TOTP 2FA secret configured and enabled.
     * <p>
     * Returns true only if:
     * <ul>
     *   <li>Credentials are valid (verified against password hash), AND</li>
     *   <li>User has an auth_secretKey stored in the database, AND</li>
     *   <li>auth_enabled flag is TRUE</li>
     * </ul>
     * </p>
     * 
     * @param username the username
     * @param password the plaintext password
     * @return true if 2FA is set up and enabled, false otherwise
     * @throws RemoteException if database access fails
     */
    @Override
    public boolean hasAuthenticatorSecret(String username, String password) throws RemoteException {
        // Input validation
        try{
            if (username == null || username.isBlank() || password == null || password.isBlank()) {
            logger.warn("Authenticator check with null or blank credentials");
            return false;
        }
        return findVerifiedUser(username, password)
                .map(user -> user.authenticatorEnabled()
                        && user.authSecret() != null
                        && !user.authSecret().isBlank())
                .orElse(false);
        } catch (AuthenticationException e) {
            logger.warn("Authenticator check failed for user: {}", username);
            return false;
        }
     
    }

    /**
     * Generate a QR code for 2FA setup via Google Authenticator or compatible app.
     * <p>
     * Requires valid credentials. If user does not yet have a TOTP secret, one is generated and saved.
     * The returned PNG byte array contains a QR code encoding the otpauth:// URI that standard TOTP apps can scan.
     * </p>
     * 
     * <p>
     * <b>Security Note:</b> The QR code is NOT the 2FA secret itself; it's merely a convenient encoding of the secret
     * for the authenticator app. The secret is stored in the database via {@link #saveAuthenticatorSecret(String, String)}.
     * </p>
     * 
     * @param username the username
     * @param password the plaintext password
     * @return PNG byte array (image data) of the QR code, suitable for display in the UI
     * @throws RemoteException if credentials are invalid or QR code generation fails
     */
    @Override
    public byte[] createAuthenticatorQrCode(String username, String password) throws RemoteException {
        try{
        // Input validation
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            logger.warn("QR code generation attempt with null or blank credentials");
            throw new RemoteException("Invalid username or password.");
        }

        UserDAO.UserRow user = findVerifiedUser(username, password)
                .orElseThrow(() -> new AuthenticationException("Invalid username or password."));

        String secret = user.authSecret();
        if (secret == null || secret.isBlank()) {
            GoogleAuthenticatorKey key = googleAuthenticator.createCredentials();
            secret = key.getKey();
            saveAuthenticatorSecret(user.id(), secret);
        }

        return createQrPng(createOtpAuthUri(user.username(), secret)); 
    } catch (Exception e) {
        throw new RemoteException("Error generating QR code.", e);
    }
    }

    /**
     * Authenticate a user with two-factor authentication (TOTP).
     * <p>
     * Full authentication flow:
     * <ol>
     *   <li>Validates username and password match database records</li>
     *   <li>Checks that user has a TOTP secret configured</li>
     *   <li>Validates the 6-digit TOTP code against the current time window</li>
     *   <li>On success: marks authenticator as enabled, adds session, logs event, returns UserDTO</li>
     *   <li>On failure: returns null; detailed reason logged server-side only</li>
     * </ol>
     * </p>
     * 
     * @param username the username (case-sensitive)
     * @param password the plaintext password
     * @param authenticationCode the 6-digit TOTP code from authenticator app
     * @return UserDTO with id, username, role, and createdAt on success; null on any failure
     * @throws RemoteException if database access fails or other system errors occur
     */
    @Override
    public UserDTO login(String username, String password, int authenticationCode) throws RemoteException {
        // Input validation
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            logger.warn("Login attempt with null or blank credentials for user: {}", username);
            return null;
        }

        UserDAO.UserRow user = findUserByUsername(username).orElse(null);

        if (user == null || user.authSecret() == null || user.authSecret().isBlank()) {
            logger.warn("Login failed: user not found or 2FA not set up for user: {}", username);
            return null;
        }

        // Verify password
        if (!passwordMatches(password, user.passwordHash())) {
            logger.warn("Login failed: incorrect password for user: {}", username);
            return null;
        }

        if (!googleAuthenticator.authorize(user.authSecret(), authenticationCode)) {
            logger.warn("Login failed: invalid 2FA code for user: {}", username);
            return null;
        }

        enableAuthenticator(user.id());
        activeSessions.add(user.id());
        logger.info("User successfully logged in: {}", username);
        return new UserDTO(user.id(), user.username(), user.role(), user.createdAt());
    }

    /**
     * Two-factor authentication is <b>MANDATORY</b>.
     * <p>
     * This method is provided for API contract completeness but MUST NOT be used by clients.
     * Clients must always provide a valid TOTP authentication code via {@link #login(String, String, int)}.
     * </p>
     * 
     * @param username the username
     * @param password the plaintext password
     * @return never
     * @throws RemoteException always, to signal that 2FA is required
     */
    @Override
    public UserDTO login(String username, String password) throws RemoteException {
        throw new RemoteException("Two-factor authentication required. Use login(username, password, authenticationCode) method.");
    }

    /**
     * Terminate an authenticated session. (LOGOUT)
     * <p>
     * Removes the user's session from the active sessions set. After logout, the user must
     * re-authenticate to perform protected operations.
     * </p>
     * 
     * @param userId the user ID to log out (typically from UserDTO.id())
     * @throws RemoteException if an unexpected error occurs
     */
    @Override
    public void logout(UUID userId) throws RemoteException {
        if (userId != null) {
            activeSessions.remove(userId);
            logger.info("User logged out: {}", userId);
        }
    }

    /**
     * Find a user by username and verify its passowrd.
     * @param username
     * @param password
     * @return
     * @throws AuthenticationException
     * @throws RemoteException
     */
    private Optional<UserDAO.UserRow> findVerifiedUser(String username, String password) throws AuthenticationException,RemoteException {
        
        Connection conn = null;
        try {

            conn = DatabaseConfig.getConnection();
            UserDAO userDAO = new UserDAO(conn);

            UserDAO.UserRow userRow = userDAO.findByUsername(username)
                .orElseThrow(() -> new AuthenticationException("Invalid username or password."));
            
            if (!PasswordUtil.verify(password, userRow.passwordHash())) {
                logger.warn("Failed authentication attempt - incorrect password for user: {}", username);
                throw new AuthenticationException("Invalid username or password.");
            }
            
            return Optional.of(new UserDAO.UserRow(
                    userRow.id(),
                    userRow.username(),
                    userRow.passwordHash(),
                    userRow.role(),
                    userRow.authSecret(),
                    userRow.authenticatorEnabled(),
                    userRow.createdAt()
            ));
        } catch (SQLException e) {
            throw new RemoteException("Unable to query user authentication data.", e);
        } finally {
            closeQuietly(conn);
        }
    }

    private Optional<UserDAO.UserRow> findUserByUsername(String username) throws RemoteException {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }

        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            if (conn == null) {
                throw new RemoteException("Database connection failed.");
            }

            UserDAO userDAO = new UserDAO(conn);
            UserDAO.UserRow userRow = userDAO.findByUsername(username).orElse(null);

            if (userRow == null) {
                return Optional.empty();
            }

            return Optional.of(new UserDAO.UserRow(
                    userRow.id(),
                    userRow.username(),
                    userRow.passwordHash(),
                    userRow.role(),
                    userRow.authSecret(),
                    userRow.authenticatorEnabled(),
                    userRow.createdAt()
            ));
        } catch (SQLException e) {
            throw new RemoteException("Unable to query user authentication data.", e);
        } finally {
            closeQuietly(conn);
        }
    }

    private boolean passwordMatches(String password, String passwordHash) {
        if (password == null || passwordHash == null || passwordHash.isBlank()) {
            return false;
        }

        try {
            return BCrypt.checkpw(password, passwordHash);
        } catch (IllegalArgumentException invalidHash) {
            return false;
        }
    }

    private void saveAuthenticatorSecret(UUID userId, String secret) throws RemoteException {
        if (userId == null || secret == null || secret.isBlank()) {
            throw new RemoteException("Invalid userId or secret.");
        }
        try (Connection connection = DatabaseConfig.getConnection()) {
            if (connection == null) {
                throw new RemoteException("Database connection failed.");
            }

            UserDAO userDAO = new UserDAO(connection);
            userDAO.saveAuthenticatorSecret(userId, secret);


        } catch (SQLException e) {
            throw new RemoteException("Unable to save authenticator secret.", e);
        } finally {
            closeQuietly(null);
        }
    }

    private void enableAuthenticator(UUID userId) throws RemoteException {
        if (userId == null) {
            throw new RemoteException("Invalid userId.");
        }

        try (Connection connection = DatabaseConfig.getConnection()) {
            if (connection == null) {
                throw new RemoteException("Database connection failed.");
            }

            UserDAO userDAO = new UserDAO(connection);
            userDAO.updateAuthEnabled(userId, true);
        } catch (SQLException e) {
            throw new RemoteException("Unable to enable authenticator.", e);
        } finally {
            closeQuietly(null);
        }
    }

    private byte[] createQrPng(String otpAuthUri) throws RemoteException {
        try {
            BitMatrix bitMatrix = new MultiFormatWriter().encode(otpAuthUri, BarcodeFormat.QR_CODE, 240, 240);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new RemoteException("Unable to generate authenticator QR code.", e);
        }
    }

    private String createOtpAuthUri(String username, String secret) {
        String label = encode(ISSUER + ":" + username);
        return "otpauth://totp/" + label
                + "?secret=" + encode(secret)
                + "&issuer=" + encode(ISSUER);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private void closeQuietly(Connection resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (Exception e) {
                logger.warn("Failed to close resource: {}", e.getMessage());
            }
        }
    }
}
