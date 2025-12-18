//package com.example.webapp.BidNow.Security;
//
//import jakarta.servlet.FilterChain;
//import jakarta.servlet.ServletException;
//import jakarta.servlet.ReadListener;
//import jakarta.servlet.ServletInputStream;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletRequestWrapper;
//import jakarta.servlet.http.HttpServletResponse;
//import org.springframework.core.annotation.Order;
//import org.springframework.stereotype.Component;
//import org.springframework.web.filter.OncePerRequestFilter;
//
//import java.io.*;
//import java.nio.charset.StandardCharsets;
//
///**
// * Ektelite proto gia na iserxonte kathara dedomena meta apo afto sta ipolipa filters
// *  φίλτρο XSS — καθαρίζει:
// * 1. Query params
// * 2. Headers
// * 3. JSON bodies
// */
//@Component
//@Order(1)
//public class XssSanitizationFilter extends OncePerRequestFilter {
//
//
//    // Oi methodoi ektelounte aftomata otan prepi na xriazonte
//
//    @Override
//    protected void doFilterInternal(HttpServletRequest request,
//                                    HttpServletResponse response,
//                                    FilterChain filterChain)
//            throws ServletException, IOException {
//
//        XssRequestWrapper sanitizedRequest = new XssRequestWrapper(request);
//        filterChain.doFilter(sanitizedRequest, response);
//    }
//
//    // Εσωτερικός Wrapper που καθαρίζει input δεδομένα
//    private static class XssRequestWrapper extends HttpServletRequestWrapper {
//
//        private byte[] sanitizedBody;
//
//        public XssRequestWrapper(HttpServletRequest request) throws IOException {
//            super(request);
//
//            // Αν το body είναι JSON, καθάρισέ το
//            if (isJson(request)) {
//                String json = readBody(request.getInputStream());
//                String cleaned = sanitize(json);
//                sanitizedBody = cleaned.getBytes(StandardCharsets.UTF_8);
//            }
//        }
//
//        private boolean isJson(HttpServletRequest request) {
//            String contentType = request.getContentType();
//            return contentType != null && contentType.contains("application/json");
//        }
//
//        private String readBody(InputStream inputStream) throws IOException {
//            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
//            StringBuilder sb = new StringBuilder();
//            String line;
//            while ((line = reader.readLine()) != null) {
//                sb.append(line);
//            }
//            return sb.toString();
//        }
//
//        @Override
//        public ServletInputStream getInputStream() {
//            if (sanitizedBody == null) {
//                try {
//                    return super.getInputStream();
//                } catch (IOException e) {
//                    throw new RuntimeException(e);
//                }
//            }
//
//            ByteArrayInputStream bais = new ByteArrayInputStream(sanitizedBody);
//            return new ServletInputStream() {
//                @Override
//                public int read() {
//                    return bais.read();
//                }
//
//                @Override
//                public boolean isFinished() {
//                    return bais.available() == 0;
//                }
//
//                @Override
//                public boolean isReady() {
//                    return true;
//                }
//
//                @Override
//                public void setReadListener(ReadListener readListener) {}
//            };
//        }
//
//        @Override
//        public String getParameter(String name) {
//            return sanitize(super.getParameter(name));
//        }
//
//        @Override
//        public String[] getParameterValues(String name) {
//            String[] values = super.getParameterValues(name);
//            if (values == null) return null;
//            for (int i = 0; i < values.length; i++) {
//                values[i] = sanitize(values[i]);
//            }
//            return values;
//        }
//
//        @Override
//        public String getHeader(String name) {
//            return sanitize(super.getHeader(name));
//        }
//
//        // === Καθαρισμός HTML/JS ===
//        private String sanitize(String input) {
//            if (input == null) return null;
//
//            // Αφαίρεση tags και επικίνδυνων patterns
//            return input
//                    .replaceAll("<", "&lt;")
//                    .replaceAll(">", "&gt;")
//                    .replaceAll("\\(", "&#40;")
//                    .replaceAll("\\)", "&#41;")
//                    .replaceAll("'", "&#39;")
//                    .replaceAll("\"", "&quot;")
//                    .replaceAll("(?i)<script.*?>.*?<\\/script>", "")
//                    .replaceAll("(?i)<.*?on\\w+=.*?>", "")
//                    .replaceAll("(?i)javascript:", "")
//                    .replaceAll("(?i)eval\\((.*?)\\)", "")
//                    .replaceAll("(?i)expression\\((.*?)\\)", "");
//        }
//    }
//}
