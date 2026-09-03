/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.booking.filter;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.booking.util.JwtUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.json.JSONObject;

/**
 *
 * @author joshu
 */
@WebFilter("/api/bookings/*")
public class JwtAuthFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String method = request.getMethod();

        // 1. Allow Read-Only access (GET) to all users
        if ("GET".equalsIgnoreCase(method)) {
            chain.doFilter(req, res);
            return;
        }

        // 2. Validate Authorization header for data-altering methods (POST, PUT, DELETE)
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(7);

        try {
            DecodedJWT jwt = JwtUtil.verifyToken(token);
            String role = jwt.getClaim("role").asString();

            // 3. Enforce Strict RBAC
            if (!"AUTHORIZED".equalsIgnoreCase(role)) {
                sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, "Access denied: Authorized privileges required");
                return;
            }

            // Attach user claims to request attributes for downstream servlets
            request.setAttribute("authenticatedUser", jwt.getSubject());
            request.setAttribute("userRole", role);

            chain.doFilter(req, res);

        } catch (JWTVerificationException e) {
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Session expired or invalid token");
        }
    }

    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        JSONObject json = new JSONObject();
        json.put("status", statusCode);
        json.put("error", message);
        response.getWriter().write(json.toString());
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void destroy() {
    }
}
