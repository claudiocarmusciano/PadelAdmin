package com.padeladmin.padeladmin.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Soporte para una SPA con BrowserRouter servida por el backend.
 *
 * Las rutas de cliente (ej: /tournaments/3/calendar) no existen como archivos:
 * reenvía cualquier path SIN extensión de archivo (sin punto) a /index.html,
 * para que React Router las resuelva.
 *
 * - Los assets estáticos (/assets/app-abc.js, /favicon.ico) tienen punto → NO matchean → se sirven normal.
 * - Las rutas /api/** las maneja su @RestController (mapping más específico) → no se ven afectadas.
 */
@Controller
public class SpaForwardController {

    @RequestMapping(value = "/**/{path:[^\\.]*}")
    public String forward() {
        return "forward:/index.html";
    }
}
