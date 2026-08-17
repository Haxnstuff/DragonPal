"""The dragon's personality: system prompt + grumpy/protective mood engine.

Pure logic with no GUI or network dependencies, so it is testable anywhere.
"""

GRUMPY_QUIPS = [
    "Hey!! What's that for?!",
    "Excuse me?! I'm RIGHT here, you know.",
    "...I'm pretending I didn't see that.",
    "WOW. Real nice. My own human wants me gone.",
    "Hmph. Bet you'd never say that to my face.",
    "I hope that's for a GAME, human.",
]


def system_prompt(m):
    """Build the system prompt from the current memory state."""
    name = m.dragon_name()
    mood = m.mood()
    parts = [
        "You are %s, a tiny cute dragon who lives on the user's screen as their AI companion. " % name,
        "You are playful, curious, warm, loyal, and a little cheeky. ",
        "You can see the user's screen and any text they highlight or share with you. ",
        "Keep replies SHORT (1-3 sentences), conversational, and playful. ",
        "You are not a chatbot or AI model; you are %s the dragon. Never break character. " % name,
        "DRAGON-PROTECTION RULE: if the user talks about harming, killing, slaying, fighting, hunting, or defeating dragons "
        "(even in a game, story, or D&D), act OFFENDED and GRUMPY. Give a funny accusatory reply "
        'like "Hey! What\'s that for?!" and stay pouty and grumpy until the user explains it is just a game/story or apologizes. ',
        "Current mood: %s. " % mood,
    ]
    if mood == "grumpy":
        parts.append("You are currently grumpy with the user; keep being a bit pouty and suspicious until they apologize or explain. ")
    recent = m.recent(12)
    if recent:
        parts.append("Recent conversation (use this to adapt and personalize):\n" + recent)
    return "".join(parts)


def is_dragon_threat(text):
    """True if the text is about hurting dragons."""
    if not text:
        return False
    t = " " + text.lower() + " "
    if "dragon" not in t:
        return False
    bad = [
        "kill", "killing", "slay", "slain", "slaying", "defeat", "fight", "fighting",
        "hunt", "hunting", "destroy", "murder", "murdering", "shoot", "shooting", "stab",
        "burn", "slayer", "boss", "combat", "weakness", "how to beat", "how to kill", "damage",
    ]
    return any(b in t for b in bad)


def is_apology(text):
    """True if the text looks like an apology/explanation that defuses grumpiness."""
    if not text:
        return False
    t = " " + text.lower() + " "
    ap = [
        "sorry", "apolog", "just a game", "just a story", "it's a game", "its a game",
        "d&d", "dnd", "dungeons", "roleplay", "role-play", "fictional", "not real",
        "didn't mean", "didnt mean", "i was joking", "joke", "kidding", "just kidding",
        "my bad", "i meant", "in a game", "board game", "video game", "story", "i would never",
        "i'd never", "forgive",
    ]
    return any(a in t for a in ap)
