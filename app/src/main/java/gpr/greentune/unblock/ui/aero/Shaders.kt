package gpr.greentune.unblock.ui.aero

object Shaders {
    const val ORB = """
uniform float2 size;
uniform float time;
uniform float2 tilt;
uniform half4 tint;
uniform float energy;
uniform float spin;
uniform float press;
uniform float dark;

float hash(float n) { return fract(sin(n) * 43758.5453); }

float powerIcon(float2 q, float s) {
    float r = length(q);
    float ring = abs(r - 0.30 * s) - 0.045 * s;
    float ang = atan(q.x, q.y);
    float2 e = float2(sin(0.62), cos(0.62)) * 0.30 * s;
    float dCap = length(float2(abs(q.x), q.y) - e) - 0.045 * s;
    float dRing = abs(ang) < 0.62 ? dCap : ring;
    float2 b = q - float2(0.0, 0.17 * s);
    float2 dd = abs(b) - float2(0.0, 0.17 * s);
    float dLine = length(max(dd, 0.0)) + min(max(dd.x, dd.y), 0.0) - 0.045 * s;
    return min(dRing, dLine);
}

half4 main(float2 fc) {
    float m = min(size.x, size.y);
    float R = 0.74 * (1.0 - 0.05 * press);
    float2 p = (fc - size * 0.5) / (m * 0.5) / R;
    float d = length(p);
    float aa = 3.0 / (m * R);
    float3 tc = float3(tint.rgb);
    float t = time;

    float3 outCol = float3(0.0);
    float outA = 0.0;
    if (d > 1.0 - aa) {
        float g = smoothstep(1.34, 1.02, d) * exp(-(d - 1.0) * 5.0) * (0.18 + 0.55 * energy) * (0.85 + 0.15 * sin(t * 2.2));
        float rp = fract(t * 0.42);
        float ring = energy * exp(-abs(d - (1.0 + rp * 0.36)) * 40.0) * (1.0 - rp) * 0.55;
        float ang = atan(p.y, p.x);
        float sp = spin * smoothstep(0.06, 0.0, abs(d - 1.12)) * (0.5 + 0.5 * sin(ang * 1.0 - t * 7.0));
        float shadow = smoothstep(1.34, 1.1, d) * (1.0 - dark * 0.4) * 0.30 * exp(-pow((p.y - 1.05) * 6.0, 2.0)) * exp(-p.x * p.x * 3.0) * step(0.0, p.y);
        float a = clamp(g + ring + sp, 0.0, 1.0);
        outCol = tc * a + float3(1.0) * sp * 0.5;
        outA = clamp(a + shadow, 0.0, 1.0);
        outCol = outCol;
    }
    if (d > 1.0) {
        return half4(half3(outCol), half(outA));
    }

    float z = sqrt(max(0.0, 1.0 - d * d));
    float3 n = normalize(float3(p.x, -p.y, z));
    float3 L = normalize(float3(-0.35 + tilt.x * 0.6, 0.65 - tilt.y * 0.6, 0.75));
    float3 V = float3(0.0, 0.0, 1.0);
    float fres = pow(1.0 - z, 2.4);

    float2 q = p * (0.82 + 0.25 * (1.0 - z)) - tilt * 0.22;

    float level = mix(0.55, -0.05, energy) + 0.035 * sin(q.x * 5.0 + t * 2.4) + 0.02 * sin(q.x * 9.0 - t * 3.1);
    level += tilt.x * q.x * 0.55;
    float liquid = smoothstep(level - 0.02, level + 0.02, q.y);

    float3 deep = mix(tc * 0.35, tc * 0.18, dark);
    float3 grey = float3(0.55, 0.62, 0.68) * mix(1.0, 0.5, dark);
    float3 liqCol = mix(mix(grey, tc, energy) * 1.15, deep, clamp((q.y - level) * 0.7, 0.0, 1.0));
    float2 cq = q * 6.0 + float2(t * 0.6, t * 0.35);
    float c = 0.0;
    float2 ip = cq;
    for (int i = 0; i < 4; i++) {
        float tt = t * 0.7 * (1.0 - 3.5 / float(i + 1));
        ip = cq + float2(cos(tt - ip.x) + sin(tt + ip.y), sin(tt - ip.y) + cos(tt + ip.x));
        c += 1.0 / length(float2(cq.x / (sin(ip.x + tt) / 0.012), cq.y / (cos(ip.y + tt) / 0.012)));
    }
    c = c / 4.0;
    c = clamp(c, 0.0, 1.17);
    c = 1.17 - pow(c, 1.4);
    float caust = clamp(pow(abs(c), 8.0), 0.0, 1.0);
    liqCol += float3(0.75, 0.95, 1.0) * caust * (0.25 + 0.55 * energy);
    float surf = exp(-abs(q.y - level) * 60.0) * (0.6 + 0.4 * energy);
    liqCol += float3(0.85, 0.97, 1.0) * surf * 0.7;
    liqCol = clamp(liqCol, 0.0, 1.0);

    float bub = 0.0;
    for (int i = 0; i < 9; i++) {
        float fi = float(i);
        float speed = 0.18 + 0.22 * hash(fi * 3.1);
        float ph = fract(hash(fi * 7.7) + t * speed);
        float bx = (hash(fi * 1.3) - 0.5) * 1.2 + 0.05 * sin(t * 2.0 + fi);
        float by = mix(1.05, level, ph);
        float br = 0.025 + 0.035 * hash(fi * 5.9);
        float bd = length(q - float2(bx, by));
        float rim = smoothstep(br, br - 0.012, bd) - smoothstep(br - 0.012, br - 0.03, bd) * 0.7;
        float hl = smoothstep(br * 0.45, 0.0, length(q - float2(bx - br * 0.35, by - br * 0.35)));
        bub += (rim * 0.6 + hl) * smoothstep(level, level + 0.08, by) * (0.3 + 0.7 * energy);
    }
    liqCol += float3(0.9, 0.98, 1.0) * bub;

    float3 airTop = mix(float3(0.93, 0.97, 1.0), float3(0.10, 0.14, 0.18), dark);
    float3 air = mix(airTop, tc * 0.35 + airTop * 0.4, 0.25 + 0.2 * q.y);
    float3 col = mix(air, liqCol, liquid);

    float pd = powerIcon(float2(q.x, -q.y) + float2(0.0, 0.02), 1.0);
    float icon = smoothstep(0.012, -0.012, pd);
    float iconGlow = exp(-max(pd, 0.0) * 22.0) * energy;
    float3 iconCol = mix(float3(1.0), mix(float3(1.0), tc, 0.25), energy);
    float airL = (1.0 - liquid) * (1.0 - dark);
    iconCol = mix(iconCol, mix(float3(0.62, 0.70, 0.76), tc * 0.85, energy), airL);
    iconGlow *= 1.0 - 0.7 * airL;
    col = mix(col, iconCol, icon * mix(0.55, 0.95, energy));
    col += tc * iconGlow * 0.6;

    float2 gc = float2(-tilt.x * 0.10, -0.50 + tilt.y * 0.06);
    float2 ge = (p - gc) / float2(0.74, 0.44);
    float gd = length(ge);
    float gEdge = smoothstep(1.0, 1.0 - aa * 4.0, gd);
    float gFall = clamp((0.30 - (p.y - gc.y)) / 0.74, 0.0, 1.0);
    float gloss = gEdge * pow(gFall, 1.3) * mix(0.90, 0.42, dark);
    float2 sp2 = p - float2(0.42 - tilt.x * 0.15, 0.58 - tilt.y * 0.10);
    float sparkle = smoothstep(0.16, 0.0, length(sp2 * float2(1.6, 1.0))) * mix(0.35, 0.22, dark) * (0.4 + 0.6 * energy);
    float bottomGlow = smoothstep(0.15, 1.0, p.y) * smoothstep(0.45, 1.0, d) * (0.35 + 0.65 * energy);
    float rimDark = smoothstep(0.78, 1.0, d);
    float rimLight = smoothstep(0.90, 0.985, d) * smoothstep(1.0, 0.985, d);

    col = mix(col, col * mix(0.62, 0.40, dark) + tc * 0.10, rimDark * 0.6);
    col += tc * bottomGlow * 0.75;
    col += float3(1.0) * fres * 0.12;
    col += float3(1.0) * rimLight * mix(0.16, 0.14, dark);
    col = mix(col, float3(1.0), gloss);

    float edge = smoothstep(1.0, 1.0 - aa, d);
    float a = edge;
    col = col * a + outCol * (1.0 - a);
    a = a + outA * (1.0 - a);
    return half4(half3(clamp(col, 0.0, 1.0)), half(a));
}
"""

    const val BACKGROUND = """
uniform float2 size;
uniform float time;
uniform float2 tilt;
uniform half4 tint;
uniform float dark;
uniform float energy;

half4 main(float2 fc) {
    float2 uv = fc / size;
    float asp = size.x / size.y;
    float3 tc = float3(tint.rgb);
    float t = time;

    float3 topL = mix(float3(0.86, 0.96, 1.0), tc, 0.18);
    float3 midL = mix(float3(0.55, 0.85, 0.95), tc, 0.55);
    float3 botL = mix(float3(0.20, 0.50, 0.70), tc * 0.75, 0.5);
    float3 topD = mix(float3(0.02, 0.10, 0.16), tc * 0.45, 0.35);
    float3 midD = mix(float3(0.01, 0.06, 0.11), tc * 0.22, 0.30);
    float3 botD = float3(0.0, 0.015, 0.03);
    float3 top = mix(topL, topD, dark);
    float3 mid = mix(midL, midD, dark);
    float3 bot = mix(botL, botD, dark);
    float y = uv.y;
    float3 col = y < 0.45 ? mix(top, mid, y / 0.45) : mix(mid, bot, (y - 0.45) / 0.55);

    float2 cuv = float2(uv.x * asp, uv.y) * 3.2 + tilt * 0.25;
    float2 p = cuv * 6.2831 - 250.0;
    float2 ip = p;
    float c = 1.0;
    float inten = 0.005;
    for (int n = 0; n < 4; n++) {
        float tt = t * 0.22 * (1.0 - (3.5 / float(n + 1)));
        ip = p + float2(cos(tt - ip.x) + sin(tt + ip.y), sin(tt - ip.y) + cos(tt + ip.x));
        c += 1.0 / length(float2(p.x / (sin(ip.x + tt) / inten), p.y / (cos(ip.y + tt) / inten)));
    }
    c /= 4.0;
    c = clamp(c, 0.0, 1.17);
    c = 1.17 - pow(c, 1.4);
    float caust = clamp(pow(abs(c), 8.0), 0.0, 1.0);
    float fade = mix(1.0 - y * 0.85, 1.0 - y * 0.6, dark);
    col += mix(float3(1.0, 1.0, 1.0) * 0.30, tc * 0.55 + 0.05, dark) * caust * fade;

    float2 rp = float2(uv.x - 0.5 + tilt.x * 0.08, uv.y);
    float rays = 0.0;
    float ang = atan(rp.x, rp.y + 0.35);
    rays += 0.5 + 0.5 * sin(ang * 13.0 + t * 0.35);
    rays *= 0.5 + 0.5 * sin(ang * 7.0 - t * 0.21 + 1.3);
    rays *= smoothstep(1.0, 0.0, y) * smoothstep(0.0, 0.25, 1.0 - abs(rp.x) * 1.2);
    col += mix(float3(1.0), tc + 0.2, dark) * rays * mix(0.16, 0.07, dark);

    float vig = smoothstep(1.25, 0.35, length((uv - 0.5) * float2(1.0, 1.15)));
    col *= mix(1.0, mix(0.82, 0.55, dark) + 0.45 * vig, 1.0);
    col += tc * energy * 0.06 * (1.0 - y);
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""
}
