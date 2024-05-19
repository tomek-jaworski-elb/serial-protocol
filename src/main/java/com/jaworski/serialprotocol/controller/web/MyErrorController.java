package com.jaworski.serialprotocol.controller.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Controller
public class MyErrorController implements ErrorController {

    @GetMapping("/error")
    public String getErrorPath(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        model.addAttribute("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        model.addAttribute("error", HttpStatusCode.valueOf(Integer.parseInt(status.toString())));
        model.addAttribute("message", request.getAttribute(RequestDispatcher.ERROR_MESSAGE));
        model.addAttribute("path", request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI));
        model.addAttribute("exception", request.getAttribute(RequestDispatcher.ERROR_EXCEPTION));
        model.addAttribute("status", request.getAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE));
        return "error";
    }
}
