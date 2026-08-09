package com.ims.platform;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.filter.OncePerRequestFilter;

@Order(Ordered.HIGHEST_PRECEDENCE)
@org.springframework.stereotype.Component
class CorrelationIdFilter extends OncePerRequestFilter {
    static final String HEADER="X-Correlation-ID";
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String supplied=request.getHeader(HEADER);
        String id=supplied!=null && supplied.matches("[A-Za-z0-9._-]{1,100}") ? supplied : UUID.randomUUID().toString();
        MDC.put("correlationId",id); response.setHeader(HEADER,id);
        try { chain.doFilter(request,response); } finally { MDC.remove("correlationId"); }
    }
}

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) { return problem(HttpStatus.NOT_FOUND,"Not found",e.getMessage()); }
    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException e) { return problem(HttpStatus.CONFLICT,"Conflict",e.getMessage()); }
    @ExceptionHandler({InvalidStateException.class, ConstraintViolationException.class})
    ProblemDetail invalid(RuntimeException e) { return problem(HttpStatus.UNPROCESSABLE_ENTITY,"Invalid state",e.getMessage()); }
    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail unauthorized(BadCredentialsException e) { return problem(HttpStatus.UNAUTHORIZED,"Unauthorized",e.getMessage()); }
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail forbidden(AccessDeniedException e) { return problem(HttpStatus.FORBIDDEN,"Forbidden",e.getMessage()); }
    @ExceptionHandler(RateLimitException.class)
    ProblemDetail rate(RateLimitException e) { return problem(HttpStatus.TOO_MANY_REQUESTS,"Rate limit exceeded",e.getMessage()); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException e) {
        ProblemDetail p=problem(HttpStatus.BAD_REQUEST,"Validation failed","Request contains invalid fields");
        p.setProperty("errors",e.getBindingResult().getFieldErrors().stream()
            .map(x -> Map.of("field",x.getField(),"message",Objects.requireNonNullElse(x.getDefaultMessage(),"invalid"))).toList());
        return p;
    }
    private ProblemDetail problem(HttpStatus status,String title,String detail) {
        ProblemDetail p=ProblemDetail.forStatusAndDetail(status,detail); p.setTitle(title);
        p.setType(URI.create("https://ims.example/problems/"+title.toLowerCase().replace(' ','-')));
        p.setProperty("correlationId",MDC.get("correlationId")); return p;
    }
}
