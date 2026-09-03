package com.yanginradar;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.imageio.ImageIO;

/**
 * Testler icin EXIF (GPS + cekim zamani + kamera imzasi) tasiyan JPEG uretir.
 * Yalnizca test kaynaklarinda kullanilir.
 */
final class ExifTestJpeg {

    private static final DateTimeFormatter EXIF_ZAMAN =
            DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss");

    private ExifTestJpeg() {
    }

    /** EXIF tasimayan duz JPEG. */
    static byte[] duz(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(190, 70, 20));
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }

    /**
     * @param offset "+03:00" gibi bir EXIF OffsetTimeOriginal degeri, ya da null
     *               (o zaman fotograf saat dilimsiz cekim zamani tasir)
     */
    static byte[] ile(double lat, double lon, LocalDateTime cekim, String offset) throws Exception {
        byte[] taban = duz(1600, 1200);
        byte[] app1 = app1(lat, lon, cekim, offset);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(taban, 0, 2);                 // SOI
        out.write(app1);
        out.write(taban, 2, taban.length - 2);
        return out.toByteArray();
    }

    private static byte[] app1(double lat, double lon, LocalDateTime cekim, String offset) {
        byte[] make = zeroTerm("Apple");
        byte[] model = zeroTerm("iPhone 15 Pro");
        byte[] date = zeroTerm(cekim.format(EXIF_ZAMAN));
        byte[] offsetB = offset == null ? null : zeroTerm(offset);

        int exifCount = offset == null ? 3 : 4;
        int ifd0Size = 2 + 4 * 12 + 4;
        int exifSize = 2 + exifCount * 12 + 4;
        int gpsSize = 2 + 4 * 12 + 4;

        int exifOff = 8 + ifd0Size;
        int gpsOff = exifOff + exifSize;
        int dataBase = gpsOff + gpsSize;

        ByteBuffer data = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN);
        int makeOff = koy(data, dataBase, make);
        int modelOff = koy(data, dataBase, model);
        int dateOff = koy(data, dataBase, date);
        int latOff = koy(data, dataBase, dms(lat));
        int lonOff = koy(data, dataBase, dms(lon));
        int offsetOff = offsetB == null ? 0 : koy(data, dataBase, offsetB);

        ByteBuffer tiff = ByteBuffer.allocate(dataBase + data.position())
                .order(ByteOrder.LITTLE_ENDIAN);
        tiff.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);

        tiff.putShort((short) 4);
        girdi(tiff, 0x010F, 2, make.length, makeOff);
        girdi(tiff, 0x0110, 2, model.length, modelOff);
        girdi(tiff, 0x8769, 4, 1, exifOff);
        girdi(tiff, 0x8825, 4, 1, gpsOff);
        tiff.putInt(0);

        tiff.putShort((short) exifCount);
        girdi(tiff, 0x9003, 2, date.length, dateOff);
        girdi(tiff, 0xA002, 4, 1, 1600);
        girdi(tiff, 0xA003, 4, 1, 1200);
        if (offsetB != null) {
            girdi(tiff, 0x9011, 2, offsetB.length, offsetOff);
        }
        tiff.putInt(0);

        tiff.putShort((short) 4);
        girdiHam(tiff, 0x0001, 2, 2, new byte[] { (byte) (lat >= 0 ? 'N' : 'S'), 0, 0, 0 });
        girdi(tiff, 0x0002, 5, 3, latOff);
        girdiHam(tiff, 0x0003, 2, 2, new byte[] { (byte) (lon >= 0 ? 'E' : 'W'), 0, 0, 0 });
        girdi(tiff, 0x0004, 5, 3, lonOff);
        tiff.putInt(0);

        data.flip();
        tiff.put(data);

        byte[] tiffBytes = tiff.array();
        int payload = 6 + tiffBytes.length;

        ByteBuffer app1 = ByteBuffer.allocate(4 + payload).order(ByteOrder.BIG_ENDIAN);
        app1.put((byte) 0xFF).put((byte) 0xE1);
        app1.putShort((short) (payload + 2));
        app1.put("Exif".getBytes()).put((byte) 0).put((byte) 0);
        app1.put(tiffBytes);
        return app1.array();
    }

    private static void girdi(ByteBuffer b, int tag, int tip, int adet, int deger) {
        b.putShort((short) tag).putShort((short) tip).putInt(adet).putInt(deger);
    }

    private static void girdiHam(ByteBuffer b, int tag, int tip, int adet, byte[] deger) {
        b.putShort((short) tag).putShort((short) tip).putInt(adet).put(deger);
    }

    private static int koy(ByteBuffer data, int dataBase, byte[] blob) {
        int off = dataBase + data.position();
        data.put(blob);
        if (data.position() % 2 != 0) {
            data.put((byte) 0);
        }
        return off;
    }

    private static byte[] zeroTerm(String s) {
        byte[] ham = s.getBytes();
        byte[] out = new byte[ham.length + 1];
        System.arraycopy(ham, 0, out, 0, ham.length);
        return out;
    }

    /** Ondalik dereceyi EXIF'in bekledigi derece/dakika/saniye rational ucluosune cevirir. */
    private static byte[] dms(double derece) {
        double d = Math.abs(derece);
        int dd = (int) d;
        int mm = (int) ((d - dd) * 60);
        long ss = Math.round((d - dd - mm / 60d) * 3600 * 1000);

        ByteBuffer b = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(dd).putInt(1);
        b.putInt(mm).putInt(1);
        b.putInt((int) ss).putInt(1000);
        return b.array();
    }
}
