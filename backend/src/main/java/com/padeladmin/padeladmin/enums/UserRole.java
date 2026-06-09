package com.padeladmin.padeladmin.enums;

public enum UserRole {
    ADMIN,        // legacy: admin global actual (= SUPER_ADMIN tras la migración)
    VIEWER,       // legacy: invitado de solo lectura
    SUPER_ADMIN,  // dueño de la plataforma: único que da de alta clubes
    CLUB,         // administrador de un club (tenant)
    PLAYER        // jugador con cuenta propia
}
