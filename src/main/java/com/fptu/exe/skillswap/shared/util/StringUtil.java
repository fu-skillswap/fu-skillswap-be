package com.fptu.exe.skillswap.shared.util;

import java.text.Normalizer;
import java.util.UUID;
import java.util.regex.Pattern;

public class StringUtil {

    // Chuyển "Nguyễn Văn A" -> "nguyen-van-a"
    public static String toSlug(String input) {
        if (input == null || input.isEmpty())
            return "";
        String nonAccent = Normalizer.normalize(input, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(nonAccent).replaceAll("").toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "") // Bỏ ký tự đặc biệt
                .replaceAll("\\s+", "-"); // Thay khoảng trắng bằng dấu gạch ngang
    }

    // Sinh mã ngẫu nhiên (Ví dụ: sinh mã đơn hàng, mã OTP, mã giới thiệu)
    public static String generateRandomCode(int length) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, length).toUpperCase();
    }

    public static boolean isNullOrEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
}
