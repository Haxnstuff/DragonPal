"""Settings + rolling memory + mood, persisted to a JSON file.

This is the Windows equivalent of the Android app's SharedPreferences store.
"""

import json
import os


class MemoryStore:
    def __init__(self, path=None):
        if path is None:
            path = os.path.join(os.path.expanduser("~"), ".dragonpal", "state.json")
        self.path = path
        self.data = self._load()

    def _load(self):
        if os.path.exists(self.path):
            try:
                with open(self.path, "r", encoding="utf-8") as f:
                    data = json.load(f)
                return data if isinstance(data, dict) else {}
            except (ValueError, OSError):
                return {}
        return {}

    def _save(self):
        try:
            os.makedirs(os.path.dirname(self.path), exist_ok=True)
            with open(self.path, "w", encoding="utf-8") as f:
                json.dump(self.data, f)
        except OSError:
            pass

    def _get(self, key, default):
        return self.data.get(key, default)

    def _set(self, key, value):
        self.data[key] = value
        self._save()

    # provider
    def provider_index(self):
        return self._get("provider", 0)

    def set_provider_index(self, i):
        self._set("provider", i)

    # api keys (per provider slot, like the Android version)
    def api_key(self):
        return self.api_key_for(self.provider_index())

    def set_api_key(self, v):
        self.set_api_key_for(self.provider_index(), v)

    def api_key_for(self, provider):
        return self._get("key_" + str(provider), "")

    def set_api_key_for(self, provider, v):
        self._set("key_" + str(provider), v)

    def migrate_legacy_key(self, provider):
        legacy = self._get("api_key", "")
        if legacy and not self.api_key_for(provider):
            self.data["key_" + str(provider)] = legacy
            self.data.pop("api_key", None)
            self._save()

    # last error
    def last_error(self):
        return self._get("last_error", "")

    def set_last_error(self, e):
        self._set("last_error", e)

    def clear_last_error(self):
        self.data.pop("last_error", None)
        self._save()

    # base url / model / vision
    def base_url(self):
        return self._get("base_url", "https://api.openai.com/v1")

    def set_base_url(self, v):
        self._set("base_url", v)

    def model(self):
        return self._get("model", "gpt-4o-mini")

    def set_model(self, v):
        self._set("model", v)

    def vision_model(self):
        return self._get("vision_model", "gpt-4o-mini")

    def set_vision_model(self, v):
        self._set("vision_model", v)

    def vision_base_url(self):
        return self._get("vision_base_url", "")

    def set_vision_base_url(self, v):
        self._set("vision_base_url", v)

    def effective_vision_base_url(self):
        v = self.vision_base_url()
        return v if v else self.base_url()

    def vision_api_key(self):
        return self._get("vision_api_key", "")

    def set_vision_api_key(self, v):
        self._set("vision_api_key", v)

    def effective_vision_api_key(self):
        v = self.vision_api_key()
        return v if v else self.api_key()

    # dragon name / max tokens / roaming / mood
    def dragon_name(self):
        return self._get("name", "Ember")

    def set_dragon_name(self, v):
        self._set("name", v)

    def max_tokens(self):
        return self._get("max_tokens", 400)

    def roaming(self):
        return self._get("roaming", False)

    def set_roaming(self, v):
        self._set("roaming", v)

    def mood(self):
        return self._get("mood", "happy")

    def set_mood(self, v):
        self._set("mood", v)

    # rolling memory
    def remember(self, role, text):
        a = self.history()
        a.append({"role": role, "text": text})
        if len(a) > 40:
            a = a[-40:]
        self.data["memory"] = a
        self._save()

    def history(self):
        return list(self._get("memory", []))

    def recent(self, n):
        a = self.history()
        start = max(0, len(a) - n)
        lines = []
        for item in a[start:]:
            t = str(item.get("text", ""))
            if len(t) > 160:
                t = t[:160] + "..."
            lines.append(str(item.get("role", "")) + ": " + t)
        if not lines:
            return ""
        return "\n".join(lines) + "\n"

    def reset_memory(self):
        self.data.pop("memory", None)
        self.data.pop("mood", None)
        self._save()
