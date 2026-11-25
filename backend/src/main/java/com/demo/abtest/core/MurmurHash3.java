package com.demo.abtest.core;

/**
 * MurmurHash3 x86_32 实现。
 *
 * 教学说明：
 *  - 原算法由 Austin Appleby 发布，参考实现为公有领域（Public Domain），可自由使用；
 *  - 本文件为按公开算法描述独立编写的实现，无任何第三方依赖；
 *  - 特点：速度快（非加密哈希）、分布均匀、雪崩效应好——输入改一个字符，输出完全无关。
 *    这正是 AB 分流"分层正交"能成立的数学基础。
 */
public final class MurmurHash3 {

    private MurmurHash3() {
    }

    public static int hash32(byte[] data, int seed) {
        final int c1 = 0xcc9e2d51;
        final int c2 = 0x1b873593;

        int h = seed;
        int len = data.length;
        int roundedEnd = len & 0xfffffffc; // 4 字节对齐的边界

        // 主体：每次处理 4 字节
        for (int i = 0; i < roundedEnd; i += 4) {
            int k = (data[i] & 0xff)
                    | ((data[i + 1] & 0xff) << 8)
                    | ((data[i + 2] & 0xff) << 16)
                    | ((data[i + 3] & 0xff) << 24);
            k *= c1;
            k = Integer.rotateLeft(k, 15);
            k *= c2;

            h ^= k;
            h = Integer.rotateLeft(h, 13);
            h = h * 5 + 0xe6546b64;
        }

        // 尾部：不足 4 字节的剩余部分
        int k = 0;
        switch (len & 3) {
            case 3:
                k ^= (data[roundedEnd + 2] & 0xff) << 16;
                // fall through
            case 2:
                k ^= (data[roundedEnd + 1] & 0xff) << 8;
                // fall through
            case 1:
                k ^= (data[roundedEnd] & 0xff);
                k *= c1;
                k = Integer.rotateLeft(k, 15);
                k *= c2;
                h ^= k;
                break;
            default:
                break;
        }

        // 混合（finalization）：让高位低位充分搅动
        h ^= len;
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return h;
    }
}
