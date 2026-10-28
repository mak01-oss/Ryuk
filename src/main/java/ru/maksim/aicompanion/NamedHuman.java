package ru.maksim.aicompanion;

/** One of the story's own people (L, Watari, the agents, the mafia): a fixed true name, maybe an alias, maybe a notebook. */
public interface NamedHuman {
    String trueName();

    /** Game time this person's life runs out. */
    long dies();

    /** A Death Note owner: the eyes can't see their lifespan. */
    boolean ownsNote();
}
