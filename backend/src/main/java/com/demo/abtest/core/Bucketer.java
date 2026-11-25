package com.demo.abtest.core;

import java.nio.charset.StandardCharsets;

/**
 * 分桶器：把"分流主体 + 盐值"稳定地映射到 0~9999 号桶。
 *
 * 教学说明（三个必知的坑，这里全部避开）：
 *  1. 不要用 String.hashCode()：对规律性 ID（自增、带前缀）分布严重不均；
 *  2. 哈希结果可能为负数：直接 % 会得到负桶号。本实现用 Math.floorMod 保证非负；
 *     （等价写法：(h & 0x7fffffff) % N，还能避开 Math.abs(Integer.MIN_VALUE) 仍为负的坑）
 *  3. 桶数用 10000 而不是 100：支持 0.01% 粒度的小流量试水。
 */
public final class Bucketer {

    /** 万分位桶：1 个桶 = 0.01% 流量 */
    public static final int BUCKET_NUM = 10000;

    private Bucketer() {
    }

    /**
     * @param unitId 分流主体（userId / deviceId）
     * @param salt   盐值（层盐）。不同层盐值不同 → 同一用户在不同层的桶号互相独立（正交）
     * @return [0, 10000) 之间的桶号，同一 (unitId, salt) 永远返回同一个值
     */
    public static int bucket(String unitId, String salt) {
        byte[] key = (unitId + "#" + salt).getBytes(StandardCharsets.UTF_8);
        int h = MurmurHash3.hash32(key, 0x9747b28c);
        return Math.floorMod(h, BUCKET_NUM);
    }
}
