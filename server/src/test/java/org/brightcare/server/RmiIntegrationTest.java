package org.brightcare.server;

import org.brightcare.common.AuthenticationService;
import org.brightcare.common.ClinicService;
import org.brightcare.common.dto.*;
import org.brightcare.common.exception.AuthenticationException;
import org.brightcare.common.exception.InvalidDataException;
import org.brightcare.common.exception.NotFoundException;
import org.brightcare.server.config.DatabaseConfig;
import org.brightcare.server.impl.AuthenticationServiceImpl;
import org.brightcare.server.impl.ClinicServiceImpl;
import org.brightcare.util.RmiUtil;
import org.junit.jupiter.api.*;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;

import static org.junit.jupiter.api.Assertions.*;
/**
 * RMI 集成测试 — 验证整个 RMI 通信流程能否跑通。
 * <p>
 * 测试模式（你想要的）：
 * <pre>{@code
 *   ClinicService stub = (ClinicService) Naming.lookup("rmi://localhost:11099/ClinicService");
 *   List<DoctorDTO> doctors = stub.getAllDoctors();
 *   assertNotNull(doctors);
 * }</pre>
 * <p>
 * <b>前提条件：</b>
 * <ol>
 *   <li>PostgreSQL 数据库已运行</li>
 *   <li>src/main/resources/application.dev.properties 配置了正确的数据库连接</li>
 *   <li>seed_data.sql 已导入（提供测试用户、医生、患者数据）</li>
 * </ol>
 * <p>
 * <b>运行方式：</b>
 * <pre>mvn test -pl server</pre>
 * <p>
 * 这个测试只验证 RMI 通信链路——stub 查找、方法调用、返回值反序列化。
 * 不验证数据库业务逻辑的正确性。
 */
@DisplayName("RMI 集成测试 — 验证 RMI 通信流程")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RmiIntegrationTest {

    // 使用独立端口避免和已运行的 server 冲突
    private static final int RMI_PORT = 11099;
    private static final String RMI_URL = "rmi://localhost:" + RMI_PORT + "/";

    // ---------- Seed data UUIDs（来自 seed_data.sql）----------
    // Users
    private static final UUID USER_ADMIN       = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_RECEPTIONIST = UUID.fromString("a0000000-0000-0000-0000-000000000002");
    private static final UUID USER_DOCTOR_SARAH  = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID USER_PATIENT_JOHN  = UUID.fromString("c0000000-0000-0000-0000-000000000001");

    // Doctors
    private static final UUID DOCTOR_SARAH     = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final UUID DOCTOR_MICHAEL   = UUID.fromString("d0000000-0000-0000-0000-000000000002");

    // Patients
    private static final UUID PATIENT_JOHN     = UUID.fromString("e0000000-0000-0000-0000-000000000001");
    private static final UUID PATIENT_JANE     = UUID.fromString("e0000000-0000-0000-0000-000000000002");

    // Appointments (scheduled, future)
    private static final UUID APPT_FUTURE_1    = UUID.fromString("ae000000-0000-0000-0000-000000000007");

    // Shared test password (seed data 中所有用户的密码都是 password123)
    private static final String TEST_PASSWORD = "password123";

    // ---------- RMI stubs（@BeforeAll 中初始化，@Test 中直接用）----------
    private static ClinicService clinicStub;
    private static AuthenticationService authStub;

    // =========================================================================
    // 生命周期：启动 / 停止 RMI Server
    // =========================================================================

    @BeforeAll
    static void startRmiServer() throws Exception {
        System.out.println("========================================");
        System.out.println("  [TEST] 启动 RMI Server (port=" + RMI_PORT + ")");
        System.out.println("========================================");

        // 1. 预热数据库连接池
        System.out.println("[TEST] 初始化 HikariCP 连接池...");
        DatabaseConfig.getDataSource();

        // 2. 创建远程对象
        System.out.println("[TEST] 创建 ClinicServiceImpl & AuthenticationServiceImpl...");
        ClinicServiceImpl clinicImpl = new ClinicServiceImpl();
        AuthenticationServiceImpl authImpl = new AuthenticationServiceImpl();

        // 3. 导出并绑定到 RMI registry
        System.out.println("[TEST] 绑定到 RMI registry...");

        // 确保 registry 已创建
        try {
            LocateRegistry.getRegistry(RMI_PORT).list();
        } catch (RemoteException e) {
            LocateRegistry.createRegistry(RMI_PORT);
        }

        RmiUtil.exportAndBind("ClinicService", clinicImpl, RMI_PORT, new SslRMIClientSocketFactory(), new  SslRMIServerSocketFactory());
        RmiUtil.exportAndBind("AuthenticationService", authImpl, RMI_PORT, new SslRMIClientSocketFactory(), new SslRMIServerSocketFactory());

        // 4. 客户端查找 stub —— 这就是你要测试的核心流程
        System.out.println("[TEST] 客户端查找 RMI stubs...");
        clinicStub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");
        authStub = (AuthenticationService) Naming.lookup(RMI_URL + "AuthenticationService");

        assertNotNull(clinicStub, "❌ 无法查找到 ClinicService RMI stub");
        assertNotNull(authStub,  "❌ 无法查找到 AuthenticationService RMI stub");

        System.out.println("[TEST] ✅ RMI stubs 查找成功，服务器就绪");
    }

    @AfterAll
    static void stopRmiServer() {
        System.out.println("[TEST] 清理：停止 RMI server...");
        DatabaseConfig.shutdown();
        System.out.println("[TEST] ✅ 清理完成");
    }

    // =========================================================================
    // 0. 最基本的 RMI 连通性测试
    // =========================================================================

    @Test
    @Order(0)
    @DisplayName("RMI 连通性：Naming.lookup 能否拿到 stub → 调用方法 → 拿到返回值")
    void rmiLookupAndCall_ShouldWork() throws Exception {
        // 1. 查找 stub（网络通信在背后发生）
        ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");
        assertNotNull(stub, "Naming.lookup 返回 null，RMI 通信失败");

        // 2. 像调用本地方法一样调用（实际上网络通信在背后发生了）
        List<DoctorDTO> response = stub.getAllDoctors();

        // 3. 验证拿到返回值
        assertNotNull(response, "getAllDoctors() 返回 null，方法调用失败");
        System.out.println("[TEST] ✅ RMI 调用成功，返回了 " + response.size() + " 位医生");
    }

    // =========================================================================
    // 1. AuthenticationService RMI 测试
    // =========================================================================

    @Nested
    @DisplayName("AuthenticationService RMI 方法")
    class AuthenticationServiceTests {

        @Test
        @DisplayName("verifyCredentials — 正确密码 → 返回 true")
        void verifyCredentials_ValidCredentials_ReturnsTrue() throws Exception {
            // Naming.lookup 获得 stub
            AuthenticationService stub = (AuthenticationService) Naming.lookup(RMI_URL + "AuthenticationService");

            // 调用远程方法
            boolean result = stub.verifyCredentials("admin1", TEST_PASSWORD);

            // 验证返回值
            assertTrue(result, "正确密码应该返回 true");
            System.out.println("[TEST] ✅ verifyCredentials('admin1', '***') = " + result);
        }

        @Test
        @DisplayName("verifyCredentials — 错误密码 → 返回 false")
        void verifyCredentials_InvalidPassword_ReturnsFalse() throws Exception {
            AuthenticationService stub = (AuthenticationService) Naming.lookup(RMI_URL + "AuthenticationService");

            boolean result = stub.verifyCredentials("admin1", "wrong_password");

            assertFalse(result, "错误密码应该返回 false");
            System.out.println("[TEST] ✅ verifyCredentials 错误密码返回 " + result);
        }

        @Test
        @DisplayName("verifyCredentials — 空用户名 → 不抛异常，返回 false")
        void verifyCredentials_NullUsername_ReturnsFalse() throws Exception {
            AuthenticationService stub = (AuthenticationService) Naming.lookup(RMI_URL + "AuthenticationService");

            // 即使传 null，RMI 调用不应该崩溃
            assertDoesNotThrow(() -> {
                boolean result = stub.verifyCredentials("", "");
                assertFalse(result);
            }, "空凭证不应该导致 RemoteException");
            System.out.println("[TEST] ✅ verifyCredentials 空凭证安全返回");
        }

        @Test
        @DisplayName("verifyCredentials — 不存在的用户 → 返回 false")
        void verifyCredentials_NonExistentUser_ReturnsFalse() throws Exception {
            AuthenticationService stub = (AuthenticationService) Naming.lookup(RMI_URL + "AuthenticationService");

            boolean result = stub.verifyCredentials("nonexistent_user_999", "whatever");

            assertFalse(result);
            System.out.println("[TEST] ✅ verifyCredentials 不存在用户返回 false");
        }

        @Test
        @DisplayName("hasAuthenticatorSecret — 未设置 2FA 的用户 → 返回 false")
        void hasAuthenticatorSecret_NoSecret_ReturnsFalse() throws Exception {
            AuthenticationService stub = (AuthenticationService) Naming.lookup(RMI_URL + "AuthenticationService");

            boolean result = stub.hasAuthenticatorSecret("admin1", TEST_PASSWORD);

            // Seed data 中 admin1 的 auth_secretKey 是 NULL
            assertFalse(result, "未设置 2FA 应该返回 false");
            System.out.println("[TEST] ✅ hasAuthenticatorSecret = " + result);
        }

        @Test
        @DisplayName("createAuthenticatorQrCode — 有效凭证 → 返回 PNG 字节数组")
        void createAuthenticatorQrCode_ValidCredentials_ReturnsPngBytes() throws Exception {
            AuthenticationService stub = (AuthenticationService) Naming.lookup(RMI_URL + "AuthenticationService");

            byte[] qrPng = stub.createAuthenticatorQrCode("admin1", TEST_PASSWORD);

            assertNotNull(qrPng, "二维码 PNG 数据不应为 null");
            assertTrue(qrPng.length > 0, "二维码数据不应为空");
            System.out.println("[TEST] ✅ createAuthenticatorQrCode 返回了 " + qrPng.length + " 字节的 PNG");
        }

        @Test
        @DisplayName("login(username, password, code) — 2FA 未设置时 → 不抛 RemoteException")
        void login_With2FA_NoSecret_ReturnsNullGracefully() throws Exception {
            AuthenticationService stub = (AuthenticationService) Naming.lookup(RMI_URL + "AuthenticationService");

            // 因为 seed user 没有 2FA secret，login 应该返回 null 而不是抛异常
            UserDTO result = stub.login("admin1", TEST_PASSWORD, 123456);

            // 没有 2FA secret → 返回 null（不会让 RMI 通信崩溃）
            assertNull(result, "无 2FA secret 时 login 应返回 null");
            System.out.println("[TEST] ✅ login 无 2FA 时安全返回 null");
        }

        @Test
        @DisplayName("login(username, password) — 无 2FA 强制登录 → 抛出 RemoteException")
        void login_Without2FA_ThrowsRemoteException() throws Exception {
            AuthenticationService stub = (AuthenticationService) Naming.lookup(RMI_URL + "AuthenticationService");

            // 该方法永远会抛出 RemoteException（因为 2FA 是强制的）
            assertThrows(RemoteException.class, () -> {
                stub.login("admin1", TEST_PASSWORD);
            }, "无 TOTP 码的 login 应该抛出 RemoteException");
            System.out.println("[TEST] ✅ login 无 2FA 正确抛出了 RemoteException");
        }

        @Test
        @DisplayName("logout — 应该不抛异常")
        void logout_ShouldNotThrow() throws Exception {
            AuthenticationService stub = (AuthenticationService) Naming.lookup(RMI_URL + "AuthenticationService");

            assertDoesNotThrow(() -> stub.logout(USER_ADMIN));
            System.out.println("[TEST] ✅ logout 执行成功");
        }

        @Test
        @DisplayName("logout — null userId → 不抛异常")
        void logout_NullUserId_ShouldNotThrow() throws Exception {
            AuthenticationService stub = (AuthenticationService) Naming.lookup(RMI_URL + "AuthenticationService");

            assertDoesNotThrow(() -> stub.logout(null));
            System.out.println("[TEST] ✅ logout(null) 安全返回");
        }
    }

    // =========================================================================
    // 2. ClinicService RMI 测试
    // =========================================================================

    @Nested
    @DisplayName("ClinicService RMI 方法")
    class ClinicServiceTests {

        // ---- 认证 ----

        @Test
        @DisplayName("login — 正确凭证 → 返回 UserDTO")
        void login_ValidCredentials_ReturnsUserDTO() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            UserDTO user = stub.login("admin1", TEST_PASSWORD);

            assertNotNull(user, "UserDTO 不应为 null");
            assertNotNull(user.id(), "用户 ID 不应为 null");
            assertEquals("admin1", user.username(), "用户名应该匹配");
            System.out.println("[TEST] ✅ login 返回: " + user.username() + " / " + user.role());
        }

        @Test
        @DisplayName("login — 错误密码 → 抛出 AuthenticationException")
        void login_InvalidCredentials_ThrowsAuthenticationException() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            assertThrows(AuthenticationException.class, () -> {
                stub.login("admin1", "wrong_password");
            }, "错误凭证应该抛出 AuthenticationException");
            System.out.println("[TEST] ✅ 错误凭证正确抛出了 AuthenticationException");
        }

        @Test
        @DisplayName("login — 空凭证 → 抛出 AuthenticationException")
        void login_BlankCredentials_ThrowsAuthenticationException() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            assertThrows(AuthenticationException.class, () -> {
                stub.login("", "");
            }, "空凭证应该抛出 AuthenticationException");
            System.out.println("[TEST] ✅ 空凭证正确抛出了 AuthenticationException");
        }

        @Test
        @DisplayName("login — 不存在用户 → 抛出 AuthenticationException")
        void login_NonExistentUser_ThrowsAuthenticationException() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            assertThrows(AuthenticationException.class, () -> {
                stub.login("ghost_user", "whatever");
            }, "不存在的用户应该抛出 AuthenticationException");
            System.out.println("[TEST] ✅ 不存在用户正确抛出了 AuthenticationException");
        }

        // ---- 查询 ----

        @Test
        @DisplayName("getAllDoctors — 返回所有活跃医生")
        void getAllDoctors_ReturnsNonEmptyList() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            List<DoctorDTO> doctors = stub.getAllDoctors();

            assertNotNull(doctors, "医生列表不应为 null");
            assertFalse(doctors.isEmpty(), "应该至少有 1 位医生（seed data 有 3 位）");
            // 验证 RMI 返回的 DTO 数据完整
            DoctorDTO first = doctors.get(0);
            assertNotNull(first.id(), "医生 ID 不应为 null");
            assertNotNull(first.fullName(), "医生姓名不应为 null");
            assertNotNull(first.specialization(), "医生专科不应为 null");
            System.out.println("[TEST] ✅ getAllDoctors 返回了 " + doctors.size() + " 位医生: " +
                    first.fullName() + " (" + first.specialization() + ")");
        }

        @Test
        @DisplayName("getDoctorByUserId — 有效 user ID → 返回 DoctorDTO")
        void getDoctorByUserId_ValidId_ReturnsDoctorDTO() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            DoctorDTO doctor = stub.getDoctorByUserId(USER_DOCTOR_SARAH);

            assertNotNull(doctor, "DoctorDTO 不应为 null");
            assertTrue(doctor.fullName().contains("Sarah"), "姓名应该包含 Sarah");
            System.out.println("[TEST] ✅ getDoctorByUserId 返回: " + doctor.fullName());
        }

        @Test
        @DisplayName("getDoctorByUserId — 不存在的 ID → 抛出 NotFoundException")
        void getDoctorByUserId_InvalidId_ThrowsNotFoundException() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            assertThrows(NotFoundException.class, () -> {
                stub.getDoctorByUserId(UUID.randomUUID());
            }, "不存在的 user ID 应该抛出 NotFoundException");
            System.out.println("[TEST] ✅ 不存在医生正确抛出了 NotFoundException");
        }

        @Test
        @DisplayName("getPatientByUserId — 有效 user ID → 返回 PatientDTO")
        void getPatientByUserId_ValidId_ReturnsPatientDTO() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            PatientDTO patient = stub.getPatientByUserId(USER_PATIENT_JOHN);

            assertNotNull(patient, "PatientDTO 不应为 null");
            assertTrue(patient.firstName().contains("John"), "名字应该包含 John");
            System.out.println("[TEST] ✅ getPatientByUserId 返回: " + patient.firstName() + " " + patient.lastName());
        }

        // ---- 预约 ----

        @Test
        @DisplayName("getDoctorAvailability — 返回 30 分钟槽位列表")
        void getDoctorAvailability_ReturnsSlotList() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            // 查询远未来的日期（确保没有被预约）
            List<AvailabilitySlotDTO> slots = stub.getDoctorAvailability(
                    DOCTOR_SARAH, LocalDate.of(2027, 1, 15));

            assertNotNull(slots, "槽位列表不应为 null");
            assertFalse(slots.isEmpty(), "营业时间内应该有槽位（8:00-17:00 = 18 个槽位）");
            // 因为是远未来，所有槽位都应该是可用的
            long availableCount = slots.stream().filter(AvailabilitySlotDTO::available).count();
            System.out.println("[TEST] ✅ getDoctorAvailability 返回 " + slots.size() +
                    " 个槽位, " + availableCount + " 个可用");
        }

        @Test
        @DisplayName("getDoctorAvailability — 不存在的医生 → 抛出 NotFoundException")
        void getDoctorAvailability_InvalidDoctor_ThrowsNotFoundException() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            assertThrows(NotFoundException.class, () -> {
                stub.getDoctorAvailability(UUID.randomUUID(), LocalDate.now());
            }, "不存在的医生应该抛出 NotFoundException");
            System.out.println("[TEST] ✅ 不存在医生正确抛出了 NotFoundException");
        }

        @Test
        @DisplayName("bookAppointment — 有效数据 → 返回 AppointmentDTO（RMI 往返成功）")
        void bookAppointment_ValidData_ReturnsAppointmentDTO() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            // 预约一个远未来的时间，避免冲突
            ZonedDateTime futureTime = ZonedDateTime.of(
                    2027, 6, 15, 10, 0, 0, 0, ZoneOffset.UTC);

            AppointmentDTO appt = stub.bookAppointment(
                    PATIENT_JOHN, DOCTOR_MICHAEL, futureTime, "RMI 集成测试 — 常规体检");

            assertNotNull(appt, "AppointmentDTO 不应为 null");
            assertNotNull(appt.id(), "预约 ID 不应为 null");
            assertEquals(PATIENT_JOHN, appt.patientId());
            assertEquals(DOCTOR_MICHAEL, appt.doctorId());
            System.out.println("[TEST] ✅ bookAppointment 返回: appointmentId=" + appt.id() +
                    ", status=" + appt.status());

            // 清理：取消刚创建的预约
            stub.cancelAppointment(appt.id());
        }

        @Test
        @DisplayName("bookAppointment — 过去的时间 → 抛出 RemoteException")
        void bookAppointment_PastTime_ThrowsException() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            ZonedDateTime pastTime = ZonedDateTime.of(2020, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);

            // 过去的时间 → InvalidDataException 会被包装成 RemoteException
            assertThrows(Exception.class, () -> {
                stub.bookAppointment(PATIENT_JOHN, DOCTOR_SARAH, pastTime, "测试");
            }, "过去的时间应该抛异常");
            System.out.println("[TEST] ✅ 过去的时间预约正确抛出了异常");
        }

        @Test
        @DisplayName("bookAppointment — 不存在患者 → 抛出 NotFoundException")
        void bookAppointment_InvalidPatient_ThrowsNotFoundException() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            ZonedDateTime futureTime = ZonedDateTime.of(2027, 8, 1, 10, 0, 0, 0, ZoneOffset.UTC);

            assertThrows(NotFoundException.class, () -> {
                stub.bookAppointment(UUID.randomUUID(), DOCTOR_SARAH, futureTime, "测试");
            }, "不存在的患者应该抛出 NotFoundException");
            System.out.println("[TEST] ✅ 不存在患者正确抛出了 NotFoundException");
        }

        @Test
        @DisplayName("cancelAppointment — SCHEDULED 预约 → 不抛异常")
        void cancelAppointment_Scheduled_ShouldNotThrow() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            // 先创建一个预约
            ZonedDateTime futureTime = ZonedDateTime.of(
                    2027, 9, 10, 14, 0, 0, 0, ZoneOffset.UTC);
            AppointmentDTO appt = stub.bookAppointment(
                    PATIENT_JANE, DOCTOR_MICHAEL, futureTime, "测试取消功能");

            // 取消它
            assertDoesNotThrow(() -> stub.cancelAppointment(appt.id()));
            System.out.println("[TEST] ✅ cancelAppointment 执行成功: " + appt.id());
        }

        @Test
        @DisplayName("cancelAppointment — 不存在的预约 ID → 抛出 NotFoundException")
        void cancelAppointment_InvalidId_ThrowsNotFoundException() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            assertThrows(NotFoundException.class, () -> {
                stub.cancelAppointment(UUID.randomUUID());
            }, "不存在的预约应该抛出 NotFoundException");
            System.out.println("[TEST] ✅ 不存在预约正确抛出了 NotFoundException");
        }

        @Test
        @DisplayName("getPatientAppointments — 有效患者 → 返回预约列表")
        void getPatientAppointments_ValidPatient_ReturnsList() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            List<AppointmentDTO> appointments = stub.getPatientAppointments(PATIENT_JOHN);

            assertNotNull(appointments, "预约列表不应为 null");
            System.out.println("[TEST] ✅ getPatientAppointments 返回了 " +
                    appointments.size() + " 条预约记录");
        }

        @Test
        @DisplayName("getDoctorAppointments — 有效医生 → 返回预约列表")
        void getDoctorAppointments_ValidDoctor_ReturnsList() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            List<AppointmentDTO> appointments = stub.getDoctorAppointments(DOCTOR_SARAH);

            assertNotNull(appointments, "预约列表不应为 null");
            System.out.println("[TEST] ✅ getDoctorAppointments 返回了 " +
                    appointments.size() + " 条预约记录");
        }

        // ---- 医疗记录 ----

        @Test
        @DisplayName("getPatientMedicalHistory — 有效患者 → 返回病史列表")
        void getPatientMedicalHistory_ValidPatient_ReturnsList() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            List<MedicalRecordDTO> records = stub.getPatientMedicalHistory(PATIENT_JOHN);

            assertNotNull(records, "病史列表不应为 null");
            System.out.println("[TEST] ✅ getPatientMedicalHistory 返回了 " +
                    records.size() + " 条医疗记录");
        }

        // ---- 报告 ----

        @Test
        @DisplayName("generateMonthlyReport — 有效月份 → 返回 ReportDTO")
        void generateMonthlyReport_ValidMonth_ReturnsReportDTO() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            ReportDTO report = stub.generateMonthlyReport(2026, 6);

            assertNotNull(report, "ReportDTO 不应为 null");
            assertNotNull(report.title(), "报告标题不应为 null");
            assertTrue(report.totalAppointments() >= 0, "总数应该 >= 0");
            System.out.println("[TEST] ✅ generateMonthlyReport: " + report.title() +
                    " — total=" + report.totalAppointments() +
                    ", completed=" + report.completedAppointments() +
                    ", cancelled=" + report.cancelledAppointments() +
                    ", noShow=" + report.noShowAppointments());
        }

        @Test
        @DisplayName("generateMonthlyReport — 无效月份 → 抛出 RemoteException")
        void generateMonthlyReport_InvalidMonth_ThrowsException() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            assertThrows(Exception.class, () -> {
                stub.generateMonthlyReport(2026, 13); // 月份 13 无效
            }, "无效月份应该抛异常");
            System.out.println("[TEST] ✅ 无效月份正确抛出了异常");
        }

        @Test
        @DisplayName("generateDoctorConsultationReport — 有效医生 → 返回 ReportDTO")
        void generateDoctorConsultationReport_ValidDoctor_ReturnsReportDTO() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            ReportDTO report = stub.generateDoctorConsultationReport(DOCTOR_SARAH);

            assertNotNull(report, "ReportDTO 不应为 null");
            assertNotNull(report.title(), "报告标题不应为 null");
            System.out.println("[TEST] ✅ generateDoctorConsultationReport: " + report.title() +
                    " — total=" + report.totalAppointments());
        }

        // ---- 患者管理 ----

        @Test
        @DisplayName("updatePatientInfo — 有效数据 → 返回更新后的 PatientDTO")
        void updatePatientInfo_ValidData_ReturnsUpdatedPatientDTO() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            // 先获取当前患者信息
            PatientDTO current = stub.getPatientByUserId(USER_PATIENT_JOHN);

            // 更新联系电话
            PatientDTO updated = stub.updatePatientInfo(new PatientDTO(
                    current.id(),
                    current.userId(),
                    current.firstName(),
                    current.lastName(),
                    current.icPassportNumber(),
                    "+60-00-0000000",  // 新电话号码
                    current.medicalRecordId(),
                    current.createdAt(),
                    current.updatedAt()
            ));

            assertNotNull(updated, "更新后的 PatientDTO 不应为 null");
            assertEquals("+60-00-0000000", updated.contactNumber(), "电话号码应该已更新");
            System.out.println("[TEST] ✅ updatePatientInfo 成功: " +
                    updated.firstName() + " " + updated.lastName() +
                    " phone=" + updated.contactNumber());

            // 还原原始电话号码
            stub.updatePatientInfo(new PatientDTO(
                    current.id(), current.userId(),
                    current.firstName(), current.lastName(),
                    current.icPassportNumber(),
                    current.contactNumber(),
                    current.medicalRecordId(),
                    current.createdAt(),
                    current.updatedAt()
            ));
        }
    }
}
