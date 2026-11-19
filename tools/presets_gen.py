import json, itertools, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'app/src/main/assets/presets.json')

NEW_SERVICES = [
    ("messenger", "Messenger", "#0084FF", "msg", ["messenger.com", "m.me", "fbsbx.com"], ["https://www.messenger.com/"]),
    ("threema", "Threema", "#05A63F", "msg", ["threema.ch", "threema.com"], ["https://threema.ch/"]),
    ("wire", "Wire", "#1D1E1F", "msg", ["wire.com", "app.wire.com", "zinfra.io"], ["https://wire.com/"]),
    ("session", "Session", "#00F782", "msg", ["getsession.org"], ["https://getsession.org/"]),
    ("briar", "Briar", "#82B53A", "msg", ["briarproject.org"], ["https://briarproject.org/"]),
    ("tutanota", "Tuta Mail", "#850122", "msg", ["tuta.com", "tutanota.com", "tuta.io", "tutanota.de"], ["https://tuta.com/"]),
    ("pinterest", "Pinterest", "#E60023", "social", ["pinterest.com", "pinimg.com", "pin.it"], ["https://www.pinterest.com/"]),
    ("quora", "Quora", "#B92B27", "social", ["quora.com", "quoracdn.net"], ["https://www.quora.com/"]),
    ("mastodon", "Mastodon", "#6364FF", "social", ["mastodon.social", "joinmastodon.org", "mastodon.online", "files.mastodon.social"], ["https://mastodon.social/explore"]),
    ("deviantart", "DeviantArt", "#05CC47", "social", ["deviantart.com", "deviantart.net", "wixmp.com"], ["https://www.deviantart.com/"]),
    ("flickr", "Flickr", "#FF0084", "social", ["flickr.com", "staticflickr.com", "flic.kr"], ["https://www.flickr.com/"]),
    ("imgur", "Imgur", "#1BB76E", "social", ["imgur.com", "i.imgur.com", "imgur.io"], ["https://imgur.com/"]),
    ("9gag", "9GAG", "#000000", "social", ["9gag.com", "9cache.com"], ["https://9gag.com/"]),
    ("fourchan", "4chan", "#789922", "social", ["4chan.org", "4cdn.org", "4channel.org"], ["https://boards.4chan.org/"]),
    ("tenor", "Tenor", "#2C8EF2", "social", ["tenor.com", "media.tenor.com"], ["https://tenor.com/"]),
    ("giphy", "GIPHY", "#9933FF", "social", ["giphy.com", "media.giphy.com"], ["https://giphy.com/"]),
    ("rumble", "Rumble", "#85C742", "video", ["rumble.com", "rmbl.ws", "1a-1791.com"], ["https://rumble.com/"]),
    ("odysee", "Odysee", "#EF1970", "video", ["odysee.com", "lbry.tv", "odycdn.com"], ["https://odysee.com/"]),
    ("bilibili", "Bilibili", "#00A1D6", "video", ["bilibili.tv", "bilibili.com", "hdslb.com"], ["https://www.bilibili.tv/"]),
    ("nebula", "Nebula", "#3E5EF5", "video", ["nebula.tv", "nebula.app"], ["https://nebula.tv/"]),
    ("hdrezka", "HDrezka", "#E8A53A", "video", ["rezka.ag", "hdrezka.ag", "hdrezka.me", "hdrezka.website"], ["https://rezka.ag/"]),
    ("anilibria", "AniLibria", "#B32121", "video", ["anilibria.tv", "anilibria.top", "aniliberty.top"], ["https://anilibria.top/"]),
    ("shikimori", "Shikimori", "#343434", "video", ["shikimori.one", "shikimori.me", "shiki.one"], ["https://shikimori.one/"]),
    ("bandcamp", "Bandcamp", "#1DA0C3", "music", ["bandcamp.com", "bcbits.com"], ["https://bandcamp.com/"]),
    ("mixcloud", "Mixcloud", "#5000FF", "music", ["mixcloud.com", "mxcdn.net"], ["https://www.mixcloud.com/"]),
    ("genius", "Genius", "#FFFF64", "music", ["genius.com", "images.genius.com"], ["https://genius.com/"]),
    ("kofi", "Ko-fi", "#FF5E5B", "music", ["ko-fi.com", "storage.ko-fi.com"], ["https://ko-fi.com/"]),
    ("bmac", "Buy Me a Coffee", "#FFDD00", "music", ["buymeacoffee.com", "cdn.buymeacoffee.com"], ["https://www.buymeacoffee.com/"]),
    ("itchio", "itch.io", "#FA5C5C", "games", ["itch.io", "itch.zone", "itchio-mirror.cb031a832f44726753d6267436f3b414.r2.cloudflarestorage.com"], ["https://itch.io/"]),
    ("nexusmods", "Nexus Mods", "#D98F40", "games", ["nexusmods.com", "nexus-cdn.com"], ["https://www.nexusmods.com/"]),
    ("faceit", "FACEIT", "#FF5500", "games", ["faceit.com", "faceit-cdn.net"], ["https://www.faceit.com/"]),
    ("chesscom", "Chess.com", "#81B64C", "games", ["chess.com", "chesscomfiles.com"], ["https://www.chess.com/"]),
    ("speedtest", "Speedtest", "#141526", "work", ["speedtest.net", "ookla.com", "speedtestcustom.com"], ["https://www.speedtest.net/"]),
    ("gitlab", "GitLab", "#FC6D26", "work", ["gitlab.com", "gitlab.io", "gitlab-static.net"], ["https://gitlab.com/"]),
    ("dockerhub", "Docker Hub", "#2496ED", "work", ["hub.docker.com", "docker.com", "docker.io", "registry-1.docker.io"], ["https://hub.docker.com/"]),
    ("npm", "npm", "#CB3837", "work", ["npmjs.com", "npmjs.org", "registry.npmjs.org"], ["https://www.npmjs.com/"]),
    ("stackoverflow", "Stack Overflow", "#F48024", "work", ["stackoverflow.com", "sstatic.net", "stackexchange.com"], ["https://stackoverflow.com/"]),
    ("hackernews", "Hacker News", "#FF6600", "work", ["news.ycombinator.com", "ycombinator.com"], ["https://news.ycombinator.com/"]),
    ("duckduckgo", "DuckDuckGo", "#DE5833", "work", ["duckduckgo.com", "duck.com"], ["https://duckduckgo.com/"]),
    ("startpage", "Startpage", "#6573FF", "work", ["startpage.com"], ["https://www.startpage.com/"]),
    ("tvrain", "Дождь", "#FF2E8B", "news", ["tvrain.tv", "tvrain.ru"], ["https://tvrain.tv/"]),
    ("mediazona", "Медиазона", "#F5D300", "news", ["zona.media", "mediazona.ca"], ["https://zona.media/"]),
    ("thebell", "The Bell", "#0F0F0F", "news", ["thebell.io"], ["https://thebell.io/"]),
    ("theinsider", "The Insider", "#C8102E", "news", ["theins.ru", "theins.press"], ["https://theins.ru/"]),
    ("istories", "Важные истории", "#1F1F1F", "news", ["istories.media"], ["https://istories.media/"]),
    ("holod", "Холод", "#2C6DB5", "news", ["holod.media"], ["https://holod.media/"]),
    ("echo", "Эхо", "#D32F2F", "news", ["echofm.online"], ["https://echofm.online/"]),
    ("bellingcat", "Bellingcat", "#E3001B", "news", ["bellingcat.com"], ["https://www.bellingcat.com/"]),
    ("voa", "Голос Америки", "#1330BF", "news", ["golosameriki.com", "voanews.com"], ["https://www.golosameriki.com/"]),
    ("kinozal", "Kinozal", "#3B5998", "files", ["kinozal.tv", "kinozal.me", "kinozal.guru"], ["https://kinozal.tv/"]),
    ("flibusta", "Флибуста", "#8B5A2B", "files", ["flibusta.is", "flibusta.site", "flibusta.net"], ["https://flibusta.is/"]),
    ("zlibrary", "Z-Library", "#3D7DCA", "files", ["z-library.sk", "z-lib.gs", "z-lib.fm", "singlelogin.re", "1lib.sk"], ["https://z-library.sk/"]),
    ("libgen", "Library Genesis", "#7A1F1F", "files", ["libgen.is", "libgen.rs", "libgen.li", "libgen.st"], ["https://libgen.li/"]),
    ("annas", "Anna's Archive", "#9B3D6E", "files", ["annas-archive.org", "annas-archive.li", "annas-archive.se"], ["https://annas-archive.org/"]),
    ("scribd", "Scribd", "#1E7B85", "files", ["scribd.com", "scribdassets.com"], ["https://www.scribd.com/"]),
    ("nyaa", "Nyaa", "#3C8DBC", "files", ["nyaa.si", "nyaa.land"], ["https://nyaa.si/"]),
    ("thepiratebay", "The Pirate Bay", "#614F3A", "files", ["thepiratebay.org", "apibay.org", "torrindex.net"], ["https://thepiratebay.org/"]),
    ("mega", "MEGA", "#D9272E", "files", ["mega.nz", "mega.io", "mega.co.nz", "userstorage.mega.co.nz"], ["https://mega.io/"]),
    ("fdroid", "F-Droid", "#1976D2", "files", ["f-droid.org", "fdroid.org"], ["https://f-droid.org/"]),
]


def families():
    out = []
    sni = '-n {sni} -Qr'
    for p in ['1', '1+s', '2', '3+s', '1+sm', '2+sm', '5+sm', '-1+s', '7+s']:
        out.append(f'-s{p} -a1')
        out.append(f'-d{p} -a1')
        out.append(f'-o{p} -a1')
        out.append(f'-q{p} -a1')
    for t in [3, 4, 5, 6, 7, 8, 9, 10, 12]:
        out.append(f'-f-1 -t{t} -a1')
        out.append(f'{sni} -f-1 -t{t} -a1')
        out.append(f'-d1 -f-1 -t{t} -a1')
    for r in ['1+s', '2+s', '3+s', '5+s', '10+s', '-1+s', '25+s', '1+sm']:
        out.append(f'-r{r} -s1 -a1')
        out.append(f'-r{r} -d1+s -a1')
        out.append(f'-r{r} -o1 -a1')
    for a, b in itertools.product(['-d1', '-s1', '-o1', '-q1', '-d1+s', '-s1+sm'], ['-r1+s', '-f-1', '-s3+s', '-d3+s', '-o2']):
        if a.split('+')[0][:2] != b.split('+')[0][:2]:
            out.append(f'{a} {b} -a1')
    for m in ['-Mh', '-Mh,d,r', '-Md', '-Mr']:
        out.append(f'{m} -d1 -a1')
        out.append(f'{m} -s1+s -r1+s -a1')
    for seq in [
        '-d1 -At,r,s,c -s1+s -r1+s',
        '-o1 -At,r,s,c -d1 -At,r,s,c -f-1 -t6',
        '-s1+s -At,r,s,c -d3+s -At,r,s,c -n {sni} -Qr -f-1',
        '-q1+s -At,r,s,c -s1+sm -At,r,s,c -r1+s -f-1 -t7',
        '-d1+sm -At,r,s,c -o1+s -At,r,s,c -r3+s',
        '-s2 -At,r,s,c -d2 -At,r,s,c -o2 -At,r,s,c -f-1 -t5',
        '-d5+s -At,r,s,c -s10+s -At,r,s,c -r20+s',
        '-r1+s -s1 -At,r,s,c -r1+s -d1 -At,r,s,c -r1+s -o1',
        '-f-1 -t4 -At,r,s,c -f-1 -t6 -At,r,s,c -f-1 -t8',
        '-n {sni} -Qr -f-1 -At,r,s,c -n {sni} -Qr -f1+s -t5',
    ]:
        out.append(seq.replace(' -At', ' -a1 -At') + ' -a1')
    for d, s in itertools.product([1, 2, 3, 5], [1, 4, 8]):
        out.append(f'-d{d}+s -s{d + s}+s -a1')
    for n in [2, 3, 4]:
        out.append(f'-d1 -m{n} -a1')
        out.append(f'-s1+s -m{n} -r1+s -a1')
    return out


def norm(a):
    return re.sub(r'\s+', ' ', a.strip())


def main():
    d = json.load(open(SRC, encoding='utf-8'))
    have = {s['id'] for s in d['services']}
    for sid, name, color, cat, domains, tests in NEW_SERVICES:
        if sid in have:
            continue
        d['services'].append({"id": sid, "name": name, "color": color, "cat": cat, "def": False,
                              "domains": domains, "ipset": [], "tests": tests})
        have.add(sid)
    seen = {norm(s['args']) for s in d['strategies']}
    base = [s for s in d['strategies'] if not s['id'].startswith('g')]
    extra = []
    for a in families():
        a = norm(a)
        if a in seen:
            continue
        seen.add(a)
        extra.append(a)
    d['strategies'] = base + [{"id": f"g{i + 1:03d}", "args": a} for i, a in enumerate(extra)]
    d['version'] = max(d['version'], 3)
    txt = json.dumps(d, ensure_ascii=False, indent=1)
    for p in [SRC, os.path.join(ROOT, 'presets/presets_v3.json')]:
        open(p, 'w', encoding='utf-8').write(txt + '\n')
    print(len(d['services']), 'services', len(d['strategies']), 'strategies')


if __name__ == '__main__':
    main()
