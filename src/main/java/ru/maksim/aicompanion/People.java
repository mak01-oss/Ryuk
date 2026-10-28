package ru.maksim.aicompanion;

import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Who the story's people are: the canon ones keep their manga names and aliases (L Lawliet as "Ryuzaki", Matsuda as
 * "Taro Matsui", Kal Snydar as "Jack Neylon", Nate River as "Near", Mihael Keehl as "Mello", the SPK under their cover
 * names), the rest get a real name and a cover name of their own. Lifespans in
 * days of play; Lind L. Tailor was a convict due to be executed, so his is short anyway.
 */
public final class People {
    public record Person(String trueName, @Nullable String alias, double lifeDays) {
    }

    private static final String[] NICKNAMES = {"Snake", "Jolly", "Ozzy", "Rat", "Joe Spades", "Skull", "Gravel", "Blue"};

    private People() {
    }

    public static Person of(HumanEntity.Role role, int variant) {
        return switch (role) {
            case TAILOR -> new Person("Lind L. Tailor", "L", 2.5);
            case L -> new Person("L Lawliet", "Ryuzaki", 70);
            case WATARI -> new Person("Quillsh Wammy", "Watari", 25);
            case BOSS -> new Person("Kal Snydar", "Jack Neylon", 45);
            case NEAR -> new Person("Nate River", "Near", 90);
            case MELLO -> new Person("Mihael Keehl", "Mello", 30);
            case SPK -> switch (variant % 3) { // the SPK's cover names are the ones they work under
                case 0 -> new Person("Anthony Carter", "Rester", 60);
                case 1 -> new Person("Stephen Loud", "Gevanni", 70);
                default -> new Person("Halle Bullook", "Lidner", 65);
            };
            case DETECTIVE -> switch (variant % 3) {
                case 0 -> new Person("Touta Matsuda", "Taro Matsui", 90);
                case 1 -> new Person("Shuichi Aizawa", "Aihara", 80);
                default -> new Person("Soichiro Yagami", "Asahi", 30);
            };
            case AGENT -> variant == 0 ? new Person("Raye Penber", generated(1, variant, false), 20)
                    : new Person(generated(2, variant, false), generated(3, variant, false), 40);
            case MAFIA -> new Person(generated(4, variant, false), NICKNAMES[Math.floorMod(variant, NICKNAMES.length)], 15 + variant % 7 * 9);
        };
    }

    /** A name of the world's pools, fixed for this role and variant. */
    private static String generated(int salt, int variant, boolean woman) {
        return Names.of(new UUID(0x4B697261L * salt, 31L * variant + salt), woman);
    }
}
