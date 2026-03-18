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

    // Matches any path where the first segment:
    //  - does NOT start with api, oauth2, login, logout, swagger, v3, or assets
    //  - does NOT contain a dot (excludes static file requests like index.html)
    // The [^.]* prevents an infinite forward loop: when this controller forwards
    // to /index.html, that path contains a dot and won't re-match this mapping,
    // so it falls through to Spring's static resource handler instead.
    @GetMapping({
        "/{path:^(?!api|oauth2|login|logout|swagger|v3|assets)[^.]*$}",
        "/{path:^(?!api|oauth2|login|logout|swagger|v3|assets)[^.]*$}/**"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
