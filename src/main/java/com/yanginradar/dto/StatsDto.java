package com.yanginradar.dto;

/** Ust bardaki ozet sayaclar. */
public record StatsDto(
        long toplam,
        long bekleyen,
        long dogrulanan,
        long reddedilen,
        long sonduruldu,
        long uyduKaydi,
        long vatandasIhbari,
        long yetkiliyeIletilen,
        int aktifDinleyici) {
}
