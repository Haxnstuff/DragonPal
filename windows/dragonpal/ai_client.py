"""Generic OpenAI-compatible chat + vision client using only the stdlib."""

import base64
import json
import urllib.error
import urllib.request

import dragonpal.persona as persona


class AiError(Exception):
    pass


class Msg:
    def __init__(self, role, content, image_base64=None):
        self.role = role
        self.content = content
        self.image_base64 = image_base64


def _system(m):
    return {"role": "system", "content": persona.system_prompt(m)}


def _to_json(m):
    if m.image_base64 is not None:
        return {
            "role": m.role,
            "content": [
                {"type": "text", "text": m.content},
                {"type": "image_url", "image_url": {"url": "data:image/png;base64," + m.image_base64}},
            ],
        }
    return {"role": m.role, "content": m.content}


def chat_url(base):
    return base.rstrip("/") + "/chat/completions"


def _post(base, key, body):
    url = chat_url(base)
    data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    if key:
        req.add_header("Authorization", "Bearer " + key)
    try:
        with urllib.request.urlopen(req, timeout=90) as resp:
            payload = resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8", "replace")
        raise AiError("API error %d: %s" % (e.code, err_body[:300]))
    except Exception as e:
        raise AiError(str(e))
    try:
        obj = json.loads(payload)
        text = obj["choices"][0]["message"]["content"]
        return (text or "").strip()
    except (ValueError, KeyError, IndexError, TypeError) as e:
        raise AiError("Bad API response: " + str(e))


def chat(mem, messages, model_override=None):
    arr = [_system(mem)]
    for m in messages:
        arr.append(_to_json(m))
    body = {"messages": arr}
    body["model"] = model_override or mem.model()
    body["temperature"] = 0.8
    body["max_tokens"] = mem.max_tokens()
    return _post(mem.base_url(), mem.api_key(), body)


def test(mem):
    body = {
        "model": mem.model(),
        "messages": [{"role": "user", "content": "ping"}],
        "max_tokens": 5,
    }
    return _post(mem.base_url(), mem.api_key(), body)


def vision(mem, png_bytes, prompt):
    b64 = base64.b64encode(png_bytes).decode("ascii")
    arr = [_system(mem), _to_json(Msg("user", prompt, b64))]
    body = {"messages": arr, "model": mem.vision_model(), "max_tokens": 300}
    return _post(mem.effective_vision_base_url(), mem.effective_vision_api_key(), body)
