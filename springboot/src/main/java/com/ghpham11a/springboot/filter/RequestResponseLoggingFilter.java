package com.ghpham11a.springboot.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Component
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestResponseLoggingFilter.class);
    private static final int MAX_PAYLOAD_LOG_SIZE = 10_000;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            logRequest(wrappedRequest);
            logResponse(wrappedRequest, wrappedResponse);
            wrappedResponse.copyBodyToResponse();
        }
    }

    private void logRequest(ContentCachingRequestWrapper request) {
        String body = PiiSanitizer.sanitize(getPayload(request.getContentAsByteArray()));
        log.info("HTTP_REQUEST method={} uri={} body={}",
                request.getMethod(),
                request.getRequestURI(),
                body);
    }

    private void logResponse(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response) {
        String body = PiiSanitizer.sanitize(getPayload(response.getContentAsByteArray()));
        log.info("HTTP_RESPONSE method={} uri={} status={} body={}",
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                body);
    }

    private String getPayload(byte[] content) {
        if (content == null || content.length == 0) {
            return "[empty]";
        }
        int length = Math.min(content.length, MAX_PAYLOAD_LOG_SIZE);
        String payload = new String(content, 0, length);
        if (content.length > MAX_PAYLOAD_LOG_SIZE) {
            payload += "...[truncated]";
        }
        return payload;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator");
    }
}
