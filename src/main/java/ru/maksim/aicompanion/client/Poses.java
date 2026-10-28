package ru.maksim.aicompanion.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import ru.maksim.aicompanion.HumanEntity.Stance;

/**
 * How the story's people hold themselves, set as places for the hands and feet ("soles on the floor", "hands on the
 * knees", "the chocolate at the mouth") and solved for the joints, two bones at a time. Model space: pixels, y down,
 * the front is -z; the hips' pivot is at y 12. {@link #seat} is where the seat's surface is in that space (the renderer
 * puts it at the entity's feet); {@code lean} tips the whole body forward about the hips.
 */
final class Poses {
    private static final float THIGH = 6, SHIN = 6, UPPER = 4, FORE = 6;
    private static final Vector3f HIP = new Vector3f(0, 12, 0);

    private Poses() {
    }

    /** Model y of the surface the body rests on, for each stance (24 = standing on it). */
    static float seat(Stance s) {
        return switch (s) {
            case CHAIR, DESK, SOFA -> 14.0F;
            case PERCH -> 13.4F;
            case FLOOR -> 13.9F;
            default -> 24;
        };
    }

    static float lean(Stance s) {
        return switch (s) {
            case DESK -> 0.12F;
            case PERCH -> 0.38F;
            case FLOOR -> 0.22F;
            case SOFA -> -0.22F;
            default -> 0;
        };
    }

    static void apply(PeopleModel m, Stance s, float age, int seed) {
        if (s == Stance.STAND) {
            return;
        }
        float lean = lean(s);
        m.lean = lean;
        float t = age + seed * 37; // each person on their own clock
        switch (s) {
            case CHAIR -> {
                chairLegs(m, lean);
                hand(m, true, lean, -3.0F, 9.3F, -4.4F, -1, 1, 1);
                hand(m, false, lean, 3.0F, 9.3F, -4.4F, 1, 1, 1);
            }
            case DESK -> {
                chairLegs(m, lean);
                float tap = Mth.sin(t * 0.9F) * 0.35F, tap2 = Mth.sin(t * 0.9F + 2.1F) * 0.35F; // typing
                hand(m, true, lean, -2.4F, 4.0F + Math.max(0, tap), -9.0F, -1, 1, 1);
                hand(m, false, lean, 2.4F, 4.0F + Math.max(0, tap2), -9.0F, 1, 1, 1);
            }
            case PERCH -> { // L: feet on the seat, knees to the chin, hands on the knees, now and then a thumb to the lip
                foot(m, true, lean, -2.2F, 13.4F, -3.2F, 0, -1, -0.45F);
                foot(m, false, lean, 2.2F, 13.4F, -3.2F, 0, -1, -0.45F);
                boolean thumb = Mth.sin(t * 0.013F) > 0.55F;
                if (thumb) {
                    hand(m, true, lean, -1.0F, -1.6F, -5.2F, -0.4F, 1, 0.3F);
                } else {
                    hand(m, true, lean, -2.4F, 6.6F, -6.4F, -1, 0.6F, 1);
                }
                hand(m, false, lean, 2.4F, 6.6F, -6.4F, 1, 0.6F, 1);
                m.head.zRot = 0.1F;
            }
            case FLOOR -> { // Near: one knee up, the other leg folded flat, a lock of hair round a finger
                foot(m, true, lean, -2.4F, 13.7F, -5.2F, 0, -1, -0.5F);
                foot(m, false, lean, -0.6F, 11.9F, -3.6F, 1, 0, -0.5F);
                float twirl = Mth.sin(t * 0.08F) * 0.5F;
                hand(m, true, lean, -4.9F, -5.4F + twirl, -1.6F, -1, 0.4F, 0.6F);
                hand(m, false, lean, 3.6F, 11.2F, -7.0F, 0.6F, 0, 1);
                m.head.xRot += 0.25F;
            }
            case SOFA -> { // Mello: sprawled back, one leg over the other, the chocolate at his mouth
                foot(m, false, lean, 2.4F, 19.6F, -6.0F, 0, -1, -1);
                foot(m, true, lean, -0.4F, 16.4F, -9.4F, -0.3F, -1, -1);
                boolean bite = Mth.sin(t * 0.05F) > 0.6F; // now and then a bite, else the bar beside the chin
                hand(m, true, lean, bite ? -1.6F : -3.4F, bite ? -0.6F : 1.4F, bite ? -5.0F : -4.2F, -0.6F, 1, 0.2F);
                hand(m, false, lean, 9.0F, 3.0F, 2.5F, 0.3F, 1, 0.5F);
            }
            case BEHIND -> { // Watari, Rester: hands clasped behind the back
                hand(m, true, 0, -1.2F, 10.8F, 3.4F, -1, 0.3F, 0.6F);
                hand(m, false, 0, 1.2F, 10.8F, 3.4F, 1, 0.3F, 0.6F);
            }
            case CROSSED -> { // a guard: arms folded on the chest
                hand(m, true, 0, 3.2F, 4.8F, -3.4F, -1, 1, 0.2F);
                hand(m, false, 0, -3.2F, 5.4F, -3.7F, 1, 1, 0.2F);
            }
            default -> {
            }
        }
    }

    /** Sitting on a chair: thighs on the seat, a little down to the knee, shins hanging (the soles just off the floor). */
    private static void chairLegs(PeopleModel m, float lean) {
        foot(m, true, lean, -2.5F, 19.3F, -5.6F, 0, -0.3F, -1);
        foot(m, false, lean, 2.5F, 19.3F, -5.6F, 0, -0.3F, -1);
    }

    /** The sole at (x, y, z), the knee bending toward (px, py, pz). */
    private static void foot(PeopleModel m, boolean right, float lean, float x, float y, float z, float px, float py, float pz) {
        ModelPart leg = right ? m.rightLeg : m.leftLeg;
        reach(leg, m.shin(right), new Vector3f(leg.x, leg.y, leg.z), unlean(new Vector3f(x, y, z), lean),
                unturn(new Vector3f(px, py, pz), lean), THIGH, SHIN, true);
    }

    /** The hand at (x, y, z), the elbow bending toward (px, py, pz). */
    private static void hand(PeopleModel m, boolean right, float lean, float x, float y, float z, float px, float py, float pz) {
        ModelPart arm = right ? m.rightArm : m.leftArm;
        Vector3f shoulder = new Vector3f(arm.x + (right ? -1 : 1), arm.y, arm.z); // the arm's centre line, not its pivot
        reach(arm, m.forearm(right), shoulder, unlean(new Vector3f(x, y, z), lean), unturn(new Vector3f(px, py, pz), lean), UPPER, FORE, false);
    }

    /** A point given upright, seen from inside the leaning body. */
    private static Vector3f unlean(Vector3f p, float lean) {
        return p.sub(HIP).rotateX(-lean).add(HIP);
    }

    private static Vector3f unturn(Vector3f dir, float lean) {
        return dir.rotateX(-lean);
    }

    /**
     * Two bones from {@code pivot} to {@code target}, the joint toward {@code pole}. Knees fold back (the shin's xRot
     * is positive), elbows fold forward; either way the limb's front faces the right way.
     */
    private static void reach(ModelPart upper, ModelPart lower, Vector3f pivot, Vector3f target, Vector3f pole, float a, float b, boolean knee) {
        Vector3f toT = new Vector3f(target).sub(pivot);
        float d = Mth.clamp(toT.length(), Math.abs(a - b) + 0.05F, a + b - 0.02F);
        Vector3f u = toT.normalize();
        Vector3f v = new Vector3f(pole).sub(new Vector3f(u).mul(pole.dot(u)));
        if (v.lengthSquared() < 1e-6F) {
            v.set(0, 0, -1).sub(new Vector3f(u).mul(-u.z));
        }
        v.normalize();
        float alpha = (float) Math.acos(Mth.clamp((a * a + d * d - b * b) / (2 * a * d), -1, 1));
        Vector3f w = new Vector3f(u).mul(Mth.cos(alpha)).add(new Vector3f(v).mul(Mth.sin(alpha))); // the first bone
        Vector3f joint = new Vector3f(w).mul(a).add(pivot);
        Vector3f l = new Vector3f(pivot).add(new Vector3f(u).mul(d)).sub(joint).normalize(); // the second bone
        float cos = Mth.clamp(w.dot(l), -1, 1);
        Vector3f q = new Vector3f(l).sub(new Vector3f(w).mul(cos));
        float bend = (float) Math.acos(cos);
        if (q.lengthSquared() < 1e-6F) {
            q.set(0, 0, knee ? 1 : -1).sub(new Vector3f(w).mul(knee ? w.z : -w.z)); // straight: bend nowhere, any side will do
            bend = 0;
        }
        q.normalize();
        Vector3f x = knee ? new Vector3f(w).cross(q) : new Vector3f(q).cross(w);
        Vector3f z = new Vector3f(x).cross(w);
        // R = Rz(c) Ry(b) Rx(a), columns x, w, z
        float r20 = x.z, r21 = w.z, r22 = z.z, r10 = x.y, r00 = x.x;
        float yRot = (float) Math.asin(Mth.clamp(-r20, -1, 1));
        float xRot, zRot;
        if (Math.abs(r20) < 0.9999F) {
            xRot = (float) Math.atan2(r21, r22);
            zRot = (float) Math.atan2(r10, r00);
        } else { // the hinge points straight ahead: any split of the roll will do
            xRot = (float) Math.atan2(-z.y, w.y);
            zRot = 0;
        }
        upper.xRot = xRot;
        upper.yRot = yRot;
        upper.zRot = zRot;
        lower.xRot = knee ? bend : -bend;
        lower.yRot = 0;
        lower.zRot = 0;
    }
}
