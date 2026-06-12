package com.padeladmin.padeladmin.enums;

/**
 * Zona de 4, Ronda 2: distingue el partido de ganadores (gan vs gan) del de
 * perdedores (perd vs perd). Permite crear ambos como placeholders (sin parejas)
 * al generar el fixture y rellenarlos cuando se cierra la Ronda 1.
 */
public enum ZoneRound2Type {
    WINNERS,
    LOSERS
}
