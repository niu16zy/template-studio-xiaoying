package com.xiaoying.studio.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// Cross-origin HTML forms cannot add this header; cross-origin scripts require a CORS
// preflight, which this same-origin application does not allow.
@Component
public class MutationGuard extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/") && !request.getMethod().equals("GET")
                && !request.getMethod().equals("HEAD") && !request.getMethod().equals("OPTIONS")
                && !"1".equals(request.getHeader("X-Studio-Request"))) {
            response.setStatus(403);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write("{\"code\":\"REQUEST_HEADER_REQUIRED\",\"message\":\"请通过本站页面提交请求\",\"fieldErrors\":{},\"requestId\":\"" + java.util.UUID.randomUUID() + "\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
