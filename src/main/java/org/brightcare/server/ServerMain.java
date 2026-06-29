package org.brightcare.server;

import org.brightcare.common.ClinicService;
import org.brightcare.server.config.DatabaseConfig;
import org.brightcare.server.impl.ClinicServiceImpl;
import org.brightcare.util.RmiUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private ServerMain() {
        // entry-point class — prevent instantiation
    }

    public static void main(String[] args) {
        log.info("============================================================");
        log.info("  BrightCare Medical Centre — RMI Server");
        log.info("  Starting up...");
        log.info("============================================================");

        ClinicService service = null;

        try {
            // 1. Pre-warm the connection pool
            log.info("Initialising database connection pool...");
            DatabaseConfig.getDataSource();
            log.info("Connection pool ready");

            // 2. Create the remote service implementation
            log.info("Creating ClinicService remote object...");
            service = new ClinicServiceImpl();

            // 3. Export and bind to RMI registry
            RmiUtil.exportAndBind(SERVICE_NAME, service, RMI_PORT);

            // 4. Register JVM shutdown hook for graceful cleanup
            final ClinicService finalService = service;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutdown hook triggered — cleaning up...");
                RmiUtil.unexport(finalService, true);
                DatabaseConfig.shutdown();
                log.info("Server stopped");
            }, "shutdown-hook"));

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
}
