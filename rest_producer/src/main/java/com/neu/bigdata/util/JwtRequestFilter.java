package com.neu.bigdata.util;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * @author Ruolin Li
 * @DATE 2023-10-09
 */
@Component
public class JwtRequestFilter extends OncePerRequestFilter {
    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/v1/token".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        final String tokenHeader = request.getHeader("Authorization");

        String jwtToken = null;

        if (tokenHeader != null ){
            if(tokenHeader.startsWith("Bearer ")){
                jwtToken = tokenHeader.split(" ")[1].trim();
            } else{
                jwtToken = tokenHeader;
            }
        }else{
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{ \"Error\": \"No Token\" }");
            return;
        }
        boolean isValid = false;
        if (jwtToken != null) {
            try {
                isValid = jwtTokenUtil.validateToken(jwtToken);

            } catch (Exception e) {
                //JWT Token format is invalid
                isValid = false;
            }
        }
        if (!isValid) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{ \"Error\": \"Invalid Token\" }");
            return;
        }

        filterChain.doFilter(request, response);

    }

}
