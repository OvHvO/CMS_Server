package org.brightcare.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

/**
 * Utility class for RMI registry operations.
 */
public final class RmiUtil {

    private static final Logger log = LoggerFactory.getLogger(RmiUtil.class);

    /** Default RMI registry port. */
    public static final int DEFAULT_RMI_PORT = 1099;

    private RmiUtil() {
        // utility class — prevent instantiation
    }

    /**
     * Create an RMI registry on the specified port if one does not already exist.
     *
     * @param port the registry port
     * @return the Registry instance
     * @throws java.rmi.RemoteException if the registry cannot be created
     */
    public static Registry createRegistry(int port) throws java.rmi.RemoteException {
        try {
            // Check if a registry already exists on this port
            Registry existing = LocateRegistry.getRegistry(port);
            existing.list(); // will throw if no registry is actually running
            log.info("RMI registry already running on port {}", port);
            return existing;
        } catch (java.rmi.RemoteException e) {
            log.info("Creating new RMI registry on port {}", port);
            return LocateRegistry.createRegistry(port);
        }
    }

    /**
     * Export a remote object and bind it to the RMI registry.
     *
     * @param <T>          the remote interface type
     * @param name         the binding name (e.g. "ClinicService")
     * @param remoteObject the remote implementation instance
     * @param port         the registry port
     * @return the exported stub
     * @throws java.rmi.RemoteException if export or binding fails
     */
    @SuppressWarnings("unchecked")
    public static <T extends Remote> T exportAndBind(String name, T remoteObject, int port)
            throws java.rmi.RemoteException {
        T stub = (T) UnicastRemoteObject.exportObject(remoteObject, 0);
        Registry registry = createRegistry(port);
        registry.rebind(name, stub);
        log.info("Bound '{}' to RMI registry on port {}", name, port);
        return stub;
    }

    /**
     * Unexport a remote object, making it unavailable for incoming RMI calls.
     *
     * @param remoteObject the remote object to unexport
     * @param force        if true, unexport even if calls are in progress
     */
    public static void unexport(Remote remoteObject, boolean force) {
        try {
            if (UnicastRemoteObject.unexportObject(remoteObject, force)) {
                log.info("Successfully unexported remote object");
            } else {
                log.warn("Failed to unexport remote object");
            }
        } catch (java.rmi.NoSuchObjectException e) {
            log.debug("Remote object was already unexported");
        }
    }
}
