package ru.maksim.aicompanion;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * True names for the humans of this world (villagers, traders, witches, illagers). Each one has a Latin and a Cyrillic
 * spelling: the notebook takes either, the eyes show the one of the game's language. A name is drawn from the entity's
 * UUID, so it never changes. Names of the story's characters (Yagami, Lawliet...) are left out of the pools.
 */
public final class Names {
    private static final String[] MEN = {"Kenji|Кэндзи", "Hiroshi|Хироси", "Takeshi|Такэси", "Daisuke|Дайсукэ", "Kaito|Кайто",
            "Ren|Рэн", "Haruto|Харуто", "Yuto|Юто", "Sora|Сора", "Akira|Акира", "Taro|Таро", "Shinji|Синдзи", "Makoto|Макото",
            "Kazuo|Кадзуо", "Isamu|Исаму", "John|Джон", "Michael|Майкл", "David|Дэвид", "James|Джеймс", "Robert|Роберт",
            "Thomas|Томас", "Daniel|Дэниел", "Peter|Питер", "Ivan|Иван", "Sergei|Сергей", "Dmitri|Дмитрий", "Nikolai|Николай",
            "Pavel|Павел", "Lucas|Лукас", "Marco|Марко", "Pierre|Пьер", "Hans|Ханс", "Carlos|Карлос", "Diego|Диего",
            "Omar|Омар", "Victor|Виктор", "Arthur|Артур", "Felix|Феликс", "Leon|Леон", "Oscar|Оскар"};
    private static final String[] WOMEN = {"Yuki|Юки", "Sakura|Сакура", "Aiko|Айко", "Hana|Хана", "Mei|Мэй", "Rin|Рин",
            "Naomi|Наоми", "Emi|Эми", "Keiko|Кэйко", "Mari|Мари", "Mary|Мэри", "Anna|Анна", "Emma|Эмма", "Sarah|Сара",
            "Laura|Лора", "Olga|Ольга", "Elena|Елена", "Natalia|Наталья", "Sofia|София", "Clara|Клара", "Lucia|Лючия",
            "Irina|Ирина", "Alice|Элис", "Julia|Джулия", "Nina|Нина", "Vera|Вера"};
    private static final String[] SURNAMES = {"Tanaka|Танака", "Suzuki|Судзуки", "Sato|Сато", "Watanabe|Ватанабэ", "Ito|Ито",
            "Nakamura|Накамура", "Kobayashi|Кобаяси", "Yamamoto|Ямамото", "Kato|Като", "Yoshida|Ёсида", "Yamada|Ямада",
            "Sasaki|Сасаки", "Matsumoto|Мацумото", "Inoue|Иноуэ", "Kimura|Кимура", "Shimizu|Симидзу", "Mori|Мори",
            "Ikeda|Икэда", "Hashimoto|Хасимото", "Ishikawa|Исикава", "Okada|Окада", "Fujita|Фудзита", "Smith|Смит",
            "Johnson|Джонсон", "Brown|Браун", "Miller|Миллер", "Wilson|Уилсон", "Taylor|Тейлор", "Clark|Кларк",
            "Walker|Уокер", "Hughes|Хьюз", "Ivanov|Иванов", "Petrov|Петров", "Smirnov|Смирнов", "Volkov|Волков",
            "Sokolov|Соколов", "Morozov|Морозов", "Novak|Новак", "Rossi|Росси", "Dubois|Дюбуа", "Muller|Мюллер",
            "Garcia|Гарсия", "Lopez|Лопес", "Costa|Коста", "Weber|Вебер", "Moreau|Моро", "Kowalski|Ковальский"};
    /** The story's own people: never drawn, only spelled (the TV says "Линд Л. Тейлор", and that has to work too). */
    private static final String[] CANON = {"Lind|Линд", "L.|Л.", "Tailor|Тейлор", "Lawliet|Лоулайт", "Quillsh|Квилш", "Wammy|Вамми",
            "Kal|Кал", "Snydar|Снайдер", "Nate|Нейт", "River|Ривер", "Mihael|Михаэль", "Keehl|Кель", "Anthony|Энтони",
            "Carter|Картер", "Stephen|Стивен", "Loud|Лауд", "Halle|Холли", "Bullook|Буллук", "Touta|Тота", "Matsuda|Мацуда",
            "Shuichi|Сюити", "Aizawa|Айдзава", "Soichiro|Соитиро", "Yagami|Ягами", "Raye|Рэй", "Penber|Пенбер"};
    private static final Map<String, String> CYRILLIC = new HashMap<>();

    static {
        for (String[] pool : new String[][]{MEN, WOMEN, SURNAMES, CANON}) {
            for (String pair : pool) {
                String[] p = pair.split("\\|");
                CYRILLIC.put(p[0], p[1]);
                if (p[0].endsWith("ov")) { // Ivanova, Иванова
                    CYRILLIC.put(p[0] + "a", p[1] + "а");
                } else if (p[0].endsWith("ski")) {
                    CYRILLIC.put(p[0].substring(0, p[0].length() - 1) + "a", p[1].substring(0, p[1].length() - 2) + "ая");
                }
            }
        }
    }

    private Names() {
    }

    /** "First Last" in Latin letters, the same for the same UUID. */
    public static String of(UUID id, boolean woman) {
        Random r = new Random(id.getMostSignificantBits() * 31 + id.getLeastSignificantBits());
        String first = pick(woman ? WOMEN : MEN, r), last = pick(SURNAMES, r);
        if (woman && last.endsWith("ov")) {
            last += "a";
        } else if (woman && last.endsWith("ski")) {
            last = last.substring(0, last.length() - 1) + "a";
        }
        return first + " " + last;
    }

    /** The other spelling of a generated name (each word on its own); any other name comes back as it is. */
    public static String cyrillic(String latin) {
        StringBuilder sb = new StringBuilder();
        for (String word : latin.split(" ")) {
            sb.append(sb.length() > 0 ? " " : "").append(CYRILLIC.getOrDefault(word, word));
        }
        return sb.toString();
    }

    /** What the eyes show in this language. */
    public static String shown(String latin) {
        return Config.english() ? latin : cyrillic(latin);
    }

    /** Written in the notebook, does it match? Case, extra spaces and the alphabet don't matter; a letter does. */
    public static boolean matches(String written, String latin) {
        String w = norm(written);
        return w.equals(norm(latin)) || w.equals(norm(cyrillic(latin)));
    }

    /** How far a written name is from the real one, in letters (Levenshtein): a near miss is still the same person. */
    public static int distance(String written, String latin) {
        return Math.min(lev(norm(written), norm(latin)), lev(norm(written), norm(cyrillic(latin))));
    }

    private static String norm(String s) {
        return s.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    private static int lev(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                cur[j] = Math.min(Math.min(cur[j - 1], prev[j]) + 1, prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    private static String pick(String[] pool, Random r) {
        return pool[r.nextInt(pool.length)].split("\\|")[0];
    }
}
