package io.freemytv;

import java.util.Random;

/** Platform-independent time and maths rules, shared by the UI and its guard. */
final class EducationRules {
    static final long DAILY_MS = 3 * 60 * 60 * 1000L;
    static final long BONUS_MS = 30 * 60 * 1000L;
    static final int QUESTIONS = 5;

    static long remaining(long used, int rewards) {
        return Math.max(0, DAILY_MS + Math.max(0, rewards) * BONUS_MS - Math.max(0, used));
    }

    static final class Problem {
        final int a, b, answer;
        final String operator;
        Problem(int a, int b, String operator, int answer) {
            this.a = a; this.b = b; this.operator = operator; this.answer = answer;
        }
        String text() { return a + " " + operator + " " + b + " = ?"; }
    }

    static Problem problem(Random random, int practiceDays) {
        int days = Math.max(0, practiceDays);
        int max = 5 + Math.min(days, 15);
        if (days >= 14 && random.nextInt(4) == 0) {
            int a = 2 + random.nextInt(Math.min(9, 2 + (days - 14) / 3));
            int b = 1 + random.nextInt(5);
            return new Problem(a, b, "×", a * b);
        }
        int a = random.nextInt(max + 1), b = random.nextInt(max + 1);
        if (days >= 3 && random.nextBoolean()) {
            return new Problem(Math.max(a, b), Math.min(a, b), "−", Math.abs(a - b));
        }
        return new Problem(a, b, "+", a + b);
    }
}
