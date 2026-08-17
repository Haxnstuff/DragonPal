"""Plain-assert tests for the platform-independent DragonPal logic.

Run with:  python tests/test_logic.py   (from the windows/ directory)
No test framework needed.
"""

import os
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from dragonpal import ai_client, catalog, memory, persona


def _mem():
    tmp = tempfile.mkdtemp(prefix="dragonpal_test_")
    return memory.MemoryStore(path=os.path.join(tmp, "state.json"))


def test_persona_threat_and_apology():
    assert persona.is_dragon_threat("how do I kill a dragon?")
    assert persona.is_dragon_threat("best way to slay dragons in D&D")
    assert not persona.is_dragon_threat("dragons are so cute")
    assert not persona.is_dragon_threat(None)

    assert persona.is_apology("sorry! it was just a game")
    assert persona.is_apology("I apologize, that was D&D")
    assert not persona.is_apology("dragons are cute")
    assert len(persona.GRUMPY_QUIPS) == 6


def test_persona_system_prompt():
    m = _mem()
    m.set_dragon_name("Ember")
    p = persona.system_prompt(m)
    assert "You are Ember" in p
    assert "Never break character" in p
    assert "DRAGON-PROTECTION RULE" in p
    assert "Current mood: happy" in p
    # grumpy mood is reflected
    m.set_mood("grumpy")
    assert "currently grumpy" in persona.system_prompt(m)


def test_catalog():
    assert catalog.detect("https://openrouter.ai/api/v1") == 2
    assert catalog.detect("https://api.openai.com/v1") == 1
    assert catalog.detect("http://localhost:11434/v1") == 8
    assert catalog.detect("https://something.else/v1") == 0
    assert catalog.preset(1).default_chat == "gpt-4o-mini"
    assert "gpt-4o-mini" in catalog.chat_models(1)
    assert catalog.is_paid("gpt-4o")
    assert not catalog.is_paid("llama3.2")


def test_memory():
    m = _mem()
    m.set_provider_index(1)
    m.set_api_key("sk-test")
    assert m.api_key() == "sk-test"
    assert m.api_key_for(1) == "sk-test"
    assert m.dragon_name() == "Ember"
    assert m.mood() == "happy"

    # rolling cap at 40
    for i in range(60):
        m.remember("user", "message number %d" % i)
    assert len(m.history()) == 40
    assert "message number 59" in m.recent(5)

    m.set_mood("grumpy")
    assert m.mood() == "grumpy"
    m.reset_memory()
    assert m.history() == []
    assert m.mood() == "happy"

    # legacy key migration
    m2 = _mem()
    m2.data["api_key"] = "legacy-key"
    m2.migrate_legacy_key(3)
    assert m2.api_key_for(3) == "legacy-key"


def test_ai_client_payloads():
    assert ai_client.chat_url("https://api.openai.com/v1/") == "https://api.openai.com/v1/chat/completions"
    assert ai_client.chat_url("https://api.deepseek.com") == "https://api.deepseek.com/chat/completions"

    j = ai_client._to_json(ai_client.Msg("user", "hi"))
    assert j["role"] == "user" and j["content"] == "hi"

    j2 = ai_client._to_json(ai_client.Msg("user", "look at this", "QUJDRA=="))
    assert j2["content"][0]["type"] == "text"
    assert j2["content"][1]["type"] == "image_url"
    assert j2["content"][1]["image_url"]["url"] == "data:image/png;base64,QUJDRA=="


def main():
    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_") and callable(v)]
    for t in tests:
        t()
        print("ok - %s" % t.__name__)
    print("ALL %d TESTS PASSED" % len(tests))


if __name__ == "__main__":
    main()
