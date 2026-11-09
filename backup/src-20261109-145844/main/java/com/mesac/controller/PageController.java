package com.mesac.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the HTML pages. Everything else on them loads from /api. */
@Controller
public class PageController {

    /**
     * The tracker. Admin and User views are on the same page,
     * picked with the switch at the top right. /admin opens it too.
     */
    @GetMapping({"/", "/admin"})
    public String tracker() {
        return "index";
    }

    /** Big-screen scoreboard for a TV or projector at the venue. */
    @GetMapping("/display")
    public String display() {
        return "display";
    }
}
