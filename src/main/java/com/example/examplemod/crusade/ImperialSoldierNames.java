package com.example.examplemod.crusade;

import java.util.UUID;

/**
 * Names for the Guard.
 *
 * <h2>Derived from the UUID, not rolled and stored</h2>
 *
 * A soldier's name is a pure function of his entity UUID. That is worth more than it looks: it costs
 * no storage, it cannot drift between the client and the server, it survives a chunk unload and a
 * save format change, and two different systems asking "who is this?" can never disagree. The record
 * keeps a copy anyway, because a fallen soldier's entity is gone and the memorial still has to know
 * whose name to carry.
 *
 * <h2>Original, on purpose</h2>
 *
 * These lists are written for this mod. They are built to sound like the Imperium — late-Roman given
 * names against blunt, worn-down surnames — without borrowing any published character. A name that
 * belongs to somebody else's book is a name that makes the project someone else's problem.
 */
public final class ImperialSoldierNames {
    private ImperialSoldierNames() {
    }

    private static final String[] GIVEN = {
            "Marius", "Caius", "Lucius", "Severan", "Octavian", "Titus", "Cassian", "Dorn",
            "Varro", "Aurel", "Quintus", "Rennick", "Halden", "Corvin", "Tiberon", "Galen",
            "Ruslan", "Mordec", "Vasil", "Estev", "Kolben", "Aramis", "Dalen", "Ferrus",
            "Iven", "Josson", "Karnak", "Lorric", "Maddox", "Nikolai", "Orven", "Petro",
            "Radek", "Stellan", "Torvan", "Ulric", "Valen", "Wendel", "Yorick", "Zoran",
    };

    private static final String[] SURNAME = {
            "Holt", "Brenn", "Venn", "Kord", "Rell", "Dray", "Vale", "Sark",
            "Thorne", "Krall", "Mott", "Bray", "Denn", "Fask", "Grell", "Harn",
            "Iker", "Jarn", "Kessel", "Lund", "Marek", "Nash", "Orlic", "Pell",
            "Quill", "Roth", "Stav", "Trask", "Ulm", "Vorn", "Wexler", "Yarrow",
            "Ziegler", "Ashe", "Burke", "Colm", "Drexel", "Ehrhardt", "Faust", "Gault",
    };

    /**
     * This soldier's full name.
     *
     * <p>The two halves are drawn from independent bits of the UUID so that two soldiers sharing a
     * given name almost never share a surname as well.
     */
    public static String forUuid(UUID id) {
        return given(id) + " " + surname(id);
    }

    public static String given(UUID id) {
        return GIVEN[index(id.getMostSignificantBits(), GIVEN.length)];
    }

    public static String surname(UUID id) {
        return SURNAME[index(id.getLeastSignificantBits(), SURNAME.length)];
    }

    /**
     * A stable, well-spread index.
     *
     * <p>The bits are mixed before being reduced: UUID halves are not uniformly distributed in their
     * low bits (version and variant nibbles live there), and taking a plain modulo would bunch names
     * together in a way a player would notice across a garrison.
     */
    private static int index(long bits, int size) {
        long mixed = bits ^ (bits >>> 32);
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;

        return (int) Math.floorMod(mixed, (long) size);
    }

    /**
     * How a soldier is addressed, given his career grade.
     *
     * <p>"Trooper Marius Holt" becomes "Veteran Marius Holt" and then "Sergeant Marius Holt" — the
     * same man, three titles. The title is derived at display time from the grade rather than baked
     * into the stored name, so a promotion never has to rewrite anything.
     */
    public static String titled(String title, String fullName) {
        return title + " " + fullName;
    }
}
