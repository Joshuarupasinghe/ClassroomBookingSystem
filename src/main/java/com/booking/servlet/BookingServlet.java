package com.booking.servlet;

import com.booking.config.DBConnection;
import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalTime;
import org.json.JSONArray;
import org.json.JSONObject;

@WebServlet("/api/bookings")
public class BookingServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processSaveOrUpdate(request, response, false);
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processSaveOrUpdate(request, response, true);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String bookingDate = request.getParameter("date");
        String sql = "SELECT id, classroom, booking_date, time_slot, module_code, lecturer_name FROM bookings";

        if (bookingDate != null && !bookingDate.trim().isEmpty()) {
            sql += " WHERE booking_date = ?";
        }

        JSONArray bookingsArray = new JSONArray();

        try (Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (bookingDate != null && !bookingDate.trim().isEmpty()) {
                stmt.setString(1, bookingDate);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    JSONObject booking = new JSONObject();
                    booking.put("id", rs.getInt("id"));
                    booking.put("classroom", rs.getString("classroom"));
                    booking.put("booking_date", rs.getString("booking_date"));
                    booking.put("time_slot", rs.getString("time_slot"));
                    booking.put("module_code", rs.getString("module_code"));
                    booking.put("lecturer_name", rs.getString("lecturer_name"));

                    bookingsArray.put(booking);
                }
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(bookingsArray.toString());

        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to retrieve bookings");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String idParam = request.getParameter("id");
        if (idParam == null || idParam.trim().isEmpty()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Booking ID is required for deletion");
            return;
        }

        String sql = "DELETE FROM bookings WHERE id = ?";

        try (Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, Integer.parseInt(idParam));
            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected > 0) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write(new JSONObject().put("message", "Booking deleted successfully").toString());
            } else {
                sendError(response, HttpServletResponse.SC_NOT_FOUND, "Booking not found");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to delete booking");
        }
    }

    /**
     * Handles both POST (Create) and PUT (Update) requests to keep code DRY.
     */
    private void processSaveOrUpdate(HttpServletRequest request, HttpServletResponse response, boolean isUpdate)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            JSONObject json = readJsonBody(request);

            Integer bookingId = isUpdate ? json.getInt("id") : null;
            String classroom = json.getString("classroom");
            String bookingDate = json.getString("booking_date");
            String timeSlot = json.getString("time_slot");
            String module = json.getString("module_code");
            String lecturer = json.getString("lecturer_name");

            // 1. Parse time
            String[] newTimes = timeSlot.split("-");
            LocalTime newStart = LocalTime.parse(newTimes[0]);
            LocalTime newEnd = LocalTime.parse(newTimes[1]);

            // 2. Validate Boundaries
            String boundaryError = validateTimeBoundaries(newStart, newEnd);
            if (boundaryError != null) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, boundaryError);
                return;
            }

            try (Connection conn = DBConnection.getConnection()) {

                // 3. Conflict Check (Passes the ID so it doesn't conflict with itself during an update)
                if (hasTimeConflict(conn, classroom, bookingDate, newStart, newEnd, bookingId)) {
                    sendError(response, HttpServletResponse.SC_CONFLICT, "Time conflict: Room is already booked during this timeframe");
                    return;
                }

                // 4. Execute DB Statement
                String sql = isUpdate
                        ? "UPDATE bookings SET classroom = ?, booking_date = ?, time_slot = ?, module_code = ?, lecturer_name = ? WHERE id = ?"
                        : "INSERT INTO bookings (classroom, booking_date, time_slot, module_code, lecturer_name) VALUES (?, ?, ?, ?, ?)";

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, classroom);
                    stmt.setString(2, bookingDate);
                    stmt.setString(3, timeSlot);
                    stmt.setString(4, module);
                    stmt.setString(5, lecturer);

                    if (isUpdate) {
                        stmt.setInt(6, bookingId);
                    }

                    stmt.executeUpdate();
                }

                response.setStatus(isUpdate ? HttpServletResponse.SC_OK : HttpServletResponse.SC_CREATED);
                String successMsg = isUpdate ? "Booking updated successfully" : "Booking scheduled successfully";
                response.getWriter().write(new JSONObject().put("message", successMsg).toString());

            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to process booking request");
        }
    }

    /**
     * Checks if the proposed time falls within the 8:00 AM - 8:00 PM boundary.
     */
    private String validateTimeBoundaries(LocalTime start, LocalTime end) {
        LocalTime minTime = LocalTime.of(8, 0);
        LocalTime maxTime = LocalTime.of(20, 0);

        if (start.isBefore(minTime) || end.isAfter(maxTime) || !start.isBefore(end)) {
            return "Invalid time range. Bookings must be scheduled between 08:00 and 20:00, and start time must precede end time.";
        }
        return null;
    }

    /**
     * Checks the database for overlapping schedules. Excludes the
     * 'excludeBookingId' from the check (used during updates).
     */
    private boolean hasTimeConflict(Connection conn, String classroom, String bookingDate, LocalTime newStart, LocalTime newEnd, Integer excludeBookingId) throws Exception {
        String checkSql = "SELECT id, time_slot FROM bookings WHERE classroom = ? AND booking_date = ?";

        try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setString(1, classroom);
            checkStmt.setString(2, bookingDate);

            try (ResultSet rs = checkStmt.executeQuery()) {
                while (rs.next()) {
                    int existingId = rs.getInt("id");

                    // Skip checking against itself during an update
                    if (excludeBookingId != null && excludeBookingId == existingId) {
                        continue;
                    }

                    String existingSlot = rs.getString("time_slot");
                    String[] existingTimes = existingSlot.split("-");
                    LocalTime existingStart = LocalTime.parse(existingTimes[0]);
                    LocalTime existingEnd = LocalTime.parse(existingTimes[1]);

                    // Overlap Formula
                    if (newStart.isBefore(existingEnd) && newEnd.isAfter(existingStart)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Standardizes JSON reading from the request body.
     */
    private JSONObject readJsonBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        try (BufferedReader reader = request.getReader()) {
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return new JSONObject(sb.toString());
    }

    /**
     * Standardizes error responses.
     */
    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.getWriter().write(new JSONObject().put("error", message).toString());
    }
}
