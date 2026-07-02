package org.brightcare.common;

import org.brightcare.common.dto.*;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;

/**
 * RMI remote interface for authentication and session management.
 * All users (Receptionist, Patient, Doctor, Admin) must authenticate
 * before accessing role-specific services.
 */
public interface AuthenticationService extends Remote {

    /**
     * Validate the first login step before asking for an authenticator code.
     *
     * @param username the user's unique username
     * @param password the user's plain-text password (validated server-side against bcrypt hash)
     * @return true when the username/password pair is valid
     */
    boolean verifyCredentials(String username, String password) throws RemoteException;

    /**
     * Return whether a user already has a Google Authenticator secret configured.
     */
    boolean hasAuthenticatorSecret(String username, String password) throws RemoteException;

    /**
     * Create and store a Google Authenticator secret, then return a PNG QR code.
     * The QR code is only generated after the user's password has been verified.
     */
    byte[] createAuthenticatorQrCode(String username, String password) throws RemoteException;

    /**
     * Authenticate a user with username, password, and Google Authenticator code.
     *
     * @return UserDTO containing user id, username, and role; null if authentication fails
     */
    UserDTO login(String username, String password, int authenticationCode) throws RemoteException;

    /**
     * Backwards-compatible password-only login is intentionally not sufficient
     * when Google Authenticator is enabled.
     */
    UserDTO login(String username, String password) throws RemoteException;

    /**
     * Terminate an active session.
     *
     * @param userId the ID of the user logging out
     */
    void logout(UUID userId) throws RemoteException;

}
