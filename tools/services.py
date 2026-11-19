import json, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FILES = [os.path.join(ROOT, "presets", "presets.json"), os.path.join(ROOT, "app", "src", "main", "assets", "presets.json")]

CATS = [
    ("msg", "Мессенджеры", "Messengers"),
    ("social", "Соцсети", "Social networks"),
    ("video", "Видео и стримы", "Video and streams"),
    ("music", "Музыка и авторы", "Music and creators"),
    ("games", "Игры", "Games"),
    ("work", "Работа и разработка", "Work and dev"),
    ("news", "Новости и медиа", "News and media"),
    ("files", "Торренты и архивы", "Torrents and archives"),
]

NEW = [
    ("element", "Element (Matrix)", "#0DBD8B", "msg", False, ["matrix.org", "element.io", "app.element.io", "matrix-client.matrix.org"], ["https://element.io/", "https://matrix.org/"]),
    ("line", "LINE", "#06C755", "msg", False, ["line.me", "line-apps.com", "line-scdn.net", "naver.jp"], ["https://line.me/"]),
    ("viber", "Viber", "#7360F2", "msg", False, ["viber.com", "cdn.viber.com", "dl-media.viber.com", "media.cdn.viber.com", "invite.viber.com"], ["https://www.viber.com/"]),
    ("threads", "Threads", "#101010", "social", False, ["threads.net", "threads.com"], ["https://www.threads.net/"]),
    ("snapchat", "Snapchat", "#FFFC00", "social", False, ["snapchat.com", "snap.com", "sc-cdn.net", "snapkit.com", "snap-dev.net", "feelinsonice-hrd.appspot.com"], ["https://www.snapchat.com/"]),
    ("tiktok", "TikTok", "#FE2C55", "social", False, ["tiktok.com", "tiktokv.com", "tiktokcdn.com", "tiktokcdn-eu.com", "ttwstatic.com", "byteoversea.com", "ibytedtos.com", "muscdn.com"], ["https://www.tiktok.com/"]),
    ("reddit", "Reddit", "#FF4500", "social", False, ["reddit.com", "redd.it", "redditmedia.com", "redditstatic.com", "reddituploads.com", "redditinc.com"], ["https://www.reddit.com/", "https://www.redditstatic.com/shreddit/assets/favicon/192x192.png"]),
    ("tumblr", "Tumblr", "#36465D", "social", False, ["tumblr.com", "tmblr.co"], ["https://www.tumblr.com/"]),
    ("bluesky", "Bluesky", "#1185FE", "social", False, ["bsky.app", "bsky.social", "bsky.network", "blueskyweb.xyz", "bsky.chat"], ["https://bsky.app/"]),
    ("twitch", "Twitch", "#9146FF", "video", True, ["twitch.tv", "ttvnw.net", "jtvnw.net", "twitchcdn.net", "twitchsvc.net", "ext-twitch.tv", "live-video.net"], ["https://www.twitch.tv/", "https://static-cdn.jtvnw.net/ttv-static/404_preview-320x180.jpg"]),
    ("vimeo", "Vimeo", "#1AB7EA", "video", False, ["vimeo.com", "vimeocdn.com", "player.vimeo.com", "vimeo.dev"], ["https://vimeo.com/"]),
    ("dailymotion", "Dailymotion", "#0D0D0D", "video", False, ["dailymotion.com", "dmcdn.net", "dm-event.net", "dmxleo.com"], ["https://www.dailymotion.com/"]),
    ("kick", "Kick", "#53FC18", "video", False, ["kick.com", "kick.video", "files.kick.com"], ["https://kick.com/"]),
    ("patreon", "Patreon", "#FF424D", "music", False, ["patreon.com", "patreonusercontent.com", "patreon.net"], ["https://www.patreon.com/"]),
    ("medium", "Medium", "#000000", "music", False, ["medium.com", "miro.medium.com", "cdn-static-1.medium.com", "glitch.medium.com"], ["https://medium.com/"]),
    ("substack", "Substack", "#FF6719", "music", False, ["substack.com", "substackcdn.com", "substack-post-media.s3.amazonaws.com"], ["https://substack.com/"]),
    ("roblox", "Roblox", "#E2231A", "games", False, ["roblox.com", "rbxcdn.com", "rbx.com", "robloxlabs.com", "roblox.cn", "rbxtrk.com"], ["https://www.roblox.com/"]),
    ("steamcommunity", "Steam Community", "#1B2838", "games", False, ["steamcommunity.com", "steamusercontent.com", "steamuserimages-a.akamaihd.net"], ["https://steamcommunity.com/"]),
    ("github", "GitHub", "#24292F", "work", False, ["github.com", "githubusercontent.com", "githubassets.com", "github.io", "githubcopilot.com", "ghcr.io", "github.dev"], ["https://github.com/", "https://raw.githubusercontent.com/github/gitignore/main/Android.gitignore"]),
    ("meet", "Google Meet", "#00897B", "work", False, ["meet.google.com", "meet.googleapis.com", "hangouts.google.com", "clients6.google.com"], ["https://meet.google.com/"]),
    ("bbc", "BBC", "#BB1919", "news", False, ["bbc.com", "bbc.co.uk", "bbci.co.uk", "bbcrussian.com"], ["https://www.bbc.com/russian"]),
    ("dw", "DW", "#05B2FC", "news", False, ["dw.com", "dw-world.de", "dw-world.com"], ["https://www.dw.com/ru/"]),
    ("meduza", "Meduza", "#B88B58", "news", False, ["meduza.io"], ["https://meduza.io/"]),
    ("svoboda", "Радио Свобода", "#E2001A", "news", False, ["svoboda.org", "rferl.org", "currenttime.tv", "rfe.rferl.org", "gdb.rferl.org"], ["https://www.svoboda.org/"]),
    ("euronews", "Euronews", "#0B3B7A", "news", False, ["euronews.com", "static.euronews.com"], ["https://ru.euronews.com/"]),
    ("novayagazeta", "Новая газета Европа", "#1A1A1A", "news", False, ["novayagazeta.eu"], ["https://novayagazeta.eu/"]),
    ("nnmclub", "NNM-Club", "#3D6E3A", "files", False, ["nnmclub.to", "nnm-club.me", "nnm-club.ws"], ["https://nnmclub.to/"]),
    ("rutor", "Rutor", "#2E5E8C", "files", False, ["rutor.info", "rutor.is", "rutor.org"], ["https://rutor.info/"]),
    ("archive", "Internet Archive", "#6B6B6B", "files", False, ["archive.org", "web.archive.org", "archive.ph", "archive.is", "archive.today"], ["https://web.archive.org/", "https://archive.org/"]),
    ("tor", "Tor Project", "#7D4698", "files", False, ["torproject.org", "bridges.torproject.org", "dist.torproject.org"], ["https://www.torproject.org/"]),
]

EXIST = {
    "youtube": ("video", True),
    "discord": ("msg", True),
    "telegram": ("msg", True),
    "whatsapp": ("msg", True),
    "instagram": ("social", True),
    "facebook": ("social", True),
    "x": ("social", True),
    "rutracker": ("files", True),
    "linkedin": ("social", False),
    "soundcloud": ("music", True),
    "proton": ("work", False),
    "signal": ("msg", True),
}

EXTRA_DOMAINS = {
    "whatsapp": ["whatsapp.com", "whatsapp.net", "wa.me", "whatsapp.biz", "wl.co"],
    "instagram": ["instagram.com", "cdninstagram.com", "ig.me", "instagr.am"],
    "facebook": ["facebook.com", "fbcdn.net", "fb.com", "fb.me", "facebook.net", "fbsbx.com", "messenger.com", "m.me"],
    "x": ["x.com", "twitter.com", "twimg.com", "t.co", "pscp.tv", "periscope.tv"],
    "linkedin": ["linkedin.com", "licdn.com", "lnkd.in"],
    "signal": ["signal.org", "signal.art", "whispersystems.org", "signal.me", "updates.signal.org", "cdn.signal.org", "chat.signal.org", "storage.signal.org"],
    "soundcloud": ["soundcloud.com", "sndcdn.com", "soundcloud.cloud", "snd.sc"],
    "discord": ["discordcdn.com", "discord.media", "dis.gd"],
    "proton": ["proton.me", "protonmail.com", "protonmail.ch", "protonvpn.com", "pm.me", "proton.ch"],
}

ORDER = ["msg", "social", "video", "music", "games", "work", "news", "files"]


def main():
    d = json.load(open(FILES[0], encoding="utf-8"))
    by = {s["id"]: s for s in d["services"]}
    for sid, (cat, df) in EXIST.items():
        s = by[sid]
        s["cat"] = cat
        s["def"] = df
        for dom in EXTRA_DOMAINS.get(sid, []):
            if dom not in s["domains"]:
                s["domains"].append(dom)
    for sid, name, color, cat, df, doms, tests in NEW:
        by[sid] = {"id": sid, "name": name, "color": color, "cat": cat, "def": df, "domains": doms, "ipset": [], "tests": tests}
    seen = {}
    for s in by.values():
        for dom in s["domains"]:
            if dom in seen and seen[dom] != s["id"]:
                raise SystemExit(f"dup {dom} {seen[dom]} {s['id']}")
            seen[dom] = s["id"]
    first = ["telegram", "youtube", "discord", "whatsapp", "instagram", "x", "facebook", "twitch", "signal", "soundcloud", "rutracker"]
    ids = first + [k for k in by if k not in first]
    pos = {k: i for i, k in enumerate(ids)}
    ids.sort(key=lambda k: (ORDER.index(by[k]["cat"]), 0 if by[k]["def"] else 1, pos[k]))
    out = {
        "version": 2,
        "updated": "2026-11-17",
        "fakeSni": d["fakeSni"],
        "categories": [{"id": c, "ru": ru, "en": en} for c, ru, en in CATS],
        "services": [by[k] for k in ids],
        "generalTests": d["generalTests"],
        "strategies": d["strategies"],
    }
    text = json.dumps(out, ensure_ascii=False, indent=1) + "\n"
    for f in FILES:
        open(f, "w", encoding="utf-8").write(text)
    print(len(out["services"]), "services", sum(1 for s in out["services"] if s["def"]), "default", len(out["strategies"]), "strategies")


main()
