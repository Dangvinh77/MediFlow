package com.mediflow.organization.application.service;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

/** Shared, self-contained Vietnamese HTML and plain-text mail renderer. */
@org.springframework.stereotype.Component
public final class SecurityMailTemplate {
    private static final byte[] LOGO = renderLogo();
    private static final String LOGO_DATA_URI = "data:image/png;base64," + Base64.getEncoder().encodeToString(LOGO);
    private final String publicWebUrl;

    public SecurityMailTemplate(@org.springframework.beans.factory.annotation.Value(
            "${mediflow.public-web-url:http://localhost:3000}") String publicWebUrl) {
        this.publicWebUrl = publicWebUrl.replaceAll("/+$", "");
    }

    public String publicWebUrl() { return publicWebUrl; }

    public Rendered credential(String displayName, String email, String temporaryPassword) {
        String link = publicWebUrl + "/account-access?purpose=login";
        String text = "Xin chào " + safeText(displayName) + ",\n\n"
                + "Tài khoản MediFlow của bạn đã được cấp.\nEmail đăng nhập: " + email
                + "\nMật khẩu tạm: " + temporaryPassword
                + "\n\nĐăng nhập: " + link + "\nMật khẩu tạm có hiệu lực trong 24 giờ."
                + " Bạn sẽ cần đổi mật khẩu sau lần đăng nhập đầu tiên.\nMediFlow";
        String html = layout(displayName, "Tài khoản của bạn đã sẵn sàng",
                "Tài khoản MediFlow đã được cấp cho bạn.",
                "<p><strong>Email đăng nhập</strong><br><span class=\"value\">" + escape(email) + "</span></p>"
                + "<p><strong>Mật khẩu tạm</strong><br><span class=\"password\">" + escape(temporaryPassword) + "</span></p>",
                link, "Đăng nhập MediFlow", "Mật khẩu tạm có hiệu lực trong 24 giờ. Hãy đổi mật khẩu sau lần đăng nhập đầu tiên.");
        return new Rendered("Thông tin đăng nhập MediFlow", text, html);
    }

    public Rendered recovery(String displayName, String link) {
        String text = "Xin chào " + safeText(displayName) + ",\n\n"
                + "Nhận được yêu cầu đặt lại mật khẩu MediFlow. Liên kết có hiệu lực trong 30 phút:\n"
                + link + "\nNếu bạn không yêu cầu, hãy bỏ qua email này.\nMediFlow";
        String html = layout(displayName, "Đặt lại mật khẩu",
                "Bạn đã yêu cầu tạo mật khẩu mới cho tài khoản MediFlow.", "",
                link, "Đặt lại mật khẩu", "Liên kết chỉ sử dụng được một lần và hết hạn sau 30 phút. Nếu bạn không yêu cầu, hãy bỏ qua email này.");
        return new Rendered("Đặt lại mật khẩu MediFlow", text, html);
    }

    public Rendered passwordChanged(String displayName) {
        String login = publicWebUrl + "/login";
        String text = "Xin chào " + safeText(displayName) + ",\n\nMật khẩu MediFlow vừa được thay đổi."
                + " Nếu bạn không thực hiện thay đổi này, hãy liên hệ quản trị viên.\nĐăng nhập: " + login;
        String html = layout(displayName, "Mật khẩu đã được thay đổi",
                "Mật khẩu MediFlow của bạn vừa được cập nhật.", "", login, "Đến trang đăng nhập",
                "Nếu bạn không thực hiện thay đổi này, hãy liên hệ quản trị viên.");
        return new Rendered("Xác nhận đổi mật khẩu MediFlow", text, html);
    }

    public Rendered emailChanged(String displayName, String newEmail, String loginUrl) {
        String text = "Xin chào " + safeText(displayName) + ",\n\nEmail đăng nhập MediFlow đã được đổi thành "
                + newEmail + ".\nĐăng nhập: " + loginUrl + "\nNếu bạn không yêu cầu, hãy liên hệ quản trị viên.";
        String html = layout(displayName, "Email đăng nhập đã được cập nhật",
                "Email dùng để đăng nhập MediFlow đã được đổi.",
                "<p>Email đăng nhập mới: <strong>" + escape(newEmail) + "</strong></p>",
                loginUrl, "Đăng nhập MediFlow", "Nếu bạn không yêu cầu thay đổi này, hãy liên hệ quản trị viên.");
        return new Rendered("Email đăng nhập MediFlow đã thay đổi", text, html);
    }

    private String layout(String displayName, String title, String intro, String detail,
            String link, String button, String note) {
        String escapedLink = escape(link);
        return "<!doctype html><html lang=\"vi\"><head><meta charset=\"utf-8\"></head><body "
                + "style=\"margin:0;background:#f1f5f9;font-family:Arial,Helvetica,sans-serif;color:#0f172a\"><table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"background:#f1f5f9;padding:28px 12px\"><tr><td align=\"center\"><table role=\"presentation\" width=\"600\" cellspacing=\"0\" cellpadding=\"0\" style=\"max-width:600px;width:100%;background:#fff;border-radius:14px;overflow:hidden\"><tr><td style=\"background:#1d4ed8;padding:22px 28px;color:#fff\"><table role=\"presentation\"><tr><td><img alt=\"MediFlow MF\" width=\"48\" height=\"48\" src=\"" + LOGO_DATA_URI + "\" style=\"display:block;border:0;border-radius:12px\"></td><td style=\"padding-left:12px;font-size:21px;font-weight:700\">MediFlow</td></tr></table></td></tr>"
                + "<tr><td style=\"padding:30px 28px 14px\"><p style=\"margin:0 0 8px;color:#475569\">Xin chào " + escape(displayName) + ",</p><h1 style=\"font-size:23px;line-height:1.3;margin:0 0 14px;color:#0f172a\">" + escape(title) + "</h1><p style=\"font-size:15px;line-height:1.65;color:#334155;margin:0 0 18px\">" + escape(intro) + "</p>" + detail
                + "<table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" style=\"margin:24px 0\"><tr><td style=\"background:#1d4ed8;border-radius:8px\"><a href=\"" + escapedLink + "\" style=\"display:inline-block;padding:13px 22px;color:#fff;text-decoration:none;font-weight:700\">" + escape(button) + "</a></td></tr></table>"
                + "<p style=\"font-size:13px;line-height:1.6;color:#64748b\">Nếu nút không hoạt động, mở liên kết này:<br><a href=\"" + escapedLink + "\" style=\"color:#1d4ed8;word-break:break-all\">" + escapedLink + "</a></p><p style=\"font-size:13px;line-height:1.6;color:#64748b\">" + escape(note) + "</p></td></tr>"
                + "<tr><td style=\"padding:16px 28px;background:#f8fafc;border-top:1px solid #e2e8f0;color:#64748b;font-size:12px\">Thông báo tự động từ MediFlow. Vui lòng không trả lời email này.</td></tr></table></td></tr></table><style>.value{color:#1d4ed8;word-break:break-all}.password{display:inline-block;background:#eff6ff;border:1px solid #bfdbfe;border-radius:6px;padding:8px 12px;font-family:monospace;font-size:16px;letter-spacing:1px}</style></body></html>";
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String safeText(String value) { return value == null ? "bạn" : value.replaceAll("[\\r\\n]", " "); }

    private static byte[] renderLogo() {
        try {
            final int size = 96, scale = 8;
            byte[] pixels = new byte[size * (1 + size * 4)];
            String[] m = {"10001", "11011", "10101", "10101", "10001", "10001", "10001"};
            String[] f = {"11111", "10000", "11110", "10000", "10000", "10000", "10000"};
            for (int y = 0; y < size; y++) {
                int row = y * (1 + size * 4);
                pixels[row] = 0;
                for (int x = 0; x < size; x++) {
                    boolean corner = (x < 20 && y < 20 && distanceSquared(x, y, 20, 20) > 400)
                            || (x >= 76 && y < 20 && distanceSquared(x, y, 75, 20) > 400)
                            || (x < 20 && y >= 76 && distanceSquared(x, y, 20, 75) > 400)
                            || (x >= 76 && y >= 76 && distanceSquared(x, y, 75, 75) > 400);
                    int offset = row + 1 + x * 4;
                    if (corner) {
                        pixels[offset + 3] = 0;
                        continue;
                    }
                    boolean letter = letterPixel(m, x, y, 8, scale) || letterPixel(f, x, y, 48, scale);
                    pixels[offset] = (byte) (letter ? 255 : 29);
                    pixels[offset + 1] = (byte) (letter ? 255 : 78);
                    pixels[offset + 2] = (byte) (letter ? 255 : 216);
                    pixels[offset + 3] = (byte) 255;
                }
            }
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) { deflater.write(pixels); }
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            png.write(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
            ByteArrayOutputStream header = new ByteArrayOutputStream();
            try (java.io.DataOutputStream data = new java.io.DataOutputStream(header)) {
                data.writeInt(size); data.writeInt(size); data.writeByte(8); data.writeByte(6);
                data.writeByte(0); data.writeByte(0); data.writeByte(0);
            }
            writePngChunk(png, "IHDR", header.toByteArray());
            writePngChunk(png, "IDAT", compressed.toByteArray());
            writePngChunk(png, "IEND", new byte[0]);
            return png.toByteArray();
        } catch (Exception failure) {
            throw new IllegalStateException("Could not render MediFlow email logo", failure);
        }
    }

    private static boolean letterPixel(String[] rows, int x, int y, int startX, int scale) {
        int localX = x - startX, localY = y - 20;
        if (localX < 0 || localX >= 5 * scale || localY < 0 || localY >= 7 * scale) return false;
        return rows[localY / scale].charAt(localX / scale) == '1';
    }

    private static int distanceSquared(int x, int y, int centerX, int centerY) {
        int dx = centerX - x, dy = centerY - y;
        return dx * dx + dy * dy;
    }

    private static void writePngChunk(ByteArrayOutputStream output, String type, byte[] data) throws Exception {
        var bytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        try (var writer = new java.io.DataOutputStream(output)) {
            writer.writeInt(data.length); writer.write(bytes); writer.write(data);
            CRC32 crc = new CRC32(); crc.update(bytes); crc.update(data); writer.writeInt((int) crc.getValue());
        }
    }

    public static byte[] logoPng() { return LOGO.clone(); }
    public static String logoDataUri() { return LOGO_DATA_URI; }

    public record Rendered(String subject, String textBody, String htmlBody) { }
}
