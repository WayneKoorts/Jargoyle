package com.jargoyle.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards non-API, non-file requests to index.html so that React Router
 * can handle client-side routing.
 *
 * Without this, refreshing the browser on a route like /admin would return
 * a 404 because Spring has no server-side mapping for that path. This
 * controller catches those requests and serves the SPA entry point instead.
 *
 * The negative lookahead ensures requests for static files (anything with
 * a dot in the last path segment, e.g. main.js, style.css) and API/auth
 * paths are not forwarded.
 */
@Controller
public class SpaForwardingController {

    // Matches any path that:
    //  - does NOT start with /api, /oauth2, /login, /logout, /swagger, /v3
    //  - does NOT contain a dot (i.e. not a static file request)
    @GetMapping("/{path:^(?!api|oauth2|login|logout|swagger|v3).*$}/**")
    public String forward() {
        return "forward:/index.html";
    }
}
