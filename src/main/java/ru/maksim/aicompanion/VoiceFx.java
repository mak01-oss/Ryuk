package ru.maksim.aicompanion;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Turns a plain synthesized voice into Ryuk's: lower and slower (one resample does both, like a tape slowed down),
 * a hoarse rattle, a darker tone and a short hollow echo. Plain Java on 16-bit mono PCM, so it is unit-testable.
 * The knobs come from the config: the right amount is a matter of taste and of the base voice.
 */
public final class VoiceFx {
    private VoiceFx() {
    }

    /**
     * @param pitch below 1 lowers the voice and drawls it by the same factor (0.85 = about 3 semitones down, 18% slower)
     * @param rasp  0..1, how hoarse: saturation plus a 40 Hz "vocal fry" flutter that follows the loudness
     */
    public static short[] ryuk(short[] in, int rate, double pitch, double rasp) {
        float[] x = resample(toFloat(in), pitch);
        double drive = 1 + 6 * rasp;
        double norm = Math.tanh(drive);
        double env = 0, low = 0, dark = 0;
        double lowA = 1 - Math.exp(-2 * Math.PI * 300 / rate);   // body below ~300 Hz
        double darkA = 1 - Math.exp(-2 * Math.PI * 3500 / rate); // cut the hiss above ~3.5 kHz
        double envA = 1 - Math.exp(-1.0 / (0.01 * rate));        // 10 ms loudness follower
        float[] y = new float[x.length];
        for (int i = 0; i < x.length; i++) {
            double s = x[i];
            env += envA * (Math.abs(s) - env);
            double flutter = 1 - rasp * 0.35 * (0.5 + 0.5 * Math.sin(2 * Math.PI * 41 * i / rate)) * Math.min(1, env * 8);
            double sat = Math.tanh(drive * s) / norm;
            s = ((1 - rasp) * s + rasp * sat) * flutter;
            low += lowA * (s - low);
            dark += darkA * (s - dark);
            y[i] = (float) (dark + 0.35 * low);
        }
        y = echo(y, rate);
        return toShort(normalize(y, 0.89F));
    }

    /**
     * Ryuk's own voice as the user picked it on 19.10.2026 (the friend's cloned voice): slowed like tape by
     * {@code pitch}, then roughened from the inside, with no echo or layer on top. Every other vocal period gets louder
     * and each one a little uneven (a gravelly throat); breath noise takes the shape of his own formants and pulses with
     * each period (hoarse, not hiss); the mids saturate a little. Port of voice_clone/scripts/rough_fx.py "rough1";
     * {@code amount} scales it (1 = as picked, 0 = just the tape).
     */
    public static short[] rough(short[] in, int rate, double pitch, double amount) {
        float[] x = resample(toFloat(in), pitch);
        Cycle c = cycle(x, rate);
        float[] y = growl(x, rate, c, 0.25 * amount, 0.08 * amount);
        float[] air = breath(y, rate, c, 0.12 * amount);
        for (int i = 0; i < y.length; i++) {
            y[i] += air[i];
        }
        grit(y, rate, 0.15 * amount);
        zeroPhase(y, Biquad.butter(true, 60, Q, rate));
        return toShort(normalize(y, 0.89F));
    }

    private static final double Q = Math.sqrt(0.5);                // 2nd-order Butterworth
    private static final double[] Q4 = {0.5411961, 1.3065630};     // 4th order as two biquads

    /**
     * The voice's own cycle, found without a pitch tracker (trackers lose this creaky voice on 3/4 of the speech).
     * wave: the fundamental, lowpassed under its 2nd harmonic, at about ±1; starts: the first sample of each vocal
     * period (a Schmitt trigger on the wave); voiced: 0..1, from the share of the sound below 1 kHz.
     */
    private record Cycle(float[] wave, int[] starts, float[] voiced) {
    }

    private static Cycle cycle(float[] x, int rate) {
        float[] lp = zeroPhase(x.clone(), lowpass4(110, rate));
        float[] eLp = rms(lp, rate), eX = rms(x, rate), eLow = rms(zeroPhase(x.clone(), lowpass4(1000, rate)), rate);
        float loudest = 0;
        for (float e : eX) {
            loudest = Math.max(loudest, e);
        }
        float[] wave = new float[x.length], voiced = new float[x.length];
        int[] starts = new int[64];
        int n = 0, state = 0;
        for (int i = 0; i < x.length; i++) {
            wave[i] = lp[i] / eLp[i];
            voiced[i] = eX[i] > 0.01F * loudest ? Math.min(1, Math.max(0, (eLow[i] / eX[i] - 0.5F) / 0.3F)) : 0;
            if (wave[i] < -0.5F) {
                state = -1;
            } else if (wave[i] > 0.5F) { // wiggles inside ±0.5 don't count
                if (state == -1) {
                    if (n == starts.length) {
                        starts = Arrays.copyOf(starts, n * 2);
                    }
                    starts[n++] = i;
                }
                state = 1;
            }
        }
        return new Cycle(wave, Arrays.copyOf(starts, n), voiced);
    }

    /** Every other period louder (period doubling) and a random gain per period (shimmer), where it's voiced. */
    private static float[] growl(float[] x, int rate, Cycle c, double depth, double shimmer) {
        float[] delta = new float[x.length]; // gain - 1, so the smoothing has no edge transient
        Random random = new Random(1);
        int[] s = c.starts();
        for (int k = 0; k < s.length; k++) {
            double gain = Math.max(0.15, 1 + (k % 2 == 0 ? depth : -depth) + shimmer * random.nextGaussian());
            Arrays.fill(delta, s[k], k + 1 < s.length ? s[k + 1] : x.length, (float) (gain - 1));
        }
        zeroPhase(delta, Biquad.butter(false, 250, Q, rate)); // no clicks at the switches
        float[] y = new float[x.length];
        for (int i = 0; i < x.length; i++) {
            y[i] = x[i] * (1 + c.voiced()[i] * delta[i]);
        }
        return y;
    }

    /**
     * White noise through each 25 ms frame's own spectral envelope (LPC), at {@code level} of the frame's loudness,
     * pulsing once per period where voiced: hoarseness inside the voice rather than hiss on top of it.
     */
    private static float[] breath(float[] x, int rate, Cycle c, double level) {
        int frame = rate / 40, hop = frame / 2, order = 18;
        double[] win = new double[frame], seg = new double[frame], noise = new double[frame];
        for (int j = 0; j < frame; j++) {
            win[j] = 0.5 - 0.5 * Math.cos(2 * Math.PI * j / (frame - 1));
        }
        float[] out = new float[x.length];
        Random random = new Random(5);
        for (int start = 0; start + frame < x.length; start += hop) {
            double energy = 0;
            for (int j = 0; j < frame; j++) {
                seg[j] = x[start + j] * win[j];
                energy += seg[j] * seg[j];
            }
            double loud = Math.sqrt(energy / frame);
            if (loud < 1e-4) {
                continue;
            }
            double[] a = lpc(seg, order);
            double power = 0;
            for (int j = 0; j < frame; j++) {
                double v = random.nextGaussian();
                for (int k = 1; k <= order && k <= j; k++) {
                    v -= a[k] * noise[j - k];
                }
                noise[j] = v;
                power += v * v;
            }
            double gain = loud / (Math.sqrt(power / frame) + 1e-12) * level;
            for (int j = 0; j < frame; j++) {
                out[start + j] += (float) (noise[j] * gain * win[j]);
            }
        }
        for (int i = 0; i < out.length; i++) {
            out[i] *= 1 - 0.7F * c.voiced()[i] * (1 - Math.min(Math.max(c.wave()[i], 0), 1.4F) / 1.4F);
        }
        return out;
    }

    /** Saturates the 300-3000 Hz band a little and adds back only the difference: grit without fuzz. */
    private static void grit(float[] x, int rate, double amount) {
        float[] band = zeroPhase(x.clone(), Biquad.butter(true, 300, Q, rate), Biquad.butter(false, 3000, Q, rate));
        float peak = 1e-9F;
        for (float v : band) {
            peak = Math.max(peak, Math.abs(v));
        }
        double drive = 6, norm = Math.tanh(drive);
        for (int i = 0; i < x.length; i++) {
            x[i] += (float) (amount * (Math.tanh(drive * band[i] / peak) * peak / norm - band[i]));
        }
    }

    /** Autocorrelation LPC by Levinson-Durbin: a[0] = 1, the frame's spectral envelope is 1 / A(z). */
    static double[] lpc(double[] x, int order) {
        double[] r = new double[order + 1], a = new double[order + 1], prev = new double[order + 1];
        for (int lag = 0; lag <= order; lag++) {
            for (int i = lag; i < x.length; i++) {
                r[lag] += x[i] * x[i - lag];
            }
        }
        r[0] *= 1 + 1e-9;
        a[0] = 1;
        double err = r[0];
        for (int i = 1; i <= order && err > 0; i++) {
            double acc = r[i];
            for (int j = 1; j < i; j++) {
                acc += a[j] * r[i - j];
            }
            double k = -acc / err;
            System.arraycopy(a, 0, prev, 0, i);
            for (int j = 1; j < i; j++) {
                a[j] = prev[j] + k * prev[i - j];
            }
            a[i] = k;
            err *= 1 - k * k;
        }
        return a;
    }

    /** ~15 ms RMS loudness, never zero. */
    private static float[] rms(float[] x, int rate) {
        float[] e = new float[x.length];
        for (int i = 0; i < x.length; i++) {
            e[i] = x[i] * x[i];
        }
        zeroPhase(e, Biquad.butter(false, 30, Q, rate));
        for (int i = 0; i < e.length; i++) {
            e[i] = (float) Math.sqrt(Math.max(0, e[i])) + 1e-9F;
        }
        return e;
    }

    private static Biquad[] lowpass4(double hz, int rate) {
        return new Biquad[] {Biquad.butter(false, hz, Q4[0], rate), Biquad.butter(false, hz, Q4[1], rate)};
    }

    /** Forward, then backward: no phase shift, so the analysis lines up with the voice (scipy's filtfilt). */
    private static float[] zeroPhase(float[] x, Biquad... stages) {
        for (int pass = 0; pass < 2; pass++) {
            for (Biquad b : stages) {
                b.run(x);
            }
            for (int i = 0, j = x.length - 1; i < j; i++, j--) {
                float t = x[i];
                x[i] = x[j];
                x[j] = t;
            }
        }
        return x;
    }

    private record Biquad(double b0, double b1, double b2, double a1, double a2) {
        /** RBJ cookbook low/high pass: bilinear and prewarped, the same response as scipy's butter. */
        static Biquad butter(boolean high, double hz, double q, int rate) {
            double w = 2 * Math.PI * hz / rate, cos = Math.cos(w), alpha = Math.sin(w) / (2 * q), a0 = 1 + alpha;
            double b1 = high ? -(1 + cos) : 1 - cos;
            return new Biquad(Math.abs(b1) / 2 / a0, b1 / a0, Math.abs(b1) / 2 / a0, -2 * cos / a0, (1 - alpha) / a0);
        }

        void run(float[] x) {
            double x1 = 0, x2 = 0, y1 = 0, y2 = 0;
            for (int i = 0; i < x.length; i++) {
                double y = b0 * x[i] + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
                x2 = x1;
                x1 = x[i];
                y2 = y1;
                y1 = y;
                x[i] = (float) y;
            }
        }
    }

    /** Linear interpolation; factor < 1 makes the sound longer and lower. */
    static float[] resample(float[] x, double factor) {
        int n = (int) (x.length / factor);
        float[] y = new float[n];
        for (int i = 0; i < n; i++) {
            double p = i * factor;
            int j = (int) p;
            double f = p - j;
            float a = x[Math.min(j, x.length - 1)], b = x[Math.min(j + 1, x.length - 1)];
            y[i] = (float) (a + (b - a) * f);
        }
        return y;
    }

    /** Two short reflections and a tail: hollow, not a cathedral. */
    private static float[] echo(float[] x, int rate) {
        int d1 = rate * 37 / 1000, d2 = rate * 61 / 1000, tail = rate / 5;
        float[] y = new float[x.length + tail];
        for (int i = 0; i < y.length; i++) {
            float dry = i < x.length ? x[i] : 0;
            float e1 = i >= d1 && i - d1 < x.length ? x[i - d1] : 0;
            float e2 = i >= d2 ? y[i - d2] : 0;
            y[i] = dry + 0.22F * e1 + 0.16F * e2;
        }
        return y;
    }

    private static float[] normalize(float[] x, float peak) {
        float max = 1e-6F;
        for (float v : x) {
            max = Math.max(max, Math.abs(v));
        }
        float k = peak / max;
        for (int i = 0; i < x.length; i++) {
            x[i] *= k;
        }
        return x;
    }

    private static float[] toFloat(short[] s) {
        float[] f = new float[s.length];
        for (int i = 0; i < s.length; i++) {
            f[i] = s[i] / 32768F;
        }
        return f;
    }

    private static short[] toShort(float[] f) {
        short[] s = new short[f.length];
        for (int i = 0; i < f.length; i++) {
            s[i] = (short) Math.max(-32768, Math.min(32767, Math.round(f[i] * 32767)));
        }
        return s;
    }

    public record Pcm(short[] samples, int rate) {
    }

    /** Pieces one after another with {@code gap} samples of silence between them. */
    public static short[] join(List<short[]> parts, int gap) {
        int n = 0;
        for (short[] p : parts) {
            n += p.length + gap;
        }
        short[] all = new short[Math.max(0, n - gap)];
        int at = 0;
        for (short[] p : parts) {
            System.arraycopy(p, 0, all, at, Math.min(p.length, all.length - at));
            at += p.length + gap;
        }
        return all;
    }

    /** 16-bit mono WAV: walks the chunks instead of assuming a 44-byte header. */
    public static Pcm readWav(byte[] bytes) {
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int rate = 22050;
        b.position(12);
        while (b.remaining() >= 8) {
            String id = new String(bytes, b.position(), 4, StandardCharsets.US_ASCII);
            b.position(b.position() + 4);
            long size = Integer.toUnsignedLong(b.getInt()); // streamed WAVs (Orpheus, ffmpeg) say 0xFFFFFFFF: "until the end"
            if (id.equals("fmt ")) {
                rate = b.getInt(b.position() + 4);
            } else if (id.equals("data")) {
                short[] samples = new short[(int) Math.min(size, b.remaining()) / 2];
                b.asShortBuffer().get(samples);
                return new Pcm(samples, rate);
            }
            b.position((int) Math.min(b.limit(), b.position() + size + (size & 1)));
        }
        throw new IllegalArgumentException("no audio data in the WAV");
    }

    /** What gets read out loud: no stage directions (*хрум*) and no emoji. */
    public static String speakable(String text) {
        return text.replaceAll("\\*[^*]*\\*", " ").replaceAll("[^\\p{L}\\p{N}\\p{P}\\s]", "").replaceAll("\\s+", " ").strip();
    }

    /** Loudness per 50 ms window, 0..1 relative to the loudest one: drives his mouth while the voice plays. */
    public static float[] envelope(short[] pcm, int rate) {
        int window = rate / 20;
        float[] e = new float[(pcm.length + window - 1) / window];
        float max = 1e-6F;
        for (int w = 0; w < e.length; w++) {
            double sum = 0;
            int end = Math.min(pcm.length, (w + 1) * window);
            for (int i = w * window; i < end; i++) {
                sum += (double) pcm[i] * pcm[i];
            }
            e[w] = (float) Math.sqrt(sum / Math.max(1, end - w * window));
            max = Math.max(max, e[w]);
        }
        for (int w = 0; w < e.length; w++) {
            e[w] /= max;
        }
        return e;
    }
}
