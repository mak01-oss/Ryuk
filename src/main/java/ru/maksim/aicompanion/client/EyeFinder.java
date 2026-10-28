package ru.maksim.aicompanion.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where the eyes are on a skin's face (8 x 8, the hat layer over the head), so the god of death's red glows on the
 * right pixels of any skin. The eye row is the one whose mirrored pixels stand out most from the skin; a white beside
 * a dark pupil settles it, and a brow, a mouth or a glasses' bridge is told apart by having no skin between the two
 * sides. On each side the darkest pixel (the pupil) glows bright and the rest of the eye dim; hair hanging down the
 * face's edges stays out. Plain Java, so the self-test can try it on every skin.
 */
public final class EyeFinder {
    /** Steve's eyes, row 4: the pupils bright, the whites dim. What a face with no eyes to find gets. */
    public static final long[] STEVE = {bit(2, 4) | bit(5, 4), bit(1, 4) | bit(6, 4)};
    private static final double[] ROW_WEIGHT = {0, 0.4, 0.8, 1.0, 1.0, 0.8, 0.4};
    private static final double[] COL_WEIGHT = {0.5, 1.0, 1.0};

    private EyeFinder() {
    }

    public static long bit(int x, int y) {
        return 1L << (y * 8 + x);
    }

    /** {@code face}: 64 pixels 0xRRGGBB, row by row as on the skin. Returns {bright, dim}. */
    public static long[] find(int[] face) {
        Map<Integer, List<Integer>> buckets = new LinkedHashMap<>(); // the skin: the commonest colour of the lower face, coarsely
        for (int y = 2; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int c = face[y * 8 + x];
                buckets.computeIfAbsent((c >> 20 & 15) << 8 | (c >> 12 & 15) << 4 | c >> 4 & 15, k -> new ArrayList<>()).add(c);
            }
        }
        List<Integer> most = null;
        for (List<Integer> b : buckets.values()) {
            if (most == null || b.size() > most.size()) {
                most = b;
            }
        }
        double[] skin = new double[3];
        for (int c : most) {
            skin[0] += c >> 16 & 255;
            skin[1] += c >> 8 & 255;
            skin[2] += c & 255;
        }
        for (int i = 0; i < 3; i++) {
            skin[i] /= most.size();
        }
        int row = -1;
        double best = 0;
        for (int y = 1; y <= 6; y++) {
            int unlike = 0;
            for (int x = 0; x < 8; x++) {
                unlike += unlike(face[y * 8 + x], skin) ? 1 : 0;
            }
            double weight = unlike >= 7 ? 0.3 : 1.0; // a whole row of hair, a fringe
            if (sclera(face, y, skin)) {
                weight *= 2.0; // a white beside a dark pupil: that's an eye, whatever the row
            } else if (unlike(face[y * 8 + 3], skin) && unlike(face[y * 8 + 4], skin)) {
                weight *= 0.5; // a brow, a mouth, sunglasses' bridge: eyes have skin between them
            }
            double s = 0;
            for (int x = 0; x < 3; x++) {
                int a = face[y * 8 + x], b = face[y * 8 + 7 - x];
                if (unlike(a, skin) && unlike(b, skin) && dist(a, b) < 0.25) {
                    s = Math.max(s, Math.min(dist(a, skin), dist(b, skin)) * COL_WEIGHT[x]);
                }
            }
            s *= ROW_WEIGHT[y] * weight;
            if (s > best) {
                best = s;
                row = y;
            }
        }
        if (row < 0 || best < 0.06) {
            return STEVE.clone();
        }
        long bright = 0, dim = 0;
        int eyeRow = row;
        for (int[] side : new int[][]{{0, 1, 2}, {7, 6, 5}}) {
            List<Integer> xs = new ArrayList<>();
            for (int x : side) {
                if (unlike(face[row * 8 + x], skin)) {
                    xs.add(x);
                }
            }
            int pupil = darkest(face, row, xs, true);
            if (pupil >= 0) { // the face's edge pixel counts only when it looks like the eye, not hair down the side
                int iris = face[row * 8 + pupil];
                xs.removeIf(x -> (x == 0 || x == 7) && dist(face[eyeRow * 8 + x], iris) >= 0.15);
            }
            int eye = darkest(face, row, xs, false);
            for (int x : xs) {
                if (x == eye) {
                    bright |= bit(x, row);
                } else {
                    dim |= bit(x, row);
                }
            }
        }
        return new long[]{bright, dim};
    }

    /** The darkest of {@code xs} in the row (only the inner columns 1, 2, 5, 6 if {@code inner}), or -1. */
    private static int darkest(int[] face, int y, List<Integer> xs, boolean inner) {
        int out = -1;
        for (int x : xs) {
            if ((!inner || x != 0 && x != 7) && (out < 0 || lum(face[y * 8 + x]) < lum(face[y * 8 + out]))) {
                out = x;
            }
        }
        return out;
    }

    /** A near-white pixel beside one far darker and unlike the skin: the white of an eye and its pupil. */
    private static boolean sclera(int[] face, int y, double[] skin) {
        for (int x : new int[]{0, 1, 2, 5, 6, 7}) {
            int c = face[y * 8 + x];
            if (dist(c, 0xFFFFFF) >= 0.15) {
                continue;
            }
            for (int d = -1; d <= 1; d += 2) {
                int n = x + d;
                if (n >= 0 && n < 8 && unlike(face[y * 8 + n], skin) && lum(face[y * 8 + n]) < lum(c) - 60) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean unlike(int c, double[] skin) {
        double r = (c >> 16 & 255) - skin[0], g = (c >> 8 & 255) - skin[1], b = (c & 255) - skin[2];
        return Math.sqrt(r * r + g * g + b * b) / 441.7 > 0.16;
    }

    private static double dist(int c, double[] skin) {
        double r = (c >> 16 & 255) - skin[0], g = (c >> 8 & 255) - skin[1], b = (c & 255) - skin[2];
        return Math.sqrt(r * r + g * g + b * b) / 441.7;
    }

    private static double dist(int a, int b) {
        return dist(a, new double[]{b >> 16 & 255, b >> 8 & 255, b & 255});
    }

    private static double lum(int c) {
        return 0.299 * (c >> 16 & 255) + 0.587 * (c >> 8 & 255) + 0.114 * (c & 255);
    }
}
