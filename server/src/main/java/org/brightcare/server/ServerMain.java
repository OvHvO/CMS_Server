package org.brightcare.server;

import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;

import org.brightcare.common.AuthenticationService;
import org.brightcare.common.ClinicService;
import org.brightcare.server.config.DatabaseConfig;
import org.brightcare.server.impl.AuthenticationServiceImpl;
import org.brightcare.server.impl.ClinicServiceImpl;
import org.brightcare.util.RmiUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


/**
 * Entry point for the BrightCare Medical Centre RMI server.
 * <p>
 * Usage: {@code java -jar brightcare-server.jar}
 * <p>
 * The server:
 * <ol>
 *   <li>Initialises the HikariCP connection pool</li>
 *   <li>Creates and exports the {@link ClinicServiceImpl} remote object</li>
 *   <li>Starts (or reuses) the RMI registry on port 1099</li>
 *   <li>Binds the service under the name {@code ClinicService}</li>
 * </ol>
 * <p>
 * Graceful shutdown is handled via a JVM shutdown hook.
 */
public final class ServerMain {

    private static final Logger log = LoggerFactory.getLogger(ServerMain.class);

    private static final String SERVICE_NAME = "ClinicService";
    private static final int RMI_PORT = 1099;
    private static final String SSL_KEYSTORE = "server.keystore";
    private static final String SSL_KEYSTORE_PASSWORD = "password"; // In a real application, do not hardcode passwords in source code. Use environment variables or secure vaults.
    private static volatile boolean sslConfigured;

    private ServerMain() {
        // entry-point class — prevent instantiation
    }

    public static void main(String[] args) {

        configureSsl();

        log.info("============================================================");
        log.info("  BrightCare Medical Centre — RMI Server");
        log.info("  Starting up...");
        log.info("============================================================");

        ClinicService service = null;
        AuthenticationService authService = null;

        try {
            // 1. Pre-warm the connection pool
            log.info("Initialising database connection pool...");
            DatabaseConfig.getDataSource();
            log.info("Connection pool ready");

            // 2. Create the remote service implementation
            log.info("Creating ClinicService remote object...");
            service = new ClinicServiceImpl();
            authService = new AuthenticationServiceImpl();
            log.info("Exporting object: {}", service.getClass().getName());
            log.info("Identity hash: {}", System.identityHashCode(service));
            log.info("Exporting object: {}", authService.getClass().getName());
            log.info("Identity hash: {}", System.identityHashCode(authService));


            // 3. Export and bind to RMI registry
            RmiUtil.exportAndBind(SERVICE_NAME, service, RMI_PORT, new SslRMIClientSocketFactory(), new SslRMIServerSocketFactory());
            RmiUtil.exportAndBind("AuthenticationService", authService, RMI_PORT, new SslRMIClientSocketFactory(), new SslRMIServerSocketFactory());

            // 4. Register JVM shutdown hook for graceful cleanup
            final ClinicService finalService = service;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutdown hook triggered — cleaning up...");
                RmiUtil.unexport(finalService, true);
                DatabaseConfig.shutdown();
                log.info("Server stopped");
            }, "shutdown-hook"));

            final AuthenticationService authFinal = authService;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutdown hook triggered — cleaning up AuthenticationService...");
                RmiUtil.unexport(authFinal, true);
            }, "shutdown-hook-auth"));

            log.info("============================================================");
            log.info("  Server is ready.");
            log.info("  RMI service bound to: rmi://localhost:{}/{}", RMI_PORT, SERVICE_NAME);
            log.info("  Press Ctrl+C to stop.");
            log.info("============================================================");

            // Prevent the main thread from exiting
            // The server runs until the JVM is terminated
            Thread.currentThread().join();

        } catch (Exception e) {
            log.error("Fatal error starting server", e);
            if (service != null) {
                RmiUtil.unexport(service, true);
            }
            DatabaseConfig.shutdown();
            System.exit(1);
        }
    }

    private static synchronized void configureSsl() {
        if (sslConfigured) {
            return;
        }

        Path keyStore = resolveStorePath(SSL_KEYSTORE);
        System.setProperty("javax.net.ssl.keyStore", keyStore.toString());
        System.setProperty("javax.net.ssl.keyStorePassword", SSL_KEYSTORE_PASSWORD);
        System.setProperty("javax.net.ssl.keyStoreType", "PKCS12");

        sslConfigured = true;
    }

    private static Path resolveStorePath(String fileName) {
        String override = firstNonBlank(System.getProperty("brightcare.server.keyStore"),
                System.getenv("BRIGHTCARE_SERVER_KEY_STORE"));
        if (override != null) {
            Path configured = Paths.get(override);
            if (Files.exists(configured)) {
                return configured.toAbsolutePath().normalize();
            }
        }

        for (Path candidate : new Path[]{
                Paths.get(fileName),
                Paths.get("CMS_Server", "server", fileName),
                moduleRoot().resolve(fileName)
        }) {
            if (Files.exists(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }

        throw new IllegalStateException("Unable to locate SSL keystore '" + fileName + "'. "
                + "Create the keystore or set -Dbrightcare.server.keyStore=<path>.");
    }

    private static Path moduleRoot() {
        try {
            URL location = ServerMain.class.getProtectionDomain().getCodeSource().getLocation();
            Path classesOrJar = Paths.get(location.toURI());
            Path parent = classesOrJar.getParent();
            if (parent != null && parent.getFileName() != null && "target".equalsIgnoreCase(parent.getFileName().toString())) {
                Path moduleRoot = parent.getParent();
                if (moduleRoot != null) {
                    return moduleRoot;
                }
            }
            if (parent != null) {
                return parent;
            }
            return classesOrJar;
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Unable to resolve server module root", e);
        }
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }
}
