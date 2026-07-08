package org.brightcare.common.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Data transfer object for report data.
 * Used for both monthly reports and doctor consultation reports.
 */
public record ReportDTO(
        String title,
        String period,
        int totalAppointments,
        int completedAppointments,
        int cancelledAppointments,
        int noShowAppointments,
        Map<String, Integer> appointmentsBySpecialization,
        List<AppointmentDTO> appointmentDetails
) implements Serializable {
    private static final long serialVersionUID = 6L;
}
