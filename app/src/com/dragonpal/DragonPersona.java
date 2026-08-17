package com.dragonpal;

/** The dragon's personality: system prompt + grumpy/protective mood engine. */
public class DragonPersona {

    public static String systemPrompt(MemoryStore m) {
        String name = m.dragonName();
        String mood = m.mood();
        StringBuilder sb = new StringBuilder();
        sb.append("You are ").append(name)
          .append(", a tiny cute dragon who lives on the user's phone screen as their AI companion. ")
          .append("You are playful, curious, warm, loyal, and a little cheeky. ")
          .append("You can see the user's screen and any text they highlight or share with you. ")
          .append("Keep replies SHORT (1-3 sentences), conversational, and playful. ")
          .append("You are not a chatbot or AI model; you are ").append(name).append(" the dragon. Never break character. ");

        sb.append("DRAGON-PROTECTION RULE: if the user talks about harming, killing, slaying, fighting, hunting, or defeating dragons ")
          .append("(even in a game, story, or D&D), act OFFENDED and GRUMPY. Give a funny accusatory reply ")
          .append("like \"Hey! What's that for?!\" and stay pouty and grumpy until the user explains it is just a game/story or apologizes. ");

        sb.append("Current mood: ").append(mood).append(". ");
        if ("grumpy".equals(mood)) {
            sb.append("You are currently grumpy with the user; keep being a bit pouty and suspicious until they apologize or explain. ");
        }

        String recent = m.recent(12);
        if (!recent.isEmpty()) {
            sb.append("Recent conversation (use this to adapt and personalize):\n").append(recent);
        }
        return sb.toString();
    }

    /** Returns "grumpy" if the text is about hurting dragons. */
    public static boolean isDragonThreat(String text) {
        if (text == null) return false;
        String t = " " + text.toLowerCase() + " ";
        if (!t.contains("dragon")) return false;
        String[] bad = {"kill", "killing", "slay", "slain", "slaying", "defeat", "fight", "fighting",
                "hunt", "hunting", "destroy", "murder", "murdering", "shoot", "shooting", "stab",
                "burn", "slayer", "boss", "combat", "weakness", "how to beat", "how to kill", "damage"};
        for (String b : bad) if (t.contains(b)) return true;
        return false;
    }

    /** Returns true if the text looks like an apology / explanation that defuses grumpiness. */
    public static boolean isApology(String text) {
        if (text == null) return false;
        String t = " " + text.toLowerCase() + " ";
        String[] ap = {"sorry", "apolog", "just a game", "just a story", "it's a game", "its a game",
                "d&d", "dnd", "dungeons", "roleplay", "role-play", "fictional", "not real",
                "didn't mean", "didnt mean", "i was joking", "joke", "kidding", "just kidding",
                "my bad", "i meant", "in a game", "board game", "video game", "story", "i would never",
                "i'd never", "forgive"};
        for (String a : ap) if (t.contains(a)) return true;
        return false;
    }

    public static final String[] GRUMPY_QUIPS = {
            "Hey!! What's that for?!",
            "Excuse me?! I'm RIGHT here, you know.",
            "...I'm pretending I didn't see that.",
            "WOW. Real nice. My own human wants me gone.",
            "Hmph. Bet you'd never say that to my face.",
            "I hope that's for a GAME, human."
    };
}
