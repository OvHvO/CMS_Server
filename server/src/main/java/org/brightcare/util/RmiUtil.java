package org.brightcare.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;

import java.rmi.server.*;

/**
 * Utility class for RMI registry operations.
 */
public final class RmiUtil {

    private static final Logger log = LoggerFactory.getLogger(RmiUtil.class);
    private static Registry registry;

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
    public static synchronized Registry createRegistry(int port) throws RemoteException {
        if (registry != null) {
            return registry;
        }

        try {
            registry = LocateRegistry.createRegistry(
                port,
                new SslRMIClientSocketFactory(),
                new SslRMIServerSocketFactory()
            );
            log.info("Created new SSL RMI registry on port {}", port);
        } catch (ExportException e) {
            throw new RemoteException("Failed to create RMI registry", e);
        }

        return registry;
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
    public static <T extends Remote> T exportAndBind(String name, T remoteObject, int port, RMIClientSocketFactory clientSocketFactory, RMIServerSocketFactory serverSocketFactory)
            throws java.rmi.RemoteException {
        T stub;
        try {
            stub = (T) UnicastRemoteObject.exportObject(remoteObject, 0, clientSocketFactory, serverSocketFactory);
        } catch (java.rmi.server.ExportException ee) {
            // If the object was already exported in this JVM, reuse the existing stub instead
            log.warn("Export failed (object may already be exported): {}", ee.getMessage());
            try {
                stub = (T) UnicastRemoteObject.toStub(remoteObject);
                log.info("Reusing existing stub for '{}'", name);
            } catch (java.rmi.NoSuchObjectException nsoe) {
                // If we cannot obtain a stub, rethrow a more descriptive exception
                throw new java.rmi.RemoteException("Failed to export remote object and could not obtain existing stub", nsoe);
            }
        }

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
