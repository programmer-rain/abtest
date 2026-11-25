package com.demo.abtest.stats;

/**
 * 统计检验引擎：实验结论可信度的最后一道防线。
 *
 * 包含四件套：
 *  1. SRM 卡方检验 —— 先验证"分流本身有没有坏"，比看业务指标更重要；
 *  2. 双比例 Z 检验 —— 转化率类指标是否显著提升；
 *  3. Welch's t 检验 —— 均值类指标（人均时长/客单价）是否显著提升；
 *  4. 最小样本量预估 —— 实验开始前先算"需要多少人"，避免跑了个寂寞。
 *
 * 教学说明：所有分布函数（正态/卡方/t）均为标准数学公式的独立实现，无第三方依赖。
 */
public final class Stats {

    private Stats() {
    }

    // ==================== 1. SRM 卡方检验 ====================

    /**
     * 样本比率失衡检测。
     * 期望 50:50，实际进组 52:48 → 卡方值偏大、p 值极小 → 报警：分流有偏，数据作废。
     *
     * @param observed      各组实际人数
     * @param expectedRatio 各组期望占比（和为 1）
     * @return [卡方值, p 值]
     */
    public static double[] chiSquareSrm(long[] observed, double[] expectedRatio) {
        long total = 0;
        for (long o : observed) {
            total += o;
        }
        double chi2 = 0;
        for (int i = 0; i < observed.length; i++) {
            double expected = total * expectedRatio[i];
            if (expected > 0) {
                chi2 += Math.pow(observed[i] - expected, 2) / expected;
            }
        }
        int df = observed.length - 1;
        double p = chiSquareSurvival(chi2, df);
        return new double[]{chi2, p};
    }

    /** 卡方分布生存函数 P(X > x) = Q(df/2, x/2)，Q 为正则化上不完全伽马函数 */
    public static double chiSquareSurvival(double x, int df) {
        if (x <= 0) {
            return 1.0;
        }
        return upperIncompleteGammaQ(df / 2.0, x / 2.0);
    }

    // ==================== 2. 双比例 Z 检验 ====================

    /**
     * @return [z 值, p 值(双侧), 提升幅度%]
     */
    public static double[] twoProportionZTest(long convA, long nA, long convB, long nB) {
        if (nA <= 0 || nB <= 0) {
            return new double[]{0, 1, 0};
        }
        double pA = (double) convA / nA;
        double pB = (double) convB / nB;
        double pPool = (double) (convA + convB) / (nA + nB);
        double se = Math.sqrt(pPool * (1 - pPool) * (1.0 / nA + 1.0 / nB));
        if (se == 0) {
            return new double[]{0, 1, 0};
        }
        double z = (pB - pA) / se;
        double pValue = 2 * (1 - normCdf(Math.abs(z)));
        double lift = pA == 0 ? 0 : (pB - pA) / pA * 100;
        return new double[]{z, pValue, lift};
    }

    /** 标准正态分布 CDF（Abramowitz-Stegun 7.1.26 近似，误差 < 7.5e-8） */
    public static double normCdf(double x) {
        if (x < -8) {
            return 0;
        }
        if (x > 8) {
            return 1;
        }
        double t = 1 / (1 + 0.2316419 * Math.abs(x));
        double d = 0.3989422804014327 * Math.exp(-x * x / 2);
        double poly = t * (0.319381530
                + t * (-0.356563782
                + t * (1.781477937
                + t * (-1.821255978
                + t * 1.330274429))));
        double p = 1 - d * poly;
        return x >= 0 ? p : 1 - p;
    }

    // ==================== 3. Welch's t 检验 ====================

    /**
     * @return [t 值, p 值(双侧), 自由度]
     */
    public static double[] welchTTest(double meanA, double varA, long nA,
                                      double meanB, double varB, long nB) {
        if (nA < 2 || nB < 2) {
            return new double[]{0, 1, 0};
        }
        double seA = varA / nA;
        double seB = varB / nB;
        double se = Math.sqrt(seA + seB);
        if (se == 0) {
            return new double[]{0, 1, 0};
        }
        double t = (meanB - meanA) / se;
        // Welch-Satterthwaite 自由度
        double df = Math.pow(seA + seB, 2)
                / (Math.pow(seA, 2) / (nA - 1) + Math.pow(seB, 2) / (nB - 1));
        // 双侧 p 值：p = I_{df/(df+t^2)}(df/2, 1/2)，I 为正则化不完全贝塔函数
        double x = df / (df + t * t);
        double p = regularizedIncompleteBeta(df / 2.0, 0.5, x);
        return new double[]{t, p, df};
    }

    // ==================== 4. 样本量预估 ====================

    /**
     * 双侧 95% 置信、80% 功效下的近似公式：n ≈ 16 × p(1-p) / δ²
     *
     * @param baselineRate 基线转化率（如 0.032）
     * @param mde          最小可检测相对提升（如 0.10 表示要能测出 10% 的相对提升）
     * @return 每组所需最小样本量
     */
    public static long requiredSampleSize(double baselineRate, double mde) {
        double delta = mde * baselineRate;
        if (delta <= 0) {
            return Long.MAX_VALUE;
        }
        return (long) Math.ceil(16 * baselineRate * (1 - baselineRate) / (delta * delta));
    }

    // ==================== 特殊函数实现 ====================

    /** ln(Gamma(x))，Lanczos 近似 */
    private static double lnGamma(double x) {
        double[] c = {76.18009172947146, -86.50532032941677, 24.01409824083091,
                -1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5};
        double y = x;
        double tmp = x + 5.5;
        tmp -= (x + 0.5) * Math.log(tmp);
        double ser = 1.000000000190015;
        for (double coef : c) {
            ser += coef / ++y;
        }
        return -tmp + Math.log(2.5066282746310005 * ser / x);
    }

    /** 正则化上不完全伽马函数 Q(a, x) = 1 - P(a, x) */
    private static double upperIncompleteGammaQ(double a, double x) {
        if (x < 0 || a <= 0) {
            return Double.NaN;
        }
        if (x == 0) {
            return 1.0;
        }
        if (x < a + 1) {
            return 1 - gammaPSeries(a, x);
        }
        return gammaQContinuedFraction(a, x);
    }

    /** P(a,x) 的级数展开（x < a+1 时收敛快） */
    private static double gammaPSeries(double a, double x) {
        double ap = a;
        double sum = 1.0 / a;
        double del = sum;
        for (int n = 0; n < 200; n++) {
            ap += 1;
            del *= x / ap;
            sum += del;
            if (Math.abs(del) < Math.abs(sum) * 1e-10) {
                break;
            }
        }
        return sum * Math.exp(-x + a * Math.log(x) - lnGamma(a));
    }

    /** Q(a,x) 的连分式展开（x >= a+1 时收敛快） */
    private static double gammaQContinuedFraction(double a, double x) {
        double b = x + 1 - a;
        double c = 1 / 1e-30;
        double d = 1 / b;
        double h = d;
        for (int i = 1; i <= 200; i++) {
            double an = -i * (i - a);
            b += 2;
            d = an * d + b;
            if (Math.abs(d) < 1e-30) {
                d = 1e-30;
            }
            c = b + an / c;
            if (Math.abs(c) < 1e-30) {
                c = 1e-30;
            }
            d = 1 / d;
            double del = d * c;
            h *= del;
            if (Math.abs(del - 1) < 1e-10) {
                break;
            }
        }
        return Math.exp(-x + a * Math.log(x) - lnGamma(a)) * h;
    }

    /** 正则化不完全贝塔函数 I_x(a, b)，t 分布 p 值依赖它 */
    private static double regularizedIncompleteBeta(double a, double b, double x) {
        if (x <= 0) {
            return 0;
        }
        if (x >= 1) {
            return 1;
        }
        double lnBeta = lnGamma(a + b) - lnGamma(a) - lnGamma(b)
                + a * Math.log(x) + b * Math.log(1 - x);
        double front = Math.exp(lnBeta);
        if (x < (a + 1) / (a + b + 2)) {
            return front * betaContinuedFraction(a, b, x) / a;
        }
        return 1 - front * betaContinuedFraction(b, a, 1 - x) / b;
    }

    /** I_x(a,b) 的连分式展开（Lentz 算法） */
    private static double betaContinuedFraction(double a, double b, double x) {
        double qab = a + b;
        double qap = a + 1;
        double qam = a - 1;
        double c = 1;
        double d = 1 - qab * x / qap;
        if (Math.abs(d) < 1e-30) {
            d = 1e-30;
        }
        d = 1 / d;
        double h = d;
        for (int m = 1; m <= 200; m++) {
            int m2 = 2 * m;
            double aa = m * (b - m) * x / ((qam + m2) * (a + m2));
            d = 1 + aa * d;
            if (Math.abs(d) < 1e-30) {
                d = 1e-30;
            }
            c = 1 + aa / c;
            if (Math.abs(c) < 1e-30) {
                c = 1e-30;
            }
            d = 1 / d;
            h *= d * c;
            aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2));
            d = 1 + aa * d;
            if (Math.abs(d) < 1e-30) {
                d = 1e-30;
            }
            c = 1 + aa / c;
            if (Math.abs(c) < 1e-30) {
                c = 1e-30;
            }
            d = 1 / d;
            double del = d * c;
            h *= del;
            if (Math.abs(del - 1) < 1e-10) {
                break;
            }
        }
        return h;
    }
}
